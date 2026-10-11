package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryFilter;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ResourceRepositoryImpl.class, remap = false)
public interface RepositoryFilterAccessor {
    @Accessor("filter")
    ResourceRepositoryFilter<?> rs2multifilter$getFilter();
}
