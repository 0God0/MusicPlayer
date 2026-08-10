package org.techfield.tfmusic.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.techfield.tfmusic.client.ClientPacketHandler;

import java.util.function.Supplier;

public record PlaylistStateS2CPacket(
        int containerId,
        CompoundTag playlistTag,
        boolean success,
        String messageKey
) {
    private static final int MAX_MESSAGE_LENGTH = 128;

    public static void encode(PlaylistStateS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        buffer.writeNbt(packet.playlistTag);
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.messageKey, MAX_MESSAGE_LENGTH);
    }

    public static PlaylistStateS2CPacket decode(FriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        CompoundTag tag = buffer.readNbt();
        return new PlaylistStateS2CPacket(
                containerId,
                tag == null ? new CompoundTag() : tag,
                buffer.readBoolean(),
                buffer.readUtf(MAX_MESSAGE_LENGTH)
        );
    }

    public static void handle(PlaylistStateS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handlePlaylistState(packet));
    }
}
