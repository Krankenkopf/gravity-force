package com.krank.gf.event;

import com.krank.gf.GravityForce;
import com.krank.gf.config.GFConfig;
import com.krank.gf.physics.PhysicsService;
import com.krank.gf.physics.TriggerAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

import static com.krank.gf.physics.PhysicsService.TASKS;

public class EventService {
	@SubscribeEvent
	public void onWorldTick(TickEvent.LevelTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		if (event.level.isClientSide) return;

		ServerLevel level = (ServerLevel) event.level;
		if (!TASKS.isEmpty()) {
			long now = level.getGameTime();

			int processed = 0;
			Iterator<Map.Entry<BlockPos, PhysicsService.FallTask>> planned = TASKS.entrySet().iterator();
			Map<BlockPos, PhysicsService.FallTask> ready = new HashMap<>();

			int maxFallingBlocks = GFConfig.COMMON.maxFallingBlocks.get();

			while (planned.hasNext() && processed < maxFallingBlocks) {
				Map.Entry<BlockPos, PhysicsService.FallTask> entry = planned.next();
				BlockPos pos = entry.getKey();
				PhysicsService.FallTask task = entry.getValue();

				if (task.scheduledTick() > now) continue; // ещё не время
				ready.put(pos, task);
				planned.remove();
				processed++;
			}

			for (Map.Entry<BlockPos, PhysicsService.FallTask> entry : ready.entrySet()) {
				BlockPos pos = entry.getKey();
				PhysicsService.FallTask task = entry.getValue();

				PhysicsService.checkStabilityRecursive(task.level(), pos);
			}

			return;
		}

		// Получаем список игроков на сервере. Если игроков нет — ничего не делаем.
		var players = level.players();
		if (players.isEmpty()) return;

		// Проверяем 50 случайных позиций вокруг случайных игроков
//		for (int i = 0; i < 50; i++) {
//			Player player = players.get(level.random.nextInt(players.size()));
//			BlockPos pos = player.blockPosition().offset(
//							level.random.nextInt(16) - 8,
//							level.random.nextInt(8) - 4,
//							level.random.nextInt(16) - 8
//			);
//
//			PhysicsService.triggerCollapse(level, pos);
//		}
	}

	@SubscribeEvent
	public void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!event.getLevel().isClientSide()) {
			ServerLevel serverLevel = (ServerLevel)event.getLevel();
			ItemStack item = event.getItemStack();
			BlockPos pos = event.getPos();
			if (event.getHand() == InteractionHand.MAIN_HAND && event.getResult() != Event.Result.DENY && !item.isEmpty() && item.getItem() instanceof BlockItem) {
				PhysicsService.trigger(serverLevel, pos, TriggerAction.CHECK);
			}

		}
	}

	@SubscribeEvent
	public void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		if (!event.getLevel().isClientSide()) {
			ServerLevel serverLevel = (ServerLevel)event.getLevel();
			BlockPos pos = event.getPos();
			if (event.getHand() == InteractionHand.MAIN_HAND && event.getResult() != Event.Result.DENY) {
				PhysicsService.trigger(serverLevel, pos, TriggerAction.CHECK);
			}
		}
	}

	@SubscribeEvent
	public void onBreakBlock(BlockEvent.BreakEvent event) {
		LevelAccessor level = event.getLevel();
		if (level.isClientSide()) return;

		ServerLevel serverLevel = (ServerLevel) level;
		BlockPos pos = event.getPos();
		PhysicsService.trigger(serverLevel, pos, TriggerAction.BREAK);
		GravityForce.LOGGER.info(TASKS.toString());
	}
}
