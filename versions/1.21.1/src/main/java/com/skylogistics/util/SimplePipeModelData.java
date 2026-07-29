package com.skylogistics.util;

import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class SimplePipeModelData {
    public static final ModelProperty<Integer> EXTRACT_SIDES = new ModelProperty<>(mask -> (mask & ~0x3F) == 0);

    private SimplePipeModelData() {
    }
}
