package org.techfield.tfmusic.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.techfield.tfmusic.TFMusic;
import org.techfield.tfmusic.block.MusicBurnerBlock;
import org.techfield.tfmusic.block.MusicPlayerBlock;

public final class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TFMusic.MOD_ID);

    public static final RegistryObject<MusicPlayerBlock> MUSIC_PLAYER = BLOCKS.register(
            "music_player",
            () -> new MusicPlayerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.5F)
                    .sound(SoundType.WOOD))
    );

    public static final RegistryObject<MusicBurnerBlock> MUSIC_BURNER = BLOCKS.register(
            "music_burner",
            () -> new MusicBurnerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .sound(SoundType.METAL))
    );

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
