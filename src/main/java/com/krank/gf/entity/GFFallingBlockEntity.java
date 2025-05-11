package com.krank.gf.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Objects;

public class GFFallingBlockEntity extends FallingBlockEntity {
	private static final Logger LOGGER = LogUtils.getLogger();

	public GFFallingBlockEntity(Level level, double x, double y, double z, BlockState state) {
		super(EntityType.FALLING_BLOCK, level); // используем тип ванильный, но логика будет своя
		this.blockState = state;
		this.blocksBuilding = true;
		this.setPos(x, y, z);
		this.setDeltaMovement(0, 0, 0);
	}

	@Override
	public void tick() {
		if (this.blockState.isAir()) {
			// Если падающий блок уже стал воздухом — убираем сущность
			this.discard();
			return;
		}

		Block fallingBlock = this.blockState.getBlock();
		++this.time; // счётчик тиков, сколько блок падает

		// Гравитация
		if (!this.isNoGravity()) {
			this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
		}

		// Перемещение
		this.move(MoverType.SELF, this.getDeltaMovement());

		if (!this.level().isClientSide) {
			BlockPos pos = this.blockPosition();

			// === Особый случай: бетонный порошок, который может гидратироваться ===
			boolean isConcretePowder = fallingBlock instanceof ConcretePowderBlock;
			boolean willSolidifyHere = isConcretePowder &&
							this.blockState.canBeHydrated(this.level(), pos, this.level().getFluidState(pos), pos);

			// Если блок быстро движется, проверим столкновение с водой на пути
			if (isConcretePowder && this.getDeltaMovement().lengthSqr() > 1.0D) {
				BlockHitResult hitResult = this.level().clip(
								new ClipContext(new Vec3(this.xo, this.yo, this.zo), this.position(),
												ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, this));
				if (hitResult.getType() != HitResult.Type.MISS &&
								this.blockState.canBeHydrated(this.level(), pos,
												this.level().getFluidState(hitResult.getBlockPos()), hitResult.getBlockPos())) {
					pos = hitResult.getBlockPos();
					willSolidifyHere = true;
				}
			}

			// === Если ещё падаем ===
			if (!this.onGround() && !willSolidifyHere) {
				boolean outOfWorld =
								this.time > 100 && (pos.getY() <= this.level().getMinBuildHeight()
												|| pos.getY() > this.level().getMaxBuildHeight());
				boolean tooLongFalling = this.time > 600;

				if (outOfWorld || tooLongFalling) {
					if (this.dropItem && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
						this.spawnAtLocation(fallingBlock);
					}
					this.discard();
				}
			} else { // === Приземлились ===
				BlockState landingState = this.level().getBlockState(pos);
				this.setDeltaMovement(this.getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));

				if (!landingState.is(Blocks.MOVING_PISTON)) {
					if (!this.cancelDrop) {
						boolean canReplaceLandingBlock =
										landingState.canBeReplaced(new DirectionalPlaceContext(
														this.level(), pos, Direction.DOWN, ItemStack.EMPTY, Direction.UP));

						boolean noSupportBelow =
										FallingBlock.isFree(this.level().getBlockState(pos.below()))
														&& (!isConcretePowder || !willSolidifyHere);

						boolean canSurviveHere =
										this.blockState.canSurvive(this.level(), pos) && !noSupportBelow;

						// === НАША ЛОГИКА ===
						// Если блок, на который мы упали, неполный (нет коллизии) или паутина
						boolean isNonSolidLanding =
										landingState.getCollisionShape(this.level(), pos).isEmpty()
														|| landingState.is(Blocks.COBWEB);

						if (isNonSolidLanding) {
							// Дропаем нижний блок
							Block.dropResources(landingState, this.level(), pos);
							// Убираем его
							this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
							// Ставим падающий
							if (!this.level.setBlock(pos, this.blockState, 3)) {
								this.spawnAtLocation(fallingBlock.asItem());
							}
							this.discard();
							return;
						}

						// === Ванильная логика ===
						if (canReplaceLandingBlock && canSurviveHere) {
							// Проверяем логгированность водой
							if (this.blockState.hasProperty(BlockStateProperties.WATERLOGGED)
											&& this.level().getFluidState(pos).getType() == Fluids.WATER) {
								this.blockState = this.blockState.setValue(
												BlockStateProperties.WATERLOGGED, Boolean.TRUE);
							}

							if (this.level().setBlock(pos, this.blockState, 3)) {
								((ServerLevel) this.level()).getChunkSource().chunkMap.broadcast(
												this, new ClientboundBlockUpdatePacket(pos, this.level().getBlockState(pos)));
								this.discard();

								if (fallingBlock instanceof Fallable fallable) {
									fallable.onLand(this.level(), pos, this.blockState, landingState, this);
								}

								// Копируем данные BlockEntity (если были)
								if (this.blockData != null && this.blockState.hasBlockEntity()) {
									BlockEntity blockEntity = this.level().getBlockEntity(pos);
									if (blockEntity != null) {
										CompoundTag nbt = blockEntity.saveWithoutMetadata();
										for (String key : this.blockData.getAllKeys()) {
											nbt.put(key, Objects.requireNonNull(this.blockData.get(key)).copy());
										}
										try {
											blockEntity.load(nbt);
										} catch (Exception e) {
											LOGGER.error("Failed to load block entity from falling block", e);
										}
										blockEntity.setChanged();
									}
								}
							} else if (this.dropItem && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
								this.discard();
								this.callOnBrokenAfterFall(fallingBlock, pos);
								this.spawnAtLocation(fallingBlock);
							}
						} else {
							this.discard();
							if (this.dropItem && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
								this.callOnBrokenAfterFall(fallingBlock, pos);
								this.spawnAtLocation(fallingBlock);
							}
						}
					} else {
						this.discard();
						this.callOnBrokenAfterFall(fallingBlock, pos);
					}
				}
			}
		}

		// Замедление
		this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
	}
}
