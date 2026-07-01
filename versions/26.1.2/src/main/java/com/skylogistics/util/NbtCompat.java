package com.skylogistics.util;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

public final class NbtCompat {
    private NbtCompat() {
    }

    public static void putUuid(CompoundTag tag, String key, UUID value) {
        if (value != null) {
            tag.putString(key, value.toString());
        }
    }

    public static boolean hasUuid(CompoundTag tag, String key) {
        return getUuidOptional(tag, key).isPresent();
    }

    public static UUID getUuid(CompoundTag tag, String key) {
        return getUuidOptional(tag, key).orElse(null);
    }

    public static Optional<UUID> getUuidOptional(CompoundTag tag, String key) {
        if (tag == null || key == null) {
            return Optional.empty();
        }
        Optional<String> stringValue = tag.getString(key);
        if (stringValue.isPresent()) {
            try {
                return Optional.of(UUID.fromString(stringValue.get()));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        Optional<int[]> intArray = tag.getIntArray(key);
        if (intArray.isPresent() && intArray.get().length == 4) {
            return Optional.of(UUIDUtil.uuidFromIntArray(intArray.get()));
        }
        return Optional.empty();
    }
}
