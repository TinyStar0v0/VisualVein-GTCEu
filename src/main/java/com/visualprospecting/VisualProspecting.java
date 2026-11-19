package com.visualprospecting;

import com.visualprospecting.config.VPConfig;
import com.visualprospecting.network.VPNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VisualProspecting.MODID)
public class VisualProspecting {

    public static final String MODID = "visualprospecting";
    public static final Logger LOGGER = LoggerFactory.getLogger("VisualProspecting");

    @SuppressWarnings("removal")
    public VisualProspecting() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, VPConfig.COMMON_SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, VPConfig.CLIENT_SPEC);
        }
        VPNetwork.register();
        LOGGER.info("Visual Prospecting: GTCEu initialised");
    }
}
