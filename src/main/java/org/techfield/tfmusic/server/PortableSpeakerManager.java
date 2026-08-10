package org.techfield.tfmusic.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.techfield.tfmusic.TFMusic;
import org.techfield.tfmusic.item.PortableSpeakerData;
import org.techfield.tfmusic.item.PortableSpeakerItem;
import org.techfield.tfmusic.music.MusicPlaylist;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.music.PlaybackCursor;
import org.techfield.tfmusic.music.PlaybackQueue;
import org.techfield.tfmusic.network.packet.SpeakerControlC2SPacket;
import org.techfield.tfmusic.server.sound.FollowPlayerSpeaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TFMusic.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortableSpeakerManager {
    private static final int PROGRESS_SAVE_INTERVAL_TICKS = 20;
    private static final Map<UUID, FollowPlayerSpeaker> ACTIVE = new HashMap<>();
    private static final Map<UUID, ItemStack> ACTIVE_STACKS = new HashMap<>();
    private static final Map<UUID, PendingPause> PENDING_PAUSES = new HashMap<>();
    private static int saveTicker;

    private PortableSpeakerManager() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        persistAndStopAll();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        ACTIVE_STACKS.clear();
        PENDING_PAUSES.clear();
    }

    public static void handleControl(ServerPlayer player, SpeakerControlC2SPacket packet) {
        ItemStack stack = PortableSpeakerItem.find(player.getInventory(), packet.speakerId());
        if (stack == null) {
            return;
        }
        PortableSpeakerData data = PortableSpeakerData.getOrCreate(stack);
        PENDING_PAUSES.remove(data.speakerId());
        PlaybackCursor synchronizedCursor = PlaybackQueue.synchronize(data.media(), data.cursor());
        if (!synchronizedCursor.equals(data.cursor())) {
            data = data.withPlayback(synchronizedCursor, data.speed(), data.playing());
            data.write(stack);
        }
        MusicTrack track = data.currentTrack();
        long duration = track == null ? 0L : track.durationMs();

        switch (packet.action()) {
            case PLAY -> {
                if (track == null) {
                    return;
                }
                FollowPlayerSpeaker existing = ACTIVE.get(packet.speakerId());
                if (data.playing()
                        && existing != null
                        && existing.isWorking()
                        && existing.isOwnedBy(player)
                        && existing.isInOwnerLevel()
                        && existing.track().equals(track)) {
                    existing.resumeAndNotify();
                    return;
                }
                long progress = clampProgress(data.progressMs(), duration);
                PlaybackCursor cursor = data.cursor().withProgress(progress);
                PortableSpeakerData updated = data.withPlayback(cursor, data.speed(), true);
                updated.write(stack);
                startOrReplace(player, stack, updated, track);
            }
            case PAUSE -> {
                FollowPlayerSpeaker speaker = ACTIVE.remove(packet.speakerId());
                ACTIVE_STACKS.remove(packet.speakerId());
                long progress = speaker == null ? data.progressMs() : speaker.captureProgress();
                if (speaker != null) {
                    speaker.pauseAndNotify();
                }
                data.withPlayback(
                        data.cursor().withProgress(clampProgress(progress, duration)),
                        data.speed(),
                        false
                ).write(stack);
            }
            case STOP -> {
                FollowPlayerSpeaker speaker = ACTIVE.remove(packet.speakerId());
                ACTIVE_STACKS.remove(packet.speakerId());
                if (speaker != null) {
                    speaker.stopAndNotify();
                }
                data.withPlayback(PlaybackQueue.initial(data.media()), data.speed(), false).write(stack);
            }
            case SEEK -> {
                if (track == null) {
                    return;
                }
                long target = clampProgress(packet.seekMs(), duration);
                data.withPlayback(data.cursor().withProgress(target), data.speed(), data.playing()).write(stack);
                FollowPlayerSpeaker speaker = ACTIVE.get(packet.speakerId());
                if (speaker != null) {
                    speaker.seek(target);
                }
            }
            case SPEED -> {
                float speed = PortableSpeakerData.sanitizeSpeed(packet.speed());
                data.withPlayback(data.cursor(), speed, data.playing()).write(stack);
                FollowPlayerSpeaker speaker = ACTIVE.get(packet.speakerId());
                if (speaker != null) {
                    speaker.setSpeed(speed);
                }
            }
            case NEXT -> changeTrack(player, stack, data, true);
            case PREVIOUS -> changeTrack(player, stack, data, false);
            case MODE -> {
                MusicPlaylist playlist = MusicPlaylist.fromStack(data.media()).orElse(null);
                if (playlist == null || packet.mode() == null) {
                    return;
                }
                MusicPlaylist updatedPlaylist = playlist.withMode(packet.mode());
                if (updatedPlaylist == playlist) {
                    return;
                }
                ItemStack editedMedia = data.media().copy();
                updatedPlaylist.write(editedMedia);
                data.withEditedMedia(editedMedia).write(stack);
            }
        }
        player.getInventory().setChanged();
    }

    private static void tick(MinecraftServer server) {
        boolean saveProgress = ++saveTicker >= PROGRESS_SAVE_INTERVAL_TICKS;
        if (saveProgress) {
            saveTicker = 0;
        }

        Set<UUID> seen = new HashSet<>();
        Set<UUID> usedIds = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (ItemStack stack : collectSpeakerStacks(player)) {
                PortableSpeakerData data = ensureUniqueId(stack, usedIds);
                UUID id = data.speakerId();
                seen.add(id);

                PlaybackCursor synchronizedCursor = PlaybackQueue.synchronize(data.media(), data.cursor());
                if (!synchronizedCursor.equals(data.cursor())) {
                    data = data.withPlayback(synchronizedCursor, data.speed(), data.playing());
                    data.write(stack);
                    player.getInventory().setChanged();
                }
                MusicTrack track = data.currentTrack();
                if (!player.isAlive() || track == null) {
                    stopAndReset(stack, data);
                    continue;
                }

                if (!data.playing()) {
                    FollowPlayerSpeaker old = ACTIVE.remove(id);
                    ACTIVE_STACKS.remove(id);
                    if (old != null) {
                        if (data.progressMs() > 0L) {
                            old.pauseAndNotify();
                        } else {
                            old.stopAndNotify();
                        }
                    }
                    continue;
                }

                FollowPlayerSpeaker speaker = ACTIVE.get(id);
                if (speaker != null && !speaker.isInOwnerLevel()) {
                    long currentProgress = speaker.captureProgress();
                    speaker.stopAndNotify();
                    ACTIVE.remove(id);
                    data = data.withPlayback(data.cursor().withProgress(currentProgress), data.speed(), true);
                    data.write(stack);
                    speaker = null;
                }
                if (speaker == null || !speaker.isOwnedBy(player) || !speaker.track().equals(track)) {
                    if (speaker != null) {
                        speaker.stopAndNotify();
                    }
                    speaker = createSpeaker(player, data, track);
                    ACTIVE.put(id, speaker);
                    ACTIVE_STACKS.put(id, stack);
                }
                speaker.setRadius(data.radius());
                speaker.setSpeed(data.speed());

                if (!speaker.tick()) {
                    ACTIVE.remove(id);
                    ACTIVE_STACKS.remove(id);
                    PlaybackQueue.AdvanceResult result = PlaybackQueue.advance(data.media(), data.cursor(), false);
                    PortableSpeakerData advanced = data.withPlayback(
                            result.cursor(),
                            data.speed(),
                            result.continuePlaying()
                    );
                    advanced.write(stack);
                    if (result.continuePlaying() && advanced.currentTrack() != null) {
                        FollowPlayerSpeaker next = createSpeaker(player, advanced, advanced.currentTrack());
                        ACTIVE.put(id, next);
                        ACTIVE_STACKS.put(id, stack);
                        next.tick();
                    }
                    player.getInventory().setChanged();
                    continue;
                }
                if (saveProgress) {
                    data.withPlayback(
                            data.cursor().withProgress(speaker.progressMs()),
                            speaker.speed(),
                            true
                    ).write(stack);
                    player.getInventory().setChanged();
                }
                ACTIVE_STACKS.put(id, stack);
            }
        }
        Iterator<Map.Entry<UUID, FollowPlayerSpeaker>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, FollowPlayerSpeaker> entry = iterator.next();
            if (!seen.contains(entry.getKey())) {
                FollowPlayerSpeaker speaker = entry.getValue();
                ItemStack trackedStack = ACTIVE_STACKS.remove(entry.getKey());
                long progress = speaker.captureProgress();
                speaker.pauseAndNotify();
                if (trackedStack != null && PortableSpeakerItem.matches(trackedStack, entry.getKey())) {
                    PortableSpeakerData data = PortableSpeakerData.getOrCreate(trackedStack);
                    data.withPlayback(data.cursor().withProgress(progress), speaker.speed(), false).write(trackedStack);
                } else {
                    PENDING_PAUSES.put(entry.getKey(), new PendingPause(progress, speaker.speed()));
                }
                iterator.remove();
            }
        }
    }

    private static void changeTrack(
            ServerPlayer player,
            ItemStack stack,
            PortableSpeakerData data,
            boolean next
    ) {
        FollowPlayerSpeaker speaker = ACTIVE.remove(data.speakerId());
        ACTIVE_STACKS.remove(data.speakerId());
        long progress = speaker == null ? data.progressMs() : speaker.captureProgress();
        if (speaker != null) {
            speaker.stopAndNotify();
        }

        PlaybackCursor current = data.cursor().withProgress(progress);
        PlaybackCursor target;
        boolean canContinue = true;
        if (next) {
            PlaybackQueue.AdvanceResult result = PlaybackQueue.advance(data.media(), current, true);
            target = result.cursor();
            canContinue = result.continuePlaying();
        } else if (progress > 3_000L) {
            target = current.withProgress(0L);
        } else {
            target = PlaybackQueue.previous(data.media(), current);
        }

        boolean playing = data.playing() && canContinue && target.track() != null;
        PortableSpeakerData updated = data.withPlayback(target, data.speed(), playing);
        updated.write(stack);
        if (playing) {
            startOrReplace(player, stack, updated, target.track());
        }
    }

    private static void startOrReplace(
            ServerPlayer player,
            ItemStack stack,
            PortableSpeakerData data,
            MusicTrack track
    ) {
        FollowPlayerSpeaker old = ACTIVE.remove(data.speakerId());
        if (old != null) {
            old.stopAndNotify();
        }
        FollowPlayerSpeaker speaker = createSpeaker(player, data, track);
        ACTIVE.put(data.speakerId(), speaker);
        ACTIVE_STACKS.put(data.speakerId(), stack);
        speaker.tick();
    }

    private static FollowPlayerSpeaker createSpeaker(
            ServerPlayer player,
            PortableSpeakerData data,
            MusicTrack track
    ) {
        return new FollowPlayerSpeaker(
                data.speakerId(),
                44_100,
                track,
                clampProgress(data.progressMs(), track.durationMs()),
                data.speed(),
                data.radius(),
                player
        );
    }

    private static List<ItemStack> collectSpeakerStacks(ServerPlayer player) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (PortableSpeakerItem.isSpeaker(stack)) {
                result.add(stack);
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (PortableSpeakerItem.isSpeaker(stack)) {
                result.add(stack);
            }
        }
        return result;
    }

    private static PortableSpeakerData ensureUniqueId(ItemStack stack, Set<UUID> usedIds) {
        PortableSpeakerData data = PortableSpeakerData.getOrCreate(stack);
        PendingPause pendingPause = PENDING_PAUSES.remove(data.speakerId());
        if (pendingPause != null) {
            data = data.withPlayback(
                    data.cursor().withProgress(pendingPause.progressMs()),
                    pendingPause.speed(),
                    false
            );
            data.write(stack);
        }
        if (usedIds.add(data.speakerId())) {
            return data;
        }
        FollowPlayerSpeaker duplicate = ACTIVE.remove(data.speakerId());
        ACTIVE_STACKS.remove(data.speakerId());
        if (duplicate != null) {
            duplicate.stopAndNotify();
        }
        PortableSpeakerData replaced = new PortableSpeakerData(
                UUID.randomUUID(),
                data.media(),
                PlaybackQueue.initial(data.media()),
                data.speed(),
                false,
                data.radius()
        );
        replaced.write(stack);
        usedIds.add(replaced.speakerId());
        return replaced;
    }

    private static void stopAndReset(ItemStack stack, PortableSpeakerData data) {
        FollowPlayerSpeaker old = ACTIVE.remove(data.speakerId());
        ACTIVE_STACKS.remove(data.speakerId());
        if (old != null) {
            old.stopAndNotify();
        }
        PlaybackCursor initial = PlaybackQueue.initial(data.media());
        if (data.playing() || !data.cursor().equals(initial)) {
            data.withPlayback(initial, data.speed(), false).write(stack);
        }
    }

    private static long clampProgress(long value, long duration) {
        return duration <= 0L ? 0L : Math.max(0L, Math.min(duration, value));
    }

    private static void persistAndStopAll() {
        for (Map.Entry<UUID, FollowPlayerSpeaker> entry : ACTIVE.entrySet()) {
            FollowPlayerSpeaker speaker = entry.getValue();
            ItemStack stack = ACTIVE_STACKS.get(entry.getKey());
            long progress = speaker.captureProgress();
            if (stack != null && PortableSpeakerItem.matches(stack, entry.getKey())) {
                PortableSpeakerData data = PortableSpeakerData.getOrCreate(stack);
                data.withPlayback(data.cursor().withProgress(progress), speaker.speed(), true).write(stack);
            }
            speaker.stopAndNotify();
        }
        ACTIVE.clear();
        ACTIVE_STACKS.clear();
    }

    private record PendingPause(long progressMs, float speed) {
    }
}
