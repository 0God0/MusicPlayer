package org.techfield.tfmusic.client;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.techfield.tfmusic.client.audio.StreamingSoundInstance;
import org.techfield.tfmusic.client.screen.BurnerScreen;
import org.techfield.tfmusic.network.packet.BurnResultS2CPacket;
import org.techfield.tfmusic.network.packet.SpeakerStartS2CPacket;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class ClientPlaybackManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MIN_SAMPLE_RATE = 8_000;
    private static final int MAX_SAMPLE_RATE = 192_000;
    private static final Map<UUID, StreamingSoundInstance> SOUNDS = new HashMap<>();

    private ClientPlaybackManager() {
    }

    public static void start(SpeakerStartS2CPacket packet) {
        if (packet.sampleRate() < MIN_SAMPLE_RATE
                || packet.sampleRate() > MAX_SAMPLE_RATE
                || packet.progressMs() < 0L
                || packet.track().validate() != null
                || packet.progressMs() > packet.track().durationMs()
                || !Float.isFinite(packet.speed())
                || packet.speed() < 0.5F
                || packet.speed() > 2.0F
                || !Double.isFinite(packet.radius())
                || packet.radius() <= 0.0D
                || packet.radius() > 128.0D) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        StreamingSoundInstance sound = new StreamingSoundInstance(packet);
        StreamingSoundInstance old = SOUNDS.put(packet.speakerId(), sound);
        if (old != null) {
            old.terminate();
            minecraft.getSoundManager().stop(old);
        }
        minecraft.getSoundManager().play(sound);

        if (packet.mode() == SpeakerStartS2CPacket.Mode.OWNER) {
            ClientSpeakerTracker.onStart(
                    packet.speakerId(),
                    packet.track().durationMs(),
                    packet.progressMs(),
                    packet.speed()
            );
        }

        sound.audioFuture().whenComplete((stream, error) -> minecraft.execute(() -> {
            if (error == null) {
                minecraft.gui.setOverlayMessage(
                        Component.translatable("message.tf_music.now_playing", packet.track().name()),
                        true
                );
            } else if (!sound.audioFuture().isCancelled()) {
                LOGGER.error("Failed to load audio for speaker {}", packet.speakerId(), error);
                String message = rootMessage(error);
                minecraft.gui.setOverlayMessage(
                        Component.translatable("message.tf_music.load_failed", message)
                                .withStyle(ChatFormatting.RED),
                        false
                );
                if (SOUNDS.get(packet.speakerId()) == sound) {
                    SOUNDS.remove(packet.speakerId());
                    sound.terminate();
                    minecraft.getSoundManager().stop(sound);
                    if (packet.mode() == SpeakerStartS2CPacket.Mode.OWNER) {
                        ClientSpeakerTracker.onPause(packet.speakerId());
                    }
                }
            }
        }));
    }

    public static void pause(UUID speakerId) {
        removeSound(speakerId);
        ClientSpeakerTracker.onPause(speakerId);
    }

    public static void stop(UUID speakerId) {
        removeSound(speakerId);
        ClientSpeakerTracker.onStop(speakerId);
    }

    public static void handleBurnResult(BurnResultS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof BurnerScreen screen) {
            screen.handleBurnResult(packet.success(), packet.message());
            return;
        }
        minecraft.gui.setOverlayMessage(
                Component.literal(packet.message())
                        .withStyle(packet.success() ? ChatFormatting.GREEN : ChatFormatting.RED),
                false
        );
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (StreamingSoundInstance sound : SOUNDS.values()) {
            sound.terminate();
            minecraft.getSoundManager().stop(sound);
        }
        SOUNDS.clear();
        ClientSpeakerTracker.clear();
    }

    private static void removeSound(UUID speakerId) {
        StreamingSoundInstance sound = SOUNDS.remove(speakerId);
        if (sound != null) {
            sound.terminate();
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
