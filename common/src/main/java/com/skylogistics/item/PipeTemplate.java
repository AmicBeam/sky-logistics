package com.skylogistics.item;

import com.skylogistics.util.SimplePipeConnection;
import com.skylogistics.util.SimplePipeType;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public record PipeTemplate(SimplePipeType type, UUID owner, Map<Direction, Face> faces) {
    public PipeTemplate {
        faces = Map.copyOf(faces);
    }

    public Face face(Direction direction) {
        return faces.getOrDefault(direction, Face.EMPTY);
    }

    public PipeTemplate withFace(Direction direction, Face face) {
        EnumMap<Direction, Face> updated = new EnumMap<>(Direction.class);
        updated.putAll(faces);
        updated.put(direction, face);
        return new PipeTemplate(type, owner, updated);
    }

    public record Face(SimplePipeConnection connection, boolean disconnected, ItemStack filter) {
        public static final Face EMPTY = new Face(SimplePipeConnection.NONE, false, ItemStack.EMPTY);

        public Face {
            filter = filter.copyWithCount(1);
        }
    }
}
