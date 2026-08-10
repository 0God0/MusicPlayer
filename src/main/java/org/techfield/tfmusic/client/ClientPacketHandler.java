package org.techfield.tfmusic.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.techfield.tfmusic.network.packet.BurnResultS2CPacket;
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
}
