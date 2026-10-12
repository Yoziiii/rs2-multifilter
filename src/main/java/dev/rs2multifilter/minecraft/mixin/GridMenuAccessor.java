package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.common.api.grid.Grid;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractGridContainerMenu.class, remap = false)
public interface GridMenuAccessor {
    @Accessor("grid")
    Grid rs2mf$getGrid();
}
