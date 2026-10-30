package org.techfield.tfmusic.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.techfield.tfmusic.menu.PlaylistEditorMenu;
import org.techfield.tfmusic.menu.PortableSpeakerMenu;
import org.techfield.tfmusic.music.MusicMedia;

import java.util.UUID;
import java.util.function.Supplier;

public record OpenSpeakerPlaylistEditorC2SPacket(UUID speakerId) {
    public static void encode(OpenSpeakerPlaylistEditorC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.speakerId);
    }

    public static OpenSpeakerPlaylistEditorC2SPacket decode(FriendlyByteBuf buffer) {
        return new OpenSpeakerPlaylistEditorC2SPacket(buffer.readUUID());
    }

    public static void handle(
            OpenSpeakerPlaylistEditorC2SPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        ServerPlayer sender = contextSupplier.get().getSender();
        if (sender == null
                || !(sender.containerMenu instanceof PortableSpeakerMenu menu)
                || !menu.speakerId().equals(packet.speakerId)
                || !menu.stillValid(sender)) {
            return;
        }
        if (MusicMedia.isPlaylist(menu.speakerData().media())) {
            PlaylistEditorMenu.open(sender, menu.playlistTarget());
        }
    }
}
