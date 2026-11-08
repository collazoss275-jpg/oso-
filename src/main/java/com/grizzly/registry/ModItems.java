package com.grizzly.registry;

import com.grizzly.GrizzlyMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GrizzlyMod.MODID);

    public static final RegistryObject<Item> GRIZZLY_BEAR_SPAWN_EGG = ITEMS.register("grizzly_bear_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.GRIZZLY_BEAR, 0x5B3A1E, 0xA5703A, new Item.Properties()));

    /** Bote de miel: con 3 el oso deja de atacar al jugador que se los dio. */
    public static final RegistryObject<Item> HONEY_JAR = ITEMS.register("honey_jar",
            () -> new Item(new Item.Properties().stacksTo(16)));
}
