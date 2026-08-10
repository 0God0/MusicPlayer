package org.techfield.tfmusic.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.techfield.tfmusic.menu.BurnerMenu;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.network.ModNetwork;

import java.util.function.Supplier;

public record BurnTrackC2SPacket(MusicTrack track) {
    public static void encode(BurnTrackC2SPacket packet, FriendlyByteBuf buffer) {
        packet.track.write(buffer);
    }

    public static BurnTrackC2SPacket decode(FriendlyByteBuf buffer) {
        return new BurnTrackC2SPacket(MusicTrack.read(buffer));
    }

    public static void handle(BurnTrackC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer sender = contextSupplier.get().getSender();
        if (sender == null
                || !(sender.containerMenu instanceof BurnerMenu menu)
                || !menu.stillValid(sender)) {
            return;
        }
        String validationError = packet.track.validate();
        if (validationError != null) {
            ModNetwork.sendTo(sender, new BurnResultS2CPacket(false, validationError));
            return;
        }
        boolean success = menu.burn(packet.track);
        ModNetwork.sendTo(sender, new BurnResultS2CPacket(
                success,
                success ? "Track written to disc" : "Insert a blank TF Music disc"
        ));
    }
}
