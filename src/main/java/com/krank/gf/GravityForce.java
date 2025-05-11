package com.krank.gf;

import com.krank.gf.config.GFConfig;
import com.krank.gf.event.EventService;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(GravityForce.MOD_ID)
public class GravityForce {
	public static final String MOD_ID = "gf";
	public static final Logger LOGGER = LogUtils.getLogger();

	public GravityForce(FMLJavaModLoadingContext context) {
		context.registerConfig(ModConfig.Type.COMMON, GFConfig.COMMON_SPEC);
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(new EventService());
	}

	public static final TagKey<Block> SOIL_LIKE =
					BlockTags.create(ResourceLocation.fromNamespaceAndPath(GravityForce.MOD_ID, "soil_like"));

	public static Logger getLogger() {
		return LOGGER;
	}
}