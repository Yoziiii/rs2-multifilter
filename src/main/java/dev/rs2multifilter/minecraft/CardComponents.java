package dev.rs2multifilter.minecraft;

import dev.rs2multifilter.core.CardWireCodec;
import dev.rs2multifilter.core.FilterCard;
import dev.rs2multifilter.core.CardReadResult;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CardComponents {
    private CardComponents() {}

    public static CardReadResult inspect(ItemStack stack) {
        return CardReadResult.inspect(stack.get(MultiFilterMod.CARD_DATA.get()));
    }

    public static FilterCard read(ItemStack stack) { return inspect(stack).requireCard(); }

    public static void write(ItemStack stack, FilterCard card) {
        inspect(stack).requireCard(); // No implicit recovery/clear of unreadable originals.
        String encoded = CardWireCodec.encode(card); // Validate fully BEFORE mutation.
        stack.set(MultiFilterMod.CARD_DATA.get(), encoded);
    }

    public static void copyConfiguration(ItemStack source, ItemStack target) {
        var from = inspect(source);
        if (from.requireCard().categories().isEmpty() || !inspect(target).requireCard().categories().isEmpty())
            throw new IllegalArgumentException("复制需要配置来源和可读取的空白目标");
        target.set(MultiFilterMod.CARD_DATA.get(), from.raw());
    }

    public static void reportReadStatus(Player player, ItemStack stack, String action) {
        if (stack.isEmpty()) return;
        var result = inspect(stack);
        if (!result.readable()) reportFailure(player, action, result.problem());
        else if (result.migrationExceedsLimit()) player.displayClientMessage(Component.literal(
            "过滤升级：旧版配置超过 v2 容量，已保留原格式用于" + action
                + "；仍可查看、使用和复制。保存若超限会被拒绝，原始配置已保留。"), false);
    }

    public static void reportFailure(Player player, String action, String reason) {
        String detail = reason == null ? "配置无效" : reason;
        if (detail.length() > 160) detail = detail.substring(0,160) + "…";
        player.displayClientMessage(Component.literal("过滤升级" + action + "失败：" + detail
            + "。原始配置已保留，未覆盖或清空。"), false);
    }
}
