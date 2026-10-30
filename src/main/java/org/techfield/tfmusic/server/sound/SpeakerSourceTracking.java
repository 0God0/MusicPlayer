package org.techfield.tfmusic.server.sound;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.techfield.tfmusic.TFMusic;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tracks the sources actually sent to each client, including reloads within one server tick. */
@Mod.EventBusSubscriber(modid = TFMusic.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpeakerSourceTracking {
    private static final Map<Object, Set<ServerPlayer>> WATCHERS = new HashMap<>();
    private static final Set<AbstractServerSpeaker> ACTIVE = new HashSet<>();

    private SpeakerSourceTracking() {
    }

    public record BlockSource(ServerLevel level, ChunkPos chunk) {
    }

    static Collection<ServerPlayer> listeners(Object source) {
        return WATCHERS.getOrDefault(source, Set.of());
    }

    static void add(AbstractServerSpeaker speaker) {
        ACTIVE.add(speaker);
    }

    static void remove(AbstractServerSpeaker speaker) {
        ACTIVE.remove(speaker);
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        update(new BlockSource(event.getLevel(), event.getPos()), event.getPlayer(), true);
    }

    @SubscribeEvent
    public static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        update(new BlockSource(event.getLevel(), event.getPos()), event.getPlayer(), false);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer listener && event.getTarget() instanceof ServerPlayer owner) {
            update(owner, listener, true);
        }
    }

    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer listener && event.getTarget() instanceof ServerPlayer owner) {
            update(owner, listener, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WATCHERS.remove(event.getEntity());
        WATCHERS.values().forEach(players -> players.remove(event.getEntity()));
        WATCHERS.values().removeIf(Set::isEmpty);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WATCHERS.clear();
        ACTIVE.clear();
    }

    private static void update(Object source, ServerPlayer player, boolean loaded) {
        if (loaded) {
            WATCHERS.computeIfAbsent(source, ignored -> new HashSet<>()).add(player);
        } else {
            Set<ServerPlayer> players = WATCHERS.get(source);
            if (players != null) {
                players.remove(player);
                if (players.isEmpty()) {
                    WATCHERS.remove(source);
                }
            }
        }
        for (AbstractServerSpeaker speaker : Set.copyOf(ACTIVE)) {
            if (source.equals(speaker.trackingSource())) {
                speaker.captureProgress();
                speaker.refreshListeners(false);
            }
        }
    }
}
