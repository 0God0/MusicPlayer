package org.techfield.tfmusic.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record PlaylistEditorTarget(Type type, UUID id, BlockPos pos, int slot) {
    private static final UUID EMPTY_ID = new UUID(0L, 0L);

    public enum Type {
        HELD,
        SPEAKER,
        BLOCK
    }

    public PlaylistEditorTarget {
        type = type == null ? Type.HELD : type;
        id = id == null ? EMPTY_ID : id;
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        slot = type == Type.HELD ? slot : -1;
    }

    public static PlaylistEditorTarget held(UUID playlistId, int slot) {
        return new PlaylistEditorTarget(Type.HELD, playlistId, BlockPos.ZERO, slot);
    }

    public static PlaylistEditorTarget speaker(UUID speakerId) {
        return new PlaylistEditorTarget(Type.SPEAKER, speakerId, BlockPos.ZERO, -1);
    }

    public static PlaylistEditorTarget block(BlockPos pos) {
        return new PlaylistEditorTarget(Type.BLOCK, EMPTY_ID, pos, -1);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeEnum(type);
        buffer.writeUUID(id);
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(slot);
    }

    public static PlaylistEditorTarget read(FriendlyByteBuf buffer) {
        return new PlaylistEditorTarget(
                buffer.readEnum(Type.class),
                buffer.readUUID(),
                buffer.readBlockPos(),
                buffer.readVarInt()
        );
    }
}
