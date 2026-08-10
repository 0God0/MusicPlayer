package org.techfield.tfmusic.music;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicTrackTest {
    @Test
    void nbtRoundTripPreservesTrack() {
        MusicTrack original = new MusicTrack(
                MusicSource.BILIBILI,
                "Test track",
                123_456L,
                "BV1xx411c7mD",
                2
        );

        CompoundTag saved = original.save();
        assertEquals(original, MusicTrack.load(saved).orElseThrow());
    }

    @Test
    void rejectsOutOfRangeDuration() {
        MusicTrack invalid = new MusicTrack(
                MusicSource.DIRECT_LINK,
                "Too long",
                MusicTrack.MAX_DURATION_MS + 1L,
                "https://example.com/track.mp3",
                1
        );
        assertTrue(invalid.validate().contains("2 hours"));
    }
}
