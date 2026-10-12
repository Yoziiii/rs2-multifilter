package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterCard;
import dev.rs2multifilter.core.FilterCategory;
import dev.rs2multifilter.core.FilterRule;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryFilter;
import com.refinedmods.refinedstorage.common.api.grid.view.GridResource;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.view.ItemGridResource;
import com.refinedmods.refinedstorage.common.grid.view.FluidGridResource;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import dev.rs2multifilter.minecraft.mixin.RepositoryFilterAccessor;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryImpl;

public final class GridFilterSession {
    private static final WeakHashMap<AbstractGridContainerMenu, GridFilterSession> SESSIONS = new WeakHashMap<>();
    private final WeakReference<AbstractGridContainerMenu> menuRef;
    private FilterCard card;
    private ResourceRepositoryFilter<GridResource> searchFilter;
    private int selected = -1;

    private GridFilterSession(AbstractGridContainerMenu menu, FilterCard card) {
        this.menuRef = new WeakReference<>(menu);
        this.card = card;
    }
    public static GridFilterSession attach(AbstractGridContainerMenu menu, FilterCard card) {
        var session = new GridFilterSession(menu, card);
        if (menu.getRepository() instanceof ResourceRepositoryImpl<?> impl) {
            @SuppressWarnings("unchecked")
            ResourceRepositoryFilter<GridResource> active =
                (ResourceRepositoryFilter<GridResource>) ((RepositoryFilterAccessor) impl).rs2multifilter$getFilter();
            session.searchFilter = active;
        }
        SESSIONS.put(menu, session);
        return session;
    }
    public static ResourceRepositoryFilter<GridResource> onNativeFilter(AbstractGridContainerMenu menu,
        ResourceRepositoryFilter<GridResource> nativeFilter) {
        GridFilterSession session = SESSIONS.get(menu);
        if (session == null) return nativeFilter;
        session.searchFilter = nativeFilter;
        return session.wrap(nativeFilter);
    }
    public static void detach(AbstractGridContainerMenu menu) {
        SESSIONS.remove(menu);
    }

    public int selected() { return selected; }
    public FilterCard card() { return card; }

    public void updateCard(FilterCard replacement) {
        if (card.equals(replacement)) return;
        card = replacement;
        if (selected >= card.categories().size()) selected = -1;
        reapply();
    }

    private void reapply() {
        AbstractGridContainerMenu menu = menuRef.get();
        if (menu == null) return;
        if (searchFilter == null) searchFilter = (r, item) -> true;
        menu.getRepository().setFilterAndSort(wrap(searchFilter));
    }


    public void select(int index) {
        if (index >= card.categories().size() || index < -1) throw new IndexOutOfBoundsException(index);
        this.selected = index;
        AbstractGridContainerMenu menu = menuRef.get();
        if (menu == null) return;
        if (searchFilter == null) searchFilter = (r, item) -> true;
        menu.getRepository().setFilterAndSort(wrap(searchFilter));
    }

    private ResourceRepositoryFilter<GridResource> wrap(ResourceRepositoryFilter<GridResource> nativeFilter) {
        if (selected < 0) return nativeFilter;
        FilterCategory category = card.categories().get(selected);
        if (category.rules().isEmpty()) return (repo, resource) -> false;
        return (repo, resource) -> nativeFilter.test(repo, resource) && matches(category, resource);
    }

    private static boolean matches(FilterCategory category, GridResource resource) {
        boolean isItem = resource instanceof ItemGridResource;
        boolean isFluid = resource instanceof FluidGridResource;
        if (!isItem && !isFluid) return false;

        ItemStack stack = isItem ? ((ItemGridResource)resource).getItemStack() : ItemStack.EMPTY;
        net.minecraft.world.level.material.Fluid fluid = isFluid
            ? ((FluidResource)resource.getResourceForRecipeMods()).fluid() : null;
        ResourceLocation id = isItem
            ? BuiltInRegistries.ITEM.getKey(stack.getItem())
            : BuiltInRegistries.FLUID.getKey(fluid);

        if (category.rules().isEmpty()) return false;
        boolean includes = false;
        boolean any = false;
        boolean all = true;
        for (FilterRule rule : category.rules()) {
            boolean matched = switch (rule.kind()) {
                case MOD -> id.getNamespace().equals(rule.value());
                case ITEM -> isItem && id.toString().equals(rule.value());
                case TAG -> isItem && stack.is(TagKey.create(Registries.ITEM,
                    ResourceLocation.parse(rule.value())));
                case FLUID -> isFluid && id.toString().equals(rule.value());
                case FLUID_TAG -> isFluid && fluid.defaultFluidState().is(TagKey.create(Registries.FLUID,
                    ResourceLocation.parse(rule.value())));
                case FOOD -> isItem && stack.has(net.minecraft.core.component.DataComponents.FOOD);
                case DAMAGED -> isItem && stack.isDamaged();
                case DAMAGEABLE -> isItem && stack.isDamageableItem();
                case ENCHANTED -> isItem && stack.isEnchanted();
                case CREATE_ATTRIBUTE -> isItem && CreateAttributeBridge.matches(rule.value(), stack);
            };
            if (rule.exclude()) {
                if (matched) return false;
            } else {
                includes = true;
                any |= matched;
                all &= matched;
            }
        }
        if (!includes) return true;
        return switch (category.mode()) {
            case ANY -> any;
            case ALL -> all;
            case DENY -> !any;
        };
    }
}
