package org.techfield.tfmusic.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.techfield.tfmusic.network.packet.BurnResultS2CPacket;
import org.techfield.tfmusic.network.packet.PlaylistStateS2CPacket;
import org.techfield.tfmusic.menu.PlaylistEditorMenu;
import org.techfield.tfmusic.music.MusicPlaylist;
import org.techfield.tfmusic.network.packet.SpeakerPauseS2CPacket;
import org.techfield.tfmusic.network.packet.SpeakerStartS2CPacket;
import org.techfield.tfmusic.network.packet.SpeakerStopS2CPacket;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handleStart(SpeakerStartS2CPacket packet) {
        ClientPlaybackManager.start(packet);
    }

    public static void handlePause(SpeakerPauseS2CPacket packet) {
        ClientPlaybackManager.pause(packet.speakerId());
    }

    public static void handleStop(SpeakerStopS2CPacket packet) {
        ClientPlaybackManager.stop(packet.speakerId());
    }

    public static void handleBurnResult(BurnResultS2CPacket packet) {
        ClientPlaybackManager.handleBurnResult(packet);
    }

    public static void handlePlaylistState(PlaylistStateS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.player.containerMenu instanceof PlaylistEditorMenu menu
                && menu.containerId == packet.containerId()) {
            MusicPlaylist.load(packet.playlistTag()).ifPresent(menu::updateClientState);
        }
        ClientPlaybackManager.handlePlaylistState(packet);
    }
}
