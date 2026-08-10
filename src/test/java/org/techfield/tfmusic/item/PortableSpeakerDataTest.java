package org.techfield.tfmusic.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortableSpeakerDataTest {
    @Test
    void sanitizesPlaybackBounds() {
        assertEquals(1.0F, PortableSpeakerData.sanitizeSpeed(Float.NaN));
        assertEquals(0.5F, PortableSpeakerData.sanitizeSpeed(0.1F));
        assertEquals(2.0F, PortableSpeakerData.sanitizeSpeed(10.0F));
        assertEquals(PortableSpeakerData.DEFAULT_RADIUS, PortableSpeakerData.sanitizeRadius(Double.NaN));
        assertEquals(PortableSpeakerData.MAX_RADIUS, PortableSpeakerData.sanitizeRadius(Double.MAX_VALUE));
    }
}
