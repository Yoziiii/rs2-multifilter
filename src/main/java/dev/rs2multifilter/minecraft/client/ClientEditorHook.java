package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.minecraft.CardComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public final class ClientEditorHook {
    private ClientEditorHook() {}
    public static void open(ItemStack stack) {
        Minecraft.getInstance().setScreen(new CardEditorScreen(CardComponents.read(stack)));
    }
}
