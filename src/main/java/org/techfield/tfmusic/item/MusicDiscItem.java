package org.techfield.tfmusic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.techfield.tfmusic.music.MusicTrack;
import org.techfield.tfmusic.util.TimeUtil;

import java.util.List;

public final class MusicDiscItem extends Item {
    public MusicDiscItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return MusicTrack.fromDisc(stack)
                .<Component>map(track -> Component.literal(track.name()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        MusicTrack.fromDisc(stack).ifPresentOrElse(track -> {
            tooltip.add(Component.translatable("tooltip.tf_music.duration", TimeUtil.format(track.durationMs()))
                    .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("tooltip.tf_music.source", track.source().serializedName())
                    .withStyle(ChatFormatting.GRAY));
            if (track.source() == org.techfield.tfmusic.music.MusicSource.BILIBILI) {
                tooltip.add(Component.translatable("tooltip.tf_music.bilibili_page", track.page())
                        .withStyle(ChatFormatting.GRAY));
            }
            if (flag.isAdvanced()) {
                tooltip.add(Component.literal(track.locator()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }, () -> tooltip.add(Component.translatable("tooltip.tf_music.blank_disc")
                .withStyle(ChatFormatting.DARK_GRAY)));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
