package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryFilter;
import com.refinedmods.refinedstorage.common.api.grid.view.GridResource;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import dev.rs2multifilter.minecraft.client.GridFilterSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = AbstractGridContainerMenu.class, remap = false)
public abstract class GridSearchMixin {
    @ModifyArg(
        method = "onSearchTextChanged",
        at = @At(value = "INVOKE", target = "Lcom/refinedmods/refinedstorage/api/resource/repository/ResourceRepository;setFilterAndSort(Lcom/refinedmods/refinedstorage/api/resource/repository/ResourceRepositoryFilter;)Lcom/refinedmods/refinedstorage/api/resource/repository/ResourceRepositoryFilter;"),
        index = 0
    )
    private ResourceRepositoryFilter<GridResource> appendFilter(ResourceRepositoryFilter<GridResource> original) {
        return GridFilterSession.onNativeFilter((AbstractGridContainerMenu) (Object) this, original);
    }
}
