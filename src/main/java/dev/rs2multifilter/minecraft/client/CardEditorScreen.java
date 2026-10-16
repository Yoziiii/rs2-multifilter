package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.*;
import dev.rs2multifilter.minecraft.network.SaveCardPayload;
import dev.rs2multifilter.minecraft.menu.CardEditorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Classic compact RS filter configuration with Create 6.0-style inline attribute
 * selection: sample ghost slot, scrollable attributes, +/- each rule, OR/AND/DENY.
 * All editing happens on ONE screen; no secondary attribute picker screen.
 */
public final class CardEditorScreen extends AbstractContainerScreen<CardEditorMenu> {
    private static final int W = CardEditorMenu.WIDTH;
    private static final int H = CardEditorMenu.HEIGHT;
    private static final int ATTRIBUTE_ROWS = 5;
    private static final int RULE_ROWS = 3;
    private FilterCard working;
    private int category = -1;
    private int attributePage;
    private int rulePage;
    private ItemStack sample = ItemStack.EMPTY;
    private ItemStack dragged = ItemStack.EMPTY;
    private boolean fuzzy = true;
    private EditBox name;
    private String notice = "";
    private boolean isError;
    private final List<AttributeOption> attributes = new ArrayList<>();

    private record AttributeOption(Component title, FilterRule.Kind kind, String value) {}

    public CardEditorScreen(CardEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
        this.working = menu.initialCard();
        if (!working.categories().isEmpty()) category = 0;
    }

    private int left() { return leftPos; }
    private int top() { return topPos; }

    @Override protected void init() {
        super.init();
        int x = left(), y = top();
        name = new EditBox(font, x + 49, y + 28, 119, 18,
            Component.literal("分类名称"));
        name.setMaxLength(32);
        addRenderableWidget(name);
        if (hasCategory()) name.setValue(current().name());
        else { name.setValue(""); name.setEditable(false); }

        addButton(x + 171, y + 28, 18, 18, "‹", "上一个分类", () -> moveCategory(-1));
        addButton(x + 190, y + 28, 18, 18, "›", "下一个分类", () -> moveCategory(1));
        addButton(x + 211, y + 28, 55, 18, "+ 分类", "新建分类", this::addCategory);
        addButton(x + 269, y + 28, 48, 18, "删除", "删除当前分类", this::deleteCategory);
        addButton(x + 320, y + 27, 58, 20, "保存", "保存所有分类至过滤卡", this::saveCard);

        addButton(x + 107, y + 77, 70, 18, "设为图标", "使用样本物品的图标",
            this::useSampleAsIcon);
        addButton(x + 11, y + 213, 166, 20,
            fuzzy ? "物品匹配：模糊 ✓" : "物品匹配：精确 ✓",
            "模糊：忽略附魔、耐久和组件数据；精确：这些数据必须完全相同",
            this::toggleFuzzy);
        addButton(x + 11, y + 237, 166, 19, "清空参考物品",
            "仅清除参考物品，不删除已添加的规则",
            this::clearReference);

        FilterCategory.Mode currentMode = hasCategory()
            ? current().mode() : FilterCategory.Mode.ANY;
        addMode(x + 191, y + 57, 58, "任意", FilterCategory.Mode.ANY, currentMode);
        addMode(x + 251, y + 57, 59, "全部", FilterCategory.Mode.ALL, currentMode);
        addMode(x + 312, y + 57, 65, "黑名单", FilterCategory.Mode.DENY, currentMode);

        final int maxAttributesPage = maxPage(attributes.size(), ATTRIBUTE_ROWS);
        attributePage = Math.max(0, Math.min(attributePage, maxAttributesPage));
        addButton(x + 343, y + 78, 17, 15, "‹", "上一页属性",
            () -> pageAttributes(-1));
        addButton(x + 361, y + 78, 17, 15, "›", "下一页属性",
            () -> pageAttributes(1));
        for (int index = 0; index < ATTRIBUTE_ROWS; index++) {
            int item = attributePage * ATTRIBUTE_ROWS + index;
            if (item >= attributes.size()) break;
            AttributeOption option = attributes.get(item);
            final int yy = y + 96 + 18 * index;
            Component full = option.title();
            addButton(x + 191, yy, 142, 17, shorten(full.getString(), 22),
                full.getString() + " (从参考物品识别)", () -> addRule(option, false));
            addButton(x + 336, yy, 20, 17, "+", "添加包含条件", () -> addRule(option, false));
            addButton(x + 358, yy, 20, 17, "−", "添加排除条件", () -> addRule(option, true));
        }

        if (hasCategory()) {
            List<FilterRule> rules = current().rules();
            rulePage = Math.max(0, Math.min(rulePage, maxPage(rules.size(), RULE_ROWS)));
            for (int index = 0; index < RULE_ROWS; index++) {
                int target = rulePage * RULE_ROWS + index;
                if (target >= rules.size()) break;
                FilterRule rule = rules.get(target);
                String label = (rule.exclude() ? "− " : "+ ") + ruleDescription(rule);
                int yy = y + 209 + index * 17;
                addButton(x + 191, yy, 162, 16, shorten(label, 22),
                    label + "（点击可查看规则，右侧 × 删除）", () -> message(label, false));
                addButton(x + 355, yy, 23, 16, "×", "删除这条规则",
                    () -> deleteRule(target));
            }
        }
        addButton(x + 333, y + 190, 21, 16, "‹", "上一页已选规则", () -> pageRules(-1));
        addButton(x + 356, y + 190, 22, 16, "›", "下一页已选规则", () -> pageRules(1));
    }

    private void addButton(int x, int y, int w, int h, String label,
                           String tooltip, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
            .bounds(x, y, w, h)
            .tooltip(Tooltip.create(Component.literal(tooltip))).build());
    }
    private void addMode(int x, int y, int w, String title,
                         FilterCategory.Mode mode, FilterCategory.Mode active) {
        addButton(x, y, w, 18, (active == mode ? "● " : "○ ") + title,
            switch (mode) {
                case ANY -> "任意一条包含规则符合即可显示";
                case ALL -> "必须符合全部包含规则才显示";
                case DENY -> "匹配包含规则的物品会被排除";
            }, () -> changeMode(mode));
    }
    private boolean hasCategory() {
        return category >= 0 && category < working.categories().size();
    }
    private FilterCategory current() { return working.categories().get(category); }
    private void message(String text, boolean error) { notice = text; isError = error; }
    private static int maxPage(int size, int pageSize) { return Math.max(0, (size - 1) / pageSize); }
    private static String shorten(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, Math.max(0,limit - 1)) + "…";
    }

    private boolean commitName() {
        if (!hasCategory()) return true;
        String changed = name.getValue().trim();
        try {
            var before = current();
            if (!changed.equals(before.name()))
                replace(new FilterCategory(changed, before.iconItemId(),
                    before.rules(), before.mode()));
            return true;
        } catch (IllegalArgumentException e) {
            message("分类名称不能为空或超过 32 字", true);
            return false;
        }
    }

    private void replace(FilterCategory next) {
        List<FilterCategory> list = new ArrayList<>(working.categories());
        list.set(category, next);
        working = new FilterCard(FilterCard.VERSION, list);
    }
    private void addCategory() {
        if (!commitName()) return;
        if (working.categories().size() >= FilterCard.MAX_PER_CARD) {
            message("一张过滤卡最多 64 个分类", true); return;
        }
        String icon = sample.isEmpty() ? "minecraft:paper" : itemId(sample).toString();
        working = working.withCategory(new FilterCategory(
            "分类 " + (working.categories().size()+1), icon, List.of()));
        category = working.categories().size()-1;
        rulePage = 0;
        message("选择背包中的物品，拖到参考槽，点击右侧属性 + 添加", false);
        rebuildWidgets();
    }
    private void deleteCategory() {
        if (!hasCategory()) return;
        List<FilterCategory> list = new ArrayList<>(working.categories());
        list.remove(category);
        working = new FilterCard(FilterCard.VERSION, list);
        category = list.isEmpty() ? -1 : Math.min(category, list.size()-1);
        rulePage = 0;
        rebuildWidgets();
    }
    private void moveCategory(int direction) {
        if (!commitName() || working.categories().isEmpty()) return;
        category = Math.floorMod(category + direction, working.categories().size());
        rulePage = 0;
        rebuildWidgets();
    }
    private void changeMode(FilterCategory.Mode mode) {
        if (!hasCategory() || !commitName()) return;
        FilterCategory old = current();
        replace(new FilterCategory(old.name(), old.iconItemId(), old.rules(), mode));
        rebuildWidgets();
    }
    private void toggleFuzzy() {
        if (!commitName()) return;
        fuzzy = !fuzzy;
        // When possible, update rules for the current reference item as well,
        // so toggling fuzzy mode is not silently limited to future new rules.
        if (hasCategory()) {
            List<FilterRule> changed = new ArrayList<>();
            String sampleId = sample.isEmpty() ? null : itemId(sample).toString();
            String exact = !fuzzy && sampleId != null ? ExactItemBridge.encode(sample) : null;
            int converted = 0;
            for (FilterRule rule : current().rules()) {
                FilterRule next = rule;
                if (fuzzy && rule.kind() == FilterRule.Kind.EXACT_ITEM) {
                    String id = ExactItemBridge.registryId(rule.value());
                    if (id != null) {
                        next = new FilterRule(FilterRule.Kind.ITEM, id, rule.exclude());
                        converted++;
                    }
                } else if (!fuzzy && rule.kind() == FilterRule.Kind.ITEM
                        && sampleId != null && sampleId.equals(rule.value()) && exact != null) {
                    next = new FilterRule(FilterRule.Kind.EXACT_ITEM, exact, rule.exclude());
                    converted++;
                }
                if (!changed.contains(next)) changed.add(next);
            }
            FilterCategory old = current();
            replace(new FilterCategory(old.name(), old.iconItemId(), changed, old.mode()));
            message((fuzzy ? "模糊匹配" : "精确匹配") + "；更新了 " + converted + " 条物品规则", false);
        }
        attributePage = 0;
        refreshOptions();
        rebuildWidgets();
    }

    private void useSampleAsIcon() {
        if (!hasCategory() || sample.isEmpty() || !commitName()) {
            message("先选择分类和参考物品", true); return;
        }
        FilterCategory old = current();
        replace(new FilterCategory(old.name(), itemId(sample).toString(),
            old.rules(), old.mode()));
        message("分类图标已更换", false);
    }
    private void addRule(AttributeOption option, boolean inverse) {
        if (!hasCategory() || !commitName()) {
            message("先新建分类", true); return;
        }
        FilterRule rule;
        try { rule = new FilterRule(option.kind(), option.value(), inverse); }
        catch (IllegalArgumentException e) { message("属性数据无效：" + e.getMessage(), true); return; }
        List<FilterRule> list = new ArrayList<>(current().rules());
        if (list.contains(rule)) { message("这条规则已经存在", false); return; }
        if (list.size() >= 256) { message("分类最多 256 条规则", true); return; }
        list.add(rule);
        var old = current();
        replace(new FilterCategory(old.name(), old.iconItemId(), list, old.mode()));
        rulePage = maxPage(list.size(), RULE_ROWS);
        message((inverse ? "排除：" : "包含：") + option.title().getString(), false);
        rebuildWidgets();
    }
    private void deleteRule(int ruleIndex) {
        if (!hasCategory() || !commitName()) return;
        List<FilterRule> list = new ArrayList<>(current().rules());
        if (ruleIndex < 0 || ruleIndex >= list.size()) return;
        list.remove(ruleIndex);
        var old = current();
        replace(new FilterCategory(old.name(), old.iconItemId(), list, old.mode()));
        rebuildWidgets();
    }
    private void pageAttributes(int delta) {
        attributePage = Math.max(0, Math.min(maxPage(attributes.size(), ATTRIBUTE_ROWS),
            attributePage + delta));
        rebuildWidgets();
    }
    private void pageRules(int delta) {
        if (!hasCategory()) return;
        rulePage = Math.max(0, Math.min(maxPage(current().rules().size(), RULE_ROWS),
            rulePage + delta));
        rebuildWidgets();
    }
    private static ResourceLocation itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private void setSample(ItemStack next) {
        if (ItemStack.matches(sample, next)) return;
        String pendingName = name == null ? null : name.getValue();
        sample = next.isEmpty() ? ItemStack.EMPTY : next.copyWithCount(1);
        attributePage = 0;
        refreshOptions();
        rebuildWidgets();
        if (pendingName != null && hasCategory()) name.setValue(pendingName);
        message(sample.isEmpty() ? "参考槽已清空"
            : "已放入参考物品：" + sample.getHoverName().getString(), false);
    }

    private void refreshOptions() {
        attributes.clear();
        if (sample.isEmpty()) return;
        ResourceLocation id = itemId(sample);
        String modName = ModList.get().getModContainerById(id.getNamespace())
            .map(m -> m.getModInfo().getDisplayName()).orElse(id.getNamespace());
        attributes.add(new AttributeOption(Component.literal("所属模组：" + modName),
            FilterRule.Kind.MOD, id.getNamespace()));
        // By default, same ID means the same type of item regardless of
        // enchantments, durability and other item components (fuzzy matching).
        if (fuzzy) {
            attributes.add(new AttributeOption(Component.literal("物品种类：" + sample.getHoverName().getString()),
                FilterRule.Kind.ITEM, id.toString()));
        } else {
            String encoded = ExactItemBridge.encode(sample);
            if (encoded != null)
                attributes.add(new AttributeOption(Component.literal("完全相同：" + sample.getHoverName().getString()),
                    FilterRule.Kind.EXACT_ITEM, encoded));
        }
        sample.getTags().map(tag -> new AttributeOption(
            Component.literal("标签：" + tag.location()), FilterRule.Kind.TAG,
            tag.location().toString()))
            .sorted(Comparator.comparing(a -> a.title().getString()))
            .forEach(attributes::add);
        if (sample.has(net.minecraft.core.component.DataComponents.FOOD))
            attributes.add(new AttributeOption(Component.literal("可食用"),
                FilterRule.Kind.FOOD, "true"));
        if (sample.isDamageableItem())
            attributes.add(new AttributeOption(Component.literal("可损坏"),
                FilterRule.Kind.DAMAGEABLE, "true"));
        if (sample.isDamaged())
            attributes.add(new AttributeOption(Component.literal("已损坏"),
                FilterRule.Kind.DAMAGED, "true"));
        if (sample.isEnchanted())
            attributes.add(new AttributeOption(Component.literal("已附魔"),
                FilterRule.Kind.ENCHANTED, "true"));

        var fluid = FluidSampleSupport.findFluid(sample);
        if (fluid != null) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            attributes.add(new AttributeOption(Component.literal("流体：" + fluidId),
                FilterRule.Kind.FLUID, fluidId.toString()));
            fluid.defaultFluidState().getTags().map(tag -> new AttributeOption(
                Component.literal("流体标签：" + tag.location()),
                FilterRule.Kind.FLUID_TAG, tag.location().toString()))
                .forEach(attributes::add);
        }
        for (var attribute : CreateAttributeBridge.getSampleAttributes(sample)) {
            attributes.add(new AttributeOption(Component.literal(attribute.label()),
                FilterRule.Kind.CREATE_ATTRIBUTE, attribute.value()));
        }
    }

    private static String ruleDescription(FilterRule rule) {
        return switch (rule.kind()) {
            case MOD -> "模组：" + ModList.get().getModContainerById(rule.value())
                .map(m -> m.getModInfo().getDisplayName()).orElse(rule.value());
            case ITEM -> "物品：" + rule.value() + "（模糊）";
            case EXACT_ITEM -> ExactItemBridge.describe(rule.value());
            case TAG -> "标签：" + rule.value();
            case FLUID -> "流体：" + rule.value();
            case FLUID_TAG -> "流体标签：" + rule.value();
            case FOOD -> "可食用";
            case DAMAGEABLE -> "可损坏";
            case DAMAGED -> "已损坏";
            case ENCHANTED -> "已附魔";
            case CREATE_ATTRIBUTE -> CreateAttributeBridge.describe(rule.value());
        };
    }

    private void saveCard() {
        if (!commitName()) return;
        try {
            PacketDistributor.sendToServer(new SaveCardPayload(menu.containerId, CardWireCodec.encode(working)));
        } catch (RuntimeException ex) {
            message("保存失败：" + ex.getMessage(), true);
        }
    }

    private void clearReference() {
        if (minecraft == null || minecraft.gameMode == null || minecraft.player == null) return;
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId,
            CardEditorMenu.REFERENCE_SLOT, 0, ClickType.PICKUP, minecraft.player);
        setSample(menu.referenceItem());
    }

    @Override protected void containerTick() {
        super.containerTick();
        setSample(menu.referenceItem());
    }

    @Override protected void slotClicked(Slot slot, int slotId, int button, ClickType type) {
        super.slotClicked(slot, slotId, button, type);
        if (slot != null && menu.isPlayerSlot(slotId) && button == 0 && type == ClickType.PICKUP) {
            // Cosmetic drag only: the real menu cursor never receives this copy.
            dragged = slot.getItem().copyWithCount(1);
        }
        setSample(menu.referenceItem());
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean result = super.mouseReleased(mouseX, mouseY, button);
        dragged = ItemStack.EMPTY;
        return result;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (name != null && name.isFocused() && keyCode != 256)
            return name.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY,
                                           double deltaX, double deltaY) {
        if (mouseX >= left()+190 && mouseX <= left()+378
            && mouseY >= top()+94 && mouseY <= top()+187) {
            pageAttributes(deltaY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {
        // Keep the existing non-blurred background when using a container screen.
        g.fill(0, 0, width, height, 0xA8000000);
        renderBg(g, tick, mx, my);
    }

    @Override protected void renderBg(GuiGraphics g, float tick, int mx, int my) {
        int x = left(), y = top();
        g.fill(x-4, y-4, x+W+4, y+H+4, 0xFF151A20);
        g.fill(x+4, y+53, x+184, y+263, 0xFF252B31);
        g.fill(x+186, y+53, x+383, y+263, 0xFF252B31);
        g.drawCenteredString(font, "精致存储 · 过滤升级", width/2, y+8, 0xFFF4F4F4);
        g.drawString(font, "分类名", x+10, y+33, 0xFFDDDDDD);
        g.drawString(font, hasCategory()
            ? (category+1)+"/"+working.categories().size() : "尚无分类",
            x+12, y+55, 0xFFADBBC7);
        g.drawString(font, "参考槽", x+48, y+81, 0xFFE1E4E6);
        g.fill(x+17,y+75,x+43,y+101,0xFF6B7680);
        g.fill(x+20,y+78,x+40,y+98,0xFF101417);
        if (hasCategory()) {
            ResourceLocation iconId = ResourceLocation.tryParse(current().iconItemId());
            ItemStack iconStack = iconId != null && BuiltInRegistries.ITEM.containsKey(iconId)
                ? new ItemStack(BuiltInRegistries.ITEM.get(iconId)) : new ItemStack(Items.PAPER);
            g.renderItem(iconStack,x+80,y+77);
        }
        g.drawString(font, sample.isEmpty() ? "从背包点选/拖入参考槽"
            : shorten(sample.getHoverName().getString(), 24),
            x+11, y+109, 0xFFDEE4E9);
        g.drawString(font, "玩家背包（仅作参考，不消耗物品）",
            x+11, y+124, 0xFFADBBC7);
        for (int i = 0; i < CardEditorMenu.PLAYER_SLOT_COUNT; i++) {
            Slot slot = menu.getSlot(i);
            int ix = x + slot.x, iy = y + slot.y;
            g.fill(ix-1, iy-1, ix+17, iy+17, 0xFF414A52);
        }
        g.drawString(font, "过滤模式", x+191, y+46, 0xFFBFC9D1);
        g.drawString(font, "参考物品的属性", x+191, y+81, 0xFFE5E5E5);
        g.drawString(font, "已选条件 " + (hasCategory()?current().rules().size():0),
            x+191, y+193, 0xFFE5E5E5);
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        // Labels are drawn above with the existing layout.
    }

    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        super.render(g, mx, my, tick);
        if (!dragged.isEmpty()) g.renderItem(dragged, mx-8, my-8);
        int x = left(), y = top();
        g.drawString(font, shorten(notice, 57), x+8, y+263,
            isError?0xFFFF7777:0xFF80D7AA);
        renderTooltip(g, mx, my);
    }

    @Override public boolean isPauseScreen() { return false; }
}
