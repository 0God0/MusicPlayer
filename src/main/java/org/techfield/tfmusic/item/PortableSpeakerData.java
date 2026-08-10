package org.techfield.tfmusic.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.techfield.tfmusic.TFMusic;
import org.techfield.tfmusic.registry.ModItems;

import java.util.UUID;

public record PortableSpeakerData(
        UUID speakerId,
        ItemStack disc,
        long progressMs,
        float speed,
        boolean playing,
        double radius
) {
    public static final double DEFAULT_RADIUS = 64.0D;
    public static final double MAX_RADIUS = 128.0D;
    public static final float MIN_SPEED = 0.5F;
    public static final float MAX_SPEED = 2.0F;

    private static final String ROOT_TAG = TFMusic.MOD_ID + ":portable_speaker";

    public static PortableSpeakerData createDefault() {
        return new PortableSpeakerData(UUID.randomUUID(), ItemStack.EMPTY, 0L, 1.0F, false, DEFAULT_RADIUS);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("SpeakerId", speakerId);
        if (!disc.isEmpty()) {
            tag.put("Disc", disc.save(new CompoundTag()));
        }
        tag.putLong("ProgressMs", Math.max(0L, progressMs));
        tag.putFloat("Speed", sanitizeSpeed(speed));
        tag.putBoolean("Playing", playing);
        tag.putDouble("Radius", sanitizeRadius(radius));
        return tag;
    }

    public static PortableSpeakerData load(ItemStack stack) {
        CompoundTag root = stack.getOrCreateTag();
        if (!root.contains(ROOT_TAG, CompoundTag.TAG_COMPOUND)) {
            PortableSpeakerData created = createDefault();
            created.write(stack);
            return created;
        }

        CompoundTag tag = root.getCompound(ROOT_TAG);
        boolean needsMigration = !tag.hasUUID("SpeakerId")
                || !tag.contains("ProgressMs", Tag.TAG_LONG)
                || !tag.contains("Speed", Tag.TAG_FLOAT)
                || !tag.contains("Playing", Tag.TAG_BYTE)
                || !tag.contains("Radius", Tag.TAG_DOUBLE);
        UUID id = tag.hasUUID("SpeakerId") ? tag.getUUID("SpeakerId") : UUID.randomUUID();
        ItemStack disc = tag.contains("Disc", CompoundTag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound("Disc"))
                : ItemStack.EMPTY;
        PortableSpeakerData loaded = new PortableSpeakerData(
                id,
                normalizeDisc(disc),
                tag.contains("ProgressMs", Tag.TAG_LONG) ? Math.max(0L, tag.getLong("ProgressMs")) : 0L,
                tag.contains("Speed", Tag.TAG_FLOAT) ? sanitizeSpeed(tag.getFloat("Speed")) : 1.0F,
                tag.contains("Playing", Tag.TAG_BYTE) && tag.getBoolean("Playing"),
                tag.contains("Radius", Tag.TAG_DOUBLE) ? sanitizeRadius(tag.getDouble("Radius")) : DEFAULT_RADIUS
        );
        if (needsMigration || !loaded.save().equals(tag)) {
            loaded.write(stack);
        }
        return loaded;
    }

    public void write(ItemStack stack) {
        stack.getOrCreateTag().put(ROOT_TAG, save());
    }

    public PortableSpeakerData withDisc(ItemStack newDisc) {
        return new PortableSpeakerData(speakerId, normalizeDisc(newDisc), 0L, speed, false, radius);
    }

    public PortableSpeakerData withPlayback(long newProgress, float newSpeed, boolean newPlaying) {
        return new PortableSpeakerData(
                speakerId,
                disc,
                Math.max(0L, newProgress),
                sanitizeSpeed(newSpeed),
                newPlaying,
                sanitizeRadius(radius)
        );
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

    private static ItemStack normalizeDisc(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModItems.MUSIC_DISC.get())) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
