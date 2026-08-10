package org.techfield.tfmusic.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.techfield.tfmusic.client.ClientSpeakerTracker;
import org.techfield.tfmusic.item.PortableSpeakerData;
import org.techfield.tfmusic.menu.PortableSpeakerMenu;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.network.ModNetwork;
import org.techfield.tfmusic.network.packet.SpeakerControlC2SPacket;
import org.techfield.tfmusic.util.TimeUtil;

import java.util.Locale;

public final class PortableSpeakerScreen extends AbstractContainerScreen<PortableSpeakerMenu> {
    private Button playPauseButton;
    private ProgressSlider progressSlider;
    private long durationMs;

    public PortableSpeakerScreen(PortableSpeakerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 210;
        imageHeight = 176;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 24;
        inventoryLabelY = 82;
    }

    @Override
    protected void init() {
        super.init();
        PortableSpeakerData data = menu.speakerData();
        MusicTrack track = MusicTrack.fromDisc(data.disc()).orElse(null);
        durationMs = track == null ? 0L : track.durationMs();
        if (durationMs > 0L) {
            ClientSpeakerTracker.localSeek(menu.speakerId(), durationMs, data.progressMs(), data.playing());
            ClientSpeakerTracker.localSpeed(menu.speakerId(), durationMs, data.speed());
        }

        playPauseButton = addRenderableWidget(Button.builder(playPauseLabel(), button -> togglePlayback())
                .bounds(leftPos + 8, topPos + 24, 58, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("button.tf_music.stop"), button -> stopPlayback())
                .bounds(leftPos + 70, topPos + 24, 48, 20)
                .build());

        progressSlider = addRenderableWidget(new ProgressSlider(
                leftPos + 8,
                topPos + 49,
                154,
                18,
                progressRatio()
        ));

        float[] speeds = {0.5F, 1.0F, 1.5F, 2.0F};
        for (int index = 0; index < speeds.length; index++) {
            float speed = speeds[index];
            addRenderableWidget(Button.builder(
                            Component.literal(String.format(Locale.ROOT, "%.1fx", speed)),
                            button -> setSpeed(speed))
                    .bounds(leftPos + 8 + index * 39, topPos + 70, 36, 18)
                    .build());
        }
        updateControls();
    }

    @Override
    protected void containerTick() {
        PortableSpeakerData data = menu.speakerData();
        MusicTrack track = MusicTrack.fromDisc(data.disc()).orElse(null);
        durationMs = track == null ? 0L : track.durationMs();
        progressSlider.sync(progressRatio());
        updateControls();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0181D22);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF65717C);
        graphics.fill(leftPos + 1, topPos + 16, leftPos + imageWidth - 1, topPos + 17, 0xFF343D45);
        graphics.fill(leftPos + 1, topPos + 80, leftPos + imageWidth - 1, topPos + 81, 0xFF343D45);

        drawSlot(graphics, leftPos + 176, topPos + 26);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, leftPos + 24 + column * 18, topPos + 92 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(graphics, leftPos + 24 + column * 18, topPos + 150);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE7EDF2, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xB7C2CC, false);
        graphics.drawString(font, Component.translatable("label.tf_music.disc"), 169, 17, 0xB7C2CC, false);
    }

    private void togglePlayback() {
        if (durationMs <= 0L) {
            return;
        }
        PortableSpeakerData data = menu.speakerData();
        boolean playing = ClientSpeakerTracker.isPlaying(menu.speakerId());
        SpeakerControlC2SPacket.Action action = playing
                ? SpeakerControlC2SPacket.Action.PAUSE
                : SpeakerControlC2SPacket.Action.PLAY;
        ModNetwork.sendToServer(new SpeakerControlC2SPacket(menu.speakerId(), action, 0L, data.speed()));
        if (playing) {
            ClientSpeakerTracker.onPause(menu.speakerId());
        } else {
            ClientSpeakerTracker.localPlay(
                    menu.speakerId(),
                    durationMs,
                    ClientSpeakerTracker.progress(menu.speakerId(), durationMs),
                    data.speed()
            );
        }
        updateControls();
    }

    private void stopPlayback() {
        ModNetwork.sendToServer(new SpeakerControlC2SPacket(
                menu.speakerId(), SpeakerControlC2SPacket.Action.STOP, 0L, 1.0F
        ));
        ClientSpeakerTracker.onStop(menu.speakerId());
        progressSlider.sync(0.0D);
        updateControls();
    }

    private void setSpeed(float speed) {
        if (durationMs <= 0L) {
            return;
        }
        ModNetwork.sendToServer(new SpeakerControlC2SPacket(
                menu.speakerId(), SpeakerControlC2SPacket.Action.SPEED, 0L, speed
        ));
        ClientSpeakerTracker.localSpeed(menu.speakerId(), durationMs, speed);
    }

    private void seek(double ratio) {
        if (durationMs <= 0L) {
            return;
        }
        long target = Math.max(0L, Math.min(durationMs, (long) (durationMs * ratio)));
        boolean playing = ClientSpeakerTracker.isPlaying(menu.speakerId());
        ModNetwork.sendToServer(new SpeakerControlC2SPacket(
                menu.speakerId(), SpeakerControlC2SPacket.Action.SEEK, target, 1.0F
        ));
        ClientSpeakerTracker.localSeek(menu.speakerId(), durationMs, target, playing);
    }

    private void updateControls() {
        if (playPauseButton == null) {
            return;
        }
        playPauseButton.active = durationMs > 0L;
        playPauseButton.setMessage(playPauseLabel());
        progressSlider.active = durationMs > 0L;
    }

    private Component playPauseLabel() {
        return Component.translatable(ClientSpeakerTracker.isPlaying(menu.speakerId())
                ? "button.tf_music.pause"
                : "button.tf_music.play");
    }

    private double progressRatio() {
        if (durationMs <= 0L) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D,
                (double) ClientSpeakerTracker.progress(menu.speakerId(), durationMs) / durationMs));
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF090C0F);
        graphics.renderOutline(x - 1, y - 1, 18, 18, 0xFF58636D);
    }

    private final class ProgressSlider extends AbstractSliderButton {
        private boolean dragging;
        private boolean syncing;

        private ProgressSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Component.empty(), value);
            updateMessage();
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            dragging = true;
            super.onClick(mouseX, mouseY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            boolean wasDragging = dragging;
            super.onRelease(mouseX, mouseY);
            dragging = false;
            if (wasDragging && !syncing) {
                seek(value);
            }
        }

        @Override
        protected void updateMessage() {
            long progress = durationMs <= 0L ? 0L : (long) (durationMs * value);
            setMessage(Component.literal(TimeUtil.format(progress) + " / " + TimeUtil.format(durationMs)));
        }

        @Override
        protected void applyValue() {
            updateMessage();
            if (!syncing && !dragging) {
                seek(value);
            }
        }

        private void sync(double newValue) {
            if (dragging) {
                return;
            }
            syncing = true;
            value = Math.max(0.0D, Math.min(1.0D, newValue));
            updateMessage();
            syncing = false;
        }
    }
}
