package org.techfield.tfmusic.music;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.techfield.tfmusic.TFMusic;

import java.util.Optional;

public record MusicTrack(
        MusicSource source,
        String name,
        long durationMs,
        String locator,
        int page
) {
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_LOCATOR_LENGTH = 1024;
    public static final long MAX_DURATION_MS = 2L * 60L * 60L * 1000L;
    public static final int MIN_BILIBILI_PAGE = 1;
    public static final int MAX_BILIBILI_PAGE = 128;

    private static final String STACK_TAG = TFMusic.MOD_ID + ":track";
    private static final String SOURCE_TAG = "Source";
    private static final String NAME_TAG = "Name";
    private static final String DURATION_TAG = "DurationMs";
    private static final String LOCATOR_TAG = "Locator";
    private static final String PAGE_TAG = "Page";

    public MusicTrack {
        if (source == null) {
            throw new IllegalArgumentException("Music source is required");
        }
        name = name == null ? "" : name.trim();
        locator = locator == null ? "" : locator.trim();
    }

    public String validate() {
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            return "Track name must contain 1-" + MAX_NAME_LENGTH + " characters";
        }
        if (durationMs <= 0L || durationMs > MAX_DURATION_MS) {
            return "Track duration must be between 1 ms and 2 hours";
        }
        if (locator.isBlank() || locator.length() > MAX_LOCATOR_LENGTH) {
            return "Track locator must contain 1-" + MAX_LOCATOR_LENGTH + " characters";
        }
        String locatorError = source.validateLocator(locator);
        if (locatorError != null) {
            return locatorError;
        }
        if (source == MusicSource.BILIBILI && (page < MIN_BILIBILI_PAGE || page > MAX_BILIBILI_PAGE)) {
            return "BiliBili page must be between 1 and 128";
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(SOURCE_TAG, source.serializedName());
        tag.putString(NAME_TAG, name);
        tag.putLong(DURATION_TAG, durationMs);
        tag.putString(LOCATOR_TAG, locator);
        tag.putInt(PAGE_TAG, page);
        return tag;
    }

    public static Optional<MusicTrack> load(CompoundTag tag) {
        try {
            MusicTrack track = new MusicTrack(
                    MusicSource.fromSerializedName(tag.getString(SOURCE_TAG)),
                    tag.getString(NAME_TAG),
                    tag.getLong(DURATION_TAG),
                    tag.getString(LOCATOR_TAG),
                    Math.max(MIN_BILIBILI_PAGE, tag.getInt(PAGE_TAG))
            );
            return track.validate() == null ? Optional.of(track) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(source.serializedName(), 32);
        buffer.writeUtf(name, MAX_NAME_LENGTH);
        buffer.writeLong(durationMs);
        buffer.writeUtf(locator, MAX_LOCATOR_LENGTH);
        buffer.writeVarInt(page);
    }

    public static MusicTrack read(FriendlyByteBuf buffer) {
        return new MusicTrack(
                MusicSource.fromSerializedName(buffer.readUtf(32)),
                buffer.readUtf(MAX_NAME_LENGTH),
                buffer.readLong(),
                buffer.readUtf(MAX_LOCATOR_LENGTH),
                buffer.readVarInt()
        );
    }

    public void writeToDisc(ItemStack stack) {
        stack.getOrCreateTag().put(STACK_TAG, save());
    }

    public static Optional<MusicTrack> fromDisc(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(STACK_TAG, CompoundTag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        return load(root.getCompound(STACK_TAG));
    }
}
