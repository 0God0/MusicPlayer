package org.techfield.tfmusic.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.registry.ModBlocks;
import org.techfield.tfmusic.registry.ModItems;
import org.techfield.tfmusic.registry.ModMenus;

public final class BurnerMenu extends AbstractContainerMenu {
    public static final int DISC_SLOT = 0;
    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final SimpleContainer burnerInventory = new SimpleContainer(1);
    private final ContainerLevelAccess access;

    public BurnerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public BurnerMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.BURNER.get(), containerId);
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);

        addSlot(new Slot(burnerInventory, DISC_SLOT, 250, 48) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.MUSIC_DISC.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addPlayerInventory(playerInventory, 62, 140);
    }

    public boolean burn(MusicTrack track) {
        if (track.validate() != null) {
            return false;
        }
        ItemStack stack = getSlot(DISC_SLOT).getItem();
        if (!stack.is(ModItems.MUSIC_DISC.get())) {
            return false;
        }
        if (MusicTrack.fromDisc(stack).isPresent()) {
            return false;
        }
        track.writeToDisc(stack);
        getSlot(DISC_SLOT).setChanged();
        broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.MUSIC_BURNER.get());
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

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            clearContainer(player, burnerInventory);
        }
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, x + column * 18, y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, x + column * 18, y + 58));
        }
    }
}
