package org.techfield.tfmusic.client.audio;

import net.minecraft.client.sounds.AudioStream;
import net.minecraftforge.fml.loading.FMLPaths;
import org.techfield.tfmusic.music.MusicSource;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.network.packet.SpeakerStartS2CPacket;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientAudioCache {
    private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(10L);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30L);
    private static final Path CACHE_DIRECTORY = FMLPaths.GAMEDIR.get()
            .resolve("tf_music")
            .resolve("cache")
            .resolve("audio");
    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "TF-Music-IO");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient DIRECT_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15L))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final Map<Path, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    private ClientAudioCache() {
    }

    public static AudioLoad load(SpeakerStartS2CPacket packet) {
        AudioLoad load = new AudioLoad();
        Future<?> task = IO_EXECUTOR.submit(() -> {
            try {
                ResolvedTrack resolved = resolveTrack(packet.track());
                if (load.isCancelled()) {
                    return;
                }
                LavaPlayerAudioStream stream = new LavaPlayerAudioStream(
                        packet.sampleRate(),
                        resolved.path(),
                        packet.progressMs(),
                        packet.mode() != SpeakerStartS2CPacket.Mode.OWNER,
                        LOAD_TIMEOUT
                );
                load.complete(stream);
            } catch (Throwable exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                load.fail(exception);
            }
        });
        load.attach(task);
        return load;
    }

    public static CompletableFuture<Long> probe(MusicSource source, String locator, int page) {
        return CompletableFuture.supplyAsync(() -> {
            MusicTrack probeTrack = new MusicTrack(source, "probe", 1L, locator, page);
            String locatorError = source.validateLocator(locator);
            if (locatorError != null) {
                throw new CompletionException(new IOException(locatorError));
            }
            try {
                ResolvedTrack resolved = resolveTrack(probeTrack);
                try (LavaPlayerAudioStream stream = new LavaPlayerAudioStream(
                        44_100,
                        resolved.path(),
                        0L,
                        true,
                        LOAD_TIMEOUT
                )) {
                    return stream.getDurationMs();
                }
            } catch (Throwable exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new CompletionException(exception);
            }
        }, IO_EXECUTOR);
    }

    private static ResolvedTrack resolveTrack(MusicTrack track) throws IOException, InterruptedException {
        Files.createDirectories(CACHE_DIRECTORY);
        String cacheKey = track.source().serializedName() + ':' + track.locator() + ':' + track.page();
        String extension = track.source() == MusicSource.BILIBILI ? "m4a" : directExtension(track.locator());
        Path target = CACHE_DIRECTORY.resolve(hash(cacheKey) + '.' + extension).normalize();
        Object lock = FILE_LOCKS.computeIfAbsent(target, ignored -> new Object());
        synchronized (lock) {
            if (Files.isRegularFile(target) && Files.size(target) > 0L) {
                return new ResolvedTrack(target);
            }
            Files.deleteIfExists(target);
            if (track.source() == MusicSource.DIRECT_LINK) {
                downloadDirect(track.locator(), target);
            } else {
                downloadBiliBili(track.locator(), track.page(), target);
            }
            if (!Files.isRegularFile(target) || Files.size(target) == 0L) {
                throw new IOException("Downloaded audio file is empty");
            }
            return new ResolvedTrack(target);
        }
    }

    private static void downloadDirect(String url, Path target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(DOWNLOAD_TIMEOUT)
                .header("User-Agent", "TF-Music/1.0")
                .GET()
                .build();
        HttpResponse<InputStream> response = DIRECT_HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );
        writeResponse(response, target);
    }

    private static void downloadBiliBili(String bvid, int page, Path target)
            throws IOException, InterruptedException {
        List<String> urls = BiliBiliResolver.resolveAudioUrls(bvid, page);
        IOException lastFailure = null;
        for (String url : urls) {
            try {
                HttpResponse<InputStream> response = BiliBiliResolver.httpClient().send(
                        BiliBiliResolver.request(url).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream()
                );
                writeResponse(response, target);
                return;
            } catch (IOException exception) {
                if (Thread.currentThread().isInterrupted()) {
                    throw exception;
                }
                lastFailure = exception;
            }
        }
        throw lastFailure == null ? new IOException("BiliBili audio download failed") : lastFailure;
    }

    private static void writeResponse(HttpResponse<InputStream> response, Path target) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException("Audio server returned HTTP " + response.statusCode());
        }
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try {
            try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("Audio download was cancelled");
                    }
                    output.write(buffer, 0, read);
                }
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String directExtension(String url) {
        String path = URI.create(url).getPath();
        int dot = path == null ? -1 : path.lastIndexOf('.');
        String extension = dot < 0 ? "audio" : path.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return extension.equals("mp3") || extension.equals("m4a") ? extension : "audio";
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ResolvedTrack(Path path) {
    }

    public static final class AudioLoad {
        private final CompletableFuture<AudioStream> future = new CompletableFuture<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<AudioStream> stream = new AtomicReference<>();
        private final AtomicReference<Future<?>> task = new AtomicReference<>();

        private AudioLoad() {
        }

        public CompletableFuture<AudioStream> future() {
            return future;
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            Future<?> runningTask = task.get();
            if (runningTask != null) {
                runningTask.cancel(true);
            }
            future.cancel(false);
            closeQuietly(stream.getAndSet(null));
        }

        private void attach(Future<?> runningTask) {
            task.set(runningTask);
            if (cancelled.get()) {
                runningTask.cancel(true);
            }
        }

        private void complete(AudioStream loadedStream) {
            stream.set(loadedStream);
            if (cancelled.get() || !future.complete(loadedStream)) {
                closeQuietly(stream.getAndSet(null));
            }
        }

        private void fail(Throwable throwable) {
            if (!cancelled.get()) {
                future.completeExceptionally(throwable);
            }
        }

        private static void closeQuietly(AudioStream audioStream) {
            if (audioStream == null) {
                return;
            }
            try {
                audioStream.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
