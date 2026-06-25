package com.skylogistics.block.entity;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.recipe.OfferingRecipe;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class OfferingAltarBlockEntity extends SingleSlotDisplayBlockEntity {
    private static final int ACTIVE_STRUCTURE_CHECK_INTERVAL = 20;

    private ResourceLocation activeRecipeId;
    private OfferingRecipe activeRecipe;
    private int progress;
    private boolean needsRecipeCheck = true;
    private boolean suppressRecipeRefresh;

    public OfferingAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OFFERING_ALTAR.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OfferingAltarBlockEntity altar) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (altar.activeRecipeId != null) {
            altar.tickActiveRecipe(serverLevel);
            return;
        }
        if (!altar.needsRecipeCheck) {
            return;
        }
        altar.needsRecipeCheck = false;
        altar.tryStartRecipe(serverLevel);
    }

    public void wakeForRecipeCheck() {
        if (suppressRecipeRefresh) {
            needsRecipeCheck = true;
            setChanged();
            return;
        }
        if (activeRecipeId != null) {
            activeRecipeId = null;
            activeRecipe = null;
            progress = 0;
        }
        if (level instanceof ServerLevel serverLevel) {
            tryStartRecipe(serverLevel);
        } else {
            needsRecipeCheck = true;
        }
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel && activeRecipeId == null && !getDisplayedItem().isEmpty()) {
            wakeForRecipeCheck();
        }
    }

    @Override
    protected void onStoredItemChanged() {
        wakeForRecipeCheck();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (activeRecipeId != null) {
            tag.putString("ActiveRecipe", activeRecipeId.toString());
            tag.putInt("Progress", progress);
        }
        tag.putBoolean("NeedsRecipeCheck", needsRecipeCheck);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        activeRecipeId = tag.contains("ActiveRecipe") ? new ResourceLocation(tag.getString("ActiveRecipe")) : null;
        progress = tag.getInt("Progress");
        needsRecipeCheck = tag.getBoolean("NeedsRecipeCheck") || activeRecipeId == null;
    }

    private void startRecipe(OfferingRecipe recipe) {
        activeRecipeId = recipe.getId();
        activeRecipe = recipe;
        progress = 0;
        needsRecipeCheck = false;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.55F, 1.6F);
            sendWorkingParticles(serverLevel, 24);
        }
    }

    private void tryStartRecipe(ServerLevel level) {
        needsRecipeCheck = false;
        if (activeRecipeId != null || worldPosition.getY() < SkyLogisticsConfig.skyRitualMinY()) {
            return;
        }
        int structureTier = getStructureTier();
        if (structureTier <= 0) {
            return;
        }
        Optional<OfferingRecipe> recipe = findMatchingRecipe(level, structureTier);
        recipe.ifPresent(this::startRecipe);
    }

    private void tickActiveRecipe(ServerLevel level) {
        OfferingRecipe recipe = activeRecipe(level);
        if (recipe == null || shouldCheckActiveStructure()
                && (worldPosition.getY() < SkyLogisticsConfig.skyRitualMinY()
                || getStructureTier() < recipe.requiredTier())) {
            wakeForRecipeCheck();
            return;
        }
        progress++;
        if (progress % 5 == 0) {
            sendWorkingParticles(level, 6);
        }
        if (progress < recipe.duration()) {
            return;
        }
        if (recipe.matches(getDisplayedItem(), getOfferingStacks())) {
            finishRecipe(level, recipe);
        } else {
            wakeForRecipeCheck();
        }
    }

    private void finishRecipe(ServerLevel level, OfferingRecipe recipe) {
        activeRecipeId = null;
        activeRecipe = null;
        progress = 0;
        needsRecipeCheck = false;
        suppressRecipeRefresh = true;
        try {
            consumeInputs(recipe);
            insertOrDropResult(level, recipe.result());
        } finally {
            suppressRecipeRefresh = false;
        }
        level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 1.25F);
        sendWorkingParticles(level, 36);
        tryStartRecipe(level);
    }

    private void consumeInputs(OfferingRecipe recipe) {
        shrinkDisplayedItem(recipe.main().count());
        List<OfferingTableBlockEntity> tables = getOfferingTables();
        boolean[] used = new boolean[tables.size()];
        for (OfferingRecipe.CountedIngredient ingredient : recipe.offerings()) {
            int tableIndex = findMatchingTable(ingredient, tables, used);
            if (tableIndex >= 0) {
                used[tableIndex] = true;
                tables.get(tableIndex).shrinkDisplayedItem(ingredient.count());
            }
        }
    }

    private void insertOrDropResult(ServerLevel level, ItemStack result) {
        ItemStack stored = getDisplayedItem();
        ItemStack remainder = result.copy();
        if (stored.isEmpty()) {
            ItemStack placed = remainder.copy();
            placed.setCount(Math.min(placed.getCount(), Math.min(64, placed.getMaxStackSize())));
            setDisplayedItem(placed);
            remainder.shrink(placed.getCount());
        } else if (ItemStack.isSameItemSameTags(stored, remainder)) {
            int limit = Math.min(64, stored.getMaxStackSize());
            int accepted = Math.min(remainder.getCount(), Math.max(0, limit - stored.getCount()));
            if (accepted > 0) {
                ItemStack merged = stored.copy();
                merged.grow(accepted);
                setDisplayedItem(merged);
                remainder.shrink(accepted);
            }
        }
        if (!remainder.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D,
                    worldPosition.getZ() + 0.5D, remainder);
        }
    }

    private Optional<OfferingRecipe> findMatchingRecipe(ServerLevel level, int structureTier) {
        ItemStack mainStack = getDisplayedItem();
        if (mainStack.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> offeringStacks = getOfferingStacks();
        return level.getRecipeManager().getAllRecipesFor(ModRecipes.SKY_OFFERING_TYPE.get()).stream()
                .filter(recipe -> structureTier >= recipe.requiredTier())
                .filter(recipe -> recipe.matches(mainStack, offeringStacks))
                .findFirst();
    }

    private OfferingRecipe activeRecipe(ServerLevel level) {
        if (activeRecipeId == null) {
            return null;
        }
        if (activeRecipe != null && activeRecipeId.equals(activeRecipe.getId())) {
            return activeRecipe;
        }
        activeRecipe = level.getRecipeManager().getAllRecipesFor(ModRecipes.SKY_OFFERING_TYPE.get()).stream()
                .filter(recipe -> activeRecipeId.equals(recipe.getId()))
                .findFirst()
                .orElse(null);
        return activeRecipe;
    }

    private boolean shouldCheckActiveStructure() {
        return progress % ACTIVE_STRUCTURE_CHECK_INTERVAL == 0;
    }

    private List<ItemStack> getOfferingStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (OfferingTableBlockEntity table : getOfferingTables()) {
            stacks.add(table.getDisplayedItem());
        }
        return stacks;
    }

    private List<OfferingTableBlockEntity> getOfferingTables() {
        List<OfferingTableBlockEntity> tables = new ArrayList<>();
        if (level == null) {
            return tables;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockEntity blockEntity = level.getBlockEntity(worldPosition.relative(direction));
            if (blockEntity instanceof OfferingTableBlockEntity table) {
                tables.add(table);
            }
        }
        return tables;
    }

    private int getStructureTier() {
        if (level == null || getOfferingTables().size() != 4) {
            return 0;
        }
        boolean tierTwo = true;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 2) {
                    continue;
                }
                BlockState frameState = level.getBlockState(worldPosition.offset(dx, 0, dz));
                if (!isCelestialStoneFrameBlock(frameState)) {
                    return 0;
                }
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2
                        && !isCelestialGlass(level.getBlockState(worldPosition.offset(dx, 1, dz)))) {
                    tierTwo = false;
                }
            }
        }
        return tierTwo ? 2 : 1;
    }

    private static boolean isCelestialStoneFrameBlock(BlockState state) {
        Block block = state.getBlock();
        return block == ModBlocks.CELESTIAL_STONE.get()
                || block == ModBlocks.CELESTIAL_STONE_SLAB.get()
                || block == ModBlocks.CELESTIAL_STONE_STAIRS.get()
                || block == ModBlocks.CELESTIAL_STONE_WALL.get();
    }

    private static boolean isCelestialGlass(BlockState state) {
        return state.is(ModBlocks.CELESTIAL_GLASS.get());
    }

    private static int findMatchingTable(OfferingRecipe.CountedIngredient ingredient,
            List<OfferingTableBlockEntity> tables, boolean[] used) {
        for (int i = 0; i < tables.size(); i++) {
            if (!used[i] && ingredient.matches(tables.get(i).getDisplayedItem())) {
                return i;
            }
        }
        return -1;
    }

    private void sendWorkingParticles(ServerLevel level, int count) {
        double x = worldPosition.getX() + 0.5D;
        double y = worldPosition.getY() + 1.15D;
        double z = worldPosition.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, count, 0.55D, 0.18D, 0.55D, 0.015D);
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.15D, z, Math.max(2, count / 3), 0.7D, 0.1D, 0.7D, 0.04D);
    }
}
