package com.grizzly;

import com.grizzly.registry.ModEntities;
import com.grizzly.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(GrizzlyMod.MODID)
public class GrizzlyMod {
    public static final String MODID = "grizzlybear";

    public GrizzlyMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
    }
}
