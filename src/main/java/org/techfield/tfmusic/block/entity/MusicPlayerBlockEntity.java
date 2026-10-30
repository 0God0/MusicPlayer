package org.techfield.tfmusic.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.techfield.tfmusic.block.MusicPlayerBlock;
import org.techfield.tfmusic.item.PortableSpeakerData;
import org.techfield.tfmusic.music.MusicMedia;
import org.techfield.tfmusic.music.MusicPlaylist;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.music.PlaybackCursor;
import org.techfield.tfmusic.music.PlaybackQueue;
import org.techfield.tfmusic.music.SoundMode;
import org.techfield.tfmusic.network.packet.SpeakerControlC2SPacket;
import org.techfield.tfmusic.registry.ModBlockEntities;
import org.techfield.tfmusic.server.sound.RangeServerSpeaker;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MusicPlayerBlockEntity extends BlockEntity {
    public static final int SAMPLE_RATE = 44_100;
    public static final double DEFAULT_RADIUS = 64.0D;

    private ItemStack media = ItemStack.EMPTY;
    private UUID speakerId = UUID.randomUUID();
    private boolean playing;
    private PlaybackCursor cursor = PlaybackQueue.initial(ItemStack.EMPTY);
    private float speed = 1.0F;
    private double radius = DEFAULT_RADIUS;
    private SoundMode soundMode = SoundMode.SPATIAL;
    private int saveTicker;
    private RangeServerSpeaker speaker;

    public MusicPlayerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MUSIC_PLAYER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MusicPlayerBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        blockEntity.tickPlayback();
    }

    public void setMedia(ItemStack stack) {
        if (!stack.isEmpty() && !MusicMedia.isSupported(stack)) {
            return;
        }
        stopSpeaker(false);
        media = MusicMedia.normalize(stack);
        cursor = PlaybackQueue.initial(media);
        playing = false;
        ensureSpeakerId();
        updateBlockState(!media.isEmpty());
        setChangedAndSync();
    }

    public PortableSpeakerData speakerData() {
        return new PortableSpeakerData(speakerId, media, cursor, speed, playing, radius, soundMode);
    }

    public void ensureSpeakerId() {
        if (level instanceof ServerLevel serverLevel && !speakerId.equals(stableSpeakerId(serverLevel))) {
            stopSpeaker(false);
            speakerId = stableSpeakerId(serverLevel);
            setChangedAndSync();
        }
    }

    public void handleControl(SpeakerControlC2SPacket packet) {
        if (!(level instanceof ServerLevel serverLevel) || !speakerId.equals(packet.speakerId())) {
            return;
        }
        cursor = PlaybackQueue.synchronize(media, cursor);
        if (speaker != null) {
            cursor = cursor.withProgress(speaker.captureProgress());
        }
        MusicTrack track = cursor.track();
        switch (packet.action()) {
            case PLAY -> {
                if (track == null) {
                    return;
                }
                playing = true;
                if (speaker != null) {
                    speaker.resumeAndNotify();
                }
            }
            case PAUSE -> {
                stopSpeaker(true);
                playing = false;
            }
            case STOP -> {
                stopSpeaker(false);
                playing = false;
                cursor = PlaybackQueue.initial(media);
            }
            case SEEK -> {
                if (track == null) {
                    return;
                }
                cursor = cursor.withProgress(Math.max(0L, Math.min(track.durationMs(), packet.seekMs())));
                if (speaker != null) {
                    speaker.seek(cursor.progressMs());
                }
            }
            case SPEED -> {
                speed = PortableSpeakerData.sanitizeSpeed(packet.speed());
                if (speaker != null) {
                    speaker.setSpeed(speed);
                }
            }
            case NEXT, PREVIOUS -> {
                stopSpeaker(false);
                if (packet.action() == SpeakerControlC2SPacket.Action.NEXT) {
                    PlaybackQueue.AdvanceResult result = PlaybackQueue.advance(media, cursor, true);
                    cursor = result.cursor();
                    playing = playing && result.continuePlaying();
                } else {
                    cursor = cursor.progressMs() > 3_000L
                            ? cursor.withProgress(0L) : PlaybackQueue.previous(media, cursor);
                }
            }
            case MODE -> {
                MusicPlaylist playlist = MusicPlaylist.fromStack(media).orElse(null);
                if (playlist == null || packet.mode() == null) {
                    return;
                }
                updatePlaylist(playlist.withMode(packet.mode()));
            }
            case SOUND_MODE -> {
                if (packet.soundMode() == null || packet.soundMode() == soundMode) {
                    return;
                }
                stopSpeaker(false);
                soundMode = packet.soundMode();
            }
        }
        if (playing && cursor.track() != null && speaker == null) {
            speaker = createSpeaker(serverLevel, cursor.track());
            speaker.tick();
        }
        setChangedAndSync();
    }

    private void stopSpeaker(boolean pause) {
        if (speaker != null) {
            if (pause) {
                speaker.pauseAndNotify();
            } else {
                speaker.stopAndNotify();
            }
            speaker = null;
        }
    }

    public ItemStack ejectMedia() {
        if (speaker != null) {
            speaker.stopAndNotify();
            speaker = null;
        }
        ItemStack result = media;
        media = ItemStack.EMPTY;
        playing = false;
        cursor = PlaybackQueue.initial(ItemStack.EMPTY);
        updateBlockState(false);
        setChangedAndSync();
        if (!result.isEmpty() && level instanceof ServerLevel serverLevel) {
            Containers.dropItemStack(
                    serverLevel,
                    worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 1.05D,
                    worldPosition.getZ() + 0.5D,
                    result
            );
        }
        return result;
    }

    public void dropMedia() {
        ejectMedia();
    }

    public boolean isPlaying() {
        return playing;
    }

    public ItemStack getMedia() {
        return media.copy();
    }

    public boolean updatePlaylist(MusicPlaylist playlist) {
        MusicPlaylist current = MusicPlaylist.fromStack(media).orElse(null);
        if (current == null || !current.id().equals(playlist.id()) || playlist.validate() != null) {
            return false;
        }
        ItemStack editedMedia = media.copy();
        playlist.write(editedMedia);
        PortableSpeakerData updated = speakerData().withEditedMedia(editedMedia);
        media = updated.media();
        cursor = updated.cursor();
        setChangedAndSync();
        return true;
    }

    public UUID getSpeakerId() {
        return speakerId;
    }

    public long getProgressMs() {
        return cursor.progressMs();
    }

    public float getSpeed() {
        return speed;
    }

    public double getRadius() {
        return radius;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (speaker != null && speaker.isWorking()) {
            cursor = cursor.withProgress(speaker.captureProgress());
        }
        if (!media.isEmpty()) {
            tag.put("Media", media.save(new CompoundTag()));
        }
        tag.putUUID("SpeakerId", speakerId);
        tag.putBoolean("Playing", playing);
        tag.put("Cursor", cursor.save());
        tag.putFloat("Speed", speed);
        tag.putDouble("Radius", radius);
        tag.putString("SoundMode", soundMode.serializedName());
    }

    @Override
    public void load(CompoundTag tag) {
        stopSpeaker(false);
        super.load(tag);
        media = tag.contains("Media", CompoundTag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound("Media"))
                : tag.contains("Disc", CompoundTag.TAG_COMPOUND)
                        ? ItemStack.of(tag.getCompound("Disc"))
                : ItemStack.EMPTY;
        media = MusicMedia.normalize(media);
        speakerId = tag.hasUUID("SpeakerId") ? tag.getUUID("SpeakerId") : UUID.randomUUID();
        cursor = tag.contains("Cursor", CompoundTag.TAG_COMPOUND)
                ? PlaybackCursor.load(tag.getCompound("Cursor"), media)
                : PlaybackQueue.initial(media).withProgress(tag.getLong("ProgressMs"));
        playing = tag.getBoolean("Playing") && cursor.track() != null;
        speed = tag.contains("Speed") ? PortableSpeakerData.sanitizeSpeed(tag.getFloat("Speed")) : 1.0F;
        radius = sanitizeRadius(tag.getDouble("Radius"));
        soundMode = SoundMode.fromSerializedName(tag.getString("SoundMode"));
        speaker = null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ensureSpeakerId();
        updateBlockState(!media.isEmpty());
    }

    @Override
    public void setRemoved() {
        if (speaker != null) {
            cursor = cursor.withProgress(speaker.captureProgress());
            speaker.detach();
            speaker = null;
            setChanged();
        }
        super.setRemoved();
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            load(tag);
        }
    }

    private void tickPlayback() {
        cursor = PlaybackQueue.synchronize(media, cursor);
        if (!playing) {
            if (speaker != null) {
                speaker.stopAndNotify();
                speaker = null;
            }
            return;
        }
        MusicTrack track = cursor.track();
        if (track == null) {
            playing = false;
            cursor = PlaybackQueue.initial(media);
            setChangedAndSync();
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        UUID stableId = stableSpeakerId(serverLevel);
        if (!speakerId.equals(stableId)) {
            if (speaker != null) {
                speaker.stopAndNotify();
                speaker = null;
            }
            speakerId = stableId;
            setChanged();
        }
        if (speaker == null || !speaker.track().equals(track)) {
            if (speaker != null) {
                speaker.stopAndNotify();
            }
            speaker = createSpeaker(serverLevel, track);
        }

        if (!speaker.tick()) {
            speaker = null;
            PlaybackQueue.AdvanceResult result = PlaybackQueue.advance(media, cursor, false);
            cursor = result.cursor();
            playing = result.continuePlaying();
            if (playing && cursor.track() != null) {
                speaker = createSpeaker(serverLevel, cursor.track());
                speaker.tick();
            }
            setChangedAndSync();
            return;
        }
        cursor = cursor.withProgress(speaker.progressMs());
        if (++saveTicker >= 20) {
            saveTicker = 0;
            setChangedAndSync();
        }
    }

    private void updateBlockState(boolean hasDisc) {
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(MusicPlayerBlock.HAS_DISC) && state.getValue(MusicPlayerBlock.HAS_DISC) != hasDisc) {
            level.setBlock(worldPosition, state.setValue(MusicPlayerBlock.HAS_DISC, hasDisc), Block.UPDATE_ALL);
        }
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }
    }

    private RangeServerSpeaker createSpeaker(ServerLevel serverLevel, MusicTrack track) {
        return new RangeServerSpeaker(
                speakerId,
                SAMPLE_RATE,
                track,
                cursor.progressMs(),
                speed,
                radius,
                serverLevel,
                worldPosition,
                soundMode
        );
    }

    private static double sanitizeRadius(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            return DEFAULT_RADIUS;
        }
        return Math.min(128.0D, value);
    }

    private UUID stableSpeakerId(ServerLevel serverLevel) {
        String identity = serverLevel.dimension().location() + "@" + worldPosition.asLong();
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }
}
