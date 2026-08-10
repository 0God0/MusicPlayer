package org.techfield.tfmusic.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.techfield.tfmusic.TFMusic;
import org.techfield.tfmusic.client.screen.BurnerScreen;
import org.techfield.tfmusic.client.screen.PortableSpeakerScreen;
import org.techfield.tfmusic.registry.ModMenus;

@Mod.EventBusSubscriber(modid = TFMusic.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.BURNER.get(), BurnerScreen::new);
            MenuScreens.register(ModMenus.PORTABLE_SPEAKER.get(), PortableSpeakerScreen::new);
        });
    }
}
