package org.techfield.tfmusic.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.techfield.tfmusic.item.PortableSpeakerData;
import org.techfield.tfmusic.item.PortableSpeakerItem;
import org.techfield.tfmusic.music.MusicMedia;
import org.techfield.tfmusic.registry.ModMenus;

import java.util.UUID;

public final class PortableSpeakerMenu extends AbstractContainerMenu {
    public static final int DISC_SLOT = 0;
    public static final int DISC_SLOT_X = 193;
    public static final int DISC_SLOT_Y = 43;
    public static final int PLAYER_INVENTORY_X = 34;
    public static final int PLAYER_INVENTORY_Y = 160;
    public static final int HOTBAR_OFFSET = 58;
    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final Inventory playerInventory;
    private final UUID speakerId;
    private final Container speakerInventory;

    public PortableSpeakerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readUUID());
    }

    public PortableSpeakerMenu(int containerId, Inventory playerInventory, UUID speakerId) {
        super(ModMenus.PORTABLE_SPEAKER.get(), containerId);
        this.playerInventory = playerInventory;
        this.speakerId = speakerId;
        this.speakerInventory = new SpeakerContainer();

        addSlot(new Slot(speakerInventory, DISC_SLOT, DISC_SLOT_X, DISC_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return MusicMedia.isSupported(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addPlayerInventory(playerInventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
    }

    public UUID speakerId() {
        return speakerId;
    }

    public PortableSpeakerData speakerData() {
        ItemStack stack = findSpeaker();
        if (stack == null) {
            return PortableSpeakerData.createDefault();
        }
        return playerInventory.player.level().isClientSide
                ? PortableSpeakerData.load(stack)
                : PortableSpeakerData.getOrCreate(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return PortableSpeakerItem.find(player.getInventory(), speakerId) != null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = getSlot(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack moving = slot.getItem();
        ItemStack original = moving.copy();
        if (index == DISC_SLOT) {
            if (!moveItemStackTo(moving, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(moving, DISC_SLOT, DISC_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }

        if (moving.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (moving.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, moving);
        return original;
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, x + column * 18, y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, x + column * 18, y + HOTBAR_OFFSET));
        }
    }

    @Nullable
    private ItemStack findSpeaker() {
        return PortableSpeakerItem.find(playerInventory, speakerId);
    }

    private final class SpeakerContainer implements Container {
        private ItemStack clientMedia = ItemStack.EMPTY;

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return getItem(0).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            if (slot != 0) {
                return ItemStack.EMPTY;
            }
            if (playerInventory.player.level().isClientSide) {
                return clientMedia;
            }
            ItemStack speaker = findSpeaker();
            return speaker == null ? ItemStack.EMPTY : PortableSpeakerData.load(speaker).media().copy();
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot != 0) {
                return ItemStack.EMPTY;
            }
            ItemStack current = getItem(0).copy();
            ItemStack removed = current.split(amount);
            setItem(0, current);
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot != 0) {
                return ItemStack.EMPTY;
            }
            ItemStack current = getItem(0).copy();
            setItem(0, ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot != 0) {
                return;
            }
            if (playerInventory.player.level().isClientSide) {
                clientMedia = MusicMedia.normalize(stack);
                return;
            }
            ItemStack speaker = findSpeaker();
            if (speaker == null) {
                return;
            }
            PortableSpeakerData.getOrCreate(speaker).withMedia(MusicMedia.normalize(stack)).write(speaker);
            setChanged();
        }

        @Override
        public void setChanged() {
            playerInventory.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return PortableSpeakerMenu.this.stillValid(player);
        }

        @Override
        public void clearContent() {
            setItem(0, ItemStack.EMPTY);
        }
    }
}
