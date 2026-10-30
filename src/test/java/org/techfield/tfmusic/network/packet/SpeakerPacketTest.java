package org.techfield.tfmusic.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.techfield.tfmusic.music.MusicSource;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.music.PlaybackMode;
import org.techfield.tfmusic.music.SoundMode;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerPacketTest {
    private static final MusicTrack TRACK = new MusicTrack(
            MusicSource.DIRECT_LINK, "Stereo track", 60_000L, "https://example.com/track.mp3", 1);

    @Test
    void globalStartsPreserveBothStereoAndSourceIdentityOverTheWire() {
        UUID id = UUID.randomUUID();
        BlockPos pos = new BlockPos(120, 64, -500);
        var block = SpeakerStartS2CPacket.range(id, 44_100, 12_345L, 1.5F, 64.0D, pos, TRACK, SoundMode.GLOBAL);
        var follow = SpeakerStartS2CPacket.follow(id, 44_100, 12_345L, 1.5F, 64.0D, 42, TRACK, SoundMode.GLOBAL);
        var owner = SpeakerStartS2CPacket.owner(id, 44_100, 12_345L, 1.5F, 42, TRACK);

        for (var packet : List.of(block, follow, owner)) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                SpeakerStartS2CPacket.encode(packet, buffer);
                var decoded = SpeakerStartS2CPacket.decode(buffer);
                assertEquals(packet, decoded);
                assertTrue(decoded.mode().isStereo());
                assertFalse(buffer.isReadable());
            } finally {
                buffer.release();
            }
        }
        assertEquals(pos, block.pos());
        assertTrue(block.mode().isBlockSource());
        assertEquals(42, follow.ownerEntityId());
        assertFalse(follow.mode().isBlockSource());
    }

    @Test
    void spatialStartsKeepPositionalMonoForOtherListeners() {
        UUID id = UUID.randomUUID();
        assertFalse(SpeakerStartS2CPacket.range(id, 44_100, 0L, 1.0F, 64.0D,
                BlockPos.ZERO, TRACK, SoundMode.SPATIAL).mode().isStereo());
        assertFalse(SpeakerStartS2CPacket.follow(id, 44_100, 0L, 1.0F, 64.0D,
                42, TRACK, SoundMode.SPATIAL).mode().isStereo());
    }

    @Test
    void soundAndPlaylistModeControlsRoundTripIndependently() {
        UUID id = UUID.randomUUID();
        for (var packet : List.of(
                new SpeakerControlC2SPacket(id, SpeakerControlC2SPacket.Action.SOUND_MODE,
                        0L, 1.0F, null, SoundMode.GLOBAL),
                new SpeakerControlC2SPacket(id, SpeakerControlC2SPacket.Action.SOUND_MODE,
                        0L, 1.0F, null, SoundMode.SPATIAL),
                new SpeakerControlC2SPacket(id, SpeakerControlC2SPacket.Action.MODE,
                        0L, 1.0F, PlaybackMode.SHUFFLE),
                new SpeakerControlC2SPacket(id, SpeakerControlC2SPacket.Action.SEEK, 12_345L, 1.5F))) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                SpeakerControlC2SPacket.encode(packet, buffer);
                assertEquals(packet, SpeakerControlC2SPacket.decode(buffer));
                assertFalse(buffer.isReadable());
            } finally {
                buffer.release();
            }
        }
    }
}
