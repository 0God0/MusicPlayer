package org.techfield.tfmusic.music;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

public record PlaybackCursor(
        @Nullable UUID entryId,
        @Nullable MusicTrack track,
        int fallbackIndex,
        long progressMs,
        PlaybackMode observedMode,
        List<UUID> shuffleOrder,
        int shuffleCursor,
        List<UUID> sequentialOrder,
        List<UUID> knownEntries
) {
    private static final String ENTRY_ID_TAG = "EntryId";
    private static final String TRACK_TAG = "Track";
    private static final String FALLBACK_INDEX_TAG = "FallbackIndex";
    private static final String PROGRESS_TAG = "ProgressMs";
    private static final String MODE_TAG = "ObservedMode";
    private static final String SHUFFLE_ORDER_TAG = "ShuffleOrder";
    private static final String SHUFFLE_CURSOR_TAG = "ShuffleCursor";
    private static final String SEQUENTIAL_ORDER_TAG = "SequentialOrder";
    private static final String KNOWN_ENTRIES_TAG = "KnownEntries";

    public PlaybackCursor {
        fallbackIndex = Math.max(0, fallbackIndex);
        progressMs = Math.max(0L, progressMs);
        observedMode = observedMode == null ? PlaybackMode.SEQUENTIAL : observedMode;
        shuffleOrder = shuffleOrder == null ? List.of() : List.copyOf(shuffleOrder);
        shuffleCursor = Math.max(0, shuffleCursor);
        sequentialOrder = sequentialOrder == null ? List.of() : List.copyOf(sequentialOrder);
        knownEntries = knownEntries == null ? List.of() : List.copyOf(knownEntries);
        if (track != null) {
            progressMs = Math.min(progressMs, track.durationMs());
        } else {
            progressMs = 0L;
        }
    }

    public PlaybackCursor(
            @Nullable UUID entryId,
            @Nullable MusicTrack track,
            int fallbackIndex,
            long progressMs,
            PlaybackMode observedMode,
            List<UUID> shuffleOrder,
            int shuffleCursor
    ) {
        this(entryId, track, fallbackIndex, progressMs, observedMode, shuffleOrder, shuffleCursor, List.of(), List.of());
    }

    public PlaybackCursor withProgress(long newProgress) {
        return new PlaybackCursor(
                entryId,
                track,
                fallbackIndex,
                newProgress,
                observedMode,
                shuffleOrder,
                shuffleCursor,
                sequentialOrder,
                knownEntries
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (entryId != null) {
            tag.putUUID(ENTRY_ID_TAG, entryId);
        }
        if (track != null) {
            tag.put(TRACK_TAG, track.save());
        }
        tag.putInt(FALLBACK_INDEX_TAG, fallbackIndex);
        tag.putLong(PROGRESS_TAG, progressMs);
        tag.putString(MODE_TAG, observedMode.serializedName());
        tag.put(SHUFFLE_ORDER_TAG, writeIds(shuffleOrder));
        tag.putInt(SHUFFLE_CURSOR_TAG, shuffleCursor);
        tag.put(SEQUENTIAL_ORDER_TAG, writeIds(sequentialOrder));
        tag.put(KNOWN_ENTRIES_TAG, writeIds(knownEntries));
        return tag;
    }

    public static PlaybackCursor load(CompoundTag tag, ItemStack media) {
        return load(tag, PlaybackQueue.initial(media), cursor -> PlaybackQueue.synchronize(media, cursor));
    }

    public static PlaybackCursor load(CompoundTag tag, MusicPlaylist playlist) {
        return load(tag, PlaybackQueue.initial(playlist), cursor -> PlaybackQueue.synchronize(playlist, cursor));
    }

    private static PlaybackCursor load(
            CompoundTag tag,
            PlaybackCursor initial,
            UnaryOperator<PlaybackCursor> synchronizer
    ) {
        MusicTrack track = tag.contains(TRACK_TAG, CompoundTag.TAG_COMPOUND)
                ? MusicTrack.load(tag.getCompound(TRACK_TAG)).orElse(null)
                : null;
        if (track == null) {
            return initial.withProgress(tag.getLong(PROGRESS_TAG));
        }

        List<UUID> order = readIds(tag, SHUFFLE_ORDER_TAG, MusicPlaylist.MAX_TRACKS + 1);
        PlaybackCursor loaded = new PlaybackCursor(
                tag.hasUUID(ENTRY_ID_TAG) ? tag.getUUID(ENTRY_ID_TAG) : null,
                track,
                tag.getInt(FALLBACK_INDEX_TAG),
                tag.getLong(PROGRESS_TAG),
                PlaybackMode.fromSerializedName(tag.getString(MODE_TAG)),
                order,
                tag.getInt(SHUFFLE_CURSOR_TAG),
                readIds(tag, SEQUENTIAL_ORDER_TAG, MusicPlaylist.MAX_TRACKS),
                readIds(tag, KNOWN_ENTRIES_TAG, MusicPlaylist.MAX_TRACKS)
        );
        return synchronizer.apply(loaded);
    }

    private static ListTag writeIds(List<UUID> ids) {
        ListTag tags = new ListTag();
        for (UUID id : ids) {
            CompoundTag idTag = new CompoundTag();
            idTag.putUUID("Id", id);
            tags.add(idTag);
        }
        return tags;
    }

    private static List<UUID> readIds(CompoundTag parent, String key, int maximum) {
        ListTag tags = parent.getList(key, CompoundTag.TAG_COMPOUND);
        int limit = Math.min(tags.size(), maximum);
        List<UUID> ids = new ArrayList<>(limit);
        Set<UUID> seen = new HashSet<>();
        for (int index = 0; index < limit; index++) {
            CompoundTag idTag = tags.getCompound(index);
            if (idTag.hasUUID("Id") && seen.add(idTag.getUUID("Id"))) {
                ids.add(idTag.getUUID("Id"));
            }
        }
        return List.copyOf(ids);
    }
}
