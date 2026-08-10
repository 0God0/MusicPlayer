package org.techfield.tfmusic.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MusicSourceTest {
    @Test
    void acceptsOriginalDirectLinkFormats() {
        assertNull(MusicSource.DIRECT_LINK.validateLocator("https://example.com/music/song.mp3"));
        assertNull(MusicSource.DIRECT_LINK.validateLocator("http://127.0.0.1:8080/audio/test.m4a?token=1"));
    }

    @Test
    void rejectsUnsupportedDirectLinkFormats() {
        assertNotNull(MusicSource.DIRECT_LINK.validateLocator("file:///tmp/song.mp3"));
        assertNotNull(MusicSource.DIRECT_LINK.validateLocator("https://example.com/song.ogg"));
        assertNotNull(MusicSource.DIRECT_LINK.validateLocator("https://example.com/"));
    }

    @Test
    void validatesBiliBiliIds() {
        assertNull(MusicSource.BILIBILI.validateLocator("BV1xx411c7mD"));
        assertNotNull(MusicSource.BILIBILI.validateLocator("av170001"));
    }
}
