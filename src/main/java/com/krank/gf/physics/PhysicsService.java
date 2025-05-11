package com.krank.gf.physics;

import com.krank.gf.GravityForce;
import com.krank.gf.entity.GFFallingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class PhysicsService {
	// фиксированный порядок обхода соседей
	private static final int[][] SUPPORTS_FRAME = {
					{0, 0, -1},   // север
					{0, 0, 1},    // юг
					{-1, 0, 0},   // запад
					{1, 0, 0},    // восток
					{-1, 0, 1},   // юго-запад
					{1, 0, -1},   // северо-восток
					{1, 0, 1},    // юго-восток
					{-1, 0, -1},  // северо-запад
	};
	private static final int[][] NEIGHBOURS_FRAME = {
					{0, 0, -1},   // север
					{0, 0, 1},    // юг
					{-1, 0, 0},   // запад
					{1, 0, 0},    // восток
					{-1, 0, 1},   // юго-запад
					{1, 0, -1},   // северо-восток
					{1, 0, 1},    // юго-восток
					{-1, 0, -1},  // северо-запад
					{0, 1, 0},    // верх
					{0, 1, -1},   // верх север
					{0, 1, 1},    // верх юг
					{-1, 1, 0},   // верх запад
					{1, 1, 0},    // верх восток
					{-1, 1, 1},   // верх юго-запад
					{1, 1, -1},   // верх северо-восток
					{1, 1, 1},    // верх юго-восток
					{-1, 1, -1}   // верх северо-запад
	};

	// Очередь отложенных проверок
	public static final Map<BlockPos, FallTask> TASKS = new HashMap<>();

	public record FallTask(ServerLevel level, long scheduledTick) {
		public ServerLevel level() { return this.level; }
	}

	public static void checkStabilityRecursive(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.is(GravityForce.SOIL_LIKE))) return;

		if (hasSupportAround(level, pos)) return;

		// --- блок падает ---
		turnIntoFalling(level, pos, state);
		GravityForce.LOGGER.debug("Обрушение запущено из {}", pos);

		// ставим соседей в очередь
		for (int[] offset : NEIGHBOURS_FRAME) {
			int delay = 5 + level.getRandom().nextInt(6);
			enqueue(level, pos.offset(offset[0], offset[1], offset[2]), delay);
		}
	}

	private static boolean hasSupportAround(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState under = level.getBlockState(below);

		// если снизу есть коллизия — блок устойчив
		if (!under.getCollisionShape(level, below).isEmpty()) return true;

		// проверяем 8 соседей по горизонтали
		for (int[] offset : SUPPORTS_FRAME) {
			BlockPos side = pos.offset(offset[0], offset[1], offset[2]);
			BlockState sideUnder = level.getBlockState(side.below());
			if (!sideUnder.getCollisionShape(level, side.below()).isEmpty()) {
				return true;
			}
		}

		return false; // нигде поддержки нет
	}

	private static void turnIntoFalling(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		GFFallingBlockEntity falling = new GFFallingBlockEntity(
						level,
						pos.getX() + 0.5,
						pos.getY(),
						pos.getZ() + 0.5,
						state
		);
		level.addFreshEntity(falling);
	}

	private static void enqueue(ServerLevel level, BlockPos pos, long delay) {
		if (TASKS.containsKey(pos)) return; // уже в очереди или обработан
		long scheduled = level.getGameTime() + delay;
		TASKS.put(pos, new FallTask(level, scheduled));
	}

	public static void trigger(ServerLevel level, BlockPos pos, TriggerAction action) {
		BlockState state = level.getBlockState(pos);

		GravityForce.LOGGER.info(String.valueOf(state.getBlock()));

		if (state.getCollisionShape(level, pos).isEmpty() || action.equals(TriggerAction.BREAK)) {
			for (int[] offset : NEIGHBOURS_FRAME) {
				BlockPos neighbor = pos.offset(offset[0], offset[1], offset[2]);
				enqueue(level, neighbor, 0);
			}
		} else {
			enqueue(level, pos, 0);
		}
	}
}
