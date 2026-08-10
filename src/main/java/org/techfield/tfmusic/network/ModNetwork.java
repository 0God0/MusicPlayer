package org.techfield.tfmusic.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.techfield.tfmusic.TFMusic;
import org.techfield.tfmusic.network.packet.BurnResultS2CPacket;
import org.techfield.tfmusic.network.packet.BurnTrackC2SPacket;
import org.techfield.tfmusic.network.packet.OpenSpeakerPlaylistEditorC2SPacket;
import org.techfield.tfmusic.network.packet.PlaylistEditC2SPacket;
import org.techfield.tfmusic.network.packet.PlaylistStateS2CPacket;
import org.techfield.tfmusic.network.packet.SpeakerControlC2SPacket;
import org.techfield.tfmusic.network.packet.SpeakerPauseS2CPacket;
import org.techfield.tfmusic.network.packet.SpeakerStartS2CPacket;
import org.techfield.tfmusic.network.packet.SpeakerStopS2CPacket;

public final class ModNetwork {
    private static final String PROTOCOL = "3";
    private static int nextPacketId;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(TFMusic.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(BurnTrackC2SPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BurnTrackC2SPacket::encode)
                .decoder(BurnTrackC2SPacket::decode)
                .consumerMainThread(BurnTrackC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(SpeakerControlC2SPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SpeakerControlC2SPacket::encode)
                .decoder(SpeakerControlC2SPacket::decode)
                .consumerMainThread(SpeakerControlC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(SpeakerStartS2CPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpeakerStartS2CPacket::encode)
                .decoder(SpeakerStartS2CPacket::decode)
                .consumerMainThread(SpeakerStartS2CPacket::handle)
                .add();
        CHANNEL.messageBuilder(SpeakerPauseS2CPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpeakerPauseS2CPacket::encode)
                .decoder(SpeakerPauseS2CPacket::decode)
                .consumerMainThread(SpeakerPauseS2CPacket::handle)
                .add();
        CHANNEL.messageBuilder(SpeakerStopS2CPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpeakerStopS2CPacket::encode)
                .decoder(SpeakerStopS2CPacket::decode)
                .consumerMainThread(SpeakerStopS2CPacket::handle)
                .add();
        CHANNEL.messageBuilder(BurnResultS2CPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BurnResultS2CPacket::encode)
                .decoder(BurnResultS2CPacket::decode)
                .consumerMainThread(BurnResultS2CPacket::handle)
                .add();
        CHANNEL.messageBuilder(PlaylistEditC2SPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PlaylistEditC2SPacket::encode)
                .decoder(PlaylistEditC2SPacket::decode)
                .consumerMainThread(PlaylistEditC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(PlaylistStateS2CPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlaylistStateS2CPacket::encode)
                .decoder(PlaylistStateS2CPacket::decode)
                .consumerMainThread(PlaylistStateS2CPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenSpeakerPlaylistEditorC2SPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenSpeakerPlaylistEditorC2SPacket::encode)
                .decoder(OpenSpeakerPlaylistEditorC2SPacket::decode)
                .consumerMainThread(OpenSpeakerPlaylistEditorC2SPacket::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
