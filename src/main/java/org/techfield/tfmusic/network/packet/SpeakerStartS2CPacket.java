package org.techfield.tfmusic.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.techfield.tfmusic.client.ClientPacketHandler;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.music.SoundMode;

import java.util.UUID;
import java.util.function.Supplier;

public record SpeakerStartS2CPacket(
        Mode mode,
        UUID speakerId,
        int sampleRate,
        long progressMs,
        float speed,
        double radius,
        BlockPos pos,
        int ownerEntityId,
        MusicTrack track
) {
    public enum Mode {
        RANGE,
        OWNER,
        FOLLOW,
        GLOBAL_RANGE,
        GLOBAL_FOLLOW;

        public boolean isStereo() {
            return this == OWNER || this == GLOBAL_RANGE || this == GLOBAL_FOLLOW;
        }

        public boolean isBlockSource() {
            return this == RANGE || this == GLOBAL_RANGE;
        }
    }

    public static SpeakerStartS2CPacket range(
            UUID speakerId,
            int sampleRate,
            long progressMs,
            float speed,
            double radius,
            BlockPos pos,
            MusicTrack track,
            SoundMode soundMode
    ) {
        return new SpeakerStartS2CPacket(
                soundMode == SoundMode.GLOBAL ? Mode.GLOBAL_RANGE : Mode.RANGE,
                speakerId, sampleRate, progressMs, speed, radius, pos, -1, track
        );
    }

    public static SpeakerStartS2CPacket owner(
            UUID speakerId,
            int sampleRate,
            long progressMs,
            float speed,
            int ownerEntityId,
            MusicTrack track
    ) {
        return new SpeakerStartS2CPacket(
                Mode.OWNER, speakerId, sampleRate, progressMs, speed, 16.0D, BlockPos.ZERO, ownerEntityId, track
        );
    }

    public static SpeakerStartS2CPacket follow(
            UUID speakerId,
            int sampleRate,
            long progressMs,
            float speed,
            double radius,
            int ownerEntityId,
            MusicTrack track,
            SoundMode soundMode
    ) {
        return new SpeakerStartS2CPacket(
                soundMode == SoundMode.GLOBAL ? Mode.GLOBAL_FOLLOW : Mode.FOLLOW,
                speakerId, sampleRate, progressMs, speed, radius, BlockPos.ZERO, ownerEntityId, track
        );
    }

    public static void encode(SpeakerStartS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.mode);
        buffer.writeUUID(packet.speakerId);
        buffer.writeVarInt(packet.sampleRate);
        buffer.writeLong(packet.progressMs);
        buffer.writeFloat(packet.speed);
        buffer.writeDouble(packet.radius);
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.ownerEntityId);
        packet.track.write(buffer);
    }

    public static SpeakerStartS2CPacket decode(FriendlyByteBuf buffer) {
        return new SpeakerStartS2CPacket(
                buffer.readEnum(Mode.class),
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readFloat(),
                buffer.readDouble(),
                buffer.readBlockPos(),
                buffer.readVarInt(),
                MusicTrack.read(buffer)
        );
    }

    public static void handle(SpeakerStartS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleStart(packet));
    }
}
