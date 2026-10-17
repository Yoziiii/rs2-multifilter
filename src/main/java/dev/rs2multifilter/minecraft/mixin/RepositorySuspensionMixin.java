package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepository;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryImpl;
import dev.rs2multifilter.core.SuspendedFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No client linkage. Only a filter explicitly implementing our suspension marker can trigger it. */
@Mixin(value=ResourceRepositoryImpl.class,remap=false)
public abstract class RepositorySuspensionMixin {
    @Inject(method="sort",at=@At(value="INVOKE",target="Lcom/refinedmods/refinedstorage/api/resource/repository/ResourceRepositoryImpl;notifyListener()V"))
    private void rs2mf$guardSortedView(CallbackInfo ci) {
        rs2mf$guardView();
    }
    @Inject(method="update",at=@At("RETURN"))
    private void rs2mf$guardIncrementalView(CallbackInfo ci) {
        rs2mf$guardView();
    }
    @Unique private void rs2mf$guardView() {
        var repository=(ResourceRepository<?>)(Object)this;
        var filter=((RepositoryFilterAccessor)this).rs2multifilter$getFilter();
        // Re-sort only on a detected fault. The suspended predicate rejects everything, so
        // this replaces both RS's cached view and index; it never clears the backing inventory.
        if(filter instanceof SuspendedFilter state && state.suspended()
                && !repository.getViewList().isEmpty()) repository.sort();
    }
}
