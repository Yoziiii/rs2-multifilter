package dev.rs2multifilter.minecraft.client;

import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import dev.rs2multifilter.core.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real item codecs/components, real bridge/cache logic; Create's registry is a declared fixture. */
class AttributeCacheTest {
    Minecraft client;
    ClientLevel level;
    MockedStatic<Minecraft> access;
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @BeforeEach void setup() {
        ItemAttribute.Fixture.reset();AttributeCaches.invalidate();ClientCardState.clearAll();
        client=mock(Minecraft.class);level=world();client.level=level;
        access=mockStatic(Minecraft.class);access.when(Minecraft::getInstance).thenReturn(client);
    }
    @AfterEach void cleanup(){AttributeCaches.invalidate();ClientCardState.clearAll();ItemAttribute.Fixture.reset();access.close();}
    ClientLevel world(){var l=mock(ClientLevel.class);when(l.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));return l;}
    Map<?,?> cache(Class<?> type)throws Exception{var f=type.getDeclaredField("CACHE");f.setAccessible(true);return (Map<?,?>)f.get(null);}
    String register(int index){String id="test:cache_"+index;ItemAttribute.Fixture.registered.put(id,new ItemAttribute.TestAttribute((s,l)->s.is(Items.STONE)));return UnavailableAttributeTest.encoded(id);}
    String[] fillCreate() {
        String[] encoded=new String[CreateAttributeBridge.CACHE_CAPACITY+20];
        for(int i=0;i<encoded.length;i++){encoded[i]=register(i);assertTrue(CreateAttributeBridge.evaluate(encoded[i],new ItemStack(Items.STONE)).matched());}
        return encoded;
    }
    ItemStack sample(int index){var s=new ItemStack(Items.STONE);s.set(DataComponents.CUSTOM_NAME,Component.literal("sample "+index));return s;}
    void seedBoth() {String encoded=register(0);assertTrue(CreateAttributeBridge.evaluate(encoded,new ItemStack(Items.STONE)).matched());assertNotNull(ExactItemBridge.encode(sample(0)));}
    void assertEmpty()throws Exception{assertTrue(cache(CreateAttributeBridge.class).isEmpty());assertTrue(cache(ExactItemBridge.class).isEmpty());}

    @Test void lruEvictsOnlyTheLeastRecentlyUsedEntry() {
        Map<String,Integer> c=AttributeCaches.bounded(2);c.put("a",1);c.put("b",2);assertEquals(1,c.get("a"));c.put("c",3);
        assertEquals(Map.of("a",1,"c",3),c);assertFalse(c.containsKey("b"));
    }
    @Test void replacementAndBulkInsertionRemainBounded() {
        Map<String,Integer> c=AttributeCaches.bounded(2);c.put("a",1);c.put("a",2);assertEquals(1,c.size());
        c.putAll(new LinkedHashMap<>(Map.of("b",3,"c",4,"d",5)));assertEquals(2,c.size());
    }
    @Test void invalidCapacityIsRejected(){assertThrows(IllegalArgumentException.class,()->AttributeCaches.bounded(0));}
    @Test void createEvaluationIsBoundedAndAnEvictedRuleReloadsWithTheSameResult()throws Exception {
        var encoded=fillCreate();assertEquals(CreateAttributeBridge.CACHE_CAPACITY,cache(CreateAttributeBridge.class).size());
        assertFalse(cache(CreateAttributeBridge.class).containsKey(encoded[0]));int loads=ItemAttribute.Fixture.loads;
        assertTrue(CreateAttributeBridge.evaluate(encoded[0],new ItemStack(Items.STONE)).matched());
        assertFalse(CreateAttributeBridge.evaluate(encoded[0],new ItemStack(Items.APPLE)).matched());
        assertEquals(loads+1,ItemAttribute.Fixture.loads);assertEquals(CreateAttributeBridge.CACHE_CAPACITY,cache(CreateAttributeBridge.class).size());
    }
    @Test void repeatedHitsReuseTheAttributeUntilExplicitH4AvailabilityCheck() {
        String encoded=register(0);for(int i=0;i<1000;i++)assertTrue(CreateAttributeBridge.evaluate(encoded,new ItemStack(Items.STONE)).matched());
        assertEquals(1,ItemAttribute.Fixture.loads);assertTrue(CreateAttributeBridge.availability(encoded).available());assertEquals(2,ItemAttribute.Fixture.loads);
    }
    @Test void discoveryInsertionUsesTheSameBoundAndCanReloadEvictedData()throws Exception {
        String first=null;
        for(int i=0;i<CreateAttributeBridge.CACHE_CAPACITY+20;i++){
            String encoded=register(i);ItemAttribute.Fixture.discovered=List.of(ItemAttribute.Fixture.registered.get("test:cache_"+i));
            var result=CreateAttributeBridge.getSampleAttributes(new ItemStack(Items.STONE));assertEquals(1,result.size());assertEquals(encoded,result.getFirst().value());
            if(i==0)first=result.getFirst().value();assertTrue(cache(CreateAttributeBridge.class).size()<=CreateAttributeBridge.CACHE_CAPACITY);
        }
        assertFalse(cache(CreateAttributeBridge.class).containsKey(first));assertTrue(CreateAttributeBridge.evaluate(first,new ItemStack(Items.STONE)).matched());
    }
    @Test void descriptionMissInsertionAlsoRespectsCapacity()throws Exception {
        for(int i=0;i<CreateAttributeBridge.CACHE_CAPACITY+20;i++){String encoded=register(i);assertEquals("测试属性",CreateAttributeBridge.describe(encoded));assertTrue(cache(CreateAttributeBridge.class).size()<=CreateAttributeBridge.CACHE_CAPACITY);}
        assertEquals(CreateAttributeBridge.CACHE_CAPACITY,cache(CreateAttributeBridge.class).size());
    }
    @Test void exactEncodingIsBoundedAndAnEvictedSampleRoundtripsAllComponents()throws Exception {
        var original=sample(0);String first=ExactItemBridge.encode(original);
        for(int i=1;i<ExactItemBridge.CACHE_CAPACITY+20;i++)assertNotNull(ExactItemBridge.encode(sample(i)));
        assertEquals(ExactItemBridge.CACHE_CAPACITY,cache(ExactItemBridge.class).size());assertFalse(cache(ExactItemBridge.class).containsKey(first));
        assertTrue(ExactItemBridge.evaluate(first,original).matched());assertFalse(ExactItemBridge.evaluate(first,sample(1)).matched());
        assertEquals(ExactItemBridge.CACHE_CAPACITY,cache(ExactItemBridge.class).size());
    }
    @Test void exactReadMissesAreBoundedAndMatchUncachedCodecResults()throws Exception {
        var values=new ArrayList<String>();for(int i=0;i<ExactItemBridge.CACHE_CAPACITY+20;i++)values.add(ExactItemBridge.encode(sample(i)));
        ExactItemBridge.clearCache();
        for(int i=0;i<values.size();i++){assertTrue(ExactItemBridge.evaluate(values.get(i),sample(i)).matched());assertTrue(cache(ExactItemBridge.class).size()<=ExactItemBridge.CACHE_CAPACITY);}
    }
    @Test void changingWorldIdentityInvalidatesBothCachesBeforeAnyHit()throws Exception {
        seedBoth();String encoded=register(0);client.level=world();
        ItemAttribute.Fixture.registered.put("test:cache_0",new ItemAttribute.TestAttribute((s,l)->false));
        assertFalse(CreateAttributeBridge.evaluate(encoded,new ItemStack(Items.STONE)).matched());assertTrue(cache(ExactItemBridge.class).isEmpty());
    }
    @Test void changingRegistryIdentityInTheSameWorldInvalidatesBothCaches()throws Exception {
        seedBoth();when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        AttributeCaches.level();assertEmpty();
    }
    @Test void unchangedWorldKeepsHitsAndMenuSnapshotsAcrossTemporaryScreens()throws Exception {
        seedBoth();var attribute=cache(CreateAttributeBridge.class).values().iterator().next();
        var card=FilterCard.blank().withCategory(new FilterCategory("C","minecraft:stone",List.of()));ClientCardState.update(1,0,CardWireCodec.encode(card));var snapshot=ClientCardState.card(1);
        for(int i=0;i<1000;i++)assertSame(level,AttributeCaches.level());
        assertSame(attribute,cache(CreateAttributeBridge.class).values().iterator().next());assertSame(snapshot,ClientCardState.card(1));
    }
    @Test void noWorldCannotReturnAnOldCachedMatch()throws Exception {
        seedBoth();String encoded=register(0);client.level=null;
        assertFalse(CreateAttributeBridge.evaluate(encoded,new ItemStack(Items.STONE)).available());assertEmpty();
    }
    @Test void logoutClearsBothCachesAndReconnectReloadsOriginalRules()throws Exception {
        seedBoth();String encoded=register(0);AttributeCacheEvents.onLogout(mock(ClientPlayerNetworkEvent.LoggingOut.class));assertEmpty();
        client.level=world();assertTrue(CreateAttributeBridge.evaluate(encoded,new ItemStack(Items.STONE)).matched());
    }
    @Test void loginDropsAnyPreviousConnectionObjects()throws Exception {
        seedBoth();AttributeCacheEvents.onLogin(mock(ClientPlayerNetworkEvent.LoggingIn.class));assertEmpty();
    }
    @Test void unloadingTheCurrentClientWorldReleasesCachedSamples()throws Exception {
        seedBoth();when(level.isClientSide()).thenReturn(true);AttributeCacheEvents.onUnload(new LevelEvent.Unload(level));assertEmpty();
    }
    @Test void unrelatedServerOrOldClientWorldUnloadDoesNotClearTheActiveWorldCache()throws Exception {
        seedBoth();var server=mock(LevelAccessor.class);AttributeCacheEvents.onUnload(new LevelEvent.Unload(server));
        var old=world();when(old.isClientSide()).thenReturn(true);AttributeCacheEvents.onUnload(new LevelEvent.Unload(old));
        assertEquals(1,cache(CreateAttributeBridge.class).size());assertEquals(1,cache(ExactItemBridge.class).size());
    }
    @Test void clientTagReloadInvalidatesButServerDataEventDoesNot()throws Exception {
        seedBoth();var event=mock(TagsUpdatedEvent.class);when(event.getUpdateCause()).thenReturn(TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD);
        AttributeCacheEvents.onTagsUpdated(event);assertEquals(1,cache(CreateAttributeBridge.class).size());
        when(event.getUpdateCause()).thenReturn(TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED);AttributeCacheEvents.onTagsUpdated(event);assertEmpty();
    }
    @Test void evictionNeverConvertsAMissingOrThrowingAttributeIntoOrdinaryNoMatch() {
        String encoded=register(0);fillCreate();ItemAttribute.Fixture.registered.remove("test:cache_0");
        assertEquals(AttributeResult.State.UNAVAILABLE,CreateAttributeBridge.evaluate(encoded,new ItemStack(Items.STONE)).state());
        ItemAttribute.Fixture.registered.put("test:cache_0",new ItemAttribute.TestAttribute((s,l)->{throw new IllegalStateException("still failed");}));
        assertEquals(AttributeResult.State.UNAVAILABLE,CreateAttributeBridge.evaluate(encoded,new ItemStack(Items.STONE)).state());
        ItemAttribute.Fixture.registered.put("test:cache_0",new ItemAttribute.TestAttribute((s,l)->true));
        assertEquals(AttributeResult.State.MATCH,CreateAttributeBridge.evaluate(encoded,new ItemStack(Items.STONE)).state());
    }
}
