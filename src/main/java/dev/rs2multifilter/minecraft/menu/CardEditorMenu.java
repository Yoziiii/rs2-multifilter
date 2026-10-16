package dev.rs2multifilter.minecraft.menu;

import dev.rs2multifilter.core.CardWireCodec;
import dev.rs2multifilter.core.FilterCard;
import dev.rs2multifilter.minecraft.CardComponents;
import dev.rs2multifilter.minecraft.MultiFilterMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Real player inventory slots plus one transient, vanilla-synchronized sample. */
public final class CardEditorMenu extends AbstractContainerMenu {
    public static final int WIDTH = CardEditorLayout.WIDTH;
    public static final int HEIGHT = CardEditorLayout.HEIGHT;
    public static final int PLAYER_SLOT_COUNT = 36;
    public static final int REFERENCE_SLOT = PLAYER_SLOT_COUNT;
    public static final int INVENTORY_X = CardEditorLayout.INVENTORY_X;
    public static final int INVENTORY_Y = CardEditorLayout.INVENTORY_Y;
    public static final int HOTBAR_Y = CardEditorLayout.HOTBAR_Y;
    public static final int REFERENCE_X = CardEditorLayout.REFERENCE_X;
    public static final int REFERENCE_Y = CardEditorLayout.REFERENCE_Y;

    private final Inventory playerInventory;
    private final int cardInventorySlot;
    private final ItemStack editingCard;
    private final FilterCard initialCard;
    private final SimpleContainer reference = new SimpleContainer(1);

    public CardEditorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(MultiFilterMod.CARD_EDITOR_MENU.get(), id, inventory, extraData.readVarInt(),
            inventory.getSelected(), CardWireCodec.decode(extraData.readUtf(CardWireCodec.MAX_ENCODED_CHARS)));
    }

    public CardEditorMenu(int id, Inventory inventory, int cardInventorySlot, ItemStack card) {
        this(MultiFilterMod.CARD_EDITOR_MENU.get(), id, inventory, cardInventorySlot,
            card, CardComponents.read(card).forEditing());
    }

    public CardEditorMenu(int id, Inventory inventory, int cardInventorySlot, ItemStack card, FilterCard prepared) {
        this(MultiFilterMod.CARD_EDITOR_MENU.get(), id, inventory, cardInventorySlot, card, prepared);
    }

    CardEditorMenu(MenuType<?> type, int id, Inventory inventory, int cardInventorySlot,
                   ItemStack card, FilterCard initialCard) {
        super(type, id);
        this.playerInventory = inventory;
        this.cardInventorySlot = cardInventorySlot;
        this.editingCard = card;
        this.initialCard = initialCard;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addPlayerSlot(column + row * 9 + 9,
                    INVENTORY_X + column * 18, INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            addPlayerSlot(column, INVENTORY_X + column * 18, HOTBAR_Y);
        }
        addSlot(new GhostReferenceSlot(reference, REFERENCE_X, REFERENCE_Y));
    }

    private void addPlayerSlot(int index, int x, int y) {
        addSlot(new Slot(playerInventory, index, x, y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
    }

    public FilterCard initialCard() { return initialCard; }
    public ItemStack editingCard() { return editingCard; }
    public ItemStack referenceItem() { return reference.getItem(0); }
    public boolean isPlayerSlot(int index) { return index >= 0 && index < PLAYER_SLOT_COUNT; }

    private void selectReference(ItemStack stack) {
        getSlot(REFERENCE_SLOT).set(stack);
    }

    @Override public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (player != playerInventory.player) return;
        // Deliberately do not delegate inventory mutation to vanilla. Both sides
        // run this same sample-only operation through normal container clicks.
        if (isPlayerSlot(slotId)) {
            if ((clickType == ClickType.PICKUP && button == 0) || clickType == ClickType.QUICK_MOVE) {
                ItemStack source = getSlot(slotId).getItem();
                if (!source.isEmpty()) selectReference(source);
            }
        } else if (slotId == REFERENCE_SLOT
                && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
            selectReference(ItemStack.EMPTY);
        }
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        clicked(index, 0, ClickType.QUICK_MOVE, player);
        return ItemStack.EMPTY;
    }

    @Override public boolean canDragTo(Slot slot) { return false; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return false; }

    @Override public boolean stillValid(Player player) {
        return player == playerInventory.player
            && playerInventory.selected == cardInventorySlot
            && !editingCard.isEmpty()
            && playerInventory.getItem(cardInventorySlot) == editingCard;
    }

    @Override public void removed(Player player) {
        // A ghost sample is neither returned to the player nor dropped in-world.
        reference.clearContent();
        super.removed(player);
    }
}
