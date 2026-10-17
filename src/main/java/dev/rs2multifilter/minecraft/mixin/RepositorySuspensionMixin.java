package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepository;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryImpl;
import dev.rs2multifilter.minecraft.client.GridFilterSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-only: prevent results accepted before an attribute fault from remaining visible. */
@Mixin(value=ResourceRepositoryImpl.class,remap=false)
public abstract class RepositorySuspensionMixin {
    @Inject(method="sort",at=@At(value="INVOKE",target="Lcom/refinedmods/refinedstorage/api/resource/repository/ResourceRepositoryImpl;notifyListener()V"))
    private void rs2mf$guardSortedView(CallbackInfo ci) {
        GridFilterSession.guardRepository((ResourceRepository<?>)(Object)this);
    }
    @Inject(method="update",at=@At("RETURN"))
    private void rs2mf$guardIncrementalView(CallbackInfo ci) {
        GridFilterSession.guardRepository((ResourceRepository<?>)(Object)this);
    }
}
