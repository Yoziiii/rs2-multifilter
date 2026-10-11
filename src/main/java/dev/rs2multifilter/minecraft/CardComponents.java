package dev.rs2multifilter.minecraft;

import dev.rs2multifilter.core.CardWireCodec;
import dev.rs2multifilter.core.FilterCard;
import net.minecraft.world.item.ItemStack;

public final class CardComponents {
    private CardComponents() {}

    public static FilterCard read(ItemStack stack) {
        String data = stack.get(MultiFilterMod.CARD_DATA.get());
        try { return CardWireCodec.decode(data); }
        catch (IllegalArgumentException e) { return FilterCard.blank(); }
    }

    public static void write(ItemStack stack, FilterCard card) {
        stack.set(MultiFilterMod.CARD_DATA.get(), CardWireCodec.encode(card));
    }
}
