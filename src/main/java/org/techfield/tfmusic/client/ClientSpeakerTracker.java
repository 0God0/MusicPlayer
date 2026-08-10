package org.techfield.tfmusic.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientSpeakerTracker {
    private static final Map<UUID, State> STATES = new HashMap<>();

    private ClientSpeakerTracker() {
    }

    public static void onStart(UUID id, long durationMs, long progressMs, float speed) {
        STATES.put(id, new State(
                Math.max(0L, durationMs),
                Math.max(0L, progressMs),
                speed,
                true,
                System.currentTimeMillis()
        ));
    }

    public static void onPause(UUID id) {
        State state = STATES.get(id);
        if (state == null) {
            return;
        }
        long now = System.currentTimeMillis();
        state.progressMs = computeProgress(state, now);
        state.playing = false;
        state.lastUpdateMs = now;
    }

    public static void onStop(UUID id) {
        State state = STATES.get(id);
        if (state == null) {
            return;
        }
        state.progressMs = 0L;
        state.playing = false;
        state.lastUpdateMs = System.currentTimeMillis();
    }

    public static void localPlay(UUID id, long durationMs, long progressMs, float speed) {
        onStart(id, durationMs, progressMs, speed);
    }

    public static void localSeek(UUID id, long durationMs, long progressMs, boolean playing) {
        State state = STATES.computeIfAbsent(id, ignored -> new State(
                durationMs, 0L, 1.0F, false, System.currentTimeMillis()
        ));
        state.durationMs = Math.max(0L, durationMs);
        state.progressMs = Math.max(0L, Math.min(state.durationMs, progressMs));
        state.playing = playing;
        state.lastUpdateMs = System.currentTimeMillis();
    }

    public static void localSpeed(UUID id, long durationMs, float speed) {
        State state = STATES.computeIfAbsent(id, ignored -> new State(
                durationMs, 0L, speed, false, System.currentTimeMillis()
        ));
        long now = System.currentTimeMillis();
        state.progressMs = computeProgress(state, now);
        state.durationMs = Math.max(0L, durationMs);
        state.speed = speed;
        state.lastUpdateMs = now;
    }

    public static boolean isPlaying(UUID id) {
        State state = STATES.get(id);
        return state != null && state.playing;
    }

    public static long progress(UUID id, long durationMs) {
        State state = STATES.get(id);
        if (state == null) {
            return 0L;
        }
        if (durationMs > 0L) {
            state.durationMs = durationMs;
        }
        long now = System.currentTimeMillis();
        long progress = Math.min(state.durationMs, computeProgress(state, now));
        if (state.playing && state.durationMs > 0L && progress >= state.durationMs) {
            state.progressMs = state.durationMs;
            state.playing = false;
            state.lastUpdateMs = now;
        }
        return progress;
    }

    public static float speed(UUID id, float fallback) {
        State state = STATES.get(id);
        return state == null ? fallback : state.speed;
    }

    public static void clear() {
        STATES.clear();
    }

    private static long computeProgress(State state, long now) {
        if (!state.playing) {
            return state.progressMs;
        }
        long elapsed = Math.max(0L, now - state.lastUpdateMs);
        return Math.max(0L, state.progressMs + (long) (elapsed * state.speed));
    }

    private static final class State {
        private long durationMs;
        private long progressMs;
        private float speed;
        private boolean playing;
        private long lastUpdateMs;

        private State(long durationMs, long progressMs, float speed, boolean playing, long lastUpdateMs) {
            this.durationMs = durationMs;
            this.progressMs = progressMs;
            this.speed = speed;
            this.playing = playing;
            this.lastUpdateMs = lastUpdateMs;
        }
    }
}
