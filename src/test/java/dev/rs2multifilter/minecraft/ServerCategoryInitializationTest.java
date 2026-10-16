package dev.rs2multifilter.minecraft;

import dev.rs2multifilter.core.*;
import dev.rs2multifilter.minecraft.menu.CardEditorMenu;
import dev.rs2multifilter.minecraft.network.SwapInstalledCardPayload;
import dev.rs2multifilter.minecraft.recipe.CopyCardRecipe;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerCategoryInitializationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private ItemStack blank() { return new ItemStack(MultiFilterMod.FILTER_CARD.get()); }
    @Test void serverUsePreparesTheSameSingleDefaultForTheMenuAndOpeningPacket() {
        var card = blank();
        var player = mock(ServerPlayer.class);
        var inventory = new Inventory(player);
        inventory.setItem(0, card);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getMainHandItem()).thenReturn(card);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(card);
        card.getItem().use(mock(Level.class), player, InteractionHand.MAIN_HAND);
        var provider = org.mockito.ArgumentCaptor.forClass(MenuProvider.class);
        @SuppressWarnings("unchecked") var writer = org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(player).openMenu(provider.capture(), writer.capture());
        var menu = (CardEditorMenu)provider.getValue().createMenu(7, inventory, player);
        assertEquals(1, menu.initialCard().categories().size());
        assertEquals("分类1", menu.initialCard().categories().getFirst().name());
        var buffer = mock(RegistryFriendlyByteBuf.class);
        writer.getValue().accept(buffer);
        verify(buffer).writeUtf(CardWireCodec.encode(menu.initialCard()), CardWireCodec.MAX_ENCODED_CHARS);
        assertTrue(CardComponents.read(card).categories().isEmpty());
        assertEquals(1, card.getCount());
    }
    @Test void installedBlankCardGetsOneServerPreparedCategoryWithoutMutatingTheItem() {
        var card = blank();
        for (int i = 0; i < 5; i++) {
            var view = CardWireCodec.decode(SwapInstalledCardPayload.cardData(card));
            assertEquals(1, view.categories().size());
            assertEquals("分类1", view.categories().getFirst().name());
        }
        assertTrue(CardComponents.read(card).categories().isEmpty());
        assertEquals("", SwapInstalledCardPayload.cardData(ItemStack.EMPTY));
    }
    @Test void existingNamesRulesOrderIconsAndModesRemainUnchanged() {
        var card = blank();
        var configured = FilterCard.blank().withCategory(new FilterCategory("旧分类2", "minecraft:stone",
            List.of(new FilterRule(FilterRule.Kind.MOD, "create", true)), FilterCategory.Mode.DENY));
        CardComponents.write(card, configured);
        String stored = card.get(MultiFilterMod.CARD_DATA.get());
        var menu = new CardEditorMenu(3, new Inventory(mock(Player.class)), 0, card);
        assertEquals(configured, menu.initialCard());
        assertEquals(configured, CardWireCodec.decode(SwapInstalledCardPayload.cardData(card)));
        assertEquals(stored, card.get(MultiFilterMod.CARD_DATA.get()));
    }
    @Test void emptySavedCardReopensWithOneDefaultThroughTheServerMenu() {
        var card = blank();
        CardComponents.write(card, FilterCard.blank());
        var inventory = new Inventory(mock(Player.class));
        assertEquals(1, new CardEditorMenu(1, inventory, 0, card).initialCard().categories().size());
        assertEquals(1, new CardEditorMenu(2, inventory, 0, card).initialCard().categories().size());
        assertTrue(CardComponents.read(card).categories().isEmpty());
    }
    @Test void openingAnEmptyUpgradeDoesNotBreakTheRealCopyRecipeOrSourceRemainder() {
        var source = blank();
        var configured = FilterCard.blank().withCategory(new FilterCategory("来源", "minecraft:paper",
            List.of(new FilterRule(FilterRule.Kind.MOD, "create", true))));
        CardComponents.write(source, configured);
        var target = blank();
        new CardEditorMenu(1, new Inventory(mock(Player.class)), 0, target);
        SwapInstalledCardPayload.cardData(target);
        var input = CraftingInput.of(2, 1, List.of(source, target));
        var recipe = new CopyCardRecipe(CraftingBookCategory.MISC);
        assertTrue(recipe.matches(input, mock(Level.class)));
        var output = recipe.assemble(input, null);
        assertEquals(configured, CardComponents.read(output));
        assertTrue(ItemStack.matches(source, recipe.getRemainingItems(input).get(0)));
        assertTrue(recipe.getRemainingItems(input).get(1).isEmpty());
        CardComponents.write(output, FilterCard.blank());
        assertEquals(configured, CardComponents.read(source));
    }
    @Test void legacyV1DataIsReadWithoutChangingTheStoredComponent() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(bytes)) {
            out.writeInt(0x52534632); out.writeByte(1); out.writeShort(1);
            out.writeUTF("旧分类"); out.writeUTF("minecraft:paper"); out.writeShort(1);
            out.writeByte(FilterRule.Kind.MOD.ordinal()); out.writeBoolean(false); out.writeUTF("create");
        }
        String legacy = "v1:" + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        var card = blank(); card.set(MultiFilterMod.CARD_DATA.get(), legacy);
        var view = CardWireCodec.decode(SwapInstalledCardPayload.cardData(card));
        assertEquals("旧分类", view.categories().getFirst().name());
        assertEquals(FilterCategory.Mode.ANY, view.categories().getFirst().mode());
        assertEquals(legacy, card.get(MultiFilterMod.CARD_DATA.get()));
    }
}
