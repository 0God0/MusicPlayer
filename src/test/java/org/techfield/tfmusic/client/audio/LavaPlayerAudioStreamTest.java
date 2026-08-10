package org.techfield.tfmusic.client.audio;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LavaPlayerAudioStreamTest {
    @Test
    void downmixesStereoPcmWithoutDroppingFrames() {
        byte[] stereo = {
                (byte) 0xE8, 0x03, (byte) 0xD0, 0x07,
                0x18, (byte) 0xFC, 0x30, (byte) 0xF8
        };

        byte[] mono = LavaPlayerAudioStream.stereo16LeToMono16Le(stereo);

        assertArrayEquals(new byte[]{(byte) 0xDC, 0x05, 0x24, (byte) 0xFA}, mono);
    }

    @Test
    void decodesConfiguredRealAudioFile() throws Exception {
        String configuredPath = System.getProperty("tfMusicTestAudio");
        assumeTrue(configuredPath != null && !configuredPath.isBlank());

        try (LavaPlayerAudioStream stream = new LavaPlayerAudioStream(
                44_100,
                Path.of(configuredPath),
                0L,
                true,
                Duration.ofSeconds(10L)
        )) {
            assertTrue(stream.getDurationMs() > 0L);
            ByteBuffer decoded = stream.read(4_096);
            assertTrue(decoded.remaining() > 0);
        }
    }
}
