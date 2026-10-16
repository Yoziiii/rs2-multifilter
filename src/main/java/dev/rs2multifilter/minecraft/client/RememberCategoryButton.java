package dev.rs2multifilter.minecraft.client;

import com.refinedmods.refinedstorage.common.support.AbstractBaseScreen;
import com.refinedmods.refinedstorage.common.support.widget.AbstractSideButtonWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Added through RS's sidebar API, which owns placement and exclusion zones. */
public final class RememberCategoryButton extends AbstractSideButtonWidget {
    private final GridCategoryState selection;
    public RememberCategoryButton(GridCategoryState selection) {
        super(button -> selection.toggleRemember());
        this.selection = selection;
        setMessage(getTitle());
    }
    @Override protected ResourceLocation getSprite() { return RsUi.id("set"); }
    @Override protected MutableComponent getTitle() { return Component.literal("记住上一次分类"); }
    @Override protected List<MutableComponent> getSubText() {
        return List.of(Component.literal(selection.remembersCategory() ? "开启：下次恢复上次分类" : "关闭：下次显示全部物品")
            .withStyle(ChatFormatting.GRAY));
    }
    @Override protected Component getHelpText() { return Component.literal("自动保存设置；仅恢复同一世界／服务器内相同过滤配置的分类。"); }
    @Override public void renderWidget(GuiGraphics g, int mx, int my, float tick) {
        g.blitSprite(RsUi.id(isHovered ? "widget/side_button/hovered" : "widget/side_button/base"), getX(), getY(), SIZE, SIZE);
        EditorIcon.PIN.render(g, getX() + 9, getY() + 8,
            selection.remembersCategory() ? 0xFF318EB2 : 0xFF666666);
        if (selection.remembersCategory()) g.fill(getX() + 4, getY() + 14, getX() + 14, getY() + 15, 0xFF318EB2);
        if (isHovered && Minecraft.getInstance().screen instanceof AbstractBaseScreen<?> screen)
            screen.setDeferredTooltip(buildTooltip());
    }
}
