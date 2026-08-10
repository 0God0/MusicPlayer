package org.techfield.tfmusic.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.techfield.tfmusic.menu.PlaylistEditorMenu;
import org.techfield.tfmusic.music.MusicPlaylist;
import org.techfield.tfmusic.music.PlaybackMode;
import org.techfield.tfmusic.music.PlaylistEntry;
import org.techfield.tfmusic.network.ModNetwork;
import org.techfield.tfmusic.network.packet.PlaylistEditC2SPacket;
import org.techfield.tfmusic.network.packet.PlaylistStateS2CPacket;
import org.techfield.tfmusic.util.TimeUtil;

public final class PlaylistEditorScreen extends AbstractContainerScreen<PlaylistEditorMenu> {
    private static final int NAME_X = 8;
    private static final int NAME_Y = 21;
    private static final int NAME_WIDTH = 304;
    private static final int MODE_Y = 43;
    private static final int MODE_WIDTH = 70;
    private static final int MODE_GAP = 6;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 68;
    private static final int LIST_WIDTH = 222;
    private static final int ROW_HEIGHT = 16;
    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_BUTTON_X = 174;
    private static final int ROW_BUTTON_WIDTH = 16;
    private static final int ROW_BUTTON_GAP = 2;
    private static final int ADD_BUTTON_X = 246;
    private static final int ADD_BUTTON_Y = 116;
    private static final int ADD_BUTTON_WIDTH = 66;

    private final Button[][] rowButtons = new Button[VISIBLE_ROWS][3];
    private final Button[] modeButtons = new Button[PlaybackMode.values().length];
    private EditBox nameInput;
    private Button addButton;
    private int scrollOffset;
    private int renameDelay = -1;
    private boolean busy;
    private Component status = Component.translatable("status.tf_music.playlist_ready");
    private int statusColor = 0xB7C2CC;

    public PlaylistEditorScreen(PlaylistEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 238;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = PlaylistEditorMenu.PLAYER_INVENTORY_X;
        inventoryLabelY = 149;
    }

    @Override
    protected void init() {
        super.init();
        nameInput = new EditBox(
                font,
                leftPos + NAME_X,
                topPos + NAME_Y,
                NAME_WIDTH,
                18,
                Component.translatable("field.tf_music.playlist_name")
        );
        nameInput.setMaxLength(MusicPlaylist.MAX_NAME_LENGTH);
        nameInput.setHint(Component.translatable("item.tf_music.music_playlist.unnamed"));
        nameInput.setValue(menu.playlist().name());
        nameInput.setResponder(value -> {
            if (!value.trim().equals(menu.playlist().name())) {
                renameDelay = 6;
            }
        });
        addRenderableWidget(nameInput);

        PlaybackMode[] modes = PlaybackMode.values();
        for (int index = 0; index < modes.length; index++) {
            PlaybackMode mode = modes[index];
            modeButtons[index] = addRenderableWidget(Button.builder(
                            Component.translatable("mode.tf_music." + mode.serializedName()),
                            button -> sendEdit(PlaylistEditC2SPacket.mode(
                                    menu.containerId,
                                    menu.playlist().revision(),
                                    mode
                            )))
                    .bounds(
                            leftPos + NAME_X + index * (MODE_WIDTH + MODE_GAP),
                            topPos + MODE_Y,
                            MODE_WIDTH,
                            18
                    )
                    .build());
        }

        addButton = addRenderableWidget(Button.builder(
                        Component.translatable("button.tf_music.playlist_add"),
                        button -> sendEdit(PlaylistEditC2SPacket.add(
                                menu.containerId,
                                menu.playlist().revision()
                        )))
                .bounds(leftPos + ADD_BUTTON_X, topPos + ADD_BUTTON_Y, ADD_BUTTON_WIDTH, 18)
                .build());

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int buttonY = topPos + LIST_Y + row * ROW_HEIGHT + 1;
            int visibleRow = row;
            rowButtons[row][0] = addRenderableWidget(Button.builder(
                            Component.literal("^"),
                            button -> moveEntry(visibleRow, -1))
                    .bounds(leftPos + ROW_BUTTON_X, buttonY, ROW_BUTTON_WIDTH, 14)
                    .build());
            rowButtons[row][1] = addRenderableWidget(Button.builder(
                            Component.literal("v"),
                            button -> moveEntry(visibleRow, 1))
                    .bounds(
                            leftPos + ROW_BUTTON_X + ROW_BUTTON_WIDTH + ROW_BUTTON_GAP,
                            buttonY,
                            ROW_BUTTON_WIDTH,
                            14
                    )
                    .build());
            rowButtons[row][2] = addRenderableWidget(Button.builder(
                            Component.literal("x"),
                            button -> removeEntry(visibleRow))
                    .bounds(
                            leftPos + ROW_BUTTON_X + 2 * (ROW_BUTTON_WIDTH + ROW_BUTTON_GAP),
                            buttonY,
                            ROW_BUTTON_WIDTH,
                            14
                    )
                    .build());
        }
        updateControls();
    }

    @Override
    protected void containerTick() {
        nameInput.tick();
        if (!busy && renameDelay >= 0 && --renameDelay <= 0) {
            renameDelay = -1;
            String name = nameInput.getValue().trim();
            if (!name.equals(menu.playlist().name())) {
                sendEdit(PlaylistEditC2SPacket.rename(menu.containerId, menu.playlist().revision(), name));
            }
        }
        updateControls();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && nameInput.isFocused()) {
            flushRename();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        flushRename();
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= leftPos + LIST_X
                && mouseX < leftPos + LIST_X + LIST_WIDTH
                && mouseY >= topPos + LIST_Y
                && mouseY < topPos + LIST_Y + VISIBLE_ROWS * ROW_HEIGHT) {
            int maxOffset = Math.max(0, menu.playlist().entries().size() - VISIBLE_ROWS);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(delta)));
            updateControls();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawSelectedMode(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0181D22);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF65717C);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 18, 0xFF20272D);
        graphics.fill(leftPos + 1, topPos + 18, leftPos + imageWidth - 1, topPos + 19, 0xFF4E9E8E);
        graphics.fill(leftPos + 1, topPos + 19, leftPos + imageWidth - 1, topPos + 148, 0xFF11161B);
        graphics.fill(leftPos + 1, topPos + 150, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF171C21);
        graphics.fill(leftPos + 1, topPos + 148, leftPos + imageWidth - 1, topPos + 150, 0xFF343D45);
        graphics.fill(leftPos + 236, topPos + 66, leftPos + 238, topPos + 146, 0xFF343D45);

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int y = topPos + LIST_Y + row * ROW_HEIGHT;
            graphics.fill(leftPos + LIST_X, y, leftPos + LIST_X + LIST_WIDTH, y + 15, 0xFF171D22);
            graphics.renderOutline(leftPos + LIST_X, y, LIST_WIDTH, 15, 0xFF3D4851);
        }
        drawSlot(graphics, leftPos + PlaylistEditorMenu.SOURCE_SLOT_X, topPos + PlaylistEditorMenu.SOURCE_SLOT_Y);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(
                        graphics,
                        leftPos + PlaylistEditorMenu.PLAYER_INVENTORY_X + column * 18,
                        topPos + PlaylistEditorMenu.PLAYER_INVENTORY_Y + row * 18
                );
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(
                    graphics,
                    leftPos + PlaylistEditorMenu.PLAYER_INVENTORY_X + column * 18,
                    topPos + PlaylistEditorMenu.PLAYER_INVENTORY_Y + PlaylistEditorMenu.HOTBAR_OFFSET
            );
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE7EDF2, false);
        graphics.drawString(
                font,
                Component.translatable("label.tf_music.playlist_count", menu.playlist().entries().size()),
                236,
                titleLabelY,
                0x8D9AA5,
                false
        );
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xB7C2CC, false);
        graphics.drawCenteredString(
                font,
                Component.translatable("label.tf_music.source_disc"),
                279,
                72,
                0xB7C2CC
        );
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scrollOffset + row;
            if (index >= menu.playlist().entries().size()) {
                break;
            }
            PlaylistEntry entry = menu.playlist().entries().get(index);
            String prefix = (index + 1) + ". ";
            String duration = TimeUtil.format(entry.track().durationMs());
            int durationWidth = font.width(duration);
            int textWidth = 162 - durationWidth;
            String name = font.plainSubstrByWidth(prefix + entry.track().name(), Math.max(20, textWidth));
            int y = LIST_Y + row * ROW_HEIGHT + 4;
            graphics.drawString(font, name, LIST_X + 3, y, 0xD6DEE5, false);
            graphics.drawString(font, duration, 169 - durationWidth, y, 0x82909B, false);
        }

        String statusText = font.plainSubstrByWidth(status.getString(), 72);
        graphics.drawCenteredString(font, statusText, 279, 138, statusColor);
    }

    public void handlePlaylistState(PlaylistStateS2CPacket packet) {
        if (packet.containerId() != menu.containerId) {
            return;
        }
        MusicPlaylist updated = MusicPlaylist.load(packet.playlistTag()).orElse(null);
        if (updated == null) {
            return;
        }
        menu.updateClientState(updated);
        busy = false;
        status = Component.translatable(packet.messageKey());
        statusColor = packet.success() ? 0x6EDB8F : 0xF06C75;
        if (!nameInput.isFocused() || nameInput.getValue().trim().equals(updated.name())) {
            nameInput.setValue(updated.name());
            renameDelay = -1;
        } else if (!nameInput.getValue().trim().equals(updated.name())) {
            renameDelay = 6;
        }
        scrollOffset = Math.min(scrollOffset, Math.max(0, updated.entries().size() - VISIBLE_ROWS));
        updateControls();
    }

    private void moveEntry(int visibleRow, int direction) {
        PlaylistEntry entry = visibleEntry(visibleRow);
        if (entry != null) {
            sendEdit(PlaylistEditC2SPacket.move(
                    menu.containerId,
                    menu.playlist().revision(),
                    entry.id(),
                    direction
            ));
        }
    }

    private void removeEntry(int visibleRow) {
        PlaylistEntry entry = visibleEntry(visibleRow);
        if (entry != null) {
            sendEdit(PlaylistEditC2SPacket.remove(
                    menu.containerId,
                    menu.playlist().revision(),
                    entry.id()
            ));
        }
    }

    private PlaylistEntry visibleEntry(int row) {
        int index = scrollOffset + row;
        return index >= 0 && index < menu.playlist().entries().size()
                ? menu.playlist().entries().get(index)
                : null;
    }

    private void sendEdit(PlaylistEditC2SPacket packet) {
        if (busy) {
            return;
        }
        busy = true;
        status = Component.translatable("status.tf_music.playlist_saving");
        statusColor = 0xF2C66D;
        ModNetwork.sendToServer(packet);
        updateControls();
    }

    private void updateControls() {
        if (addButton == null) {
            return;
        }
        int size = menu.playlist().entries().size();
        nameInput.setEditable(!busy);
        addButton.active = !busy && size < MusicPlaylist.MAX_TRACKS;
        for (int index = 0; index < modeButtons.length; index++) {
            modeButtons[index].active = !busy && PlaybackMode.values()[index] != menu.playlist().mode();
        }
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scrollOffset + row;
            boolean visible = index < size;
            for (Button button : rowButtons[row]) {
                button.visible = visible;
            }
            rowButtons[row][0].active = !busy && visible && index > 0;
            rowButtons[row][1].active = !busy && visible && index < size - 1;
            rowButtons[row][2].active = !busy && visible;
        }
    }

    private void drawSelectedMode(GuiGraphics graphics) {
        int index = menu.playlist().mode().ordinal();
        graphics.renderOutline(
                leftPos + NAME_X + index * (MODE_WIDTH + MODE_GAP),
                topPos + MODE_Y,
                MODE_WIDTH,
                18,
                0xFFF2C66D
        );
    }

    private void flushRename() {
        if (busy || nameInput == null) {
            return;
        }
        String name = nameInput.getValue().trim();
        if (!name.equals(menu.playlist().name())) {
            renameDelay = -1;
            sendEdit(PlaylistEditC2SPacket.rename(menu.containerId, menu.playlist().revision(), name));
        }
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF090C0F);
        graphics.renderOutline(x - 1, y - 1, 18, 18, 0xFF58636D);
    }
}
