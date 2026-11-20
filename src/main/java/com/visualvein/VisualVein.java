package com.visualvein;

import com.visualvein.config.VPConfig;
import com.visualvein.network.VPNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VisualVein.MODID)
public class VisualVein {

    public static final String MODID = "visualvein";
    public static final Logger LOGGER = LoggerFactory.getLogger("VisualVein");

    @SuppressWarnings("removal")
    public VisualVein() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, VPConfig.COMMON_SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, VPConfig.CLIENT_SPEC);
        }
        VPNetwork.register();
        LOGGER.info("VisualVein: GTCEu (Beta b0.06) initialised");
    }
}
