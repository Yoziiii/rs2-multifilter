package dev.rs2multifilter.minecraft.client;

import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.refinedmods.refinedstorage.common.grid.GridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.GridData;
import com.refinedmods.refinedstorage.common.grid.view.ItemGridResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import dev.rs2multifilter.core.*;
import dev.rs2multifilter.minecraft.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real RS repository and native filter adapter; Create registry/API is an explicit test fixture. */
class UnavailableAttributeTest {
    private MockedStatic<Minecraft> access;
    private GridContainerMenu menu;
    private GridFilterSession session;
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @BeforeEach void setup() throws Exception {
        ItemAttribute.Fixture.reset(); ClientCardState.clearAll();
        for(Class<?> type:List.of(CreateAttributeBridge.class,ExactItemBridge.class)) {
            var f=type.getDeclaredField("CACHE");f.setAccessible(true);((Map<?,?>)f.get(null)).clear();
        }
        var client=mock(Minecraft.class);var level=mock(ClientLevel.class);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
        client.level=level;
        access=mockStatic(Minecraft.class);access.when(Minecraft::getInstance).thenReturn(client);
        var player=mock(Player.class);var inventory=new Inventory(player);
        when(player.getInventory()).thenReturn(inventory);when(player.level()).thenReturn(level);
        menu=new GridContainerMenu(23,inventory,new GridData(false,List.of(),Set.of()));
        menu.getRepository().update(new ItemResource(Items.STONE),64);
        menu.getRepository().update(new ItemResource(Items.APPLE),64);
    }
    @AfterEach void cleanup(){if(menu!=null)GridFilterSession.detach(menu);ClientCardState.clearAll();ItemAttribute.Fixture.reset();if(access!=null)access.close();}
    static String encoded(String id){return "create:"+HexFormat.of().formatHex(("{attribute:{type:\""+id+"\"}}").getBytes(StandardCharsets.UTF_8));}
    static FilterRule attribute(String id,boolean exclude){return new FilterRule(FilterRule.Kind.CREATE_ATTRIBUTE,encoded(id),exclude);}
    static FilterRule stone(){return new FilterRule(FilterRule.Kind.ITEM,"minecraft:stone",false);}
    static FilterCategory category(String name,FilterCategory.Mode mode,FilterRule...rules){return new FilterCategory(name,"minecraft:stone",List.of(rules),mode);}
    void use(FilterCategory...categories){session=GridFilterSession.attach(menu,new FilterCard(FilterCard.VERSION,List.of(categories)));}
    Set<Item> visible(){return menu.getRepository().getViewList().stream().map(r->((ItemGridResource)r).getItemStack().getItem()).collect(java.util.stream.Collectors.toSet());}
    void register(String id,java.util.function.BiPredicate<ItemStack,net.minecraft.world.level.Level> predicate){ItemAttribute.Fixture.registered.put(id,new ItemAttribute.TestAttribute(predicate));}

    @Test void healthyCreatePredicateStillDistinguishesMatchAndNoMatch(){
        register("test:stone",(s,l)->s.is(Items.STONE));
        use(category("正常",FilterCategory.Mode.ANY,attribute("test:stone",false)));session.select(0);
        assertEquals(Set.of(Items.STONE),visible());
        assertTrue(CreateAttributeBridge.matches(encoded("test:stone"),new ItemStack(Items.STONE)));
        assertFalse(CreateAttributeBridge.matches(encoded("test:stone"),new ItemStack(Items.APPLE)));
    }
    @Test void unavailableOrdinaryOrRuleMustNotLetAnotherIncludeSilentlyPass(){
        use(category("缺失",FilterCategory.Mode.ANY,stone(),attribute("missing:type",false)));session.select(0);
        assertTrue(visible().isEmpty());
    }
    @Test void unavailableExcludeMustNotTurnIntoPermissionForAllItems(){
        for(var mode:FilterCategory.Mode.values()){
            use(category("排除",mode,attribute("missing:type",true)));session.select(0);
            assertTrue(visible().isEmpty(),mode.toString());
        }
    }
    @Test void unavailableAttributeSuspendsAnyAllAndDenyWithoutChangingTheirHealthySemantics(){
        for(var mode:FilterCategory.Mode.values()){
            use(category("模式",mode,stone(),attribute("missing:type",false)));session.select(0);
            assertTrue(visible().isEmpty(),mode.toString());
        }
    }
    @Test void aMissingDependencyDuringAttributeLoadingMustSuspendTheWholeCard(){
        ItemAttribute.Fixture.dependencyFailure=new NoClassDefFoundError("addon/RemovedDependency");
        use(category("依赖",FilterCategory.Mode.ANY,stone(),attribute("test:dependency",false)));session.select(0);
        assertTrue(visible().isEmpty());
    }
    @Test void runtimeExceptionInExcludedPredicateMustNotBecomeNonMatch(){
        register("test:throws",(s,l)->{throw new IllegalStateException("provider failed");});
        use(category("异常",FilterCategory.Mode.DENY,attribute("test:throws",true)));session.select(0);
        assertTrue(visible().isEmpty());
    }
    @Test void executionFailureInvalidatesEarlierAcceptedResultsBeforeSortReturns(){
        var calls=new AtomicInteger();register("test:late",(s,l)->{if(calls.incrementAndGet()>1)throw new IllegalStateException("late failure");return true;});
        use(category("中途失败",FilterCategory.Mode.ANY,attribute("test:late",false)));session.select(0);
        assertTrue(visible().isEmpty());
        assertEquals(64,menu.getRepository().getAmount(new ItemResource(Items.STONE)));
        assertEquals(64,menu.getRepository().getAmount(new ItemResource(Items.APPLE)));
    }
    @Test void incrementalResourceFailureAlsoClearsPreviouslyCachedResults(){
        register("test:incremental",(s,l)->{if(s.is(Items.DIRT))throw new IllegalStateException("new item failed");return true;});
        use(category("增量",FilterCategory.Mode.ANY,attribute("test:incremental",false)));session.select(0);
        assertEquals(2,visible().size());menu.getRepository().update(new ItemResource(Items.DIRT),1);
        assertTrue(visible().isEmpty());assertEquals(1,menu.getRepository().getAmount(new ItemResource(Items.DIRT)));
    }
    @Test void anUnavailableRuleInASiblingCategoryPausesTheCardInsteadOfOnlyOneCategory(){
        use(category("正常分类",FilterCategory.Mode.ANY,stone()),category("坏分类",FilterCategory.Mode.ALL,attribute("missing:type",false)));
        session.select(0);assertTrue(visible().isEmpty());session.select(1);assertTrue(visible().isEmpty());
    }
    @Test void fourPhysicalCardsKeepTheirOwnFailureBoundaries(){
        var first=new FilterCard(FilterCard.VERSION,List.of(category("第一卡正常分类",FilterCategory.Mode.ANY,stone()),category("第一卡坏分类",FilterCategory.Mode.ANY,attribute("missing:type",true))));
        var healthy=new FilterCard(FilterCard.VERSION,List.of(category("健康",FilterCategory.Mode.ANY,stone())));
        for(int slot=0;slot<4;slot++)ClientCardState.update(menu.containerId,slot,CardWireCodec.encode(slot==0?first:healthy));
        var original=ClientCardState.card(menu.containerId);session=GridFilterSession.attach(menu,original);
        session.select(0);assertTrue(visible().isEmpty());session.select(1);assertTrue(visible().isEmpty());
        for(int i=2;i<5;i++){session.select(i);assertEquals(Set.of(Items.STONE),visible());}
        assertEquals(original,ClientCardState.card(menu.containerId));
    }
    @Test void recoveringTheProviderReusesTheOriginalRuleAndRestoresNativeSearch(){
        var rule=attribute("test:recover",true);use(category("恢复",FilterCategory.Mode.ANY,rule));session.select(0);
        register("test:recover",(s,l)->s.is(Items.APPLE));
        session.select(0);
        menu.getRepository().setFilterAndSort(GridFilterSession.onNativeFilter(menu,(r,item)->((ItemGridResource)item).getItemStack().is(Items.STONE)));
        assertEquals(Set.of(Items.STONE),visible());assertEquals(rule,session.card().categories().getFirst().rules().getFirst());
    }
    @Test void allViewIsNeverSelectedAutomaticallyAndRemainsAnExplicitUserChoice(){
        use(category("暂停",FilterCategory.Mode.ANY,attribute("missing:type",true)));session.select(0);
        assertEquals(0,session.selected());assertTrue(visible().isEmpty());session.select(-1);
        assertEquals(Set.of(Items.STONE,Items.APPLE),visible());
    }
    @Test void corruptAndUnknownStoredCardsStayUnreadableAndCannotBeOverwritten(){
        for(String raw:List.of("v99:unknown","v2:%%%")){
            var item=new ItemStack(MultiFilterMod.FILTER_CARD.get());item.set(MultiFilterMod.CARD_DATA.get(),raw);
            assertFalse(CardComponents.inspect(item).readable());assertThrows(IllegalArgumentException.class,()->CardComponents.write(item,FilterCard.blank()));
            assertEquals(raw,item.get(MultiFilterMod.CARD_DATA.get()));
        }
    }
}
