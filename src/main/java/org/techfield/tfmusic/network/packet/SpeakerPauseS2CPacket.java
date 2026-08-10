package org.techfield.tfmusic.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.techfield.tfmusic.client.ClientPacketHandler;

import java.util.UUID;
import java.util.function.Supplier;

public record SpeakerPauseS2CPacket(UUID speakerId) {
    public static void encode(SpeakerPauseS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.speakerId);
    }

    public static SpeakerPauseS2CPacket decode(FriendlyByteBuf buffer) {
        return new SpeakerPauseS2CPacket(buffer.readUUID());
    }

    public static void handle(SpeakerPauseS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handlePause(packet));
    }
}
