package org.techfield.tfmusic.music;

import net.minecraft.nbt.CompoundTag;

import java.util.Optional;
import java.util.UUID;

public record PlaylistEntry(UUID id, MusicTrack track) {
    private static final String ID_TAG = "Id";
    private static final String TRACK_TAG = "Track";

    public PlaylistEntry {
        if (id == null || track == null || track.validate() != null) {
            throw new IllegalArgumentException("Playlist entries require a valid id and track");
        }
    }

    public static PlaylistEntry create(MusicTrack track) {
        return new PlaylistEntry(UUID.randomUUID(), track);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID_TAG, id);
        tag.put(TRACK_TAG, track.save());
        return tag;
    }

    public static Optional<PlaylistEntry> load(CompoundTag tag) {
        if (!tag.hasUUID(ID_TAG) || !tag.contains(TRACK_TAG, CompoundTag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        return MusicTrack.load(tag.getCompound(TRACK_TAG))
                .map(track -> new PlaylistEntry(tag.getUUID(ID_TAG), track));
    }
}
