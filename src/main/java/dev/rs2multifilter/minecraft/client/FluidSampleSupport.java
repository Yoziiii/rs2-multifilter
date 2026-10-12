package dev.rs2multifilter.minecraft.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.lang.reflect.Method;
import java.util.Optional;

/** Recognizes fluids from Forge/NeoForge fluid containers and vanilla buckets. */
public final class FluidSampleSupport {
    private FluidSampleSupport() {}

    public static Fluid findFluid(ItemStack item) {
        if (item.isEmpty()) return null;
        try {
            Class<?> util = Class.forName("net.neoforged.neoforge.fluids.FluidUtil");
            Method contained = util.getMethod("getFluidContained", ItemStack.class);
            Object found = contained.invoke(null, item);
            if (found instanceof Optional<?> optional && optional.isPresent()) {
                Object stack = optional.get();
                Object fluid = stack.getClass().getMethod("getFluid").invoke(stack);
                if (fluid instanceof Fluid result) return result;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Vanilla bucket fallback below.
        }

        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(item.getItem());
        String path = itemKey.getPath();
        if (!path.endsWith("_bucket")) return null;
        ResourceLocation possible = ResourceLocation.fromNamespaceAndPath(
            itemKey.getNamespace(), path.substring(0, path.length() - 7));
        if (!BuiltInRegistries.FLUID.containsKey(possible)) return null;
        Fluid result = BuiltInRegistries.FLUID.get(possible);
        return result == net.minecraft.world.level.material.Fluids.EMPTY ? null : result;
    }
}
