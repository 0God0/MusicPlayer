package org.techfield.tfmusic.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.techfield.tfmusic.TFMusic;
import org.techfield.tfmusic.block.entity.MusicPlayerBlockEntity;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TFMusic.MOD_ID);

    public static final RegistryObject<BlockEntityType<MusicPlayerBlockEntity>> MUSIC_PLAYER = BLOCK_ENTITIES.register(
            "music_player",
            () -> BlockEntityType.Builder.of(MusicPlayerBlockEntity::new, ModBlocks.MUSIC_PLAYER.get()).build(null)
    );

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
