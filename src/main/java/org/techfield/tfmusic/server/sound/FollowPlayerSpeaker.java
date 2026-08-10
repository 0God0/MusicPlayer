package org.techfield.tfmusic.server.sound;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.network.ModNetwork;
import org.techfield.tfmusic.network.packet.SpeakerStartS2CPacket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class FollowPlayerSpeaker extends AbstractServerSpeaker {
    private final ServerPlayer owner;
    private final ServerLevel ownerLevel;

    public FollowPlayerSpeaker(
            UUID id,
            int sampleRate,
            MusicTrack track,
            long progressMs,
            float speed,
            double radius,
            ServerPlayer owner
    ) {
        super(id, sampleRate, track, progressMs, speed, radius);
        this.owner = owner;
        this.ownerLevel = owner.serverLevel();
    }

    public boolean isOwnedBy(ServerPlayer player) {
        return owner == player;
    }

    public boolean isInOwnerLevel() {
        return owner.serverLevel() == ownerLevel;
    }

    @Override
    protected ServerLevel level() {
        return owner.serverLevel();
    }

    @Override
    protected Collection<ServerPlayer> findListeners() {
        ServerLevel level = level();
        double packetRadius = radius + 32.0D;
        double radiusSquared = packetRadius * packetRadius;
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player == owner || player.distanceToSqr(owner) <= radiusSquared) {
                players.add(player);
            }
        }
        return players;
    }

    @Override
    protected void sendStart(ServerPlayer player) {
        if (player == owner) {
            ModNetwork.sendTo(player, SpeakerStartS2CPacket.owner(
                    id,
                    sampleRate,
                    progressMs,
                    speed,
                    track
            ));
        } else {
            ModNetwork.sendTo(player, SpeakerStartS2CPacket.follow(
                    id,
                    sampleRate,
                    progressMs,
                    speed,
                    radius,
                    owner.getId(),
                    track
            ));
        }
    }
}
