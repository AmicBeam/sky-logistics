package com.skylogistics.storage;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class FluidStackKey {
    private final Fluid fluid;
    private final CompoundTag tag;

    private FluidStackKey(Fluid fluid, CompoundTag tag) {
        this.fluid = fluid;
        this.tag = tag == null ? null : tag.copy();
    }

    public static FluidStackKey of(FluidStack stack) {
        return new FluidStackKey(stack.getFluid(), stack.hasTag() ? stack.getTag() : null);
    }

    public Fluid fluid() {
        return fluid;
    }

    public FluidStack toStack(int amount) {
        FluidStack stack = new FluidStack(fluid, amount);
        if (tag != null) {
            stack.setTag(tag.copy());
        }
        return stack;
    }

    public CompoundTag save() {
        CompoundTag data = new CompoundTag();
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
        data.putString("Fluid", id == null ? "minecraft:empty" : id.toString());
        if (tag != null) {
            data.put("Tag", tag.copy());
        }
        return data;
    }

    public static FluidStackKey load(CompoundTag data) {
        ResourceLocation id = ResourceLocation.tryParse(data.getString("Fluid"));
        Fluid fluid = id == null ? Fluids.EMPTY : ForgeRegistries.FLUIDS.getValue(id);
        if (fluid == null) {
            fluid = Fluids.EMPTY;
        }
        CompoundTag tag = data.contains("Tag") ? data.getCompound("Tag") : null;
        return new FluidStackKey(fluid, tag);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof FluidStackKey other)) {
            return false;
        }
        return fluid == other.fluid && Objects.equals(tag, other.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fluid, tag);
    }
}
