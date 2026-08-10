package org.techfield.tfmusic.client.audio;

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.minecraft.client.sounds.AudioStream;
import org.slf4j.Logger;
import org.techfield.tfmusic.TFMusic;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LavaPlayerAudioStream implements AudioStream {
    private static final Logger LOGGER = TFMusic.LOGGER;

    private final DefaultAudioPlayerManager playerManager;
    private final AudioPlayer player;
    private final AudioFormat audioFormat;
    private final boolean convertToMono;

    private AudioTrack track;
    private byte[] pending = new byte[0];
    private int pendingOffset;
    private final AtomicBoolean closed = new AtomicBoolean();

    public LavaPlayerAudioStream(
            int sampleRate,
            Path path,
            long startAtMs,
            boolean mono,
            Duration loadTimeout
    ) throws IOException {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive");
        }
        if (startAtMs < 0L) {
            throw new IllegalArgumentException("Start position cannot be negative");
        }

        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        AudioPlayer createdPlayer = null;
        try {
            int chunkSamples = Math.max(1, (int) (sampleRate * 0.02D));
            manager.getConfiguration().setOutputFormat(
                    new Pcm16AudioDataFormat(2, sampleRate, chunkSamples, false)
            );
            manager.registerSourceManager(new LocalAudioSourceManager());
            manager.registerSourceManager(new HttpAudioSourceManager());

            AudioDataFormat outputFormat = manager.getConfiguration().getOutputFormat();
            this.convertToMono = mono && outputFormat.channelCount == 2;
            this.audioFormat = new AudioFormat(
                    outputFormat.sampleRate,
                    16,
                    mono ? 1 : outputFormat.channelCount,
                    true,
                    false
            );

            this.track = loadTrackBlocking(manager, path.toAbsolutePath().toString(), loadTimeout);
            this.track.setPosition(Math.min(startAtMs, track.getDuration()));
            createdPlayer = manager.createPlayer();
            createdPlayer.setVolume(100);
            createdPlayer.playTrack(track);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (createdPlayer != null) {
                createdPlayer.destroy();
            }
            manager.shutdown();
            throw new IOException("Interrupted while loading audio track", exception);
        } catch (RuntimeException | IOException exception) {
            if (createdPlayer != null) {
                createdPlayer.destroy();
            }
            manager.shutdown();
            throw exception;
        }

        this.playerManager = manager;
        this.player = createdPlayer;
    }

    public long getDurationMs() {
        return track == null ? 0L : track.getDuration();
    }

    @Override
    public AudioFormat getFormat() {
        return audioFormat;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("Read size cannot be negative");
        }
        if (closed.get() || size == 0) {
            return ByteBuffer.allocateDirect(0);
        }

        ByteBuffer output = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);
        while (output.hasRemaining()) {
            if (closed.get()) {
                break;
            }
            copyPending(output);
            if (!output.hasRemaining()) {
                break;
            }

            final AudioFrame frame;
            try {
                frame = player.provide(10L, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                if (player.getPlayingTrack() == null) {
                    break;
                }
                continue;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while decoding audio", exception);
            }

            if (frame == null) {
                if (player.getPlayingTrack() == null) {
                    break;
                }
                continue;
            }
            byte[] data = frame.getData();
            if (data == null || data.length == 0) {
                continue;
            }
            pending = convertToMono ? stereo16LeToMono16Le(data) : data;
            pendingOffset = 0;
        }
        output.flip();
        return output;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        AudioTrack closingTrack = track;
        track = null;
        try {
            player.stopTrack();
        } catch (Throwable exception) {
            LOGGER.warn("Failed to stop LavaPlayer audio", exception);
        }
        try {
            player.destroy();
        } catch (Throwable exception) {
            LOGGER.warn("Failed to destroy LavaPlayer audio", exception);
        }
        if (closingTrack != null) {
            try {
                closingTrack.stop();
            } catch (Throwable exception) {
                LOGGER.debug("Failed to stop LavaPlayer track", exception);
            }
        }
        try {
            playerManager.shutdown();
        } catch (Throwable exception) {
            LOGGER.warn("Failed to shut down LavaPlayer", exception);
        }
        pending = new byte[0];
        pendingOffset = 0;
    }

    public static byte[] stereo16LeToMono16Le(byte[] stereo) {
        if (stereo == null || stereo.length < 4) {
            return new byte[0];
        }
        int usableBytes = stereo.length / 4 * 4;
        byte[] mono = new byte[usableBytes / 2];
        int monoOffset = 0;
        for (int offset = 0; offset < usableBytes; offset += 4) {
            int left = (short) ((stereo[offset] & 0xFF) | ((stereo[offset + 1] & 0xFF) << 8));
            int right = (short) ((stereo[offset + 2] & 0xFF) | ((stereo[offset + 3] & 0xFF) << 8));
            int mixed = (left + right) >> 1;
            mono[monoOffset++] = (byte) (mixed & 0xFF);
            mono[monoOffset++] = (byte) ((mixed >> 8) & 0xFF);
        }
        return mono;
    }

    private void copyPending(ByteBuffer output) {
        int available = pending.length - pendingOffset;
        if (available <= 0) {
            pending = new byte[0];
            pendingOffset = 0;
            return;
        }
        int length = Math.min(available, output.remaining());
        output.put(pending, pendingOffset, length);
        pendingOffset += length;
        if (pendingOffset >= pending.length) {
            pending = new byte[0];
            pendingOffset = 0;
        }
    }

    private static AudioTrack loadTrackBlocking(
            DefaultAudioPlayerManager manager,
            String identifier,
            Duration timeout
    ) throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AudioTrack> loadedTrack = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        manager.loadItem(identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                loadedTrack.set(track);
                latch.countDown();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getTracks().isEmpty()) {
                    failure.set(new IOException("Audio playlist is empty"));
                } else {
                    loadedTrack.set(playlist.getTracks().get(0));
                }
                latch.countDown();
            }

            @Override
            public void noMatches() {
                failure.set(new IOException("No audio track matched " + identifier));
                latch.countDown();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                failure.set(exception);
                latch.countDown();
            }
        });

        if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IOException("Timed out while loading audio track");
        }
        if (failure.get() != null) {
            throw new IOException("Failed to load audio track", failure.get());
        }
        AudioTrack result = loadedTrack.get();
        if (result == null) {
            throw new IOException("Audio track loader returned no result");
        }
        return result;
    }
}
