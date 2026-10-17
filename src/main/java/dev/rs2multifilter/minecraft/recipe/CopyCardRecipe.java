package dev.rs2multifilter.minecraft.recipe;

import dev.rs2multifilter.core.CardCopyRules;
import dev.rs2multifilter.minecraft.CardComponents;
import dev.rs2multifilter.minecraft.MultiFilterMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class CopyCardRecipe extends CustomRecipe {
    public CopyCardRecipe(CraftingBookCategory category) { super(category); }

    private static int[] validInputs(CraftingInput input) {
        int a = -1, b = -1;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (!stack.is(MultiFilterMod.FILTER_CARD.get())) return null;
            if (a == -1) a = i;
            else if (b == -1) b = i;
            else return null;
        }
        if (a < 0 || b < 0) return null;
        var one = CardComponents.inspect(input.getItem(a));
        var two = CardComponents.inspect(input.getItem(b));
        if (!one.readable() || !two.readable()
                || CardCopyRules.copyConfiguration(one.requireCard(), two.requireCard()) == null) return null;
        return new int[]{a, b};
    }

    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }

    @Override public boolean matches(CraftingInput input, Level level) {
        return validInputs(input) != null;
    }

    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int[] pos = validInputs(input);
        if (pos == null) return ItemStack.EMPTY;
        var source = CardComponents.read(input.getItem(pos[0])).categories().isEmpty() ? input.getItem(pos[1]) : input.getItem(pos[0]);
        ItemStack result = new ItemStack(MultiFilterMod.FILTER_CARD.get());
        CardComponents.copyConfiguration(source, result);
        return result;
    }

    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remains = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        int[] pos = validInputs(input);
        if (pos == null) return remains;
        for (int p : pos) {
            if (!CardComponents.read(input.getItem(p)).categories().isEmpty()) {
                remains.set(p, input.getItem(p).copy());
            }
        }
        return remains;
    }

    @Override public RecipeSerializer<?> getSerializer() { return MultiFilterMod.COPY_RECIPE.get(); }
}
