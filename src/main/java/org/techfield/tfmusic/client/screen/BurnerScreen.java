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
        imageWidth = 286;
        imageHeight = 222;
        titleLabelX = 12;
        titleLabelY = 7;
        inventoryLabelX = 62;
        inventoryLabelY = 130;
    }

    @Override
    protected void init() {
        String previousName = nameInput == null ? "" : nameInput.getValue();
        String previousLocator = locatorInput == null ? "" : locatorInput.getValue();
        String previousPage = pageInput == null ? "1" : pageInput.getValue();
        super.init();

        int formX = leftPos + 12;
        int formWidth = 210;
        directButton = addRenderableWidget(Button.builder(
                        Component.translatable("source.tf_music.direct_link"),
                        button -> selectSource(MusicSource.DIRECT_LINK))
                .bounds(formX, topPos + 21, 102, 20)
                .build());
        bilibiliButton = addRenderableWidget(Button.builder(
                        Component.translatable("source.tf_music.bilibili"),
                        button -> selectSource(MusicSource.BILIBILI))
                .bounds(formX + 108, topPos + 21, 102, 20)
                .build());

        nameInput = new EditBox(font, formX, topPos + 52, formWidth, 18, Component.translatable("field.tf_music.track_name"));
        nameInput.setMaxLength(MusicTrack.MAX_NAME_LENGTH);
        nameInput.setHint(Component.translatable("field.tf_music.track_name"));
        nameInput.setValue(previousName);
        addRenderableWidget(nameInput);

        locatorInput = new EditBox(font, formX, topPos + 78, formWidth, 18, Component.translatable("field.tf_music.locator"));
        locatorInput.setMaxLength(MusicTrack.MAX_LOCATOR_LENGTH);
        locatorInput.setValue(previousLocator);
        addRenderableWidget(locatorInput);

        pageInput = new EditBox(font, formX, topPos + 104, 64, 18, Component.translatable("field.tf_music.page"));
        pageInput.setMaxLength(3);
        pageInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        pageInput.setHint(Component.translatable("field.tf_music.page"));
        pageInput.setValue(previousPage.isBlank() ? "1" : previousPage);
        addRenderableWidget(pageInput);

        burnButton = addRenderableWidget(Button.builder(
                        Component.translatable("button.tf_music.burn"),
                        button -> beginBurn())
                .bounds(leftPos + 230, topPos + 76, 48, 20)
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
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0181D22);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF65717C);
        graphics.fill(leftPos + 1, topPos + 16, leftPos + imageWidth - 1, topPos + 17, 0xFF343D45);
        graphics.fill(leftPos + 1, topPos + 128, leftPos + imageWidth - 1, topPos + 129, 0xFF343D45);

        drawSlot(graphics, leftPos + 250, topPos + 48);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, leftPos + 62 + column * 18, topPos + 140 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(graphics, leftPos + 62 + column * 18, topPos + 198);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE7EDF2, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xB7C2CC, false);
        graphics.drawString(font, Component.translatable("label.tf_music.disc"), 240, 37, 0xB7C2CC, false);
        graphics.drawString(font, status, 84, 109, statusColor, false);
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
