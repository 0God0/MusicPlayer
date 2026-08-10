package org.techfield.tfmusic.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.techfield.tfmusic.TFMusic;
import org.techfield.tfmusic.item.MusicDiscItem;
import org.techfield.tfmusic.item.PortableSpeakerItem;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TFMusic.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TFMusic.MOD_ID);

    public static final RegistryObject<Item> MUSIC_PLAYER = ITEMS.register(
            "music_player",
            () -> new BlockItem(ModBlocks.MUSIC_PLAYER.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> MUSIC_BURNER = ITEMS.register(
            "music_burner",
            () -> new BlockItem(ModBlocks.MUSIC_BURNER.get(), new Item.Properties())
    );
    public static final RegistryObject<MusicDiscItem> MUSIC_DISC = ITEMS.register(
            "music_disc",
            () -> new MusicDiscItem(new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<PortableSpeakerItem> PORTABLE_SPEAKER = ITEMS.register(
            "portable_speaker",
            () -> new PortableSpeakerItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<CreativeModeTab> TF_MUSIC_TAB = TABS.register(
            "tf_music",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tf_music"))
                    .icon(() -> new ItemStack(MUSIC_PLAYER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(MUSIC_PLAYER.get());
                        output.accept(MUSIC_BURNER.get());
                        output.accept(MUSIC_DISC.get());
                        output.accept(PORTABLE_SPEAKER.get());
                    })
                    .build()
    );

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
