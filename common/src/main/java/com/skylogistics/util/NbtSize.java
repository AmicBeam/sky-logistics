package com.skylogistics.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

public final class NbtSize {
    private NbtSize() {
    }

    public static int serializedBytes(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return 0;
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(buffer));
            return buffer.size();
        } catch (IOException exception) {
            return Integer.MAX_VALUE;
        }
    }
}
