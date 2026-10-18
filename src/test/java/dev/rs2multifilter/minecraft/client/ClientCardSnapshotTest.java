package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.*;
import java.io.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientCardSnapshotTest {
    @BeforeEach @AfterEach void clear() { ClientCardState.clearAll(); }
    static FilterCard card(String name, String icon, String item) {
        return FilterCard.blank().withCategory(new FilterCategory(name, icon,
            List.of(new FilterRule(FilterRule.Kind.ITEM, item, false))));
    }
    static FilterCard original() { return card("Original", "minecraft:stone", "minecraft:stone"); }
    void sync(int menu, int slot, FilterCard card) { ClientCardState.update(menu, slot, CardWireCodec.encode(card)); }
    @Test void unchangedFrameAndSourceReadsDecodeOnlyOnceAndReuseTheSameSnapshots() {
        String raw = CardWireCodec.encode(original());
        ClientCardState.update(1, 0, raw);
        try (var codec = mockStatic(CardWireCodec.class, CALLS_REAL_METHODS)) {
            var first = ClientCardState.card(1);
            var owner = ClientCardState.source(1, 0);
            for (int frame = 0; frame < 1000; frame++) {
                assertSame(first, ClientCardState.card(1));
                assertSame(owner, ClientCardState.source(1, 0));
            }
            codec.verify(() -> CardWireCodec.decode(raw), times(1));
        }
    }
    @Test void replacingOneSlotDoesNotDecodeOrReplaceUnchangedSlotOwners() {
        String a = CardWireCodec.encode(original()), b = CardWireCodec.encode(card("B", "minecraft:dirt", "minecraft:dirt"));
        ClientCardState.update(1, 0, a); ClientCardState.update(1, 1, a);
        try (var codec = mockStatic(CardWireCodec.class, CALLS_REAL_METHODS)) {
            var old = ClientCardState.card(1); var owner = ClientCardState.source(1, 0);
            ClientCardState.update(1, 1, b);
            assertNotSame(old, ClientCardState.card(1));
            assertSame(owner, ClientCardState.source(1, 0));
            assertEquals("B", ClientCardState.card(1).categories().get(1).name());
            codec.verify(() -> CardWireCodec.decode(a), times(2));
            codec.verify(() -> CardWireCodec.decode(b), times(1));
        }
    }
    @Test void duplicatePacketsKeepRevisionAndSnapshotIdentity() {
        sync(1, 0, original()); var first = ClientCardState.card(1); int revision = ClientCardState.revision(1);
        sync(1, 0, original()); assertSame(first, ClientCardState.card(1)); assertEquals(revision, ClientCardState.revision(1));
    }
    static Stream<FilterCard> edits() {
        var c=original().categories().getFirst();
        return Stream.of(card("Renamed",c.iconItemId(),"minecraft:stone"),
            card(c.name(),"minecraft:dirt","minecraft:stone"), card(c.name(),c.iconItemId(),"minecraft:dirt"),
            original().withCategory(new FilterCategory("Added","minecraft:paper",List.of())), FilterCard.blank(),
            FilterCard.blank().withCategory(new FilterCategory(c.name(),c.iconItemId(),c.rules(),FilterCategory.Mode.ALL)),
            FilterCard.blank().withCategory(new FilterCategory(c.name(),c.iconItemId(),List.of(new FilterRule(FilterRule.Kind.ITEM,"minecraft:stone",true)))));
    }
    @ParameterizedTest @MethodSource("edits") void everySerializedConfigurationEditInvalidatesTheSnapshot(FilterCard replacement) {
        sync(1,0,original()); var before=ClientCardState.card(1); sync(1,0,replacement);
        assertNotSame(before,ClientCardState.card(1)); assertEquals(replacement,ClientCardState.card(1));
        if(!replacement.categories().isEmpty())assertEquals(replacement,ClientCardState.source(1,0).card());
    }
    @ParameterizedTest @ValueSource(strings={"v99:unknown", "v2:%%%", "broken", "v2:UlNG"})
    void unreadableRawDataIsRetainedAndItsFailureIsNotDecodedEveryFrame(String raw) throws Exception {
        ClientCardState.update(1,0,raw);
        try(var codec=mockStatic(CardWireCodec.class,CALLS_REAL_METHODS)) {
            for(int i=0;i<100;i++){assertTrue(ClientCardState.card(1).categories().isEmpty());assertNull(ClientCardState.source(1,0));}
            codec.verify(()->CardWireCodec.decode(raw),times(1));
        }
        assertTrue(ClientCardState.installed(1,0));
        var f=ClientCardState.class.getDeclaredField("SYNCED");f.setAccessible(true);
        var snapshot=((Map<?,?>)f.get(null)).get(1);var rawField=snapshot.getClass().getDeclaredField("raw");rawField.setAccessible(true);
        assertEquals(raw,((String[])rawField.get(snapshot))[0]);
        sync(1,0,original());assertEquals(original(),ClientCardState.card(1));
    }
    @Test void partialAndOutOfOrderPacketsDoNotPrematurelyCompleteSync() {
        sync(1,3,original());assertFalse(ClientCardState.hasCompleteSync(1));assertEquals(3,ClientCardState.source(1,0).slot());
        for(int slot:new int[]{1,0,2})ClientCardState.update(1,slot,"");
        assertTrue(ClientCardState.hasCompleteSync(1));assertEquals(original(),ClientCardState.card(1));
        ClientCardState.update(1,3,"");assertTrue(ClientCardState.card(1).categories().isEmpty());assertNull(ClientCardState.source(1,0));
    }
    @Test void distinctMenusClearAndNumericIdReuseDoNotShareSnapshots() {
        sync(1,0,original());sync(2,0,card("Other","minecraft:dirt","minecraft:dirt"));
        var one=ClientCardState.card(1);var two=ClientCardState.card(2);
        ClientCardState.clear(1);assertSame(two,ClientCardState.card(2));assertFalse(ClientCardState.hasSync(1));
        sync(1,0,original());assertNotSame(one,ClientCardState.card(1));
        ClientCardState.clearAll();assertFalse(ClientCardState.hasSync(2));assertEquals(0,ClientCardState.revision(2));
    }
    @Test void all256CategoriesKeepPhysicalSlotOrderAndOwners() {
        var categories=java.util.stream.IntStream.range(0,64).mapToObj(i->new FilterCategory("C"+i,"minecraft:paper",List.<FilterRule>of())).toList();
        var card=new FilterCard(FilterCard.VERSION,categories);
        for(int slot=0;slot<4;slot++)sync(1,slot,card);
        assertEquals(256,ClientCardState.card(1).categories().size());
        for(int category=0;category<256;category++)assertEquals(category/64,ClientCardState.source(1,category).slot());
        assertNull(ClientCardState.source(1,256));assertNull(ClientCardState.source(1,-1));
    }
    @Test void nearLimitV1IsCachedWithoutTryingToReencodeItAsV2() throws Exception {
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)) {
            out.writeInt(0x52534632);out.writeByte(1);out.writeShort(64);
            for(int i=0;i<64;i++){out.writeUTF("c");out.writeUTF("minecraft:paper");out.writeShort(1);out.writeByte(FilterRule.Kind.ITEM.ordinal());out.writeBoolean(false);out.writeUTF("minecraft:"+"a".repeat(213));}
        }
        String raw="v1:"+Base64.getEncoder().encodeToString(bytes.toByteArray());ClientCardState.update(1,0,raw);
        try(var codec=mockStatic(CardWireCodec.class,CALLS_REAL_METHODS)) {
            var first=ClientCardState.card(1);assertEquals(64,first.categories().size());
            for(int i=0;i<100;i++)assertSame(first,ClientCardState.card(1));
            codec.verify(()->CardWireCodec.decode(raw),times(1));codec.verify(()->CardWireCodec.encode(any()),never());
        }
    }
    @Test void rejectedPayloadsLeaveExistingCacheUnchanged() {
        sync(1,0,original());var first=ClientCardState.card(1);int revision=ClientCardState.revision(1);
        ClientCardState.update(1,0,null);ClientCardState.update(1,0,"x".repeat(CardWireCodec.MAX_ENCODED_CHARS+1));
        ClientCardState.update(1,4,"");ClientCardState.update(256,0,"");
        assertSame(first,ClientCardState.card(1));assertEquals(revision,ClientCardState.revision(1));assertFalse(ClientCardState.hasSync(256));
    }
}
