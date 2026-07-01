package com.skylogistics.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skylogistics.item.EulogiaCrystalItem;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public class OfferingRecipe implements Recipe<OfferingRecipe.Input> {
    public static final int MAX_OFFERINGS = 4;

    private final CountedIngredient main;
    private final NonNullList<CountedIngredient> offerings;
    private final ItemStack result;
    private final int duration;
    private final int requiredTier;

    public OfferingRecipe(CountedIngredient main, List<CountedIngredient> offerings, ItemStack result,
            int duration, int requiredTier) {
        if (offerings.size() > MAX_OFFERINGS) {
            throw new IllegalArgumentException("Sky offering recipes support at most " + MAX_OFFERINGS + " offerings");
        }
        this.main = main;
        this.offerings = NonNullList.create();
        this.offerings.addAll(offerings);
        this.result = result.copy();
        this.duration = Math.max(1, duration);
        this.requiredTier = Math.max(1, requiredTier);
    }

    public CountedIngredient main() {
        return main;
    }

    public NonNullList<CountedIngredient> offerings() {
        return offerings;
    }

    public int duration() {
        return duration;
    }

    public int requiredTier() {
        return requiredTier;
    }

    public boolean matches(ItemStack mainStack, List<ItemStack> offeringStacks) {
        if (!main.matches(mainStack)) {
            return false;
        }
        boolean[] used = new boolean[offeringStacks.size()];
        for (CountedIngredient offering : offerings) {
            int found = findMatchingOffering(offering, offeringStacks, used);
            if (found < 0) {
                return false;
            }
            used[found] = true;
        }
        for (int i = 0; i < offeringStacks.size(); i++) {
            if (!used[i] && !offeringStacks.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static int findMatchingOffering(CountedIngredient ingredient, List<ItemStack> stacks, boolean[] used) {
        for (int i = 0; i < stacks.size(); i++) {
            if (!used[i] && ingredient.matches(stacks.get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean matches(Input input, Level level) {
        return matches(input.main(), input.offerings());
    }

    @Override
    public ItemStack assemble(Input input) {
        return result.copy();
    }

    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
    }

    public ItemStack result() {
        return result.copy();
    }

    @Override
    public RecipeSerializer<OfferingRecipe> getSerializer() {
        return ModRecipes.SKY_OFFERING_SERIALIZER.get();
    }

    @Override
    public RecipeType<OfferingRecipe> getType() {
        return ModRecipes.SKY_OFFERING_TYPE.get();
    }

    public ItemStack getToastSymbol() {
        return new ItemStack(ModItems.OFFERING_ALTAR.get());
    }

    public static StreamCodec<RegistryFriendlyByteBuf, OfferingRecipe> streamCodec() {
        return Serializer.STREAM_CODEC;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    public record Input(ItemStack main, List<ItemStack> offerings) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return index == 0 ? main : offerings.get(index - 1);
        }

        @Override
        public int size() {
            return 1 + offerings.size();
        }
    }

    public record CountedIngredient(Ingredient ingredient, int count, boolean requireChargedCrystal) {
        private static final MapCodec<CountedIngredient> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
                Codec.INT.optionalFieldOf("count", 1).forGetter(CountedIngredient::count),
                Codec.BOOL.optionalFieldOf("charged", false).forGetter(CountedIngredient::requireChargedCrystal)
        ).apply(instance, CountedIngredient::new));

        public CountedIngredient {
            count = Math.max(1, count);
        }

        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.getCount() >= count && ingredient.test(stack)
                    && (!requireChargedCrystal || EulogiaCrystalItem.isCharged(stack));
        }

        private void toNetwork(RegistryFriendlyByteBuf buffer) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
            buffer.writeVarInt(count);
            buffer.writeBoolean(requireChargedCrystal);
        }

        private static CountedIngredient fromNetwork(RegistryFriendlyByteBuf buffer) {
            return new CountedIngredient(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                    buffer.readVarInt(), buffer.readBoolean());
        }
    }

    public static final class Serializer {
        private static final MapCodec<OfferingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                CountedIngredient.MAP_CODEC.codec().fieldOf("main").forGetter(OfferingRecipe::main),
                CountedIngredient.MAP_CODEC.codec().listOf().optionalFieldOf("offerings", List.of())
                        .forGetter(recipe -> List.copyOf(recipe.offerings)),
                ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                Codec.INT.optionalFieldOf("duration", 200).forGetter(OfferingRecipe::duration),
                Codec.INT.optionalFieldOf("altar_tier", 1).forGetter(OfferingRecipe::requiredTier)
        ).apply(instance, OfferingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, OfferingRecipe> STREAM_CODEC =
                StreamCodec.ofMember(Serializer::toNetwork, Serializer::fromNetwork);

        public static RecipeSerializer<OfferingRecipe> create() {
            return new RecipeSerializer<>(CODEC, STREAM_CODEC);
        }

        private static OfferingRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            CountedIngredient main = CountedIngredient.fromNetwork(buffer);
            int offeringCount = buffer.readVarInt();
            List<CountedIngredient> offerings = new ArrayList<>(offeringCount);
            for (int i = 0; i < offeringCount; i++) {
                offerings.add(CountedIngredient.fromNetwork(buffer));
            }
            ItemStack result = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            int duration = buffer.readVarInt();
            int requiredTier = buffer.readVarInt();
            return new OfferingRecipe(main, offerings, result, duration, requiredTier);
        }

        private static void toNetwork(OfferingRecipe recipe, RegistryFriendlyByteBuf buffer) {
            recipe.main.toNetwork(buffer);
            buffer.writeVarInt(recipe.offerings.size());
            for (CountedIngredient offering : recipe.offerings) {
                offering.toNetwork(buffer);
            }
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, recipe.result);
            buffer.writeVarInt(recipe.duration);
            buffer.writeVarInt(recipe.requiredTier);
        }
    }
}
