package org.techfield.tfmusic.client.audio;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BiliBiliResolverTest {
    @Test
    void resolvesConfiguredLiveVideo() throws Exception {
        assumeTrue(Boolean.getBoolean("tfMusicBiliTest"));

        List<String> urls = BiliBiliResolver.resolveAudioUrls("BV1r4afeGEaq", 1);

        assertFalse(urls.isEmpty());
        assertTrue(urls.stream().allMatch(url -> url.startsWith("http://") || url.startsWith("https://")));

        Path downloaded = Files.createTempFile("tf_music_bilibili_", ".m4a");
        try {
            HttpResponse<InputStream> response = BiliBiliResolver.httpClient().send(
                    BiliBiliResolver.request(urls.get(0)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            assertTrue(response.statusCode() >= 200 && response.statusCode() < 300);
            try (InputStream input = response.body()) {
                Files.copy(input, downloaded, StandardCopyOption.REPLACE_EXISTING);
            }
            try (LavaPlayerAudioStream stream = new LavaPlayerAudioStream(
                    44_100,
                    downloaded,
                    0L,
                    true,
                    Duration.ofSeconds(10L)
            )) {
                assertTrue(stream.getDurationMs() > 0L);
                assertTrue(stream.read(4_096).hasRemaining());
            }
        } finally {
            Files.deleteIfExists(downloaded);
        }
    }
}
