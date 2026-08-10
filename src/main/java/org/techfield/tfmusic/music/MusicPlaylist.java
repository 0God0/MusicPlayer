package org.techfield.tfmusic.music;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.techfield.tfmusic.TFMusic;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record MusicPlaylist(
        UUID id,
        String name,
        int revision,
        PlaybackMode mode,
        List<PlaylistEntry> entries
) {
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_TRACKS = 64;
    public static final int MAX_TEXT_BYTES = 96 * 1024;

    private static final String STACK_TAG = TFMusic.MOD_ID + ":playlist";
    private static final String ID_TAG = "Id";
    private static final String NAME_TAG = "Name";
    private static final String REVISION_TAG = "Revision";
    private static final String MODE_TAG = "Mode";
    private static final String ENTRIES_TAG = "Entries";

    public MusicPlaylist {
        if (id == null) {
            throw new IllegalArgumentException("Playlist id is required");
        }
        name = name == null ? "" : name.trim();
        mode = mode == null ? PlaybackMode.SEQUENTIAL : mode;
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static MusicPlaylist createDefault() {
        return new MusicPlaylist(UUID.randomUUID(), "", 0, PlaybackMode.SEQUENTIAL, List.of());
    }

    public String validate() {
        if (name.length() > MAX_NAME_LENGTH) {
            return "Playlist name exceeds " + MAX_NAME_LENGTH + " characters";
        }
        if (entries.size() > MAX_TRACKS) {
            return "Playlist exceeds " + MAX_TRACKS + " tracks";
        }

        Set<UUID> ids = new HashSet<>();
        Set<MusicTrack> tracks = new HashSet<>();
        int textBytes = utf8Length(name);
        for (PlaylistEntry entry : entries) {
            if (entry == null || entry.track().validate() != null || !ids.add(entry.id()) || !tracks.add(entry.track())) {
                return "Playlist contains an invalid or duplicate entry";
            }
            textBytes += utf8Length(entry.track().name());
            textBytes += utf8Length(entry.track().locator());
            if (textBytes > MAX_TEXT_BYTES) {
                return "Playlist metadata exceeds " + MAX_TEXT_BYTES + " bytes";
            }
        }
        return null;
    }

    public boolean containsTrack(MusicTrack track) {
        return entries.stream().anyMatch(entry -> entry.track().equals(track));
    }

    public Optional<PlaylistEntry> findEntry(UUID entryId) {
        return entries.stream().filter(entry -> entry.id().equals(entryId)).findFirst();
    }

    public int indexOf(UUID entryId) {
        if (entryId == null) {
            return -1;
        }
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).id().equals(entryId)) {
                return index;
            }
        }
        return -1;
    }

    public MusicPlaylist add(MusicTrack track) {
        if (track == null || track.validate() != null || containsTrack(track) || entries.size() >= MAX_TRACKS) {
            return this;
        }
        List<PlaylistEntry> updated = new ArrayList<>(entries);
        updated.add(PlaylistEntry.create(track));
        MusicPlaylist result = withEntries(updated);
        return result.validate() == null ? result : this;
    }

    public MusicPlaylist remove(UUID entryId) {
        List<PlaylistEntry> updated = entries.stream()
                .filter(entry -> !entry.id().equals(entryId))
                .toList();
        return updated.size() == entries.size() ? this : withEntries(updated);
    }

    public MusicPlaylist move(UUID entryId, int direction) {
        int from = indexOf(entryId);
        int to = from + Integer.signum(direction);
        if (from < 0 || to < 0 || to >= entries.size()) {
            return this;
        }
        List<PlaylistEntry> updated = new ArrayList<>(entries);
        PlaylistEntry moving = updated.remove(from);
        updated.add(to, moving);
        return withEntries(updated);
    }

    public MusicPlaylist rename(String newName) {
        String sanitized = newName == null ? "" : newName.trim();
        if (sanitized.length() > MAX_NAME_LENGTH || sanitized.equals(name)) {
            return this;
        }
        return new MusicPlaylist(id, sanitized, nextRevision(), mode, entries);
    }

    public MusicPlaylist withMode(PlaybackMode newMode) {
        PlaybackMode sanitized = newMode == null ? PlaybackMode.SEQUENTIAL : newMode;
        if (sanitized == mode) {
            return this;
        }
        return new MusicPlaylist(id, name, nextRevision(), sanitized, entries);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID_TAG, id);
        tag.putString(NAME_TAG, name);
        tag.putInt(REVISION_TAG, Math.max(0, revision));
        tag.putString(MODE_TAG, mode.serializedName());
        ListTag entryTags = new ListTag();
        for (PlaylistEntry entry : entries) {
            entryTags.add(entry.save());
        }
        tag.put(ENTRIES_TAG, entryTags);
        return tag;
    }

    public static Optional<MusicPlaylist> load(CompoundTag tag) {
        if (!tag.hasUUID(ID_TAG)) {
            return Optional.empty();
        }
        try {
            ListTag entryTags = tag.getList(ENTRIES_TAG, CompoundTag.TAG_COMPOUND);
            if (entryTags.size() > MAX_TRACKS) {
                return Optional.empty();
            }
            List<PlaylistEntry> entries = new ArrayList<>(entryTags.size());
            for (int index = 0; index < entryTags.size(); index++) {
                Optional<PlaylistEntry> entry = PlaylistEntry.load(entryTags.getCompound(index));
                if (entry.isEmpty()) {
                    return Optional.empty();
                }
                entries.add(entry.get());
            }
            MusicPlaylist playlist = new MusicPlaylist(
                    tag.getUUID(ID_TAG),
                    tag.getString(NAME_TAG),
                    Math.max(0, tag.getInt(REVISION_TAG)),
                    PlaybackMode.fromSerializedName(tag.getString(MODE_TAG)),
                    entries
            );
            return playlist.validate() == null ? Optional.of(playlist) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public void write(ItemStack stack) {
        stack.getOrCreateTag().put(STACK_TAG, save());
    }

    public static Optional<MusicPlaylist> fromStack(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(STACK_TAG, CompoundTag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        return load(root.getCompound(STACK_TAG));
    }

    public static Optional<MusicPlaylist> getOrCreate(ItemStack stack) {
        if (hasSerializedData(stack)) {
            return fromStack(stack);
        }
        MusicPlaylist created = createDefault();
        created.write(stack);
        return Optional.of(created);
    }

    public static boolean hasSerializedData(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root != null && root.contains(STACK_TAG, CompoundTag.TAG_COMPOUND);
    }

    private MusicPlaylist withEntries(List<PlaylistEntry> updated) {
        return new MusicPlaylist(id, name, nextRevision(), mode, updated);
    }

    private int nextRevision() {
        return revision == Integer.MAX_VALUE ? 0 : revision + 1;
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
