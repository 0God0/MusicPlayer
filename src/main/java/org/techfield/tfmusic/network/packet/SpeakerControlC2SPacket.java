package org.techfield.tfmusic.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.techfield.tfmusic.server.PortableSpeakerManager;

import java.util.UUID;
import java.util.function.Supplier;

public record SpeakerControlC2SPacket(UUID speakerId, Action action, long seekMs, float speed) {
    public enum Action {
        PLAY,
        PAUSE,
        STOP,
        SEEK,
        SPEED
    }

    public static void encode(SpeakerControlC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.speakerId);
        buffer.writeEnum(packet.action);
        buffer.writeLong(packet.seekMs);
        buffer.writeFloat(packet.speed);
    }

    public static SpeakerControlC2SPacket decode(FriendlyByteBuf buffer) {
        return new SpeakerControlC2SPacket(
                buffer.readUUID(),
                buffer.readEnum(Action.class),
                buffer.readLong(),
                buffer.readFloat()
        );
    }

    public static void handle(SpeakerControlC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer sender = contextSupplier.get().getSender();
        if (sender != null) {
            PortableSpeakerManager.handleControl(sender, packet);
        }
    }
}
