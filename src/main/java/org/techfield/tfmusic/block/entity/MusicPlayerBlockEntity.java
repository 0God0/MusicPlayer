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
import org.techfield.tfmusic.music.MusicMedia;
import org.techfield.tfmusic.music.MusicPlaylist;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.music.PlaybackCursor;
import org.techfield.tfmusic.music.PlaybackQueue;
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

    public boolean insertMedia(ItemStack sourceStack) {
        if (!media.isEmpty() || !MusicMedia.isSupported(sourceStack)) {
            return false;
        }
        ItemStack inserted = MusicMedia.normalize(sourceStack);
        PlaybackCursor insertedCursor = PlaybackQueue.initial(inserted);
        MusicTrack track = insertedCursor.track();
        if (track == null) {
            return false;
        }

        media = inserted;
        sourceStack.shrink(1);
        if (level instanceof ServerLevel serverLevel) {
            speakerId = stableSpeakerId(serverLevel);
        } else {
            speakerId = UUID.randomUUID();
        }
        cursor = insertedCursor;
        speed = 1.0F;
        playing = true;
        speaker = null;
        if (level instanceof ServerLevel serverLevel) {
            speaker = createSpeaker(serverLevel, track);
            speaker.tick();
        }
        updateBlockState(true);
        setChangedAndSync();
        return true;
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
        playlist.write(media);
        cursor = PlaybackQueue.synchronize(media, cursor);
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
    }

    @Override
    public void load(CompoundTag tag) {
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
        speed = sanitizeSpeed(tag.getFloat("Speed"));
        radius = sanitizeRadius(tag.getDouble("Radius"));
        speaker = null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
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
            } else {
                setChangedAndSync();
            }
            return;
        }
        cursor = cursor.withProgress(speaker.progressMs());
        if (++saveTicker >= 20) {
            saveTicker = 0;
            setChanged();
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
                worldPosition
        );
    }

    private static float sanitizeSpeed(float value) {
        return Float.isFinite(value) && value > 0.0F ? value : 1.0F;
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
