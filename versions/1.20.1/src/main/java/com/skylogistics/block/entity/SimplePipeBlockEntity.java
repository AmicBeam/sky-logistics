package com.skylogistics.block.entity;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.util.SimplePipeConnection;
import com.skylogistics.util.SimplePipeType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public class SimplePipeBlockEntity extends BlockEntity {
    private int targetCursor;

    public SimplePipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIMPLE_PIPE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SimplePipeBlockEntity pipeEntity) {
        if (!(state.getBlock() instanceof SimplePipeBlock pipe) || !pipe.enabled()) {
            return;
        }
        for (Direction direction : Direction.values()) {
            if (state.getValue(SimplePipeBlock.connectionProperty(direction)) == SimplePipeConnection.EXTRACT) {
                pipeEntity.transferFrom(level, pos, pipe, direction);
            }
        }
    }

    public static boolean hasCapability(Level level, BlockPos pos, Direction side, SimplePipeType type) {
        BlockEntity target = level.getBlockEntity(pos);
        if (target == null) {
            return false;
        }
        return switch (type) {
            case ITEM -> target.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
                    .map(handler -> handler.getSlots() > 0).orElse(false);
            case FLUID -> target.getCapability(ForgeCapabilities.FLUID_HANDLER, side)
                    .map(handler -> handler.getTanks() > 0).orElse(false);
            case ENERGY -> target.getCapability(ForgeCapabilities.ENERGY, side)
                    .map(storage -> storage.getMaxEnergyStored() > 0 || storage.canExtract() || storage.canReceive())
                    .orElse(false);
        };
    }

    private void transferFrom(Level level, BlockPos pipePos, SimplePipeBlock pipe, Direction sourceDirection) {
        BlockPos sourcePos = pipePos.relative(sourceDirection);
        Direction sourceSide = sourceDirection.getOpposite();
        List<Endpoint> targets = discoverTargets(level, pipePos, pipe, sourcePos);
        if (targets.isEmpty()) {
            return;
        }
        boolean moved = switch (pipe.pipeType()) {
            case ITEM -> moveItem(level, sourcePos, sourceSide, targets,
                    SkyLogisticsConfig.simpleItemPipeTransferRate());
            case FLUID -> moveFluid(level, sourcePos, sourceSide, targets,
                    SkyLogisticsConfig.simpleFluidPipeTransferRate());
            case ENERGY -> moveEnergy(level, sourcePos, sourceSide, targets,
                    SkyLogisticsConfig.simpleEnergyPipeTransferRate());
        };
        if (moved) {
            targetCursor++;
            setChanged();
        }
    }

    private List<Endpoint> discoverTargets(Level level, BlockPos origin, SimplePipeBlock pipe, BlockPos sourcePos) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<Endpoint> targets = new ArrayList<>();
        pending.add(origin);
        while (!pending.isEmpty()) {
            BlockPos current = pending.removeFirst();
            if (!visited.add(current) || !level.hasChunkAt(current)) {
                continue;
            }
            BlockState state = level.getBlockState(current);
            if (!(state.getBlock() instanceof SimplePipeBlock currentPipe)
                    || currentPipe.pipeType() != pipe.pipeType()) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                SimplePipeConnection connection = state.getValue(SimplePipeBlock.connectionProperty(direction));
                if (connection == SimplePipeConnection.PIPE) {
                    BlockPos next = current.relative(direction);
                    if (!visited.contains(next)) {
                        pending.addLast(next);
                    }
                } else if (connection == SimplePipeConnection.INSERT) {
                    BlockPos targetPos = current.relative(direction);
                    if (!targetPos.equals(sourcePos)) {
                        targets.add(new Endpoint(targetPos, direction.getOpposite()));
                    }
                }
            }
        }
        return targets;
    }

    private boolean moveItem(Level level, BlockPos sourcePos, Direction sourceSide, List<Endpoint> targets,
            int limit) {
        IItemHandler source = itemHandler(level, sourcePos, sourceSide);
        if (source == null) {
            return false;
        }
        for (int sourceSlot = 0; sourceSlot < source.getSlots(); sourceSlot++) {
            ItemStack offered = source.extractItem(sourceSlot, limit, true);
            if (offered.isEmpty()) {
                continue;
            }
            for (int offset = 0; offset < targets.size(); offset++) {
                Endpoint endpoint = targets.get(Math.floorMod(targetCursor + offset, targets.size()));
                IItemHandler target = itemHandler(level, endpoint.pos(), endpoint.side());
                if (target == null) {
                    continue;
                }
                for (int targetSlot = 0; targetSlot < target.getSlots(); targetSlot++) {
                    ItemStack remainder = target.insertItem(targetSlot, offered.copy(), true);
                    int accepted = offered.getCount() - remainder.getCount();
                    if (accepted <= 0) {
                        continue;
                    }
                    ItemStack extracted = source.extractItem(sourceSlot, accepted, false);
                    if (extracted.isEmpty()) {
                        return false;
                    }
                    ItemStack notInserted = target.insertItem(targetSlot, extracted, false);
                    if (!notInserted.isEmpty()) {
                        source.insertItem(sourceSlot, notInserted, false);
                    }
                    return notInserted.getCount() < extracted.getCount();
                }
            }
        }
        return false;
    }

    private boolean moveFluid(Level level, BlockPos sourcePos, Direction sourceSide, List<Endpoint> targets,
            int limit) {
        IFluidHandler source = fluidHandler(level, sourcePos, sourceSide);
        if (source == null) {
            return false;
        }
        for (int tank = 0; tank < source.getTanks(); tank++) {
            FluidStack stored = source.getFluidInTank(tank);
            if (stored.isEmpty()) {
                continue;
            }
            FluidStack request = stored.copy();
            request.setAmount(Math.min(limit, stored.getAmount()));
            FluidStack offered = source.drain(request, IFluidHandler.FluidAction.SIMULATE);
            if (offered.isEmpty()) {
                continue;
            }
            for (int offset = 0; offset < targets.size(); offset++) {
                Endpoint endpoint = targets.get(Math.floorMod(targetCursor + offset, targets.size()));
                IFluidHandler target = fluidHandler(level, endpoint.pos(), endpoint.side());
                if (target == null) {
                    continue;
                }
                int accepted = target.fill(offered.copy(), IFluidHandler.FluidAction.SIMULATE);
                if (accepted <= 0) {
                    continue;
                }
                FluidStack drained = source.drain(copyWithAmount(offered, accepted),
                        IFluidHandler.FluidAction.EXECUTE);
                if (drained.isEmpty()) {
                    return false;
                }
                int inserted = target.fill(drained.copy(), IFluidHandler.FluidAction.EXECUTE);
                if (inserted < drained.getAmount()) {
                    source.fill(copyWithAmount(drained, drained.getAmount() - inserted),
                            IFluidHandler.FluidAction.EXECUTE);
                }
                return inserted > 0;
            }
        }
        return false;
    }

    private boolean moveEnergy(Level level, BlockPos sourcePos, Direction sourceSide, List<Endpoint> targets,
            int limit) {
        IEnergyStorage source = energyHandler(level, sourcePos, sourceSide);
        if (source == null) {
            return false;
        }
        int offered = source.extractEnergy(limit, true);
        if (offered <= 0) {
            return false;
        }
        for (int offset = 0; offset < targets.size(); offset++) {
            Endpoint endpoint = targets.get(Math.floorMod(targetCursor + offset, targets.size()));
            IEnergyStorage target = energyHandler(level, endpoint.pos(), endpoint.side());
            if (target == null) {
                continue;
            }
            int accepted = target.receiveEnergy(offered, true);
            if (accepted <= 0) {
                continue;
            }
            int extracted = source.extractEnergy(accepted, false);
            int inserted = target.receiveEnergy(extracted, false);
            if (inserted < extracted) {
                source.receiveEnergy(extracted - inserted, false);
            }
            return inserted > 0;
        }
        return false;
    }

    private static IItemHandler itemHandler(Level level, BlockPos pos, Direction side) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity == null ? null : entity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
    }

    private static IFluidHandler fluidHandler(Level level, BlockPos pos, Direction side) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity == null ? null : entity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
    }

    private static IEnergyStorage energyHandler(Level level, BlockPos pos, Direction side) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity == null ? null : entity.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
    }

    private static FluidStack copyWithAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    private record Endpoint(BlockPos pos, Direction side) {
    }
}
