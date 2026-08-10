package org.techfield.tfmusic.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.techfield.tfmusic.client.ClientPacketHandler;

import java.util.UUID;
import java.util.function.Supplier;

public record SpeakerStopS2CPacket(UUID speakerId) {
    public static void encode(SpeakerStopS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.speakerId);
    }

    public static SpeakerStopS2CPacket decode(FriendlyByteBuf buffer) {
        return new SpeakerStopS2CPacket(buffer.readUUID());
    }

    public static void handle(SpeakerStopS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleStop(packet));
    }
}
