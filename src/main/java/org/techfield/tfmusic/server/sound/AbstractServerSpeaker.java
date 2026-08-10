package org.techfield.tfmusic.server.sound;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.network.ModNetwork;
import org.techfield.tfmusic.network.packet.SpeakerPauseS2CPacket;
import org.techfield.tfmusic.network.packet.SpeakerStopS2CPacket;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractServerSpeaker {
    private static final int LISTENER_REFRESH_TICKS = 20;

    protected final UUID id;
    protected final int sampleRate;
    protected final MusicTrack track;
    protected final Set<ServerPlayer> listeners = new HashSet<>();

    protected float speed;
    protected double radius;
    protected long progressMs;

    private boolean working = true;
    private boolean paused;
    private long lastTickNanos = System.nanoTime();
    private int listenerRefreshTicker = LISTENER_REFRESH_TICKS;

    protected AbstractServerSpeaker(
            UUID id,
            int sampleRate,
            MusicTrack track,
            long progressMs,
            float speed,
            double radius
    ) {
        this.id = id;
        this.sampleRate = sampleRate;
        this.track = track;
        this.progressMs = clampProgress(progressMs);
        this.speed = sanitizeSpeed(speed);
        this.radius = sanitizeRadius(radius);
    }

    public boolean tick() {
        if (!working) {
            return false;
        }
        updateProgress();
        if (progressMs >= track.durationMs()) {
            progressMs = track.durationMs();
            stopAndNotify();
            return false;
        }
        if (++listenerRefreshTicker >= LISTENER_REFRESH_TICKS) {
            listenerRefreshTicker = 0;
            refreshListeners(false);
        }
        return true;
    }

    public void pauseAndNotify() {
        if (!working) {
            return;
        }
        updateProgress();
        paused = true;
        forEachListener(player -> ModNetwork.sendTo(player, new SpeakerPauseS2CPacket(id)));
    }

    public void resumeAndNotify() {
        if (!working) {
            return;
        }
        paused = false;
        lastTickNanos = System.nanoTime();
        refreshListeners(true);
    }

    public void stopAndNotify() {
        if (!working) {
            return;
        }
        working = false;
        paused = false;
        forEachListener(player -> ModNetwork.sendTo(player, new SpeakerStopS2CPacket(id)));
        listeners.clear();
    }

    public void detach() {
        updateProgress();
        stopAndNotify();
    }

    public void seek(long targetMs) {
        long clampedTarget = clampProgress(targetMs);
        if (Math.abs(progressMs - clampedTarget) < 50L) {
            return;
        }
        progressMs = clampedTarget;
        lastTickNanos = System.nanoTime();
        refreshListeners(true);
    }

    public void setSpeed(float newSpeed) {
        float sanitized = sanitizeSpeed(newSpeed);
        if (Math.abs(speed - sanitized) < 0.0001F) {
            return;
        }
        updateProgress();
        speed = sanitized;
        lastTickNanos = System.nanoTime();
        refreshListeners(true);
    }

    public void setRadius(double newRadius) {
        radius = sanitizeRadius(newRadius);
    }

    public long captureProgress() {
        updateProgress();
        return progressMs;
    }

    public long progressMs() {
        return progressMs;
    }

    public float speed() {
        return speed;
    }

    public boolean isWorking() {
        return working;
    }

    public MusicTrack track() {
        return track;
    }

    protected abstract ServerLevel level();

    protected abstract Collection<ServerPlayer> findListeners();

    protected abstract void sendStart(ServerPlayer player);

    protected final void refreshListeners(boolean forceStart) {
        if (!working || paused) {
            return;
        }

        Set<ServerPlayer> current = new HashSet<>();
        for (ServerPlayer player : findListeners()) {
            if (player.isAlive() && player.level() == level()) {
                current.add(player);
            }
        }

        Set<ServerPlayer> departed = new HashSet<>(listeners);
        departed.removeAll(current);
        for (ServerPlayer player : departed) {
            ModNetwork.sendTo(player, new SpeakerStopS2CPacket(id));
        }

        Set<ServerPlayer> arrivals = new HashSet<>(current);
        arrivals.removeAll(listeners);
        for (ServerPlayer player : forceStart ? current : arrivals) {
            sendStart(player);
        }

        listeners.clear();
        listeners.addAll(current);
    }

    private void updateProgress() {
        long now = System.nanoTime();
        if (working && !paused) {
            long elapsedMs = Math.max(0L, (now - lastTickNanos) / 1_000_000L);
            progressMs = Math.min(track.durationMs(), progressMs + (long) (elapsedMs * speed));
        }
        lastTickNanos = now;
    }

    private void forEachListener(java.util.function.Consumer<ServerPlayer> action) {
        for (ServerPlayer player : Set.copyOf(listeners)) {
            action.accept(player);
        }
    }

    private long clampProgress(long value) {
        return Math.max(0L, Math.min(track.durationMs(), value));
    }

    private static float sanitizeSpeed(float value) {
        if (!Float.isFinite(value)) {
            return 1.0F;
        }
        return Math.max(0.5F, Math.min(2.0F, value));
    }

    private static double sanitizeRadius(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            return 64.0D;
        }
        return Math.min(128.0D, value);
    }
}
