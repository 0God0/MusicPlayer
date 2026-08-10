package org.techfield.tfmusic.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.techfield.tfmusic.TFMusic;
import org.techfield.tfmusic.music.MusicMedia;
import org.techfield.tfmusic.music.MusicPlaylist;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.music.PlaybackCursor;
import org.techfield.tfmusic.music.PlaybackQueue;

import java.util.UUID;

public record PortableSpeakerData(
        UUID speakerId,
        ItemStack media,
        PlaybackCursor cursor,
        float speed,
        boolean playing,
        double radius
) {
    public static final double DEFAULT_RADIUS = 64.0D;
    public static final double MAX_RADIUS = 128.0D;
    public static final float MIN_SPEED = 0.5F;
    public static final float MAX_SPEED = 2.0F;

    private static final String ROOT_TAG = TFMusic.MOD_ID + ":portable_speaker";

    public PortableSpeakerData {
        media = MusicMedia.normalize(media);
        cursor = cursor == null ? PlaybackQueue.initial(media) : PlaybackQueue.synchronize(media, cursor);
        speed = sanitizeSpeed(speed);
        radius = sanitizeRadius(radius);
        playing = playing && cursor.track() != null;
    }

    public static PortableSpeakerData createDefault() {
        ItemStack media = ItemStack.EMPTY;
        return new PortableSpeakerData(
                UUID.randomUUID(),
                media,
                PlaybackQueue.initial(media),
                1.0F,
                false,
                DEFAULT_RADIUS
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("SpeakerId", speakerId);
        if (!media.isEmpty()) {
            tag.put("Media", media.save(new CompoundTag()));
        }
        tag.put("Cursor", cursor.save());
        tag.putFloat("Speed", sanitizeSpeed(speed));
        tag.putBoolean("Playing", playing);
        tag.putDouble("Radius", sanitizeRadius(radius));
        return tag;
    }

    public static PortableSpeakerData load(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(ROOT_TAG, CompoundTag.TAG_COMPOUND)) {
            return createDefault();
        }

        CompoundTag tag = root.getCompound(ROOT_TAG);
        UUID id = tag.hasUUID("SpeakerId") ? tag.getUUID("SpeakerId") : UUID.randomUUID();
        ItemStack media = tag.contains("Media", CompoundTag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound("Media"))
                : tag.contains("Disc", CompoundTag.TAG_COMPOUND)
                        ? ItemStack.of(tag.getCompound("Disc"))
                : ItemStack.EMPTY;
        media = MusicMedia.normalize(media);
        PlaybackCursor cursor = tag.contains("Cursor", CompoundTag.TAG_COMPOUND)
                ? PlaybackCursor.load(tag.getCompound("Cursor"), media)
                : PlaybackQueue.initial(media).withProgress(
                        tag.contains("ProgressMs", Tag.TAG_LONG) ? tag.getLong("ProgressMs") : 0L
                );
        PortableSpeakerData loaded = new PortableSpeakerData(
                id,
                media,
                cursor,
                tag.contains("Speed", Tag.TAG_FLOAT) ? sanitizeSpeed(tag.getFloat("Speed")) : 1.0F,
                tag.contains("Playing", Tag.TAG_BYTE) && tag.getBoolean("Playing"),
                tag.contains("Radius", Tag.TAG_DOUBLE) ? sanitizeRadius(tag.getDouble("Radius")) : DEFAULT_RADIUS
        );
        return loaded;
    }

    public static PortableSpeakerData getOrCreate(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(ROOT_TAG, CompoundTag.TAG_COMPOUND)) {
            PortableSpeakerData created = createDefault();
            created.write(stack);
            return created;
        }
        PortableSpeakerData loaded = load(stack);
        CompoundTag stored = root.getCompound(ROOT_TAG);
        if (!loaded.save().equals(stored)) {
            loaded.write(stack);
        }
        return loaded;
    }

    public void write(ItemStack stack) {
        stack.getOrCreateTag().put(ROOT_TAG, save());
    }

    public PortableSpeakerData withMedia(ItemStack newMedia) {
        ItemStack normalized = MusicMedia.normalize(newMedia);
        return new PortableSpeakerData(
                speakerId,
                normalized,
                PlaybackQueue.initial(normalized),
                speed,
                false,
                radius
        );
    }

    public PortableSpeakerData withEditedMedia(ItemStack editedMedia) {
        ItemStack normalized = MusicMedia.normalize(editedMedia);
        PlaybackCursor synchronizedCursor = PlaybackQueue.synchronize(normalized, cursor);
        MusicPlaylist playlist = MusicPlaylist.fromStack(normalized).orElse(null);
        if (!playing
                && playlist != null
                && synchronizedCursor.entryId() != null
                && playlist.indexOf(synchronizedCursor.entryId()) < 0) {
            synchronizedCursor = PlaybackQueue.initial(normalized);
        }
        return new PortableSpeakerData(
                speakerId,
                normalized,
                synchronizedCursor,
                speed,
                playing,
                radius
        );
    }

    public PortableSpeakerData withPlayback(PlaybackCursor newCursor, float newSpeed, boolean newPlaying) {
        return new PortableSpeakerData(
                speakerId,
                media,
                newCursor,
                sanitizeSpeed(newSpeed),
                newPlaying,
                sanitizeRadius(radius)
        );
    }

    public PortableSpeakerData withPlayback(long newProgress, float newSpeed, boolean newPlaying) {
        return withPlayback(cursor.withProgress(newProgress), newSpeed, newPlaying);
    }

    public long progressMs() {
        return cursor.progressMs();
    }

    public MusicTrack currentTrack() {
        return cursor.track();
    }

    public static float sanitizeSpeed(float value) {
        if (!Float.isFinite(value)) {
            return 1.0F;
        }
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, value));
    }

    public static double sanitizeRadius(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            return DEFAULT_RADIUS;
        }
        return Math.min(MAX_RADIUS, value);
    }

}
