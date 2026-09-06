package com.skylogistics.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RecipeResourceFormatTest
{
    @Test
    void dominionWandUses26IngredientSyntax() throws Exception
    {
        JsonObject recipe = read("kleis_dominion_wand");
        assertTrue(recipe.getAsJsonObject("main").get("ingredient").isJsonPrimitive());
        recipe.getAsJsonArray("offerings").forEach(offering ->
                assertTrue(offering.getAsJsonObject().get("ingredient").isJsonPrimitive()));
    }

    @Test
    void necklaceUsesRenamedIronChain() throws Exception
    {
        JsonObject recipe = read("sky_necklace");
        assertEquals("minecraft:iron_chain", recipe.getAsJsonArray("offerings").get(0)
                .getAsJsonObject().get("ingredient").getAsString());
    }

    private static JsonObject read(String name) throws Exception
    {
        try (var stream = RecipeResourceFormatTest.class.getResourceAsStream(
                "/data/skylogistics/recipe/" + name + ".json"))
        {
            if (stream == null) throw new IllegalStateException("Missing recipe " + name);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
