package com.skylogistics.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void packMetadataUses26FormatRange() throws Exception
    {
        JsonObject pack = readResource("/pack.mcmeta").getAsJsonObject("pack");
        assertFalse(pack.has("pack_format"));
        assertFalse(pack.has("supported_formats"));
        assertEquals(84, pack.get("min_format").getAsInt());
        assertEquals(101, pack.getAsJsonArray("max_format").get(0).getAsInt());
        assertEquals(1, pack.getAsJsonArray("max_format").get(1).getAsInt());
    }

    private static JsonObject read(String name) throws Exception
    {
        return readResource("/data/skylogistics/recipe/" + name + ".json");
    }

    private static JsonObject readResource(String path) throws Exception
    {
        try (var stream = RecipeResourceFormatTest.class.getResourceAsStream(path))
        {
            if (stream == null) throw new IllegalStateException("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
