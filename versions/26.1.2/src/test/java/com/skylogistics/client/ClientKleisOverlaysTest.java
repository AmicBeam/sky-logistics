package com.skylogistics.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClientKleisOverlaysTest
{
    @Test
    void lineCompletesThe26VertexFormat()
    {
        try (var bytes = new ByteBufferBuilder(256))
        {
            var vertices = new BufferBuilder(bytes, VertexFormat.Mode.LINES,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
            ClientKleisOverlays.line(vertices, new PoseStack().last(),
                    0.0D, 0.0D, 0.0D, 1.0D, 0.0D, 0.0D,
                    1.0F, 1.0F, 1.0F, 1.0F);

            try (var mesh = vertices.buildOrThrow())
            {
                assertEquals(4, mesh.drawState().vertexCount());
            }
        }
    }
}
