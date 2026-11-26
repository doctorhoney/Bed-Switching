package com.doctorhoney.bedswitching;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point of the bedrespawn mod.
 */
@Mod(BedSwitching.MODID)
public class BedSwitching {
    public static final String MODID = "bedrespawn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BedSwitching(IEventBus modEventBus) {
        ModAttachments.register(modEventBus);
        modEventBus.addListener(ModNetwork::register);
        LOGGER.info("BedSwitching initialized");
    }
}
