package org.techfield.tfmusic.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.techfield.tfmusic.block.entity.MusicPlayerBlockEntity;
import org.techfield.tfmusic.network.packet.SpeakerStartS2CPacket;
import org.techfield.tfmusic.registry.ModSounds;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class StreamingSoundInstance extends AbstractTickableSoundInstance {
    private final SpeakerStartS2CPacket.Mode mode;
    private final int ownerEntityId;
    private final BlockPos sourcePos;
    private final UUID speakerId;
    private final ClientAudioCache.AudioLoad audioLoad;

    public StreamingSoundInstance(SpeakerStartS2CPacket packet) {
        super(ModSounds.STREAM.get(), net.minecraft.sounds.SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.mode = packet.mode();
        this.ownerEntityId = packet.ownerEntityId();
        this.sourcePos = packet.pos();
        this.speakerId = packet.speakerId();
        this.audioLoad = ClientAudioCache.load(packet);
        this.pitch = packet.speed();
        this.looping = false;
        this.delay = 0;

        if (mode.isStereo()) {
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
            this.volume = 1.0F;
            this.x = 0.0D;
            this.y = 0.0D;
            this.z = 0.0D;
        } else {
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.volume = (float) (packet.radius() / 16.0D);
            if (mode.isBlockSource()) {
                this.x = packet.pos().getX() + 0.5D;
                this.y = packet.pos().getY() + 0.5D;
                this.z = packet.pos().getZ() + 0.5D;
            } else {
                updateOwnerPosition();
            }
        }
    }

    @Override
    public CompletableFuture<AudioStream> getStream(
            SoundBufferLibrary soundBuffers,
            Sound sound,
            boolean looping
    ) {
        return audioLoad.future();
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            terminate();
            return;
        }
        if (mode == SpeakerStartS2CPacket.Mode.RANGE) {
            return;
        }
        if (mode == SpeakerStartS2CPacket.Mode.GLOBAL_RANGE) {
            if (!minecraft.level.hasChunkAt(sourcePos)
                    || !(minecraft.level.getBlockEntity(sourcePos) instanceof MusicPlayerBlockEntity block)
                    || block.isRemoved() || !block.getSpeakerId().equals(speakerId)) {
                terminate();
            }
        } else if (mode == SpeakerStartS2CPacket.Mode.FOLLOW) {
            if (!updateOwnerPosition()) {
                terminate();
            }
        } else {
            Entity owner = minecraft.level.getEntity(ownerEntityId);
            if (owner == null || owner.isRemoved()) {
                terminate();
            }
        }
    }

    public void terminate() {
        stop();
        audioLoad.cancel();
    }

    public CompletableFuture<AudioStream> audioFuture() {
        return audioLoad.future();
    }

    private boolean updateOwnerPosition() {
        if (Minecraft.getInstance().level == null) {
            return false;
        }
        Entity owner = Minecraft.getInstance().level.getEntity(ownerEntityId);
        if (owner == null || owner.isRemoved()) {
            return false;
        }
        this.x = owner.getX();
        this.y = owner.getY();
        this.z = owner.getZ();
        return true;
    }
}
