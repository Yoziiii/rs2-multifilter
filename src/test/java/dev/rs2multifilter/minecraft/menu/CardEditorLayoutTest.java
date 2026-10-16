package dev.rs2multifilter.minecraft.menu;

import dev.rs2multifilter.core.FilterCard;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static dev.rs2multifilter.minecraft.menu.CardEditorLayout.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CardEditorLayoutTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void controlsDoNotOverlapEachOtherOrTheGhostHitBox() {
        var controls = controls();
        var ghost = new Rect(REFERENCE_X - 1, REFERENCE_Y - 1, 18, 18);
        for (int i = 0; i < controls.size(); i++) {
            Rect a = controls.get(i);
            assertTrue(a.x() >= 0 && a.y() >= 15);
            assertTrue(a.x() + a.width() <= WIDTH && a.y() + a.height() <= CONFIG_HEIGHT);
            assertFalse(a.intersects(ghost));
            for (int j = i + 1; j < controls.size(); j++)
                assertFalse(a.intersects(controls.get(j)), a + " overlaps " + controls.get(j));
        }
    }

    @Test void actualMenuSlotsFitTheirPaintedPanelAndNeverOverlapAControl() {
        var inventory = new Inventory(mock(Player.class));
        var menu = new CardEditorMenu(null, 1, inventory, 0, new ItemStack(Items.PAPER), FilterCard.blank());
        assertEquals(37, menu.slots.size());
        for (int i = 0; i < 36; i++) {
            var slot = menu.getSlot(i);
            var hitbox = new Rect(slot.x - 1, slot.y - 1, 18, 18);
            assertTrue(INVENTORY.contains(hitbox.x(), hitbox.y()));
            assertTrue(INVENTORY.contains(hitbox.x() + 17, hitbox.y() + 17));
            for (Rect control : controls()) assertFalse(control.intersects(hitbox));
        }
        assertEquals(REFERENCE_X, menu.getSlot(36).x);
        assertEquals(REFERENCE_Y, menu.getSlot(36).y);
        assertSame(inventory, menu.getSlot(0).container);
    }

    @Test void panelAndClickCoordinatesFitSupportedGuiViewportSizes() {
        for (int[] viewport : new int[][]{{320, 240}, {426, 240}, {480, 270}, {640, 360}, {960, 540}}) {
            int left = (viewport[0] - WIDTH) / 2, top = (viewport[1] - HEIGHT) / 2;
            assertTrue(left >= 0 && top >= 0);
            assertTrue(left + WIDTH <= viewport[0] && top + HEIGHT <= viewport[1]);
            for (Rect r : controls()) {
                double mouseX = left + r.x() + r.width() / 2.0;
                double mouseY = top + r.y() + r.height() / 2.0;
                assertTrue(r.contains(mouseX - left, mouseY - top));
            }
        }
    }
}
