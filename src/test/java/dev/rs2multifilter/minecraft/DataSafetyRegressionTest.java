package dev.rs2multifilter.minecraft;

import dev.rs2multifilter.core.*;
import dev.rs2multifilter.minecraft.menu.CardEditorMenu;
import dev.rs2multifilter.minecraft.network.SaveCardPayload;
import dev.rs2multifilter.minecraft.network.InstalledCardDataPayload;
import dev.rs2multifilter.minecraft.network.SwapInstalledCardPayload;
import dev.rs2multifilter.minecraft.client.ClientCardState;
import dev.rs2multifilter.minecraft.recipe.CopyCardRecipe;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real components, menus, payload codec/handler and recipe; transport/player are mocked. */
class DataSafetyRegressionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    static FilterCard normal() {
        return FilterCard.blank().withCategory(new FilterCategory("原始分类", "minecraft:stone",
            List.of(new FilterRule(FilterRule.Kind.ITEM, "minecraft:stone", false))));
    }
    static String legacy(boolean large) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(0x52534632); out.writeByte(1); out.writeShort(large ? 64 : 1);
            for (int i = 0; i < (large ? 64 : 1); i++) {
                out.writeUTF("c"); out.writeUTF("minecraft:paper"); out.writeShort(1);
                out.writeByte(FilterRule.Kind.ITEM.ordinal()); out.writeBoolean(false);
                out.writeUTF(large ? "minecraft:" + "a".repeat(213) : "minecraft:stone");
            }
        }
        if (large) assertEquals(15_943, bytes.size());
        return "v1:" + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    static String typedData(int version, int mode, int kind) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(0x52534632); out.writeByte(version); out.writeShort(1);
            out.writeUTF("c"); out.writeUTF("minecraft:paper"); out.writeByte(mode);
            out.writeShort(1); out.writeByte(kind); out.writeBoolean(false); out.writeUTF("minecraft:stone");
        }
        return "v2:" + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    static List<String> invalidData() throws IOException {
        String valid = CardWireCodec.encode(normal());
        byte[] bytes = Base64.getDecoder().decode(valid.substring(3));
        return List.of("v99:" + valid.substring(3), typedData(99,0,1), typedData(2,0,250),
            typedData(2,250,1), "v2:%%%", "broken configuration",
            "v2:" + Base64.getEncoder().encodeToString(Arrays.copyOf(bytes, bytes.length - 2)),
            valid + "AAAA");
    }
    private ItemStack card(String raw) {
        var result = new ItemStack(MultiFilterMod.FILTER_CARD.get());
        if (raw != null) result.set(MultiFilterMod.CARD_DATA.get(), raw);
        return result;
    }
    private ServerPlayer player(ItemStack card) {
        var player = mock(ServerPlayer.class);
        var inventory = new Inventory(player);
        inventory.setItem(0, card);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getMainHandItem()).thenReturn(card);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(card);
        return player;
    }
    private SaveCardPayload wire(SaveCardPayload payload) {
        var bytes = Unpooled.buffer();
        try { SaveCardPayload.STREAM_CODEC.encode(bytes, payload); return SaveCardPayload.STREAM_CODEC.decode(bytes); }
        finally { bytes.release(); }
    }
    private void handle(ServerPlayer player, SaveCardPayload payload) {
        var context = mock(IPayloadContext.class);
        when(context.player()).thenReturn(player);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return CompletableFuture.completedFuture(null); })
            .when(context).enqueueWork(any(Runnable.class));
        SaveCardPayload.handle(wire(payload), context);
    }

    @Test void normalV1ReadsAndMigratesWithoutChangingTheOriginalUntilSave() throws Exception {
        String raw = legacy(false);
        var stack = card(raw);
        var decoded = CardComponents.read(stack);
        assertEquals(decoded, CardWireCodec.decode(CardWireCodec.encode(decoded)));
        assertEquals(raw, stack.get(MultiFilterMod.CARD_DATA.get()));
    }
    @Test void v2RoundtripPreservesEveryRuleAndMode() {
        assertEquals(normal(), CardWireCodec.decode(CardWireCodec.encode(normal())));
    }
    @Test void largeV1CanSynchronizeWithoutForcedOversizedV2Encoding() throws Exception {
        String raw = legacy(true);
        var stack = card(raw);
        String view = assertDoesNotThrow(() -> SwapInstalledCardPayload.cardData(stack));
        assertEquals(CardWireCodec.decode(raw), CardWireCodec.decode(view));
        assertTrue(view.length() <= CardWireCodec.MAX_ENCODED_CHARS);
        assertEquals(raw, stack.get(MultiFilterMod.CARD_DATA.get()));
    }
    @Test void largeV1OpeningUsesALosslessBoundedPacketAndPreservesTheCard() throws Exception {
        String raw = legacy(true);
        var stack = card(raw);
        var player = player(stack);
        assertDoesNotThrow(() -> stack.getItem().use(mock(Level.class), player, InteractionHand.MAIN_HAND));
        var provider = org.mockito.ArgumentCaptor.forClass(MenuProvider.class);
        @SuppressWarnings("unchecked") var writer = org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(player).openMenu(provider.capture(), writer.capture());
        var menu = (CardEditorMenu)provider.getValue().createMenu(7, player.getInventory(), player);
        assertEquals(CardWireCodec.decode(raw), menu.initialCard());
        var buffer = mock(RegistryFriendlyByteBuf.class);
        writer.getValue().accept(buffer);
        var text = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(buffer).writeUtf(text.capture(), eq(CardWireCodec.MAX_ENCODED_CHARS));
        assertEquals(menu.initialCard(), CardWireCodec.decode(text.getValue()));
        var actual = new RegistryFriendlyByteBuf(Unpooled.buffer(),
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            writer.getValue().accept(actual);
            var received = new CardEditorMenu(7, player.getInventory(), actual);
            assertEquals(menu.initialCard(), received.initialCard());
            assertEquals(0, actual.readableBytes());
        } finally { actual.release(); }
        assertEquals(raw, stack.get(MultiFilterMod.CARD_DATA.get()));
    }
    @Test void largeV1CopyRecipePreservesAllRulesAndTheSourceRawBytes() throws Exception {
        String raw = legacy(true);
        var source = card(raw); var target = card(null);
        var input = CraftingInput.of(2,1,List.of(source,target));
        var recipe = new CopyCardRecipe(CraftingBookCategory.MISC);
        assertTrue(recipe.matches(input,mock(Level.class)));
        var output = assertDoesNotThrow(() -> recipe.assemble(input,null));
        assertEquals(CardWireCodec.decode(raw),CardComponents.read(output));
        assertEquals(raw,source.get(MultiFilterMod.CARD_DATA.get()));
        assertEquals(raw,recipe.getRemainingItems(input).get(0).get(MultiFilterMod.CARD_DATA.get()));
        assertNull(target.get(MultiFilterMod.CARD_DATA.get()));
    }
    @Test void unknownAndDamagedDataMustNotBeReadAsANormalBlankCard() throws Exception {
        for (String raw : invalidData()) {
            var stack=card(raw);
            assertThrows(IllegalArgumentException.class,() -> CardComponents.read(stack));
            assertEquals(raw,stack.get(MultiFilterMod.CARD_DATA.get()));
        }
    }
    @Test void invalidCardCannotBeOpenedAsANewEditableDefault() throws Exception {
        for (String raw : invalidData()) {
            var stack=card(raw); var player=player(stack);
            assertDoesNotThrow(() -> stack.getItem().use(mock(Level.class),player,InteractionHand.MAIN_HAND));
            verify(player,never()).openMenu(any(MenuProvider.class),any(Consumer.class));
            verify(player).displayClientMessage(argThat(c -> c.getString().contains("原始")),eq(false));
            assertEquals(raw,stack.get(MultiFilterMod.CARD_DATA.get()));
        }
    }
    @Test void invalidCopyTargetAndSourceCannotBeMistakenForBlankInputs() throws Exception {
        var recipe=new CopyCardRecipe(CraftingBookCategory.MISC);
        for (String raw : invalidData()) {
            var source=card(CardWireCodec.encode(normal())); var invalid=card(raw);
            for (var inputs : List.of(List.of(source,invalid),List.of(invalid,source),List.of(invalid,card(null)))) {
                var input=CraftingInput.of(2,1,inputs);
                assertFalse(recipe.matches(input,mock(Level.class)));
                assertTrue(recipe.assemble(input,null).isEmpty());
                assertEquals(raw,invalid.get(MultiFilterMod.CARD_DATA.get()));
            }
        }
    }
    @Test void directWriteCannotSilentlyOverwriteUnreadableConfiguration() throws Exception {
        for (String raw : invalidData()) {
            var stack=card(raw);
            assertThrows(IllegalArgumentException.class,() -> CardComponents.write(stack,normal()));
            assertEquals(raw,stack.get(MultiFilterMod.CARD_DATA.get()));
        }
    }
    @Test void realSaveHandlerRejectsAnInvalidTargetEvenAfterItsMenuWasOpened() throws Exception {
        for (String raw : invalidData()) {
            var stack=card(CardWireCodec.encode(normal()));var player=player(stack);
            var menu=new CardEditorMenu(7,player.getInventory(),0,stack);
            player.containerMenu=menu;
            stack.set(MultiFilterMod.CARD_DATA.get(),raw);
            handle(player,new SaveCardPayload(7,CardWireCodec.encode(normal())));
            assertEquals(raw,stack.get(MultiFilterMod.CARD_DATA.get()));
            verify(player,never()).closeContainer();
            verify(player).displayClientMessage(argThat(c -> c.getString().contains("原始")),eq(false));
        }
    }
    @Test void realSaveHandlerReportsOversizedMigrationWithoutChangingLegacyBytes() throws Exception {
        String raw=legacy(true);var stack=card(raw);var player=player(stack);
        player.containerMenu=new CardEditorMenu(7,player.getInventory(),0,stack);
        handle(player,new SaveCardPayload(7,raw));
        assertEquals(raw,stack.get(MultiFilterMod.CARD_DATA.get()));
        verify(player,never()).closeContainer();
        verify(player).displayClientMessage(argThat(c -> c.getString().contains("原始")),eq(false));
    }
    @Test void realSaveHandlerMigratesNormalV1AndAcceptsValidV2() throws Exception {
        for (String raw : List.of(legacy(false),CardWireCodec.encode(normal()))) {
            var stack=card(raw);var player=player(stack);
            player.containerMenu=new CardEditorMenu(7,player.getInventory(),0,stack);
            var replacement=normal().withCategory(new FilterCategory("追加", "minecraft:paper",List.of()));
            handle(player,new SaveCardPayload(7,CardWireCodec.encode(replacement)));
            assertEquals(replacement,CardComponents.read(stack));
            assertTrue(stack.get(MultiFilterMod.CARD_DATA.get()).startsWith("v2:"));
            verify(player).closeContainer();
        }
    }

    @Test void inspectionDistinguishesMissingEmptyValidUnknownAndCorruptData() throws Exception {
        assertEquals(CardReadResult.State.EMPTY, CardComponents.inspect(card(null)).state());
        assertEquals(CardReadResult.State.EMPTY, CardComponents.inspect(card("")).state());
        for (String raw : List.of(legacy(false), legacy(true), CardWireCodec.encode(normal()),
                CardWireCodec.encode(FilterCard.blank()))) {
            var result = CardComponents.inspect(card(raw));
            assertEquals(CardReadResult.State.VALID, result.state());
            assertEquals(raw, result.raw());
            assertTrue(result.diagnostic().isEmpty());
        }
        var invalid = invalidData();
        for (int i = 0; i < invalid.size(); i++) {
            var result = CardComponents.inspect(card(invalid.get(i)));
            assertEquals(i < 4 ? CardReadResult.State.UNKNOWN : CardReadResult.State.CORRUPT, result.state());
            assertEquals(invalid.get(i), result.raw());
            assertFalse(result.diagnostic().isBlank());
            assertFalse(result.readable());
            assertThrows(IllegalArgumentException.class, result::requireCard);
        }
    }

    @Test void legalNearLimitV1WouldGain64BytesWithoutChangingAnyExistingLimit() throws Exception {
        String raw = legacy(true);
        assertEquals(16_000, CardWireCodec.MAX_BYTES);
        assertEquals(22_000, CardWireCodec.MAX_ENCODED_CHARS);
        assertEquals(16_007, Base64.getDecoder().decode(raw.substring(3)).length +
            CardWireCodec.decode(raw).categories().size());
        assertThrows(IllegalArgumentException.class, () -> CardWireCodec.encode(CardWireCodec.decode(raw)));
        assertTrue(CardReadResult.inspect(raw).migrationExceedsLimit());
        assertFalse(CardReadResult.inspect(legacy(false)).migrationExceedsLimit());
    }

    @Test void largeV1UsesActualInstalledPacketCodecAndClientAggregationWithoutDataLoss() throws Exception {
        String raw = legacy(true); var stack = card(raw); var bytes = Unpooled.buffer();
        try {
            var sent = new InstalledCardDataPayload(19, 3, SwapInstalledCardPayload.cardData(stack));
            InstalledCardDataPayload.STREAM_CODEC.encode(bytes, sent);
            var received = InstalledCardDataPayload.STREAM_CODEC.decode(bytes);
            assertEquals(sent, received);
            assertEquals(raw, received.encodedCard());
            assertEquals(0, bytes.readableBytes());
            ClientCardState.update(19, 3, received.encodedCard());
            assertEquals(CardWireCodec.decode(raw), ClientCardState.card(19));
            assertEquals(raw, stack.get(MultiFilterMod.CARD_DATA.get()));
        } finally { bytes.release(); ClientCardState.clear(19); }
    }

    @Test void realItemNbtSaveAndParseRetainLegacyUnknownAndDamagedBytes() throws Exception {
        var samples = new ArrayList<>(invalidData());
        samples.add(legacy(true)); samples.add(legacy(false)); samples.add(CardWireCodec.encode(normal()));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (String raw : samples) {
            var original = card(raw);
            var restored = ItemStack.parse(registries, original.save(registries)).orElseThrow();
            assertTrue(restored.is(MultiFilterMod.FILTER_CARD.get()));
            assertEquals(raw, restored.get(MultiFilterMod.CARD_DATA.get()));
            assertEquals(CardComponents.inspect(original), CardComponents.inspect(restored));
        }
    }

    @Test void realSavedMenuResultSurvivesActualItemNbtPersistence() throws Exception {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var stack = card(legacy(false)); var player = player(stack);
        player.containerMenu = new CardEditorMenu(7, player.getInventory(), 0, stack);
        handle(player, new SaveCardPayload(7, CardWireCodec.encode(normal())));
        var restored = ItemStack.parse(registries, stack.save(registries)).orElseThrow();
        assertEquals(normal(), CardComponents.read(restored));
        assertTrue(restored.get(MultiFilterMod.CARD_DATA.get()).startsWith("v2:"));
        verify(player).closeContainer();
    }

    @Test void invalidIncomingSaveCannotChangeAValidOriginal() throws Exception {
        var invalid = new ArrayList<>(invalidData());
        invalid.add("x".repeat(CardWireCodec.MAX_ENCODED_CHARS + 1));
        for (String payload : invalid) {
            String raw = CardWireCodec.encode(normal());
            var stack = card(raw); var player = player(stack);
            player.containerMenu = new CardEditorMenu(7, player.getInventory(), 0, stack);
            handle(player, new SaveCardPayload(7, payload));
            assertEquals(raw, stack.get(MultiFilterMod.CARD_DATA.get()));
            verify(player, never()).closeContainer();
            verify(player).displayClientMessage(argThat(c -> c.getString().contains("原始")), eq(false));
        }
    }

    @Test void largeV1CanBeExplicitlyReducedAndThenSavedAsV2() throws Exception {
        var stack = card(legacy(true)); var player = player(stack);
        var initial = CardComponents.read(stack);
        player.containerMenu = new CardEditorMenu(7, player.getInventory(), 0, stack);
        var reduced = new FilterCard(FilterCard.VERSION, initial.categories().subList(0, 63));
        handle(player, new SaveCardPayload(7, CardWireCodec.encode(reduced)));
        assertEquals(reduced, CardComponents.read(stack));
        assertTrue(stack.get(MultiFilterMod.CARD_DATA.get()).startsWith("v2:"));
        verify(player).closeContainer();
    }

    @Test void componentCopyGuardProtectsInvalidTargetsIndependentlyOfTheRecipe() throws Exception {
        var source = card(CardWireCodec.encode(normal()));
        for (String raw : invalidData()) {
            var target = card(raw);
            assertThrows(IllegalArgumentException.class, () -> CardComponents.copyConfiguration(source, target));
            assertEquals(raw, target.get(MultiFilterMod.CARD_DATA.get()));
        }
    }

    @Test void invalidInstalledDataIsReportedAndNeverRewrittenIntoABlankCard() throws Exception {
        var samples = new ArrayList<>(invalidData());
        samples.add("x".repeat(CardWireCodec.MAX_ENCODED_CHARS + 1));
        for (String raw : samples) {
            var stack = card(raw); var player = player(stack);
            String data = assertDoesNotThrow(() -> SwapInstalledCardPayload.cardData(stack));
            assertTrue(data.length() <= CardWireCodec.MAX_ENCODED_CHARS);
            if (raw.length() <= CardWireCodec.MAX_ENCODED_CHARS) assertEquals(raw, data);
            CardComponents.reportReadStatus(player, stack, "终端同步");
            verify(player).displayClientMessage(argThat(c -> c.getString().contains("原始")), eq(false));
            assertEquals(raw, stack.get(MultiFilterMod.CARD_DATA.get()));
        }
    }
}
