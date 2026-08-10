package org.techfield.tfmusic.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.techfield.tfmusic.menu.PlaylistEditorMenu;
import org.techfield.tfmusic.music.MusicPlaylist;
import org.techfield.tfmusic.music.PlaybackMode;

import java.util.UUID;
import java.util.function.Supplier;

public record PlaylistEditC2SPacket(
        int containerId,
        int expectedRevision,
        Action action,
        UUID entryId,
        int direction,
        String value,
        PlaybackMode mode
) {
    private static final UUID EMPTY_ID = new UUID(0L, 0L);

    public enum Action {
        ADD,
        REMOVE,
        MOVE,
        RENAME,
        MODE
    }

    public PlaylistEditC2SPacket {
        entryId = entryId == null ? EMPTY_ID : entryId;
        direction = Integer.signum(direction);
        value = value == null ? "" : value;
    }

    public static PlaylistEditC2SPacket add(int containerId, int revision) {
        return new PlaylistEditC2SPacket(containerId, revision, Action.ADD, EMPTY_ID, 0, "", null);
    }

    public static PlaylistEditC2SPacket remove(int containerId, int revision, UUID entryId) {
        return new PlaylistEditC2SPacket(containerId, revision, Action.REMOVE, entryId, 0, "", null);
    }

    public static PlaylistEditC2SPacket move(int containerId, int revision, UUID entryId, int direction) {
        return new PlaylistEditC2SPacket(containerId, revision, Action.MOVE, entryId, direction, "", null);
    }

    public static PlaylistEditC2SPacket rename(int containerId, int revision, String name) {
        return new PlaylistEditC2SPacket(containerId, revision, Action.RENAME, EMPTY_ID, 0, name, null);
    }

    public static PlaylistEditC2SPacket mode(int containerId, int revision, PlaybackMode mode) {
        return new PlaylistEditC2SPacket(containerId, revision, Action.MODE, EMPTY_ID, 0, "", mode);
    }

    public static void encode(PlaylistEditC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        buffer.writeVarInt(packet.expectedRevision);
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.entryId);
        buffer.writeByte(packet.direction);
        buffer.writeUtf(packet.value, MusicPlaylist.MAX_NAME_LENGTH);
        buffer.writeBoolean(packet.mode != null);
        if (packet.mode != null) {
            buffer.writeEnum(packet.mode);
        }
    }

    public static PlaylistEditC2SPacket decode(FriendlyByteBuf buffer) {
        return new PlaylistEditC2SPacket(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readEnum(Action.class),
                buffer.readUUID(),
                buffer.readByte(),
                buffer.readUtf(MusicPlaylist.MAX_NAME_LENGTH),
                buffer.readBoolean() ? buffer.readEnum(PlaybackMode.class) : null
        );
    }

    public static void handle(PlaylistEditC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer sender = contextSupplier.get().getSender();
        if (sender != null
                && sender.containerMenu instanceof PlaylistEditorMenu menu
                && menu.containerId == packet.containerId
                && menu.stillValid(sender)) {
            menu.applyEdit(sender, packet);
        }
    }
}
