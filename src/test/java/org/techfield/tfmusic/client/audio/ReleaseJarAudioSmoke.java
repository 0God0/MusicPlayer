package org.techfield.tfmusic.client.audio;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

public final class ReleaseJarAudioSmoke {
    private ReleaseJarAudioSmoke() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one audio file path");
        }
        Class<?> streamType = Class.forName("org.techfield.tfmusic.client.audio.LavaPlayerAudioStream");
        Constructor<?> constructor = streamType.getConstructor(
                int.class, Path.class, long.class, boolean.class, Duration.class
        );
        Object stream = constructor.newInstance(44_100, Path.of(args[0]), 0L, true, Duration.ofSeconds(10L));
        try {
            Method durationMethod = streamType.getMethod("getDurationMs");
            Method readMethod = Arrays.stream(streamType.getMethods())
                    .filter(method -> method.getReturnType() == ByteBuffer.class)
                    .filter(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[]{int.class}))
                    .findFirst()
                    .orElseThrow();
            long durationMs = (long) durationMethod.invoke(stream);
            ByteBuffer pcm = (ByteBuffer) readMethod.invoke(stream, 4_096);
            if (durationMs <= 0L || !pcm.hasRemaining()) {
                throw new IllegalStateException("Release JAR audio decoder produced no PCM data");
            }
        } finally {
            streamType.getMethod("close").invoke(stream);
        }
    }
}
