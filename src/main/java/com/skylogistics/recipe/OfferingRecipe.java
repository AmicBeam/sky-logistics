package com.skylogistics.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skylogistics.item.EulogiaCrystalItem;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

public class OfferingRecipe implements Recipe<SimpleContainer> {
    public static final int MAX_OFFERINGS = 4;

    private final ResourceLocation id;
    private final CountedIngredient main;
    private final NonNullList<CountedIngredient> offerings;
    private final ItemStack result;
    private final int duration;
    private final int requiredTier;

    public OfferingRecipe(ResourceLocation id, CountedIngredient main, NonNullList<CountedIngredient> offerings,
            ItemStack result, int duration, int requiredTier) {
        this.id = id;
        this.main = main;
        this.offerings = offerings;
        this.result = result;
        this.duration = duration;
        this.requiredTier = requiredTier;
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
    public boolean matches(SimpleContainer container, Level level) {
        if (container.getContainerSize() < 1) {
            return false;
        }
        List<ItemStack> offeringStacks = new ArrayList<>();
        for (int i = 1; i < container.getContainerSize(); i++) {
            offeringStacks.add(container.getItem(i));
        }
        return matches(container.getItem(0), offeringStacks);
    }

    @Override
    public ItemStack assemble(SimpleContainer container, RegistryAccess access) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return result.copy();
    }

    public ItemStack result() {
        return result.copy();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SKY_OFFERING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.SKY_OFFERING_TYPE.get();
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ModItems.OFFERING_ALTAR.get());
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public record CountedIngredient(Ingredient ingredient, int count, boolean requireChargedCrystal) {
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.getCount() >= count && ingredient.test(stack)
                    && (!requireChargedCrystal || EulogiaCrystalItem.isCharged(stack));
        }

        private static CountedIngredient fromJson(JsonElement element) {
            JsonObject object = GsonHelper.convertToJsonObject(element, "ingredient");
            int count = Math.max(1, GsonHelper.getAsInt(object, "count", 1));
            boolean requireChargedCrystal = GsonHelper.getAsBoolean(object, "charged", false);
            return new CountedIngredient(Ingredient.fromJson(object), count, requireChargedCrystal);
        }

        private void toNetwork(FriendlyByteBuf buffer) {
            ingredient.toNetwork(buffer);
            buffer.writeVarInt(count);
            buffer.writeBoolean(requireChargedCrystal);
        }

        private static CountedIngredient fromNetwork(FriendlyByteBuf buffer) {
            return new CountedIngredient(Ingredient.fromNetwork(buffer), buffer.readVarInt(), buffer.readBoolean());
        }
    }

    public static class Serializer implements RecipeSerializer<OfferingRecipe> {
        @Override
        public OfferingRecipe fromJson(ResourceLocation id, JsonObject json) {
            CountedIngredient main = CountedIngredient.fromJson(GsonHelper.getAsJsonObject(json, "main"));
            NonNullList<CountedIngredient> offerings = NonNullList.create();
            JsonArray offeringArray = GsonHelper.getAsJsonArray(json, "offerings", new JsonArray());
            if (offeringArray.size() > MAX_OFFERINGS) {
                throw new IllegalArgumentException("Sky offering recipes support at most " + MAX_OFFERINGS + " offerings");
            }
            for (JsonElement element : offeringArray) {
                offerings.add(CountedIngredient.fromJson(element));
            }
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            int duration = Math.max(1, GsonHelper.getAsInt(json, "duration", 200));
            int requiredTier = Math.max(1, json.has("altar_tier")
                    ? GsonHelper.getAsInt(json, "altar_tier")
                    : GsonHelper.getAsInt(json, "tier", 1));
            return new OfferingRecipe(id, main, offerings, result, duration, requiredTier);
        }

        @Override
        public OfferingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            CountedIngredient main = CountedIngredient.fromNetwork(buffer);
            NonNullList<CountedIngredient> offerings = NonNullList.create();
            int offeringCount = buffer.readVarInt();
            for (int i = 0; i < offeringCount; i++) {
                offerings.add(CountedIngredient.fromNetwork(buffer));
            }
            ItemStack result = buffer.readItem();
            int duration = buffer.readVarInt();
            int requiredTier = buffer.readVarInt();
            return new OfferingRecipe(id, main, offerings, result, duration, requiredTier);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, OfferingRecipe recipe) {
            recipe.main.toNetwork(buffer);
            buffer.writeVarInt(recipe.offerings.size());
            for (CountedIngredient offering : recipe.offerings) {
                offering.toNetwork(buffer);
            }
            buffer.writeItem(recipe.result);
            buffer.writeVarInt(recipe.duration);
            buffer.writeVarInt(recipe.requiredTier);
        }
    }
}
