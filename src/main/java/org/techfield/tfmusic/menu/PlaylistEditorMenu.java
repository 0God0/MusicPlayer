package org.techfield.tfmusic.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import org.techfield.tfmusic.block.entity.MusicPlayerBlockEntity;
import org.techfield.tfmusic.item.MusicPlaylistItem;
import org.techfield.tfmusic.item.PortableSpeakerData;
import org.techfield.tfmusic.item.PortableSpeakerItem;
import org.techfield.tfmusic.music.MusicPlaylist;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.network.ModNetwork;
import org.techfield.tfmusic.network.packet.PlaylistEditC2SPacket;
import org.techfield.tfmusic.network.packet.PlaylistStateS2CPacket;
import org.techfield.tfmusic.registry.ModItems;
import org.techfield.tfmusic.registry.ModMenus;

import java.util.Optional;
import java.util.UUID;

public final class PlaylistEditorMenu extends AbstractContainerMenu {
    public static final int SOURCE_SLOT = 0;
    public static final int SOURCE_SLOT_X = 276;
    public static final int SOURCE_SLOT_Y = 92;
    public static final int PLAYER_INVENTORY_X = 79;
    public static final int PLAYER_INVENTORY_Y = 160;
    public static final int HOTBAR_OFFSET = 58;

    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final Inventory playerInventory;
    private final PlaylistEditorTarget target;
    private final SimpleContainer sourceInventory = new SimpleContainer(1);
    private MusicPlaylist playlist;

    public PlaylistEditorMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(
                containerId,
                inventory,
                PlaylistEditorTarget.read(buffer),
                MusicPlaylist.createDefault()
        );
    }

    public PlaylistEditorMenu(int containerId, Inventory inventory, PlaylistEditorTarget target) {
        this(containerId, inventory, target, resolvePlaylist(inventory.player, target).orElseGet(MusicPlaylist::createDefault));
    }

    private PlaylistEditorMenu(
            int containerId,
            Inventory inventory,
            PlaylistEditorTarget target,
            MusicPlaylist playlist
    ) {
        super(ModMenus.PLAYLIST_EDITOR.get(), containerId);
        this.playerInventory = inventory;
        this.target = target;
        this.playlist = playlist;

        addSlot(new Slot(sourceInventory, SOURCE_SLOT, SOURCE_SLOT_X, SOURCE_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.MUSIC_DISC.get()) && MusicTrack.fromDisc(stack).isPresent();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addPlayerInventory(inventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
    }

    public MusicPlaylist playlist() {
        return playlist;
    }

    public PlaylistEditorTarget target() {
        return target;
    }

    public void updateClientState(MusicPlaylist updated) {
        playlist = updated;
    }

    public void applyEdit(ServerPlayer player, PlaylistEditC2SPacket packet) {
        MusicPlaylist current = resolvePlaylist(player, target).orElse(null);
        if (current == null || current.revision() != packet.expectedRevision()) {
            sendState(player, current == null ? playlist : current, false, "status.tf_music.playlist_stale");
            return;
        }

        MusicPlaylist updated = current;
        String successMessage = "status.tf_music.playlist_updated";
        switch (packet.action()) {
            case ADD -> {
                MusicTrack track = MusicTrack.fromDisc(getSlot(SOURCE_SLOT).getItem()).orElse(null);
                if (track == null) {
                    sendState(player, current, false, "status.tf_music.playlist_insert_recorded_disc");
                    return;
                }
                if (current.containsTrack(track)) {
                    sendState(player, current, false, "status.tf_music.playlist_duplicate");
                    return;
                }
                if (current.entries().size() >= MusicPlaylist.MAX_TRACKS) {
                    sendState(player, current, false, "status.tf_music.playlist_full");
                    return;
                }
                updated = current.add(track);
                successMessage = "status.tf_music.playlist_added";
            }
            case REMOVE -> updated = current.remove(packet.entryId());
            case MOVE -> updated = current.move(packet.entryId(), packet.direction());
            case RENAME -> updated = current.rename(packet.value());
            case MODE -> {
                if (packet.mode() == null) {
                    sendState(player, current, false, "status.tf_music.playlist_update_failed");
                    return;
                }
                updated = current.withMode(packet.mode());
            }
        }

        if (updated == current) {
            sendState(player, current, false, "status.tf_music.playlist_no_change");
            return;
        }
        if (updated.validate() != null || !writePlaylist(player, target, updated)) {
            sendState(player, current, false, "status.tf_music.playlist_update_failed");
            return;
        }

        playlist = updated;
        broadcastChanges();
        sendState(player, updated, true, successMessage);
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide) {
            return true;
        }
        return resolvePlaylist(player, target)
                .map(current -> current.id().equals(playlist.id()))
                .orElse(false);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = getSlot(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack moving = slot.getItem();
        ItemStack original = moving.copy();
        if (index == SOURCE_SLOT) {
            if (!moveItemStackTo(moving, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(moving, SOURCE_SLOT, SOURCE_SLOT + 1, false)) {
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
            clearContainer(player, sourceInventory);
        }
    }

    public static boolean open(ServerPlayer player, PlaylistEditorTarget target) {
        MusicPlaylist playlist = ensurePlaylist(player, target).orElse(null);
        if (playlist == null) {
            return false;
        }
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new PlaylistEditorMenu(containerId, inventory, target),
                        Component.translatable("screen.tf_music.playlist_editor")
                ),
                buffer -> {
                    target.write(buffer);
                }
        );
        if (player.containerMenu instanceof PlaylistEditorMenu menu) {
            ModNetwork.sendTo(player, new PlaylistStateS2CPacket(
                    menu.containerId,
                    playlist.save(),
                    true,
                    "status.tf_music.playlist_ready"
            ));
        }
        return true;
    }

    private void sendState(ServerPlayer player, MusicPlaylist state, boolean success, String messageKey) {
        playlist = state;
        ModNetwork.sendTo(player, new PlaylistStateS2CPacket(containerId, state.save(), success, messageKey));
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

    private static Optional<MusicPlaylist> ensurePlaylist(Player player, PlaylistEditorTarget target) {
        ItemStack stack = resolveTargetStack(player, target);
        if (stack.isEmpty() || !stack.is(ModItems.MUSIC_PLAYLIST.get())) {
            return Optional.empty();
        }
        MusicPlaylist existing = MusicPlaylist.fromStack(stack).orElse(null);
        if (existing != null) {
            return Optional.of(existing);
        }
        if (MusicPlaylist.hasSerializedData(stack)) {
            return Optional.empty();
        }
        MusicPlaylist created = MusicPlaylist.createDefault();
        return writePlaylist(player, target, created) ? Optional.of(created) : Optional.empty();
    }

    private static Optional<MusicPlaylist> resolvePlaylist(Player player, PlaylistEditorTarget target) {
        if (target.type() == PlaylistEditorTarget.Type.BLOCK
                && player.distanceToSqr(
                        target.pos().getX() + 0.5D,
                        target.pos().getY() + 0.5D,
                        target.pos().getZ() + 0.5D) > 64.0D) {
            return Optional.empty();
        }
        return MusicPlaylist.fromStack(resolveTargetStack(player, target));
    }

    private static ItemStack resolveTargetStack(Player player, PlaylistEditorTarget target) {
        return switch (target.type()) {
            case HELD -> {
                ItemStack playlist = MusicPlaylistItem.find(player.getInventory(), target.id(), target.slot());
                yield playlist == null ? ItemStack.EMPTY : playlist;
            }
            case SPEAKER -> {
                ItemStack speaker = PortableSpeakerItem.find(player.getInventory(), target.id());
                yield speaker == null ? ItemStack.EMPTY : PortableSpeakerData.getOrCreate(speaker).media();
            }
            case BLOCK -> player.level().getBlockEntity(target.pos()) instanceof MusicPlayerBlockEntity blockEntity
                    ? blockEntity.getMedia()
                    : ItemStack.EMPTY;
        };
    }

    private static boolean writePlaylist(Player player, PlaylistEditorTarget target, MusicPlaylist playlist) {
        switch (target.type()) {
            case HELD -> {
                ItemStack stack = MusicPlaylistItem.find(player.getInventory(), target.id(), target.slot());
                if (stack == null) {
                    return false;
                }
                playlist.write(stack);
                player.getInventory().setChanged();
                return true;
            }
            case SPEAKER -> {
                ItemStack speaker = PortableSpeakerItem.find(player.getInventory(), target.id());
                if (speaker == null) {
                    return false;
                }
                PortableSpeakerData data = PortableSpeakerData.getOrCreate(speaker);
                ItemStack media = data.media().copy();
                if (!media.is(ModItems.MUSIC_PLAYLIST.get())) {
                    return false;
                }
                playlist.write(media);
                data.withEditedMedia(media).write(speaker);
                player.getInventory().setChanged();
                return true;
            }
            case BLOCK -> {
                if (!(player.level().getBlockEntity(target.pos()) instanceof MusicPlayerBlockEntity blockEntity)
                        || player.distanceToSqr(
                                target.pos().getX() + 0.5D,
                                target.pos().getY() + 0.5D,
                                target.pos().getZ() + 0.5D) > 64.0D) {
                    return false;
                }
                return blockEntity.updatePlaylist(playlist);
            }
        }
        return false;
    }
}
