package org.techfield.tfmusic.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.techfield.tfmusic.client.audio.ClientAudioCache;
import org.techfield.tfmusic.menu.BurnerMenu;
import org.techfield.tfmusic.music.MusicSource;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.network.ModNetwork;
import org.techfield.tfmusic.network.packet.BurnTrackC2SPacket;
import org.techfield.tfmusic.registry.ModItems;

import java.util.concurrent.CompletableFuture;

public final class BurnerScreen extends AbstractContainerScreen<BurnerMenu> {
    private static final int FORM_X = 8;
    private static final int FORM_WIDTH = 158;
    private static final int SOURCE_DIRECT_X = 8;
    private static final int SOURCE_BILIBILI_X = 82;
    private static final int SOURCE_Y = 22;
    private static final int SOURCE_WIDTH = 70;
    private static final int SOURCE_HEIGHT = 20;
    private static final int NAME_INPUT_Y = 47;
    private static final int LOCATOR_INPUT_Y = 69;
    private static final int PAGE_INPUT_Y = 91;
    private static final int BURN_BUTTON_X = 62;
    private static final int BURN_BUTTON_Y = 91;
    private static final int BURN_BUTTON_WIDTH = 70;
    private static final int STATUS_X = 8;
    private static final int STATUS_Y = 114;
    private static final int STATUS_WIDTH = 158;
    private static final int CONTENT_DIVIDER_Y = 124;

    private MusicSource selectedSource = MusicSource.DIRECT_LINK;
    private EditBox nameInput;
    private EditBox locatorInput;
    private EditBox pageInput;
    private Button directButton;
    private Button bilibiliButton;
    private Button burnButton;
    private CompletableFuture<Long> probeFuture;
    private Component status = Component.translatable("status.tf_music.ready");
    private int statusColor = 0xB7C2CC;
    private boolean busy;

    public BurnerScreen(BurnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 230;
        imageHeight = 218;
        titleLabelX = FORM_X;
        titleLabelY = 6;
        inventoryLabelX = BurnerMenu.PLAYER_INVENTORY_X;
        inventoryLabelY = 128;
    }

    @Override
    protected void init() {
        String previousName = nameInput == null ? "" : nameInput.getValue();
        String previousLocator = locatorInput == null ? "" : locatorInput.getValue();
        String previousPage = pageInput == null ? "1" : pageInput.getValue();
        super.init();

        directButton = addRenderableWidget(Button.builder(
                        Component.translatable("source.tf_music.direct_link"),
                        button -> selectSource(MusicSource.DIRECT_LINK))
                .bounds(leftPos + SOURCE_DIRECT_X, topPos + SOURCE_Y, SOURCE_WIDTH, SOURCE_HEIGHT)
                .build());
        bilibiliButton = addRenderableWidget(Button.builder(
                        Component.translatable("source.tf_music.bilibili"),
                        button -> selectSource(MusicSource.BILIBILI))
                .bounds(leftPos + SOURCE_BILIBILI_X, topPos + SOURCE_Y, SOURCE_WIDTH, SOURCE_HEIGHT)
                .build());

        nameInput = new EditBox(
                font,
                leftPos + FORM_X,
                topPos + NAME_INPUT_Y,
                FORM_WIDTH,
                18,
                Component.translatable("field.tf_music.track_name")
        );
        nameInput.setMaxLength(MusicTrack.MAX_NAME_LENGTH);
        nameInput.setHint(Component.translatable("field.tf_music.track_name"));
        nameInput.setValue(previousName);
        addRenderableWidget(nameInput);

        locatorInput = new EditBox(
                font,
                leftPos + FORM_X,
                topPos + LOCATOR_INPUT_Y,
                FORM_WIDTH,
                18,
                Component.translatable("field.tf_music.locator")
        );
        locatorInput.setMaxLength(MusicTrack.MAX_LOCATOR_LENGTH);
        locatorInput.setValue(previousLocator);
        addRenderableWidget(locatorInput);

        pageInput = new EditBox(
                font,
                leftPos + FORM_X,
                topPos + PAGE_INPUT_Y,
                50,
                18,
                Component.translatable("field.tf_music.page")
        );
        pageInput.setMaxLength(3);
        pageInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        pageInput.setHint(Component.translatable("field.tf_music.page"));
        pageInput.setValue(previousPage.isBlank() ? "1" : previousPage);
        addRenderableWidget(pageInput);

        burnButton = addRenderableWidget(Button.builder(
                        Component.translatable("button.tf_music.burn"),
                        button -> beginBurn())
                .bounds(leftPos + BURN_BUTTON_X, topPos + BURN_BUTTON_Y, BURN_BUTTON_WIDTH, 20)
                .build());
        updateSourceWidgets();
        updateBusyState();
    }

    @Override
    protected void containerTick() {
        nameInput.tick();
        locatorInput.tick();
        pageInput.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawSelectedSource(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0181D22);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF65717C);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 18, 0xFF20272D);
        graphics.fill(leftPos + 1, topPos + 18, leftPos + imageWidth - 1, topPos + 19, 0xFF4E9E8E);
        graphics.fill(leftPos + 1, topPos + 19, leftPos + imageWidth - 1, topPos + CONTENT_DIVIDER_Y, 0xFF11161B);
        graphics.fill(leftPos + 1, topPos + CONTENT_DIVIDER_Y + 2, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF171C21);
        graphics.fill(
                leftPos + 1,
                topPos + CONTENT_DIVIDER_Y,
                leftPos + imageWidth - 1,
                topPos + CONTENT_DIVIDER_Y + 2,
                0xFF343D45
        );
        graphics.fill(leftPos + 172, topPos + 24, leftPos + 173, topPos + 84, 0xFF343D45);
        graphics.fill(
                leftPos + BurnerMenu.PLAYER_INVENTORY_X,
                topPos + BurnerMenu.PLAYER_INVENTORY_Y + 54,
                leftPos + BurnerMenu.PLAYER_INVENTORY_X + 162,
                topPos + BurnerMenu.PLAYER_INVENTORY_Y + 56,
                0xFF343D45
        );

        drawSlot(graphics, leftPos + BurnerMenu.DISC_SLOT_X, topPos + BurnerMenu.DISC_SLOT_Y);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(
                        graphics,
                        leftPos + BurnerMenu.PLAYER_INVENTORY_X + column * 18,
                        topPos + BurnerMenu.PLAYER_INVENTORY_Y + row * 18
                );
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(
                    graphics,
                    leftPos + BurnerMenu.PLAYER_INVENTORY_X + column * 18,
                    topPos + BurnerMenu.PLAYER_INVENTORY_Y + BurnerMenu.HOTBAR_OFFSET
            );
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE7EDF2, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xB7C2CC, false);
        graphics.drawCenteredString(font, Component.translatable("label.tf_music.disc"), 201, 27, 0xB7C2CC);
        drawStatus(graphics);
    }

    @Override
    public void removed() {
        if (probeFuture != null) {
            probeFuture.cancel(true);
            probeFuture = null;
        }
        super.removed();
    }

    public void handleBurnResult(boolean success, String message) {
        busy = false;
        status = Component.literal(message);
        statusColor = success ? 0x6EDB8F : 0xF06C75;
        updateBusyState();
    }

    private void selectSource(MusicSource source) {
        if (busy) {
            return;
        }
        selectedSource = source;
        updateSourceWidgets();
    }

    private void updateSourceWidgets() {
        if (directButton == null) {
            return;
        }
        directButton.active = !busy && selectedSource != MusicSource.DIRECT_LINK;
        bilibiliButton.active = !busy && selectedSource != MusicSource.BILIBILI;
        pageInput.visible = selectedSource == MusicSource.BILIBILI;
        pageInput.setEditable(!busy && pageInput.visible);
        locatorInput.setHint(Component.translatable(
                selectedSource == MusicSource.DIRECT_LINK
                        ? "field.tf_music.direct_url"
                        : "field.tf_music.bvid"
        ));
    }

    private void drawSelectedSource(GuiGraphics graphics) {
        int x = selectedSource == MusicSource.DIRECT_LINK ? SOURCE_DIRECT_X : SOURCE_BILIBILI_X;
        graphics.renderOutline(
                leftPos + x,
                topPos + SOURCE_Y,
                SOURCE_WIDTH,
                SOURCE_HEIGHT,
                0xFFF2C66D
        );
    }

    private void drawStatus(GuiGraphics graphics) {
        String text = status.getString().replace('\n', ' ');
        String line = font.plainSubstrByWidth(text, STATUS_WIDTH);
        if (line.length() < text.length()) {
            line = font.plainSubstrByWidth(line, Math.max(0, STATUS_WIDTH - 12)) + "...";
        }
        graphics.drawString(font, line, STATUS_X, STATUS_Y, statusColor, false);
    }

    private void beginBurn() {
        if (busy) {
            return;
        }
        if (!menu.getSlot(BurnerMenu.DISC_SLOT).getItem().is(ModItems.MUSIC_DISC.get())) {
            fail(Component.translatable("status.tf_music.insert_disc"));
            return;
        }
        if (MusicTrack.fromDisc(menu.getSlot(BurnerMenu.DISC_SLOT).getItem()).isPresent()) {
            fail(Component.translatable("status.tf_music.disc_already_recorded"));
            return;
        }

        String name = nameInput.getValue().trim();
        String locator = locatorInput.getValue().trim();
        if (name.isBlank() || name.length() > MusicTrack.MAX_NAME_LENGTH) {
            fail(Component.translatable("status.tf_music.invalid_name"));
            return;
        }
        String locatorError = selectedSource.validateLocator(locator);
        if (locatorError != null) {
            fail(Component.literal(locatorError));
            return;
        }

        int page = 1;
        if (selectedSource == MusicSource.BILIBILI) {
            try {
                page = Integer.parseInt(pageInput.getValue());
            } catch (NumberFormatException exception) {
                fail(Component.translatable("status.tf_music.invalid_page"));
                return;
            }
            if (page < MusicTrack.MIN_BILIBILI_PAGE || page > MusicTrack.MAX_BILIBILI_PAGE) {
                fail(Component.translatable("status.tf_music.invalid_page"));
                return;
            }
        }

        busy = true;
        status = Component.translatable("status.tf_music.loading");
        statusColor = 0xF2C66D;
        updateBusyState();
        int finalPage = page;
        probeFuture = ClientAudioCache.probe(selectedSource, locator, page);
        probeFuture.whenComplete((duration, error) -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen != this || probeFuture == null || probeFuture.isCancelled()) {
                return;
            }
            probeFuture = null;
            if (error != null) {
                busy = false;
                fail(Component.literal(rootMessage(error)));
                updateBusyState();
                return;
            }
            MusicTrack track = new MusicTrack(selectedSource, name, duration, locator, finalPage);
            String validationError = track.validate();
            if (validationError != null) {
                busy = false;
                fail(Component.literal(validationError));
                updateBusyState();
                return;
            }
            status = Component.translatable("status.tf_music.writing");
            ModNetwork.sendToServer(new BurnTrackC2SPacket(track));
        }));
    }

    private void updateBusyState() {
        if (burnButton == null) {
            return;
        }
        burnButton.active = !busy;
        nameInput.setEditable(!busy);
        locatorInput.setEditable(!busy);
        updateSourceWidgets();
    }

    private void fail(Component message) {
        status = message.copy().withStyle(ChatFormatting.RED);
        statusColor = 0xF06C75;
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF090C0F);
        graphics.renderOutline(x - 1, y - 1, 18, 18, 0xFF58636D);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
