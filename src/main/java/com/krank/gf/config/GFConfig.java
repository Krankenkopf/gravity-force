package com.krank.gf.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class GFConfig {
	public static final ForgeConfigSpec COMMON_SPEC;
	public static final Common COMMON;

	static {
		Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
		COMMON_SPEC = specPair.getRight();
		COMMON = specPair.getLeft();
	}

	public static class Common {
		public final ForgeConfigSpec.IntValue maxFallingBlocks;

		public Common(ForgeConfigSpec.Builder builder) {
			builder.push("general");

			maxFallingBlocks = builder
							.comment("Максимальное количество блоков, которые могут начать падать за одно событие")
							.defineInRange("maxFallingBlocks", 1024, 1, 100000);

			builder.pop();
		}
	}
}