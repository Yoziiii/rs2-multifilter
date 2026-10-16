package dev.rs2multifilter.minecraft.menu;

import dev.rs2multifilter.core.FilterCard;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CardEditorMenuTest {
    private Player player;
    private Inventory inventory;
    private ItemStack card;
    private CardEditorMenu menu;

    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach void openEditor() {
        player = mock(Player.class);
        inventory = new Inventory(player);
        when(player.getInventory()).thenReturn(inventory);
        card = new ItemStack(Items.PAPER);
        inventory.setItem(0, card);
        menu = openMenu();
    }

    private CardEditorMenu openMenu() {
        return new CardEditorMenu(null, 1, inventory, 0, card, FilterCard.blank());
    }

    @Test void playerSlotsReferenceTheExistingInventory() {
        assertEquals(37, menu.slots.size());
        for (int i = 0; i < 36; i++) {
            assertSame(inventory, menu.getSlot(i).container);
            assertEquals(i < 27 ? i + 9 : i - 27, menu.getSlot(i).getContainerSlot());
        }
        assertInstanceOf(GhostReferenceSlot.class, menu.getSlot(36));
    }

    @Test void singleClickCopiesOneSampleWithAllComponents() {
        ItemStack source = new ItemStack(Items.IRON_PICKAXE, 1);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("参考镐"));
        source.setDamageValue(42);
        inventory.setItem(9, source);

        menu.clicked(0, 0, ClickType.PICKUP, player);

        assertSame(source, inventory.getItem(9));
        assertEquals(1, source.getCount());
        assertNotSame(source, menu.referenceItem());
        assertTrue(ItemStack.isSameItemSameComponents(source, menu.referenceItem()));
        assertTrue(menu.getCarried().isEmpty());
        menu.referenceItem().setDamageValue(7);
        assertEquals(42, source.getDamageValue());
    }

    @Test void selectingAndReplacingSamplesNeverConsumesStacks() {
        inventory.setItem(9, new ItemStack(Items.REDSTONE, 64));
        inventory.setItem(10, new ItemStack(Items.IRON_INGOT, 32));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        assertEquals(1, menu.referenceItem().getCount());
        menu.clicked(1, 0, ClickType.PICKUP, player);
        assertTrue(menu.referenceItem().is(Items.IRON_INGOT));
        assertEquals(64, inventory.getItem(9).getCount());
        assertEquals(32, inventory.getItem(10).getCount());
    }

    @Test void shiftClickSelectsWithoutTransferringItems() {
        inventory.setItem(9, new ItemStack(Items.REDSTONE, 64));
        assertTrue(menu.quickMoveStack(player, 0).isEmpty());
        assertTrue(menu.referenceItem().is(Items.REDSTONE));
        assertEquals(1, menu.referenceItem().getCount());
        assertEquals(64, inventory.getItem(9).getCount());
    }

    @Test void ghostCannotBeTakenOrTransferredToTheRealInventory() {
        inventory.setItem(9, new ItemStack(Items.REDSTONE, 64));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        var ghost = menu.getSlot(CardEditorMenu.REFERENCE_SLOT);
        assertFalse(ghost.mayPickup(player));
        assertFalse(ghost.mayPlace(inventory.getItem(9)));
        assertTrue(ghost.remove(1).isEmpty());
        for (ClickType type : new ClickType[]{ClickType.SWAP, ClickType.CLONE,
                ClickType.THROW, ClickType.QUICK_CRAFT, ClickType.PICKUP_ALL}) {
            menu.clicked(CardEditorMenu.REFERENCE_SLOT, 0, type, player);
        }
        assertTrue(menu.getCarried().isEmpty());
        assertEquals(64, inventory.getItem(9).getCount());
        assertEquals(1, menu.referenceItem().getCount());
    }

    @Test void clearingAReferenceNeverReturnsOrDropsAnItem() {
        inventory.setItem(9, new ItemStack(Items.REDSTONE, 64));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(CardEditorMenu.REFERENCE_SLOT, 0, ClickType.PICKUP, player);
        assertTrue(menu.referenceItem().isEmpty());
        assertEquals(64, inventory.getItem(9).getCount());
        assertTrue(menu.getCarried().isEmpty());
        verify(player, never()).drop(any(ItemStack.class), anyBoolean());
    }

    @Test void unsupportedClicksCannotChangePlayerInventory() {
        ItemStack source = new ItemStack(Items.REDSTONE, 64);
        inventory.setItem(9, source);
        for (ClickType type : ClickType.values()) {
            menu.clicked(0, 0, type, player);
            menu.clicked(0, 1, type, player);
            menu.clicked(-999, 0, type, player);
            menu.clicked(999, 0, type, player);
            assertSame(source, inventory.getItem(9));
            assertEquals(64, source.getCount());
            assertSame(card, inventory.getItem(0));
            assertTrue(menu.getCarried().isEmpty());
        }
    }

    @Test void closingDiscardsOnlyTheTemporarySample() {
        inventory.setItem(9, new ItemStack(Items.REDSTONE, 64));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.removed(player);
        assertTrue(menu.referenceItem().isEmpty());
        assertTrue(openMenu().referenceItem().isEmpty());
        assertEquals(64, inventory.getItem(9).getCount());
        assertSame(card, inventory.getItem(0));
        assertTrue(card.getComponentsPatch().isEmpty());
    }

    @Test void editingSessionRejectsCardSwitchesAndReplacement() {
        assertTrue(menu.stillValid(player));
        inventory.selected = 1;
        assertFalse(menu.stillValid(player));
        inventory.selected = 0;
        inventory.setItem(0, new ItemStack(Items.PAPER));
        assertFalse(menu.stillValid(player));
        assertFalse(menu.stillValid(mock(Player.class)));
    }
}
