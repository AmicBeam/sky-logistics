package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.client.ClientOfferingRecipes;
import com.skylogistics.recipe.OfferingRecipe;
import com.skylogistics.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SkyOfferingRecipesPacket(List<RecipeHolder<OfferingRecipe>> recipes) implements CustomPacketPayload {
    public static final Type<SkyOfferingRecipesPacket> TYPE = new Type<>(SkyLogistics.id("sky_offering_recipes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkyOfferingRecipesPacket> STREAM_CODEC =
            StreamCodec.ofMember(SkyOfferingRecipesPacket::encode, SkyOfferingRecipesPacket::decode);
    private static final int MAX_RECIPES = 512;

    public static void encode(SkyOfferingRecipesPacket packet, RegistryFriendlyByteBuf buffer) {
        int size = Math.min(packet.recipes.size(), MAX_RECIPES);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            RecipeHolder<OfferingRecipe> recipe = packet.recipes.get(i);
            buffer.writeResourceKey(recipe.id());
            OfferingRecipe.streamCodec().encode(buffer, recipe.value());
        }
    }

    public static SkyOfferingRecipesPacket decode(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_RECIPES) {
            throw new IllegalArgumentException("Invalid sky offering recipe packet size: " + size);
        }
        List<RecipeHolder<OfferingRecipe>> recipes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceKey<Recipe<?>> id = buffer.readResourceKey(Registries.RECIPE);
            recipes.add(new RecipeHolder<>(id, OfferingRecipe.streamCodec().decode(buffer)));
        }
        return new SkyOfferingRecipesPacket(recipes);
    }

    public static void handle(SkyOfferingRecipesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientOfferingRecipes.apply(packet.recipes()));
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(ModRecipes.SKY_OFFERING_TYPE.get());
        List<RecipeHolder<OfferingRecipe>> recipes = skyOfferingRecipes(
                event.getPlayerList().getServer().getRecipeManager());
        event.getRelevantPlayers().forEach(player -> sendToPlayer(player, recipes));
    }

    public static void sendToPlayer(ServerPlayer player) {
        if (player.level().getServer() == null) {
            return;
        }
        sendToPlayer(player, skyOfferingRecipes(player.level().getServer().getRecipeManager()));
    }

    private static void sendToPlayer(ServerPlayer player, List<RecipeHolder<OfferingRecipe>> recipes) {
        ModNetworking.sendToPlayer(player, new SkyOfferingRecipesPacket(recipes));
    }

    private static List<RecipeHolder<OfferingRecipe>> skyOfferingRecipes(RecipeManager recipeManager) {
        return List.copyOf(recipeManager.recipeMap().byType(ModRecipes.SKY_OFFERING_TYPE.get()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
