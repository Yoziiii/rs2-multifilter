package dev.rs2multifilter.minecraft.storage;

import com.refinedmods.refinedstorage.common.grid.CraftingGridBlockEntity;
import com.refinedmods.refinedstorage.common.support.AbstractBaseBlock;
import dev.rs2multifilter.core.*;
import dev.rs2multifilter.minecraft.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real RS blocks, injected holders, removal/wrench methods and NBT; world is a test double.
 * Actual ItemEntity instances are captured at the world insertion boundary. */
class InstalledCardRemovalTest {
    private static final BlockPos POS = new BlockPos(10, 64, 10);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static class Fixture {
        final ServerLevel world = mock(ServerLevel.class);
        final BlockState state;
        final BlockEntity grid;
        final InstalledCardHolder cards;
        final AtomicReference<BlockEntity> present;
        final List<ItemStack> originals = new ArrayList<>();
        final List<ItemEntity> drops = new ArrayList<>();
        Fixture(String name) throws Exception {
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("refinedstorage:" + name));
            assertInstanceOf(AbstractBaseBlock.class, block);
            state = block.defaultBlockState();
            grid = ((EntityBlock) block).newBlockEntity(POS, state);
            cards = assertInstanceOf(InstalledCardHolder.class, grid);
            present = new AtomicReference<>(grid);
            var random = Level.class.getDeclaredField("random");
            random.setAccessible(true); random.set(world, RandomSource.create(1));
            when(world.getRandom()).thenReturn(RandomSource.create(2));
            when(world.getGameRules()).thenReturn(new GameRules());
            when(world.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            when(world.getBlockEntity(POS)).thenAnswer(call -> present.get());
            when(world.getBlockState(POS)).thenAnswer(call -> present.get() == null ? Blocks.AIR.defaultBlockState() : state);
            doAnswer(call -> { present.set(null); return null; }).when(world).removeBlockEntity(POS);
            when(world.setBlockAndUpdate(eq(POS), any())).thenAnswer(call -> {
                state.onRemove(world, POS, call.getArgument(1), false); return true;
            });
            when(world.setBlock(eq(POS), any(), anyInt())).thenAnswer(call -> {
                state.onRemove(world, POS, call.getArgument(1), false); return true;
            });
            when(world.addFreshEntity(any())).thenAnswer(call -> {
                Entity entity = call.getArgument(0);
                if (entity instanceof ItemEntity item) drops.add(item);
                return true;
            });
            for (int slot = 0; slot < 4; slot++) {
                var card = new ItemStack(MultiFilterMod.FILTER_CARD.get());
                CardComponents.write(card, FilterCard.blank().withCategory(new FilterCategory("卡" + slot,
                    "minecraft:stone", List.of(new FilterRule(FilterRule.Kind.ITEM,"minecraft:stone",slot%2==1)))));
                originals.add(card.copy()); cards.rs2mf$setInstalledCard(slot,card);
            }
        }
        BlockEvent.BreakEvent breakEvent() { return new BlockEvent.BreakEvent(world,POS,state,mock(ServerPlayer.class)); }
        void remove() { state.onRemove(world, POS, Blocks.AIR.defaultBlockState(), false); }
        List<ItemStack> looseCards() {
            return drops.stream().map(ItemEntity::getItem).filter(s -> s.is(MultiFilterMod.FILTER_CARD.get())).toList();
        }
        void assertInstalled() { for(int i=0;i<4;i++) assertTrue(ItemStack.matches(originals.get(i),cards.rs2mf$getInstalledCard(i))); }
        void assertDroppedExactlyOnce() {
            var loose = looseCards(); assertEquals(4,loose.size());
            for(var original: originals) assertEquals(1,loose.stream().filter(s -> ItemStack.matches(original,s)).count());
            for(int i=0;i<4;i++) assertTrue(cards.rs2mf$getInstalledCard(i).isEmpty());
        }
        ItemStack dismantle() throws Exception {
            var method=AbstractBaseBlock.class.getDeclaredMethod("dismantle",BlockState.class,Level.class,BlockHitResult.class,ServerPlayer.class);
            method.setAccessible(true);
            var hit=new BlockHitResult(Vec3.atCenterOf(POS),Direction.UP,POS,false);
            assertEquals(true,method.invoke(state.getBlock(),state,world,hit,mock(ServerPlayer.class)));
            assertTrue(looseCards().isEmpty());
            assertEquals(1,drops.size());
            return drops.getFirst().getItem();
        }
        BlockEntity restore(ItemStack terminal) {
            var restored=((EntityBlock)state.getBlock()).newBlockEntity(POS,state);
            var data=terminal.get(DataComponents.BLOCK_ENTITY_DATA);
            assertNotNull(data);
            restored.loadWithComponents(data.copyTag(),world.registryAccess());
            return restored;
        }
    }

    @Test void breakIntentMustNotSpawnOrDrainCardsBeforeAnyBlockIsRemoved() throws Exception {
        var f=new Fixture("grid"); NeoForge.EVENT_BUS.post(f.breakEvent());
        assertTrue(f.drops.isEmpty()); f.assertInstalled();
    }
    @Test void canceledBreakRetainsAllFourOriginalStacks() throws Exception {
        var f=new Fixture("grid"); var event=f.breakEvent(); event.setCanceled(true);
        NeoForge.EVENT_BUS.post(event);
        assertTrue(f.drops.isEmpty()); f.assertInstalled();
    }
    @Test void cancellationByALaterLowestPriorityListenerCannotLeakEarlyDrops() throws Exception {
        var f=new Fixture("grid"); Consumer<BlockEvent.BreakEvent> cancel=e -> { if(e.getLevel()==f.world)e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,BlockEvent.BreakEvent.class,cancel);
        try {
            var event=NeoForge.EVENT_BUS.post(f.breakEvent()); assertTrue(event.isCanceled());
            assertTrue(f.drops.isEmpty()); f.assertInstalled();
        } finally { NeoForge.EVENT_BUS.unregister(cancel); }
    }
    @Test void normalPlayerRemovalDropsFourCardsOnlyOnce() throws Exception {
        var f=new Fixture("grid"); NeoForge.EVENT_BUS.post(f.breakEvent()); f.remove();
        f.assertDroppedExactlyOnce();
    }
    @Test void removalWithoutAPlayerBreakEventStillDropsAllFourCards() throws Exception {
        var f=new Fixture("grid"); f.remove(); f.assertDroppedExactlyOnce();
    }
    @Test void explosionHitCallbackKeepsAllCardsEvenWithLootDecay() throws Exception {
        var f=new Fixture("grid"); var state=spy(f.state); var explosion=mock(Explosion.class);
        when(explosion.getBlockInteraction()).thenReturn(Explosion.BlockInteraction.DESTROY_WITH_DECAY);
        when(explosion.radius()).thenReturn(4f);
        // Loot lookup is external to the card hook; exercise the real explosion/removal callbacks.
        doReturn(List.of(new ItemStack(f.state.getBlock()))).when(state).getDrops(any(LootParams.Builder.class));
        state.onExplosionHit(f.world,POS,explosion,(item,pos)->{});
        f.assertDroppedExactlyOnce();
        verify(f.world).setBlock(eq(POS),eq(Blocks.AIR.defaultBlockState()),eq(3));
    }
    @Test void repeatedRemovalCannotDuplicateCardsFromTheSameHolder() throws Exception {
        var f=new Fixture("grid");
        // Keep exposing the old object as a reentrant caller could; draining must be idempotent.
        doAnswer(call -> null).when(f.world).removeBlockEntity(POS);
        f.remove(); f.remove(); f.remove(); f.assertDroppedExactlyOnce();
    }
    @Test void craftingGridDropsCardsAndPreservesItsOwnMatrixDrops() throws Exception {
        var f=new Fixture("crafting_grid");
        ((CraftingGridBlockEntity)f.grid).getCraftingMatrix().setItem(0,new ItemStack(Items.STONE,7));
        f.remove(); f.assertDroppedExactlyOnce();
        assertEquals(7,f.drops.stream().map(ItemEntity::getItem).filter(s->s.is(Items.STONE)).mapToInt(ItemStack::getCount).sum());
    }
    @Test void sameBlockStateChangeDoesNotDropOrClearCards() throws Exception {
        var f=new Fixture("grid"); f.state.onRemove(f.world,POS,f.state,false);
        assertTrue(f.drops.isEmpty()); f.assertInstalled();
    }
    @Test void rsWrenchBackendKeepsCardsInsideTerminalItemWithoutLooseDuplicates() throws Exception {
        for(String type:List.of("grid","crafting_grid")) {
            var f=new Fixture(type); var terminal=f.dismantle();
            var restored=assertInstanceOf(InstalledCardHolder.class,f.restore(terminal));
            for(int i=0;i<4;i++) assertTrue(ItemStack.matches(f.originals.get(i),restored.rs2mf$getInstalledCard(i)));
        }
    }
    @Test void wrenchRestoreThenOrdinaryRemovalDoesNotDuplicateEmbeddedCards() throws Exception {
        var f=new Fixture("grid"); var terminal=f.dismantle(); var restored=f.restore(terminal);
        f.present.set(restored); f.drops.clear(); f.remove();
        var loose=f.looseCards(); assertEquals(4,loose.size());
        for(var original:f.originals)assertEquals(1,loose.stream().filter(s->ItemStack.matches(original,s)).count());
        for(int i=0;i<4;i++)assertTrue(((InstalledCardHolder)restored).rs2mf$getInstalledCard(i).isEmpty());
    }
    @Test void abnormalRawConfigurationsAreDroppedVerbatimWithoutReencoding() throws Exception {
        var f=new Fixture("grid");
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)) {
            out.writeInt(0x52534632);out.writeByte(1);out.writeShort(64);
            for(int i=0;i<64;i++) {
                out.writeUTF("c");out.writeUTF("minecraft:paper");out.writeShort(1);
                out.writeByte(FilterRule.Kind.ITEM.ordinal());out.writeBoolean(false);out.writeUTF("minecraft:"+"a".repeat(213));
            }
        }
        var raw=List.of(f.originals.getFirst().get(MultiFilterMod.CARD_DATA.get()),
            "v1:"+Base64.getEncoder().encodeToString(bytes.toByteArray()),"v99:unknown","v2:%%%");
        for(int i=0;i<4;i++) {
            var card=f.originals.get(i).copy();card.set(MultiFilterMod.CARD_DATA.get(),raw.get(i));
            f.originals.set(i,card.copy()); f.cards.rs2mf$setInstalledCard(i,card);
        }
        f.remove(); f.assertDroppedExactlyOnce();
    }
    @Test void emptyAndPartiallyFilledHoldersDoNotProduceExtraCards() throws Exception {
        var f=new Fixture("grid");
        f.cards.rs2mf$setInstalledCard(0,ItemStack.EMPTY); f.cards.rs2mf$setInstalledCard(2,ItemStack.EMPTY);
        f.remove(); assertEquals(2,f.looseCards().size());
        var empty=new Fixture("grid");for(int i=0;i<4;i++)empty.cards.rs2mf$setInstalledCard(i,ItemStack.EMPTY);
        empty.remove();assertTrue(empty.drops.isEmpty());
    }
}
