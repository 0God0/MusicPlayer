package org.techfield.tfmusic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import org.techfield.tfmusic.menu.PortableSpeakerMenu;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.registry.ModItems;
import org.techfield.tfmusic.util.TimeUtil;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PortableSpeakerItem extends Item {
    public PortableSpeakerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            PortableSpeakerData data = PortableSpeakerData.load(stack);
            NetworkHooks.openScreen(
                    serverPlayer,
                    new SimpleMenuProvider(
                            (containerId, inventory, ignored) ->
                                    new PortableSpeakerMenu(containerId, inventory, data.speakerId()),
                            Component.translatable("screen.tf_music.portable_speaker")
                    ),
                    buffer -> buffer.writeUUID(data.speakerId())
            );
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        PortableSpeakerData data = PortableSpeakerData.load(stack);
        MusicTrack.fromDisc(data.disc()).ifPresentOrElse(track -> {
            tooltip.add(Component.translatable("tooltip.tf_music.loaded_disc", track.name())
                    .withStyle(ChatFormatting.AQUA));
            long progress = Math.min(data.progressMs(), track.durationMs());
            tooltip.add(Component.translatable(
                            "tooltip.tf_music.progress",
                            TimeUtil.format(progress),
                            TimeUtil.format(track.durationMs()))
                    .withStyle(ChatFormatting.GREEN));
        }, () -> tooltip.add(Component.translatable("tooltip.tf_music.no_disc")
                .withStyle(ChatFormatting.DARK_GRAY)));
        tooltip.add(Component.translatable(
                        "tooltip.tf_music.speed",
                        String.format(Locale.ROOT, "%.1fx", data.speed()))
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    public static boolean isSpeaker(ItemStack stack) {
        return stack.is(ModItems.PORTABLE_SPEAKER.get());
    }

    @Nullable
    public static ItemStack find(Inventory inventory, UUID speakerId) {
        for (ItemStack stack : inventory.items) {
            if (matches(stack, speakerId)) {
                return stack;
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (matches(stack, speakerId)) {
                return stack;
            }
        }
        return null;
    }

    public static boolean matches(ItemStack stack, UUID speakerId) {
        return isSpeaker(stack) && PortableSpeakerData.load(stack).speakerId().equals(speakerId);
    }
}
