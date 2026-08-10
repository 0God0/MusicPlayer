package org.techfield.tfmusic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.techfield.tfmusic.menu.PlaylistEditorMenu;
import org.techfield.tfmusic.menu.PlaylistEditorTarget;
import org.techfield.tfmusic.music.MusicPlaylist;
import org.techfield.tfmusic.registry.ModItems;
import org.techfield.tfmusic.util.TimeUtil;

import java.util.List;
import java.util.UUID;

public final class MusicPlaylistItem extends Item {
    public MusicPlaylistItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            MusicPlaylist.getOrCreate(stack).ifPresentOrElse(
                    playlist -> PlaylistEditorMenu.open(
                            serverPlayer,
                            PlaylistEditorTarget.held(playlist.id(), hand == InteractionHand.MAIN_HAND
                                    ? serverPlayer.getInventory().selected
                                    : -1)
                    ),
                    () -> serverPlayer.displayClientMessage(
                            Component.translatable("status.tf_music.playlist_invalid"),
                            true
                    )
            );
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        return MusicPlaylist.fromStack(stack)
                .filter(playlist -> !playlist.name().isBlank())
                .<Component>map(playlist -> Component.literal(playlist.name()))
                .orElseGet(() -> Component.translatable("item.tf_music.music_playlist.unnamed"));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        MusicPlaylist playlist = MusicPlaylist.fromStack(stack).orElse(null);
        if (playlist == null || playlist.entries().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.tf_music.empty_playlist")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            long duration = playlist.entries().stream()
                    .mapToLong(entry -> entry.track().durationMs())
                    .sum();
            tooltip.add(Component.translatable("tooltip.tf_music.playlist_tracks", playlist.entries().size())
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.tf_music.duration", TimeUtil.format(duration))
                    .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable(
                            "tooltip.tf_music.playback_mode",
                            Component.translatable("mode.tf_music." + playlist.mode().serializedName()))
                    .withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Nullable
    public static ItemStack find(Inventory inventory, UUID playlistId) {
        for (ItemStack stack : inventory.items) {
            if (matches(stack, playlistId)) {
                return stack;
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (matches(stack, playlistId)) {
                return stack;
            }
        }
        return null;
    }

    @Nullable
    public static ItemStack find(Inventory inventory, UUID playlistId, int slot) {
        if (slot >= 0 && slot < inventory.items.size()) {
            ItemStack stack = inventory.items.get(slot);
            return matches(stack, playlistId) ? stack : null;
        }
        if (slot == -1 && !inventory.offhand.isEmpty()) {
            ItemStack stack = inventory.offhand.get(0);
            return matches(stack, playlistId) ? stack : null;
        }
        return find(inventory, playlistId);
    }

    public static boolean matches(ItemStack stack, UUID playlistId) {
        return stack.is(ModItems.MUSIC_PLAYLIST.get())
                && MusicPlaylist.fromStack(stack)
                        .map(playlist -> playlist.id().equals(playlistId))
                        .orElse(false);
    }
}
