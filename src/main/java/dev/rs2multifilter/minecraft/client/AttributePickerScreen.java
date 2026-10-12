package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A sample-driven selector inspired by Create's Attribute Filter controls. */
public final class AttributePickerScreen extends Screen {
    private static final int PAGE_SIZE = 8;
    private final Screen parent;
    private final ItemStack sample;
    private final Consumer<FilterRule> onPick;
    private final List<Option> options = new ArrayList<>();
    private int page;
    private boolean inverted;

    private record Option(String label, FilterRule.Kind kind, String value) {}

    public AttributePickerScreen(Screen parent, ItemStack sample, Consumer<FilterRule> onPick) {
        super(Component.literal("从样本选择属性"));
        this.parent = parent;
        this.sample = sample.copy();
        this.onPick = onPick;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(sample.getItem());
        String modName = ModList.get().getModContainerById(id.getNamespace())
            .map(m -> m.getModInfo().getDisplayName()).orElse(id.getNamespace());
        options.add(new Option("所属模组: " + modName, FilterRule.Kind.MOD, id.getNamespace()));
        options.add(new Option("仅这个物品: " + sample.getHoverName().getString(), FilterRule.Kind.ITEM, id.toString()));
        sample.getTags().map(t -> new Option("物品标签: " + t.location(), FilterRule.Kind.TAG,
            t.location().toString())).sorted(java.util.Comparator.comparing(Option::label)).forEach(options::add);
        if (sample.has(DataComponents.FOOD))
            options.add(new Option("物品属性: 可以食用", FilterRule.Kind.FOOD, "true"));
        if (sample.isDamageableItem())
            options.add(new Option("物品属性: 可损坏", FilterRule.Kind.DAMAGEABLE, "true"));
        if (sample.isDamaged())
            options.add(new Option("物品属性: 已损坏", FilterRule.Kind.DAMAGED, "true"));
        if (sample.isEnchanted())
            options.add(new Option("物品属性: 已附魔", FilterRule.Kind.ENCHANTED, "true"));
        var containedFluid = FluidSampleSupport.findFluid(sample);
        if (containedFluid != null) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(containedFluid);
            options.add(new Option("流体: " + fluidId, FilterRule.Kind.FLUID, fluidId.toString()));
            containedFluid.defaultFluidState().getTags()
                .map(tag -> new Option("流体标签: " + tag.location(),
                    FilterRule.Kind.FLUID_TAG, tag.location().toString()))
                .sorted(java.util.Comparator.comparing(Option::label))
                .forEach(options::add);
        }
        String precise = ExactItemBridge.encode(sample);
        if (precise != null) {
            options.add(new Option("物品属性: 严格匹配附加数据", FilterRule.Kind.EXACT_ITEM, precise));
        }
        for (var attribute : CreateAttributeBridge.getSampleAttributes(sample)) {
            options.add(new Option(attribute.label(), FilterRule.Kind.CREATE_ATTRIBUTE, attribute.value()));
        }
    }

    @Override protected void init() {
        int x = width / 2 - 155;
        int y = height / 2 - 125;
        addRenderableWidget(Button.builder(
            Component.literal(inverted ? "条件: 排除" : "条件: 包含"), b -> {
                inverted = !inverted; rebuildWidgets();
            }).bounds(x, y - 24, 310, 20).build());
        int count = options.size();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int selected = page * PAGE_SIZE + i;
            if (selected >= count) break;
            Option option = options.get(selected);
            addRenderableWidget(Button.builder(Component.literal(option.label()), b -> {
                onPick.accept(new FilterRule(option.kind(), option.value(), inverted));
                Minecraft.getInstance().setScreen(parent);
            }).bounds(x, y + i * 23, 310, 21).build());
        }
        addRenderableWidget(Button.builder(Component.literal("‹ 上一页"), b -> {
            page = Math.max(0, page - 1); rebuildWidgets();
        }).bounds(x, y + 189, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
            .bounds(x + 108, y + 189, 94, 20).build());
        addRenderableWidget(Button.builder(Component.literal("下一页 ›"), b -> {
            page = Math.min(Math.max(0, (options.size() - 1) / PAGE_SIZE), page + 1);
            rebuildWidgets();
        }).bounds(x + 215, y + 189, 95, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        renderBackground(g, mx, my, tick);
        g.drawCenteredString(font, "选择样本属性 · " + sample.getHoverName().getString(),
            width / 2, height / 2 - 169, 0xFFFFFF);
        g.renderItem(sample, width / 2 - 8, height / 2 - 151);
        super.render(g, mx, my, tick);
        g.drawCenteredString(font, "第 " + (page + 1) + " / " +
            Math.max(1, (options.size() + PAGE_SIZE - 1) / PAGE_SIZE) + " 页",
            width / 2, height / 2 + 95, 0xCCCCCC);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
