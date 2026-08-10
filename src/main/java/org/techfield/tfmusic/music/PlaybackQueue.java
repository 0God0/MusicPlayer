package org.techfield.tfmusic.music;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class PlaybackQueue {
    private PlaybackQueue() {
    }

    public record AdvanceResult(PlaybackCursor cursor, boolean continuePlaying) {
    }

    public static PlaybackCursor initial(ItemStack media) {
        MusicTrack discTrack = MusicTrack.fromDisc(media).orElse(null);
        MusicPlaylist playlist = MusicPlaylist.fromStack(media).orElse(null);
        return initial(discTrack, playlist);
    }

    public static PlaybackCursor initial(MusicPlaylist playlist) {
        return initial(null, playlist);
    }

    private static PlaybackCursor initial(MusicTrack discTrack, MusicPlaylist playlist) {
        if (discTrack != null) {
            return new PlaybackCursor(null, discTrack, 0, 0L, PlaybackMode.SEQUENTIAL, List.of(), 0);
        }

        if (playlist == null || playlist.entries().isEmpty()) {
            PlaybackMode mode = playlist == null ? PlaybackMode.SEQUENTIAL : playlist.mode();
            return new PlaybackCursor(null, null, 0, 0L, mode, List.of(), 0);
        }
        if (playlist.mode() == PlaybackMode.SHUFFLE) {
            List<UUID> order = shuffledIds(playlist, null);
            return atEntry(playlist, order.get(0), PlaybackMode.SHUFFLE, order, 0);
        }
        return atEntry(playlist, playlist.entries().get(0).id(), playlist.mode(), List.of(), 0);
    }

    public static PlaybackCursor synchronize(ItemStack media, PlaybackCursor cursor) {
        MusicTrack discTrack = MusicTrack.fromDisc(media).orElse(null);
        MusicPlaylist playlist = MusicPlaylist.fromStack(media).orElse(null);
        return synchronize(discTrack, playlist, cursor);
    }

    public static PlaybackCursor synchronize(MusicPlaylist playlist, PlaybackCursor cursor) {
        return synchronize(null, playlist, cursor);
    }

    private static PlaybackCursor synchronize(
            MusicTrack discTrack,
            MusicPlaylist playlist,
            PlaybackCursor cursor
    ) {
        if (discTrack != null) {
            if (cursor.track() == null || cursor.entryId() != null || !cursor.track().equals(discTrack)) {
                return initial(discTrack, playlist);
            }
            return new PlaybackCursor(
                    null,
                    discTrack,
                    0,
                    cursor.progressMs(),
                    PlaybackMode.SEQUENTIAL,
                    List.of(),
                    0
            );
        }

        if (playlist == null) {
            return initial(null, null);
        }
        if (cursor.track() == null) {
            return initial(null, playlist);
        }

        int currentIndex = playlist.indexOf(cursor.entryId());
        int fallbackIndex = currentIndex >= 0 ? currentIndex : cursor.fallbackIndex();
        List<UUID> activeOrder = playlist.entries().stream().map(PlaylistEntry::id).toList();
        List<UUID> sequentialOrder;
        List<UUID> knownEntries;
        MusicTrack currentTrack = cursor.track();
        if (currentIndex >= 0) {
            sequentialOrder = List.copyOf(activeOrder.subList(currentIndex + 1, activeOrder.size()));
            knownEntries = activeOrder;
            MusicTrack storedTrack = playlist.entries().get(currentIndex).track();
            if (!storedTrack.equals(currentTrack)) {
                currentTrack = storedTrack;
                cursor = cursor.withProgress(0L);
            }
        } else {
            Set<UUID> pending = new HashSet<>(cursor.sequentialOrder());
            if (cursor.knownEntries().isEmpty()) {
                int start = Math.min(fallbackIndex, activeOrder.size());
                pending.addAll(activeOrder.subList(start, activeOrder.size()));
            } else {
                Set<UUID> known = new HashSet<>(cursor.knownEntries());
                for (UUID id : activeOrder) {
                    if (!known.contains(id)) {
                        pending.add(id);
                    }
                }
            }
            sequentialOrder = activeOrder.stream().filter(pending::contains).toList();
            knownEntries = activeOrder;
            fallbackIndex = sequentialOrder.isEmpty()
                    ? activeOrder.size()
                    : playlist.indexOf(sequentialOrder.get(0));
        }

        List<UUID> shuffleOrder = cursor.shuffleOrder();
        int shuffleCursor = cursor.shuffleCursor();
        if (playlist.mode() == PlaybackMode.SHUFFLE) {
            if (cursor.observedMode() != PlaybackMode.SHUFFLE) {
                ShuffleState initialized = initializeShuffle(playlist, cursor.entryId());
                shuffleOrder = initialized.order();
                shuffleCursor = initialized.cursor();
            } else {
                ShuffleState reconciled = reconcileShuffle(playlist, cursor);
                shuffleOrder = reconciled.order();
                shuffleCursor = reconciled.cursor();
            }
        }

        return new PlaybackCursor(
                cursor.entryId(),
                currentTrack,
                fallbackIndex,
                cursor.progressMs(),
                playlist.mode(),
                shuffleOrder,
                shuffleCursor,
                sequentialOrder,
                knownEntries
        );
    }

    public static AdvanceResult advance(ItemStack media, PlaybackCursor source, boolean manual) {
        MusicTrack discTrack = MusicTrack.fromDisc(media).orElse(null);
        MusicPlaylist playlist = MusicPlaylist.fromStack(media).orElse(null);
        return advance(discTrack, playlist, source, manual);
    }

    public static AdvanceResult advance(MusicPlaylist playlist, PlaybackCursor source, boolean manual) {
        return advance(null, playlist, source, manual);
    }

    private static AdvanceResult advance(
            MusicTrack discTrack,
            MusicPlaylist playlist,
            PlaybackCursor source,
            boolean manual
    ) {
        PlaybackCursor cursor = synchronize(discTrack, playlist, source);
        if (discTrack != null) {
            return new AdvanceResult(initial(discTrack, playlist), false);
        }

        if (playlist == null || playlist.entries().isEmpty()) {
            return new AdvanceResult(initial(null, playlist), false);
        }
        if (playlist.mode() == PlaybackMode.SHUFFLE) {
            return advanceShuffle(playlist, cursor);
        }

        int currentIndex = playlist.indexOf(cursor.entryId());
        if (!manual && playlist.mode() == PlaybackMode.REPEAT_ONE && currentIndex >= 0) {
            return new AdvanceResult(atEntry(
                    playlist,
                    cursor.entryId(),
                    playlist.mode(),
                    cursor.shuffleOrder(),
                    cursor.shuffleCursor()
            ), true);
        }

        int queuedIndex = cursor.sequentialOrder().isEmpty()
                ? -1
                : playlist.indexOf(cursor.sequentialOrder().get(0));
        int nextIndex = currentIndex >= 0
                ? currentIndex + 1
                : queuedIndex >= 0 ? queuedIndex : cursor.fallbackIndex();
        if (nextIndex >= playlist.entries().size()) {
            if (playlist.mode() == PlaybackMode.REPEAT_ALL || playlist.mode() == PlaybackMode.REPEAT_ONE) {
                nextIndex = 0;
            } else {
                return new AdvanceResult(initial(null, playlist), false);
            }
        }
        return new AdvanceResult(atEntry(
                playlist,
                playlist.entries().get(nextIndex).id(),
                playlist.mode(),
                cursor.shuffleOrder(),
                cursor.shuffleCursor()
        ), true);
    }

    public static PlaybackCursor previous(ItemStack media, PlaybackCursor source) {
        MusicTrack discTrack = MusicTrack.fromDisc(media).orElse(null);
        MusicPlaylist playlist = MusicPlaylist.fromStack(media).orElse(null);
        return previous(discTrack, playlist, source);
    }

    public static PlaybackCursor previous(MusicPlaylist playlist, PlaybackCursor source) {
        return previous(null, playlist, source);
    }

    private static PlaybackCursor previous(
            MusicTrack discTrack,
            MusicPlaylist playlist,
            PlaybackCursor source
    ) {
        PlaybackCursor cursor = synchronize(discTrack, playlist, source);
        if (playlist == null || playlist.entries().isEmpty()) {
            return initial(discTrack, playlist);
        }
        if (playlist.mode() == PlaybackMode.SHUFFLE) {
            int previousCursor = Math.max(0, cursor.shuffleCursor() - 1);
            if (previousCursor < cursor.shuffleOrder().size()) {
                UUID previousId = cursor.shuffleOrder().get(previousCursor);
                if (playlist.indexOf(previousId) >= 0) {
                    return atEntry(
                            playlist,
                            previousId,
                            PlaybackMode.SHUFFLE,
                            cursor.shuffleOrder(),
                            previousCursor
                    );
                }
            }
            return cursor.withProgress(0L);
        }

        int currentIndex = playlist.indexOf(cursor.entryId());
        int previousIndex = currentIndex > 0 ? currentIndex - 1 : 0;
        if (currentIndex < 0) {
            previousIndex = Math.max(0, Math.min(playlist.entries().size() - 1, cursor.fallbackIndex() - 1));
        } else if (currentIndex == 0 && playlist.mode() == PlaybackMode.REPEAT_ALL) {
            previousIndex = playlist.entries().size() - 1;
        }
        return atEntry(
                playlist,
                playlist.entries().get(previousIndex).id(),
                playlist.mode(),
                cursor.shuffleOrder(),
                cursor.shuffleCursor()
        );
    }

    private static AdvanceResult advanceShuffle(
            MusicPlaylist playlist,
            PlaybackCursor cursor
    ) {
        int nextCursor = cursor.shuffleCursor() + 1;
        if (nextCursor < cursor.shuffleOrder().size()) {
            UUID nextId = cursor.shuffleOrder().get(nextCursor);
            if (playlist.indexOf(nextId) >= 0) {
                return new AdvanceResult(atEntry(
                        playlist,
                        nextId,
                        PlaybackMode.SHUFFLE,
                        cursor.shuffleOrder(),
                        nextCursor
                ), true);
            }
        }

        List<UUID> nextOrder = shuffledIds(playlist, cursor.entryId());
        if (nextOrder.isEmpty()) {
            return new AdvanceResult(initial(null, playlist), false);
        }
        return new AdvanceResult(atEntry(
                playlist,
                nextOrder.get(0),
                PlaybackMode.SHUFFLE,
                nextOrder,
                0
        ), true);
    }

    private static PlaybackCursor atEntry(
            MusicPlaylist playlist,
            UUID entryId,
            PlaybackMode mode,
            List<UUID> shuffleOrder,
            int shuffleCursor
    ) {
        int index = playlist.indexOf(entryId);
        if (index < 0) {
            return new PlaybackCursor(null, null, 0, 0L, mode, shuffleOrder, shuffleCursor);
        }
        PlaylistEntry entry = playlist.entries().get(index);
        List<UUID> sequentialOrder = playlist.entries().subList(index + 1, playlist.entries().size()).stream()
                .map(PlaylistEntry::id)
                .toList();
        List<UUID> knownEntries = playlist.entries().stream().map(PlaylistEntry::id).toList();
        return new PlaybackCursor(
                entry.id(),
                entry.track(),
                index,
                0L,
                mode,
                shuffleOrder,
                shuffleCursor,
                sequentialOrder,
                knownEntries
        );
    }

    private static ShuffleState initializeShuffle(MusicPlaylist playlist, UUID currentId) {
        List<UUID> order = new ArrayList<>();
        if (currentId != null) {
            order.add(currentId);
        }
        List<UUID> remaining = playlist.entries().stream()
                .map(PlaylistEntry::id)
                .filter(id -> !id.equals(currentId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(remaining);
        order.addAll(remaining);
        if (order.isEmpty()) {
            order.addAll(shuffledIds(playlist, null));
        }
        return new ShuffleState(List.copyOf(order), 0);
    }

    private static ShuffleState reconcileShuffle(MusicPlaylist playlist, PlaybackCursor cursor) {
        UUID currentId = cursor.entryId();
        Set<UUID> activeIds = new HashSet<>();
        for (PlaylistEntry entry : playlist.entries()) {
            activeIds.add(entry.id());
        }

        List<UUID> order = new ArrayList<>();
        Set<UUID> retained = new HashSet<>();
        for (UUID id : cursor.shuffleOrder()) {
            if ((activeIds.contains(id) || id.equals(currentId)) && retained.add(id)) {
                order.add(id);
            }
        }
        int currentCursor = currentId == null ? -1 : order.indexOf(currentId);
        if (currentCursor < 0) {
            return initializeShuffle(playlist, currentId);
        }

        Set<UUID> known = new HashSet<>(order);
        for (PlaylistEntry entry : playlist.entries()) {
            if (known.add(entry.id())) {
                int insertionStart = Math.min(currentCursor + 1, order.size());
                int insertionIndex = ThreadLocalRandom.current().nextInt(insertionStart, order.size() + 1);
                order.add(insertionIndex, entry.id());
            }
        }
        return new ShuffleState(List.copyOf(order), currentCursor);
    }

    private static List<UUID> shuffledIds(MusicPlaylist playlist, UUID previousId) {
        List<UUID> ids = playlist.entries().stream()
                .map(PlaylistEntry::id)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(ids);
        if (previousId != null && ids.size() > 1 && ids.get(0).equals(previousId)) {
            Collections.swap(ids, 0, 1);
        }
        return List.copyOf(ids);
    }

    private record ShuffleState(List<UUID> order, int cursor) {
    }
}
