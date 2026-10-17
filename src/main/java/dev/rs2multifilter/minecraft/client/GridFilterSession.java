package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterCard;
import dev.rs2multifilter.core.FilterCategory;
import dev.rs2multifilter.core.FilterRule;
import dev.rs2multifilter.core.SuspendedFilter;
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
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepository;
import dev.rs2multifilter.minecraft.mixin.RepositoryFilterAccessor;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryImpl;

public final class GridFilterSession {
    private static final Logger LOGGER = Logger.getLogger(GridFilterSession.class.getName());
    private static final long RETRY_NANOS = 2_000_000_000L;
    private static final WeakHashMap<AbstractGridContainerMenu, GridFilterSession> SESSIONS = new WeakHashMap<>();
    private final WeakReference<AbstractGridContainerMenu> menuRef;
    private FilterCard card;
    private ResourceRepositoryFilter<GridResource> searchFilter;
    private ResourceRepositoryFilter<GridResource> lastWrapped;
    private int selected = -1;
    private record Suspension(FilterRule rule, String reason, Map<FilterRule,ItemStack> failedSamples) {}
    private final Map<ClientCardState.Source, Suspension> suspended = new HashMap<>();
    private final Map<ClientCardState.Source, String> reported = new HashMap<>();
    private long nextRetryNanos;
    private ClientCardState.Source currentOwner;
    private int sourceRevision;

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

    /**
     * RS can replace the repository filter when its native controls change.
     * Reapply our category layer only when that actually happens, preserving
     * the latest native search/view/sorting predicate.
     */
    public void keepNativeControlsWorking() {
        if (selected < 0) return;
        if (isSuspended() && System.nanoTime() >= nextRetryNanos) reapply();
        reportRecovery();
        AbstractGridContainerMenu menu = menuRef.get();
        if (menu == null || !(menu.getRepository() instanceof ResourceRepositoryImpl<?> repository)) return;
        if(sourceRevision!=ClientCardState.revision(menu.containerId)) reapply();
        @SuppressWarnings("unchecked")
        ResourceRepositoryFilter<GridResource> active =
            (ResourceRepositoryFilter<GridResource>)
                ((RepositoryFilterAccessor)repository).rs2multifilter$getFilter();
        if (active != null && active != lastWrapped) {
            searchFilter = active;
            reapply();
        }
    }

    public int selected() { return selected; }
    public FilterCard card() { return card; }

    public void updateCard(FilterCard replacement) {
        if (card.equals(replacement)) return;
        card = replacement;
        suspended.clear();
        reported.clear();
        if (selected >= card.categories().size()) selected = -1;
        reapply();
    }

    private void reapply() {
        AbstractGridContainerMenu menu = menuRef.get();
        if (menu == null) return;
        if (searchFilter == null) searchFilter = (r, item) -> true;
        menu.getRepository().setFilterAndSort(wrap(searchFilter));
        reportRecovery();
    }


    public void select(int index) {
        if (index >= card.categories().size() || index < -1) throw new IndexOutOfBoundsException(index);
        this.selected = index;
        AbstractGridContainerMenu menu = menuRef.get();
        if (menu == null) return;
        if (searchFilter == null) searchFilter = (r, item) -> true;
        menu.getRepository().setFilterAndSort(wrap(searchFilter));
        reportRecovery();
    }

    private ClientCardState.Source resolveSource() {
        AbstractGridContainerMenu menu=menuRef.get();
        sourceRevision=menu==null ? 0 : ClientCardState.revision(menu.containerId);
        var physical=menu==null ? null : ClientCardState.source(menu.containerId,selected);
        return physical!=null && physical.card().categories().contains(card.categories().get(selected))
            ? physical : new ClientCardState.Source(-1,card);
    }
    public boolean isSuspended() { return selected>=0 && currentOwner!=null && suspended.containsKey(currentOwner); }
    public String suspensionReason() {
        return isSuspended() ? suspended.get(currentOwner).reason() : "";
    }
    private static AttributeResult availability(FilterRule rule) {
        return switch(rule.kind()) {
            case CREATE_ATTRIBUTE -> CreateAttributeBridge.availability(rule.value());
            case EXACT_ITEM -> ExactItemBridge.availability(rule.value());
            default -> AttributeResult.match(true);
        };
    }
    private static AttributeResult evaluateAttribute(FilterRule rule,ItemStack stack) {
        return rule.kind()==FilterRule.Kind.CREATE_ATTRIBUTE
            ? CreateAttributeBridge.evaluate(rule.value(),stack) : ExactItemBridge.evaluate(rule.value(),stack);
    }
    private void prepare(ClientCardState.Source owner) {
        var previous=suspended.get(owner);
        for(var category:owner.card().categories()) for(var rule:category.rules()) {
            var result=availability(rule);
            if(!result.available()) {
                suspend(owner,rule,result.diagnostic(),ItemStack.EMPTY);
                return;
            }
        }
        // A registered provider can still be broken. Recheck the actual failing sample before
        // unpausing sibling categories, rather than considering registration alone a recovery.
        if(previous!=null) {
            for(var failure:previous.failedSamples().entrySet()) {
                var result=evaluateAttribute(failure.getKey(),failure.getValue());
                if(!result.available()) {
                    suspend(owner,failure.getKey(),result.diagnostic(),failure.getValue());
                    return;
                }
            }
        }
        suspended.remove(owner);
    }
    private static String ownerName(ClientCardState.Source owner) {
        return owner.slot()<0 ? "当前过滤升级" : "第 "+(owner.slot()+1)+" 张过滤升级";
    }
    private void suspend(ClientCardState.Source owner,FilterRule rule,String reason,ItemStack sample) {
        var previous=suspended.get(owner);
        Map<FilterRule,ItemStack> samples=previous==null ? new HashMap<>() : previous.failedSamples();
        if(!sample.isEmpty()) samples.put(rule,sample.copyWithCount(1));
        suspended.put(owner,new Suspension(rule,reason,samples));
        nextRetryNanos=System.nanoTime()+RETRY_NANOS;
        String detail=rule.kind()+" / "+(rule.exclude()?"排除":"包含")+" / "+rule.value()+" / "+reason;
        if(detail.equals(reported.put(owner,detail))) return;
        LOGGER.warning(ownerName(owner)+"属性不可用；该卡所有分类已暂停，显示空结果；规则及库存未修改。"+detail);
        var client=Minecraft.getInstance();
        if(client!=null && client.player!=null) {
            String brief=reason.length()>160 ? reason.substring(0,160)+"…" : reason;
            client.player.displayClientMessage(Component.literal(ownerName(owner)+"属性不可用，已暂停该卡所有分类。"
                +brief+"；将自动重试，原始规则未修改。"),false);
        }
    }
    private void reportRecovery() {
        if(selected<0 || isSuspended()) return;
        var owner=currentOwner;
        if(owner==null) return;
        if(reported.remove(owner)==null) return;
        LOGGER.info(ownerName(owner)+"属性检查恢复，已重新执行原始过滤规则。");
        var client=Minecraft.getInstance();
        if(client!=null && client.player!=null)client.player.displayClientMessage(
            Component.literal(ownerName(owner)+"属性检查恢复，已重新执行原始规则。"),false);
    }

    private final class CardFilter implements ResourceRepositoryFilter<GridResource>, SuspendedFilter {
        private final ClientCardState.Source owner;
        private final FilterCategory category;
        private final ResourceRepositoryFilter<GridResource> nativeFilter;
        CardFilter(ClientCardState.Source owner,FilterCategory category,ResourceRepositoryFilter<GridResource> nativeFilter) {
            this.owner=owner;this.category=category;this.nativeFilter=nativeFilter;
        }
        @Override public boolean suspended() { return suspended.containsKey(owner); }
        @Override public boolean test(ResourceRepository<GridResource> repo,GridResource resource) {
            return !suspended() && nativeFilter.test(repo,resource) && matches(owner,category,resource);
        }
    }

    private ResourceRepositoryFilter<GridResource> wrap(ResourceRepositoryFilter<GridResource> nativeFilter) {
        if (selected < 0) {
            lastWrapped = nativeFilter;
            return nativeFilter;
        }
        FilterCategory category = card.categories().get(selected);
        var owner=resolveSource();
        currentOwner=owner;
        suspended.keySet().removeIf(old -> old.slot()==owner.slot() && !old.equals(owner));
        reported.keySet().removeIf(old -> old.slot()==owner.slot() && !old.equals(owner));
        prepare(owner);
        lastWrapped = category.rules().isEmpty()
            ? (repo, resource) -> false
            : new CardFilter(owner,category,nativeFilter);
        return lastWrapped;
    }

    private boolean matches(ClientCardState.Source owner,FilterCategory category, GridResource resource) {
        boolean isItem = resource instanceof ItemGridResource;
        boolean isFluid = resource instanceof FluidGridResource;
        if (!isItem && !isFluid) return false;

        ItemStack stack = isItem ? ((ItemGridResource)resource).getItemStack() : ItemStack.EMPTY;
        net.minecraft.world.level.material.Fluid fluid = isFluid
            ? ((FluidResource)resource.getResourceForRecipeMods()).fluid() : null;
        ResourceLocation id = isItem
            ? BuiltInRegistries.ITEM.getKey(stack.getItem())
            : BuiltInRegistries.FLUID.getKey(fluid);

        try {
            return category.matches(rule -> switch (rule.kind()) {
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
                case CREATE_ATTRIBUTE, EXACT_ITEM -> isItem && checkedAttribute(owner,rule,stack);
            });
        } catch (UnavailableRule ignored) {
            return false; // Abort the entire combination; never turn an excluded failure into "not matched".
        }
    }
    private static final class UnavailableRule extends RuntimeException {}
    private boolean checkedAttribute(ClientCardState.Source owner,FilterRule rule,ItemStack stack) {
        var result=evaluateAttribute(rule,stack);
        if(result.available())return result.matched();
        suspend(owner,rule,result.diagnostic(),stack);
        throw new UnavailableRule();
    }
}
