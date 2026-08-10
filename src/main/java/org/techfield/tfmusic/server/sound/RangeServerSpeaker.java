package org.techfield.tfmusic.server.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.network.ModNetwork;
import org.techfield.tfmusic.network.packet.SpeakerStartS2CPacket;

import java.util.Collection;
import java.util.UUID;

public final class RangeServerSpeaker extends AbstractServerSpeaker {
    private final ServerLevel level;
    private final BlockPos pos;

    public RangeServerSpeaker(
            UUID id,
            int sampleRate,
            MusicTrack track,
            long progressMs,
            float speed,
            double radius,
            ServerLevel level,
            BlockPos pos
    ) {
        super(id, sampleRate, track, progressMs, speed, radius);
        this.level = level;
        this.pos = pos.immutable();
    }

    @Override
    protected ServerLevel level() {
        return level;
    }

    @Override
    protected Collection<ServerPlayer> findListeners() {
        double packetRadius = radius + 32.0D;
        double radiusSquared = packetRadius * packetRadius;
        return level.players().stream()
                .filter(player -> player.distanceToSqr(
                        pos.getX() + 0.5D,
                        pos.getY() + 0.5D,
                        pos.getZ() + 0.5D) <= radiusSquared)
                .toList();
    }

    @Override
    protected void sendStart(ServerPlayer player) {
        ModNetwork.sendTo(player, SpeakerStartS2CPacket.range(
                id,
                sampleRate,
                progressMs,
                speed,
                radius,
                pos,
                track
        ));
    }
}
