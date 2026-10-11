package dev.rs2multifilter.minecraft;

import dev.rs2multifilter.minecraft.client.ClientEditorHook;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

public final class FilterCardItem extends Item {
    public FilterCardItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && level.isClientSide) ClientEditorHook.open(player.getMainHandItem());
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var card = CardComponents.read(stack);
        lines.add(Component.literal("已保存分类: " + card.categories().size() + " / 64"));
        lines.add(Component.translatable("item.rs2multifilter.filter_card.desc"));
    }
}
