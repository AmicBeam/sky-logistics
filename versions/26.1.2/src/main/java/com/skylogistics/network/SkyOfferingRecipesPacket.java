package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.client.ClientOfferingRecipes;
import com.skylogistics.recipe.OfferingRecipe;
import com.skylogistics.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SkyOfferingRecipesPacket(List<OfferingRecipe> recipes) implements CustomPacketPayload {
    public static final Type<SkyOfferingRecipesPacket> TYPE = new Type<>(SkyLogistics.id("sky_offering_recipes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkyOfferingRecipesPacket> STREAM_CODEC =
            StreamCodec.ofMember(SkyOfferingRecipesPacket::encode, SkyOfferingRecipesPacket::decode);
    private static final int MAX_RECIPES = 512;

    public static void encode(SkyOfferingRecipesPacket packet, RegistryFriendlyByteBuf buffer) {
        int size = Math.min(packet.recipes.size(), MAX_RECIPES);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            OfferingRecipe.streamCodec().encode(buffer, packet.recipes.get(i));
        }
    }

    public static SkyOfferingRecipesPacket decode(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<OfferingRecipe> recipes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            recipes.add(OfferingRecipe.streamCodec().decode(buffer));
        }
        return new SkyOfferingRecipesPacket(recipes);
    }

    public static void handle(SkyOfferingRecipesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientOfferingRecipes.apply(packet.recipes()));
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        List<OfferingRecipe> recipes = skyOfferingRecipes(event.getPlayerList().getServer().getRecipeManager());
        event.getRelevantPlayers().forEach(player -> ModNetworking.sendToPlayer(player,
                new SkyOfferingRecipesPacket(recipes)));
    }

    private static List<OfferingRecipe> skyOfferingRecipes(RecipeManager recipeManager) {
        return recipeManager.getRecipes().stream()
                .map(holder -> holder.value())
                .filter(recipe -> recipe.getType() == ModRecipes.SKY_OFFERING_TYPE.get())
                .map(OfferingRecipe.class::cast)
                .toList();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
