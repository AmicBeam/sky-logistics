package com.skylogistics.block.entity;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.item.EulogiaCrystalItem;
import com.skylogistics.recipe.OfferingRecipe;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModRecipes;
import com.skylogistics.util.StackData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class OfferingAltarBlockEntity extends SingleSlotDisplayBlockEntity {
    private static final String DATA_TAG = "SkyLogisticsOfferingAltar";
    private static final int ACTIVE_STRUCTURE_CHECK_INTERVAL = 20;
    private static final int TIER_ONE_FRAME_RADIUS = 2;
    private static final int TIER_TWO_PILLAR_RADIUS = 3;
    private static final int FRAME_Y_OFFSET = -1;
    private static final int PILLAR_SHAFT_Y_OFFSET = 0;
    private static final int PILLAR_CAP_Y_OFFSET = 1;

    private ResourceKey<Recipe<?>> activeRecipeId;
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

    public boolean hasActiveRecipe() {
        return activeRecipeId != null;
    }

    @Override
    protected boolean isDisplaySlotLocked() {
        return hasActiveRecipe();
    }

    @Override
    protected boolean canPlayerExtractDisplayWhileLocked(Player player) {
        return true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        if (activeRecipeId != null) {
            tag.putString("ActiveRecipe", activeRecipeId.identifier().toString());
            tag.putInt("Progress", progress);
        }
        tag.putBoolean("NeedsRecipeCheck", needsRecipeCheck);
        output.store(DATA_TAG, CompoundTag.CODEC, tag);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        CompoundTag tag = input.read(DATA_TAG, CompoundTag.CODEC).orElse(new CompoundTag());
        String savedRecipeId = tag.getStringOr("ActiveRecipe", "");
        activeRecipeId = savedRecipeId.isEmpty()
                ? null
                : ResourceKey.create(Registries.RECIPE, Identifier.parse(savedRecipeId));
        progress = tag.getIntOr("Progress", 0);
        needsRecipeCheck = tag.getBooleanOr("NeedsRecipeCheck", false) || activeRecipeId == null;
    }

    private void startRecipe(RecipeHolder<OfferingRecipe> recipe) {
        activeRecipeId = recipe.id();
        activeRecipe = recipe.value();
        progress = 0;
        needsRecipeCheck = false;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.55F, 1.6F);
            sendStartParticles(serverLevel);
        }
    }

    public void sendRecipeStartFailureMessage(Player player) {
        if (activeRecipeId != null || getDisplayedItem().isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Component message = recipeStartFailureMessage(serverLevel);
        if (message != null) {
            player.sendOverlayMessage(message);
        }
    }

    private Component recipeStartFailureMessage(ServerLevel level) {
        int minY = SkyLogisticsConfig.skyRitualMinY();
        if (worldPosition.getY() < minY) {
            return Component.translatable("message.skylogistics.offering_altar.too_low", worldPosition.getY(), minY);
        }

        List<OfferingTableBlockEntity> tables = getOfferingTables();
        if (tables.size() != 4) {
            return Component.translatable("message.skylogistics.offering_altar.tables", tables.size());
        }
        if (!hasTierOneFrame()) {
            return Component.translatable("message.skylogistics.offering_altar.frame");
        }

        int structureTier = hasTierTwoPillars() ? 2 : 1;
        ItemStack mainStack = getDisplayedItem();
        List<OfferingRecipe> recipes = skyOfferingRecipes(level).stream()
                .map(RecipeHolder::value)
                .toList();
        if (recipes.isEmpty()) {
            return Component.translatable("message.skylogistics.offering_altar.no_recipes");
        }

        Optional<OfferingRecipe> sameMainItemRecipe = recipes.stream()
                .filter(recipe -> recipe.main().ingredient().test(mainStack))
                .findFirst();
        if (sameMainItemRecipe.isEmpty()) {
            return Component.translatable("message.skylogistics.offering_altar.no_main_recipe",
                    mainStack.getHoverName());
        }

        List<OfferingRecipe> mainMatches = recipes.stream()
                .filter(recipe -> recipe.main().matches(mainStack))
                .toList();
        if (mainMatches.isEmpty()) {
            OfferingRecipe.CountedIngredient main = sameMainItemRecipe.get().main();
            if (main.requireChargedCrystal() && !EulogiaCrystalItem.isCharged(mainStack)) {
                return Component.translatable("message.skylogistics.offering_altar.main_uncharged",
                        mainStack.getHoverName());
            }
            if (mainStack.getCount() < main.count()) {
                return Component.translatable("message.skylogistics.offering_altar.main_count",
                        describeIngredient(main));
            }
            return Component.translatable("message.skylogistics.offering_altar.no_main_recipe",
                    mainStack.getHoverName());
        }

        List<ItemStack> offeringStacks = tables.stream()
                .map(OfferingTableBlockEntity::getDisplayedItem)
                .toList();
        Optional<OfferingRecipe> matchedHigherTierRecipe = mainMatches.stream()
                .filter(recipe -> structureTier < recipe.requiredTier())
                .filter(recipe -> OfferingCheck.evaluate(recipe, offeringStacks).score() == 0)
                .min(Comparator.comparingInt(OfferingRecipe::requiredTier));
        if (matchedHigherTierRecipe.isPresent()) {
            return Component.translatable("message.skylogistics.offering_altar.tier",
                    matchedHigherTierRecipe.get().requiredTier(), structureTier);
        }

        List<OfferingRecipe> tierMatches = mainMatches.stream()
                .filter(recipe -> structureTier >= recipe.requiredTier())
                .toList();
        if (tierMatches.isEmpty()) {
            int requiredTier = mainMatches.stream()
                    .mapToInt(OfferingRecipe::requiredTier)
                    .min()
                    .orElse(1);
            return Component.translatable("message.skylogistics.offering_altar.tier", requiredTier, structureTier);
        }

        OfferingCheck bestCheck = tierMatches.stream()
                .map(recipe -> OfferingCheck.evaluate(recipe, offeringStacks))
                .min(Comparator.comparingInt(OfferingCheck::score))
                .orElse(null);
        if (bestCheck == null) {
            return Component.translatable("message.skylogistics.offering_altar.no_recipe_match");
        }
        if (!bestCheck.missing().isEmpty()) {
            return Component.translatable("message.skylogistics.offering_altar.missing_offerings",
                    describeIngredients(bestCheck.missing()));
        }
        if (bestCheck.extraCount() > 0) {
            return Component.translatable("message.skylogistics.offering_altar.extra_offerings");
        }
        return Component.translatable("message.skylogistics.offering_altar.no_recipe_match");
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
        Optional<RecipeHolder<OfferingRecipe>> recipe = findMatchingRecipe(level, structureTier);
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
            sendRitualParticles(level, recipe);
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
        sendCompletionParticles(level);
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
        } else if (StackData.sameItemAndComponents(stored, remainder)) {
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

    private Optional<RecipeHolder<OfferingRecipe>> findMatchingRecipe(ServerLevel level, int structureTier) {
        ItemStack mainStack = getDisplayedItem();
        if (mainStack.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> offeringStacks = getOfferingStacks();
        OfferingRecipe.Input input = new OfferingRecipe.Input(mainStack, offeringStacks);
        return level.recipeAccess().recipeMap()
                .getRecipesFor(ModRecipes.SKY_OFFERING_TYPE.get(), input, level)
                .filter(recipe -> structureTier >= recipe.value().requiredTier())
                .findFirst();
    }

    private OfferingRecipe activeRecipe(ServerLevel level) {
        if (activeRecipeId == null) {
            return null;
        }
        if (activeRecipe != null) {
            return activeRecipe;
        }
        activeRecipe = skyOfferingRecipes(level).stream()
                .filter(recipe -> activeRecipeId.equals(recipe.id()))
                .map(RecipeHolder::value)
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
        if (!hasTierOneFrame()) {
            return 0;
        }
        return hasTierTwoPillars() ? 2 : 1;
    }

    private boolean hasTierOneFrame() {
        for (int dx = -TIER_ONE_FRAME_RADIUS; dx <= TIER_ONE_FRAME_RADIUS; dx++) {
            for (int dz = -TIER_ONE_FRAME_RADIUS; dz <= TIER_ONE_FRAME_RADIUS; dz++) {
                if (Math.abs(dx) != TIER_ONE_FRAME_RADIUS && Math.abs(dz) != TIER_ONE_FRAME_RADIUS) {
                    continue;
                }
                BlockState frameState = level.getBlockState(worldPosition.offset(dx, FRAME_Y_OFFSET, dz));
                if (!isCelestialStoneFrameBlock(frameState)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasTierTwoPillars() {
        for (int dx : new int[] {-TIER_TWO_PILLAR_RADIUS, TIER_TWO_PILLAR_RADIUS}) {
            for (int dz : new int[] {-TIER_TWO_PILLAR_RADIUS, TIER_TWO_PILLAR_RADIUS}) {
                if (!isCelestialStoneFrameBlock(level.getBlockState(worldPosition.offset(dx, FRAME_Y_OFFSET, dz)))
                        || !isCelestialStoneFrameBlock(level.getBlockState(worldPosition.offset(dx, PILLAR_SHAFT_Y_OFFSET, dz)))
                        || !isCelestialGlass(level.getBlockState(worldPosition.offset(dx, PILLAR_CAP_Y_OFFSET, dz)))) {
                    return false;
                }
            }
        }
        return true;
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

    private static Component describeIngredients(List<OfferingRecipe.CountedIngredient> ingredients) {
        MutableComponent description = Component.empty();
        for (int i = 0; i < ingredients.size(); i++) {
            if (i > 0) {
                description.append(", ");
            }
            description.append(describeIngredient(ingredients.get(i)));
        }
        return description;
    }

    private static Component describeIngredient(OfferingRecipe.CountedIngredient ingredient) {
        Optional<ItemStack> example = ingredient.ingredient().items()
                .findFirst()
                .map(holder -> new ItemStack(holder.value()));
        Component name = example.map(ItemStack::getHoverName)
                .orElseGet(() -> Component.translatable("message.skylogistics.offering_altar.unknown_ingredient"));
        return Component.literal(ingredient.count() + "x ").append(name);
    }

    private static List<RecipeHolder<OfferingRecipe>> skyOfferingRecipes(ServerLevel level) {
        RecipeManager recipes = level.recipeAccess();
        return List.copyOf(recipes.recipeMap().byType(ModRecipes.SKY_OFFERING_TYPE.get()));
    }

    private record OfferingCheck(List<OfferingRecipe.CountedIngredient> missing, int extraCount) {
        private static OfferingCheck evaluate(OfferingRecipe recipe, List<ItemStack> offeringStacks) {
            boolean[] used = new boolean[offeringStacks.size()];
            List<OfferingRecipe.CountedIngredient> missing = new ArrayList<>();
            for (OfferingRecipe.CountedIngredient ingredient : recipe.offerings()) {
                int found = findMatchingOffering(ingredient, offeringStacks, used);
                if (found < 0) {
                    missing.add(ingredient);
                } else {
                    used[found] = true;
                }
            }

            int extraCount = 0;
            for (int i = 0; i < offeringStacks.size(); i++) {
                if (!used[i] && !offeringStacks.get(i).isEmpty()) {
                    extraCount++;
                }
            }
            return new OfferingCheck(missing, extraCount);
        }

        private static int findMatchingOffering(OfferingRecipe.CountedIngredient ingredient,
                List<ItemStack> stacks, boolean[] used) {
            for (int i = 0; i < stacks.size(); i++) {
                if (!used[i] && ingredient.matches(stacks.get(i))) {
                    return i;
                }
            }
            return -1;
        }

        private int score() {
            return missing.size() * 2 + extraCount;
        }
    }

    private void sendStartParticles(ServerLevel level) {
        double x = worldPosition.getX() + 0.5D;
        double y = worldPosition.getY() + 1.15D;
        double z = worldPosition.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 12, 0.4D, 0.08D, 0.4D, 0.012D);
        level.sendParticles(ParticleTypes.GLOW, x, y + 0.15D, z, 6, 0.35D, 0.05D, 0.35D, 0.006D);
        sendFramePulse(level, 0.2D);
    }

    private void sendRitualParticles(ServerLevel level, OfferingRecipe recipe) {
        double progressRatio = recipe.duration() <= 0 ? 1.0D : Math.min(1.0D, (double) progress / recipe.duration());
        double x = worldPosition.getX() + 0.5D;
        double y = worldPosition.getY() + 1.15D;
        double z = worldPosition.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.END_ROD, x, y + progressRatio * 0.25D, z,
                2, 0.18D, 0.04D, 0.18D, 0.012D + progressRatio * 0.008D);
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.2D, z, 1, 0.48D, 0.04D, 0.48D, 0.018D);
        if (progress % 10 == 0) {
            sendTableTrails(level);
        }
        if (progress % 20 == 0) {
            sendFramePulse(level, progressRatio);
        }
        if (progressRatio > 0.85D && progress % 10 == 0) {
            level.sendParticles(ParticleTypes.GLOW, x, y + 0.55D, z, 2, 0.25D, 0.06D, 0.25D, 0.004D);
        }
    }

    private void sendCompletionParticles(ServerLevel level) {
        double x = worldPosition.getX() + 0.5D;
        double y = worldPosition.getY() + 1.18D;
        double z = worldPosition.getZ() + 0.5D;
        sendFramePulse(level, 1.0D);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 18, 0.5D, 0.16D, 0.5D, 0.018D);
        level.sendParticles(ParticleTypes.GLOW, x, y + 0.35D, z, 10, 0.32D, 0.12D, 0.32D, 0.01D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.2D, z, 6, 0.35D, 0.08D, 0.35D, 0.025D);
    }

    private void sendTableTrails(ServerLevel level) {
        double targetX = worldPosition.getX() + 0.5D;
        double targetY = worldPosition.getY() + 1.25D;
        double targetZ = worldPosition.getZ() + 0.5D;
        for (OfferingTableBlockEntity table : getOfferingTables()) {
            ItemStack stack = table.getDisplayedItem();
            if (stack.isEmpty()) {
                continue;
            }
            BlockPos tablePos = table.getBlockPos();
            double sourceX = tablePos.getX() + 0.5D;
            double sourceY = tablePos.getY() + 1.18D;
            double sourceZ = tablePos.getZ() + 0.5D;
            ItemStack particleStack = stack.copy();
            particleStack.setCount(1);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM,
                            ItemStackTemplate.fromNonEmptyStack(particleStack)), sourceX, sourceY, sourceZ,
                    0, (targetX - sourceX) * 0.1D, (targetY - sourceY) * 0.1D + 0.03D,
                    (targetZ - sourceZ) * 0.1D, 1.0D);
        }
    }

    private void sendFramePulse(ServerLevel level, double progressRatio) {
        int[][] points = {
                {-2, -2}, {0, -2}, {2, -2},
                {-2, 0}, {2, 0},
                {-2, 2}, {0, 2}, {2, 2}
        };
        double y = worldPosition.getY() + 0.05D;
        double lift = 0.012D + progressRatio * 0.018D;
        for (int[] point : points) {
            level.sendParticles(ParticleTypes.GLOW, worldPosition.getX() + point[0] + 0.5D, y,
                    worldPosition.getZ() + point[1] + 0.5D, 1, 0.0D, lift, 0.0D, 0.0D);
        }
    }
}
