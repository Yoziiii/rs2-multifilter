package dev.rs2multifilter.minecraft;

import dev.rs2multifilter.core.CardWireCodec;
import dev.rs2multifilter.minecraft.menu.CardEditorMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
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
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
            ItemStack card = player.getMainHandItem();
            int cardSlot = player.getInventory().selected;
            var prepared = CardComponents.read(card).forEditing();
            String initial = CardWireCodec.encode(prepared);
            serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new CardEditorMenu(id, inventory, cardSlot, card, prepared),
                Component.literal("过滤升级")), buffer -> {
                    buffer.writeVarInt(cardSlot);
                    buffer.writeUtf(initial, CardWireCodec.MAX_ENCODED_CHARS);
                });
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var card = CardComponents.read(stack);
        lines.add(Component.literal("已保存分类: " + card.categories().size() + " / 64"));
        lines.add(Component.translatable("item.rs2multifilter.filter_card.desc"));
    }
}
