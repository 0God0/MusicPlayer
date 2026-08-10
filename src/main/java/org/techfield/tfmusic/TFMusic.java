package org.techfield.tfmusic;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.techfield.tfmusic.network.ModNetwork;
import org.techfield.tfmusic.registry.ModBlockEntities;
import org.techfield.tfmusic.registry.ModBlocks;
import org.techfield.tfmusic.registry.ModItems;
import org.techfield.tfmusic.registry.ModMenus;
import org.techfield.tfmusic.registry.ModSounds;

@Mod(TFMusic.MOD_ID)
public final class TFMusic {
    public static final String MOD_ID = "tf_music";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TFMusic() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModSounds.register(modBus);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
