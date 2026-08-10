package org.techfield.tfmusic.music;

import net.minecraft.world.item.ItemStack;
import org.techfield.tfmusic.registry.ModItems;

import java.util.Optional;

public final class MusicMedia {
    private MusicMedia() {
    }

    public static boolean isSupported(ItemStack stack) {
        return stack.is(ModItems.MUSIC_DISC.get()) || stack.is(ModItems.MUSIC_PLAYLIST.get());
    }

    public static boolean isPlaylist(ItemStack stack) {
        return stack.is(ModItems.MUSIC_PLAYLIST.get());
    }

    public static Optional<MusicTrack> firstTrack(ItemStack stack) {
        if (stack.is(ModItems.MUSIC_DISC.get())) {
            return MusicTrack.fromDisc(stack);
        }
        if (isPlaylist(stack)) {
            return MusicPlaylist.fromStack(stack)
                    .flatMap(playlist -> playlist.entries().stream().findFirst())
                    .map(PlaylistEntry::track);
        }
        return Optional.empty();
    }

    public static ItemStack normalize(ItemStack stack) {
        if (stack.isEmpty() || !isSupported(stack)) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
