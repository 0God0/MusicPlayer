package org.techfield.tfmusic.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.techfield.tfmusic.menu.PortableSpeakerMenu;
import org.techfield.tfmusic.music.PlaybackMode;
import org.techfield.tfmusic.music.SoundMode;
import org.techfield.tfmusic.server.PortableSpeakerManager;

import java.util.UUID;
import java.util.function.Supplier;

public record SpeakerControlC2SPacket(
        UUID speakerId,
        Action action,
        long seekMs,
        float speed,
        PlaybackMode mode,
        SoundMode soundMode
) {
    public enum Action {
        PLAY,
        PAUSE,
        STOP,
        SEEK,
        SPEED,
        NEXT,
        PREVIOUS,
        MODE,
        SOUND_MODE
    }

    public SpeakerControlC2SPacket(UUID speakerId, Action action, long seekMs, float speed) {
        this(speakerId, action, seekMs, speed, null, null);
    }

    public SpeakerControlC2SPacket(UUID speakerId, Action action, long seekMs, float speed, PlaybackMode mode) {
        this(speakerId, action, seekMs, speed, mode, null);
    }

    public static void encode(SpeakerControlC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.speakerId);
        buffer.writeEnum(packet.action);
        buffer.writeLong(packet.seekMs);
        buffer.writeFloat(packet.speed);
        buffer.writeBoolean(packet.mode != null);
        if (packet.mode != null) {
            buffer.writeEnum(packet.mode);
        }
        buffer.writeBoolean(packet.soundMode != null);
        if (packet.soundMode != null) {
            buffer.writeEnum(packet.soundMode);
        }
    }

    public static SpeakerControlC2SPacket decode(FriendlyByteBuf buffer) {
        return new SpeakerControlC2SPacket(
                buffer.readUUID(),
                buffer.readEnum(Action.class),
                buffer.readLong(),
                buffer.readFloat(),
                buffer.readBoolean() ? buffer.readEnum(PlaybackMode.class) : null,
                buffer.readBoolean() ? buffer.readEnum(SoundMode.class) : null
        );
    }

    public static void handle(SpeakerControlC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer sender = contextSupplier.get().getSender();
        if (sender != null) {
            if (sender.containerMenu instanceof PortableSpeakerMenu menu && menu.isBlockSpeaker()) {
                if (menu.speakerId().equals(packet.speakerId()) && menu.stillValid(sender)) {
                    menu.blockSpeaker().handleControl(packet);
                }
                return;
            }
            PortableSpeakerManager.handleControl(sender, packet);
        }
    }
}
