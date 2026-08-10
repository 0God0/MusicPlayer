package org.techfield.tfmusic.registry;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.techfield.tfmusic.TFMusic;
import org.techfield.tfmusic.menu.BurnerMenu;
import org.techfield.tfmusic.menu.PlaylistEditorMenu;
import org.techfield.tfmusic.menu.PortableSpeakerMenu;

public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, TFMusic.MOD_ID);

    public static final RegistryObject<MenuType<BurnerMenu>> BURNER = MENUS.register(
            "music_burner",
            () -> IForgeMenuType.create(BurnerMenu::new)
    );
    public static final RegistryObject<MenuType<PortableSpeakerMenu>> PORTABLE_SPEAKER = MENUS.register(
            "portable_speaker",
            () -> IForgeMenuType.create(PortableSpeakerMenu::new)
    );
    public static final RegistryObject<MenuType<PlaylistEditorMenu>> PLAYLIST_EDITOR = MENUS.register(
            "playlist_editor",
            () -> IForgeMenuType.create(PlaylistEditorMenu::new)
    );

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
