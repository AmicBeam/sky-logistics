package com.skylogistics.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World-local permissions keyed by owner UUID, shared by all of that owner's lines. */
public final class PlayerLinePermissions {
    private final Set<UUID> publicOwners = new HashSet<>();
    private final Map<UUID, Set<UUID>> grants = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    public boolean allows(UUID owner, UUID actor) {
        return owner != null && actor != null && (owner.equals(actor) || isPublic(owner)
                || granted(owner, actor));
    }
    public boolean isPublic(UUID owner) { return publicOwners.contains(owner); }
    public boolean granted(UUID owner, UUID actor) { return grants.getOrDefault(owner, Set.of()).contains(actor); }
    public Set<UUID> grantedPlayers(UUID owner) { return Set.copyOf(grants.getOrDefault(owner, Set.of())); }
    public Map<UUID, String> knownPlayers() { return Map.copyOf(names); }
    public String name(UUID player) { return names.getOrDefault(player, player.toString()); }
    public boolean remember(UUID player, String name) { return !name.equals(names.put(player, name)); }
    public void setPublic(UUID owner, boolean value) {
        if (value) publicOwners.add(owner); else publicOwners.remove(owner);
    }
    public void setGranted(UUID owner, UUID player, boolean value) {
        if (owner.equals(player)) return;
        if (value) grants.computeIfAbsent(owner, ignored -> new HashSet<>()).add(player);
        else if (grants.containsKey(owner)) grants.get(owner).remove(player);
    }
    public String save() {
        JsonObject root = new JsonObject();
        JsonArray publics = new JsonArray();
        publicOwners.forEach(id -> publics.add(id.toString()));
        root.add("public", publics);
        JsonObject allowed = new JsonObject();
        grants.forEach((owner, players) -> {
            JsonArray ids = new JsonArray();
            players.forEach(id -> ids.add(id.toString()));
            allowed.add(owner.toString(), ids);
        });
        root.add("grants", allowed);
        JsonObject profiles = new JsonObject();
        names.forEach((id, name) -> profiles.addProperty(id.toString(), name));
        root.add("names", profiles);
        return root.toString();
    }
    public static PlayerLinePermissions load(String json) {
        PlayerLinePermissions result = new PlayerLinePermissions();
        if (json == null || json.isBlank()) return result;
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("public")) root.getAsJsonArray("public").forEach(id -> result.publicOwners.add(UUID.fromString(id.getAsString())));
        if (root.has("grants")) root.getAsJsonObject("grants").entrySet().forEach(entry -> {
            UUID owner = UUID.fromString(entry.getKey());
            entry.getValue().getAsJsonArray().forEach(id -> result.setGranted(owner, UUID.fromString(id.getAsString()), true));
        });
        if (root.has("names")) root.getAsJsonObject("names").entrySet().forEach(entry ->
                result.names.put(UUID.fromString(entry.getKey()), entry.getValue().getAsString()));
        return result;
    }
}
