package org.techfield.tfmusic.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.techfield.tfmusic.client.ClientPacketHandler;

import java.util.function.Supplier;

public record BurnResultS2CPacket(boolean success, String message) {
    private static final int MAX_MESSAGE_LENGTH = 256;

    public static void encode(BurnResultS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.message, MAX_MESSAGE_LENGTH);
    }

    public static BurnResultS2CPacket decode(FriendlyByteBuf buffer) {
        return new BurnResultS2CPacket(buffer.readBoolean(), buffer.readUtf(MAX_MESSAGE_LENGTH));
    }

    public static void handle(BurnResultS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleBurnResult(packet));
    }
}
