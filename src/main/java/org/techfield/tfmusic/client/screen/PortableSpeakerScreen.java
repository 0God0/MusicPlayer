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
    private static final int PLAY_BUTTON_X = 8;
    private static final int PAUSE_BUTTON_X = 62;
    private static final int STOP_BUTTON_X = 116;
    private static final int CONTROL_BUTTON_Y = 27;
    private static final int PROGRESS_X = 8;
    private static final int PROGRESS_Y = 53;
    private static final int PROGRESS_WIDTH = 158;
    private static final int PROGRESS_HEIGHT = 18;
    private static final int SPEED_LABEL_Y = 79;
    private static final int SPEED_BUTTON_Y = 91;
    private static final int SPEED_BUTTON_WIDTH = 36;
    private static final int SPEED_BUTTON_GAP = 4;
    private static final int[] SPEEDS = {50, 100, 150, 200};

    private Button playButton;
    private Button pauseButton;
    private Button stopButton;
    private final Button[] speedButtons = new Button[SPEEDS.length];
    private ProgressSlider progressSlider;
    private long durationMs;
    private float displayedSpeed = 1.0F;

    public PortableSpeakerScreen(PortableSpeakerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 230;
        imageHeight = 218;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = PortableSpeakerMenu.PLAYER_INVENTORY_X;
        inventoryLabelY = 121;
    }

    @Override
    protected void init() {
        super.init();
        PortableSpeakerData data = menu.speakerData();
        MusicTrack track = MusicTrack.fromDisc(data.disc()).orElse(null);
        durationMs = track == null ? 0L : track.durationMs();
        displayedSpeed = ClientSpeakerTracker.speed(menu.speakerId(), data.speed());
        if (durationMs > 0L) {
            ClientSpeakerTracker.localSeek(menu.speakerId(), durationMs, data.progressMs(), data.playing());
            ClientSpeakerTracker.localSpeed(menu.speakerId(), durationMs, data.speed());
        }

        playButton = addRenderableWidget(Button.builder(Component.translatable("button.tf_music.play"), button -> playPlayback())
                .bounds(leftPos + PLAY_BUTTON_X, topPos + CONTROL_BUTTON_Y, 50, 20)
                .build());
        pauseButton = addRenderableWidget(Button.builder(Component.translatable("button.tf_music.pause"), button -> pausePlayback())
                .bounds(leftPos + PAUSE_BUTTON_X, topPos + CONTROL_BUTTON_Y, 50, 20)
                .build());
        stopButton = addRenderableWidget(Button.builder(Component.translatable("button.tf_music.stop"), button -> stopPlayback())
                .bounds(leftPos + STOP_BUTTON_X, topPos + CONTROL_BUTTON_Y, 50, 20)
                .build());

        progressSlider = addRenderableWidget(new ProgressSlider(
                leftPos + PROGRESS_X,
                topPos + PROGRESS_Y,
                PROGRESS_WIDTH,
                PROGRESS_HEIGHT,
                progressRatio()
        ));

        for (int index = 0; index < SPEEDS.length; index++) {
            float speed = SPEEDS[index] / 100.0F;
            addSpeedButton(index, speed);
        }
        updateControls();
    }

    private void addSpeedButton(int index, float speed) {
        addRenderableWidget(speedButtons[index] = Button.builder(
                        Component.literal(String.format(Locale.ROOT, "%.1fx", speed)),
                        button -> setSpeed(speed))
                .bounds(
                        leftPos + PROGRESS_X + index * (SPEED_BUTTON_WIDTH + SPEED_BUTTON_GAP),
                        topPos + SPEED_BUTTON_Y,
                        SPEED_BUTTON_WIDTH,
                        18
                )
                .build());
    }

    @Override
    protected void containerTick() {
        PortableSpeakerData data = menu.speakerData();
        MusicTrack track = MusicTrack.fromDisc(data.disc()).orElse(null);
        durationMs = track == null ? 0L : track.durationMs();
        displayedSpeed = ClientSpeakerTracker.speed(menu.speakerId(), data.speed());
        if (progressSlider != null) {
            progressSlider.sync(progressRatio());
        }
        updateControls();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawSelectedSpeed(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0181D22);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF65717C);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 18, 0xFF20272D);
        graphics.fill(leftPos + 1, topPos + 18, leftPos + imageWidth - 1, topPos + 19, 0xFF4E9E8E);
        graphics.fill(leftPos + 1, topPos + 19, leftPos + imageWidth - 1, topPos + 114, 0xFF11161B);
        graphics.fill(leftPos + 1, topPos + 116, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF171C21);
        graphics.fill(leftPos + 1, topPos + 114, leftPos + imageWidth - 1, topPos + 116, 0xFF343D45);
        graphics.fill(leftPos + 172, topPos + 24, leftPos + 173, topPos + 76, 0xFF343D45);
        graphics.fill(
                leftPos + PortableSpeakerMenu.PLAYER_INVENTORY_X,
                topPos + 189,
                leftPos + PortableSpeakerMenu.PLAYER_INVENTORY_X + 162,
                topPos + 190,
                0xFF343D45
        );

        drawSlot(graphics, leftPos + PortableSpeakerMenu.DISC_SLOT_X, topPos + PortableSpeakerMenu.DISC_SLOT_Y);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(
                        graphics,
                        leftPos + PortableSpeakerMenu.PLAYER_INVENTORY_X + column * 18,
                        topPos + PortableSpeakerMenu.PLAYER_INVENTORY_Y + row * 18
                );
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(
                    graphics,
                    leftPos + PortableSpeakerMenu.PLAYER_INVENTORY_X + column * 18,
                    topPos + PortableSpeakerMenu.PLAYER_INVENTORY_Y + 60
            );
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE7EDF2, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xB7C2CC, false);
        graphics.drawString(font, Component.translatable("label.tf_music.speed"), PROGRESS_X, SPEED_LABEL_Y, 0xB7C2CC, false);
        graphics.drawCenteredString(font, Component.translatable("label.tf_music.disc"), 201, 27, 0xB7C2CC);
    }

    private void playPlayback() {
        if (durationMs <= 0L) {
            return;
        }
        PortableSpeakerData data = menu.speakerData();
        long progress = ClientSpeakerTracker.progress(menu.speakerId(), durationMs);
        ModNetwork.sendToServer(new SpeakerControlC2SPacket(
                menu.speakerId(), SpeakerControlC2SPacket.Action.PLAY, 0L, data.speed()
        ));
        ClientSpeakerTracker.localPlay(menu.speakerId(), durationMs, progress, data.speed());
        updateControls();
    }

    private void pausePlayback() {
        if (durationMs <= 0L) {
            return;
        }
        PortableSpeakerData data = menu.speakerData();
        ModNetwork.sendToServer(new SpeakerControlC2SPacket(
                menu.speakerId(), SpeakerControlC2SPacket.Action.PAUSE, 0L, data.speed()
        ));
        ClientSpeakerTracker.onPause(menu.speakerId());
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
        displayedSpeed = speed;
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
        if (playButton == null || progressSlider == null) {
            return;
        }
        boolean hasTrack = durationMs > 0L;
        boolean playing = ClientSpeakerTracker.isPlaying(menu.speakerId());
        playButton.active = hasTrack;
        pauseButton.active = hasTrack && playing;
        stopButton.active = hasTrack;
        progressSlider.active = hasTrack;
        for (Button speedButton : speedButtons) {
            speedButton.active = hasTrack;
        }
    }

    private void drawSelectedSpeed(GuiGraphics graphics) {
        if (durationMs <= 0L) {
            return;
        }
        int speedPercent = Math.round(displayedSpeed * 100.0F);
        for (int index = 0; index < SPEEDS.length; index++) {
            if (SPEEDS[index] == speedPercent) {
                graphics.renderOutline(
                        leftPos + PROGRESS_X + index * (SPEED_BUTTON_WIDTH + SPEED_BUTTON_GAP),
                        topPos + SPEED_BUTTON_Y,
                        SPEED_BUTTON_WIDTH,
                        18,
                        0xFFF2C66D
                );
                return;
            }
        }
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
