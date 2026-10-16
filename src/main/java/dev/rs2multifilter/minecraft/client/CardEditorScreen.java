package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.*;
import dev.rs2multifilter.minecraft.network.SaveCardPayload;
import dev.rs2multifilter.minecraft.menu.CardEditorMenu;
import dev.rs2multifilter.minecraft.menu.CardEditorLayout;
import dev.rs2multifilter.minecraft.menu.CardEditorLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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

import static dev.rs2multifilter.minecraft.menu.CardEditorLayout.*;

/** Create-style compact configuration above the existing real inventory menu. */
public final class CardEditorScreen extends AbstractContainerScreen<CardEditorMenu> {
    private static final int W = CardEditorLayout.WIDTH, H = CardEditorLayout.HEIGHT;
    private static final int RULE_ROWS = 5, RULE_ROW_HEIGHT = 14;
    private FilterCard working;
    private int category = -1, displayedCategory = -2, attributeIndex, ruleOffset;
    private ItemStack sample = ItemStack.EMPTY, dragged = ItemStack.EMPTY;
    private boolean fuzzy = true, isError, ruleMouseDown;
    private EditBox name;
    private AttributeSelector selector;
    private EditorButton includeButton, excludeButton;
    private String notice = "";
    private int noticeTicks;
    private Rect ruleArea;
    private final List<AbstractWidget> ruleWidgets = new ArrayList<>();
    private final List<AttributeOption> attributes = new ArrayList<>();

    private record AttributeOption(Component title, FilterRule.Kind kind, String value) {}

    public CardEditorScreen(CardEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
        working = menu.initialCard();
        if (!working.categories().isEmpty()) category = 0;
    }

    private int left() { return leftPos; }
    private int top() { return topPos; }

    @Override protected void init() {
        String draft = name != null && displayedCategory == category ? name.getValue()
            : hasCategory() ? current().name() : "";
        super.init();
        name = new EditBox(font, left() + NAME.x(), top() + NAME.y(), NAME.width(), NAME.height(),
            Component.literal("分类名称"));
        name.setMaxLength(32);
        name.setValue(draft);
        name.setEditable(hasCategory());
        addRenderableWidget(name);
        displayedCategory = category;
        button(PREVIOUS, "‹", "上一个分类", () -> moveCategory(-1)).active = hasCategory();
        button(NEXT, "›", "下一个分类", () -> moveCategory(1)).active = hasCategory();
        button(NEW_CATEGORY, "+", "新建分类", this::addCategory);
        button(DELETE_CATEGORY, "删", "删除当前分类及其规则", this::deleteCategory).active = hasCategory();

        selector = new AttributeSelector(left() + ATTRIBUTE.x(), top() + ATTRIBUTE.y(),
            ATTRIBUTE.width(), ATTRIBUTE.height(), this::copy, index -> {
                attributeIndex = index;
                updateAddButtons();
            });
        selector.setOptions(attributes.stream().map(option -> new AttributeSelector.Entry(option.title(),
            RuleClipboard.describe(option.kind(), option.title().getString(), option.value(), null),
            ruleTooltip(option.kind(), option.title().getString(), option.value(), null))).toList(), attributeIndex);
        attributeIndex = selector.selectedIndex();
        addRenderableWidget(selector);
        includeButton = button(INCLUDE, "+", "添加普通词条：按当前过滤模式参与匹配", () -> addSelected(false));
        excludeButton = button(EXCLUDE, "+!", "添加反向词条：排除匹配该属性的物品", () -> addSelected(true));
        includeButton.setSelected(true);
        excludeButton.setSelected(true);
        excludeButton.setAccent(0xFF9A6852);
        updateAddButtons();

        FilterCategory.Mode mode = hasCategory() ? current().mode() : FilterCategory.Mode.ANY;
        modeButton(ANY, "或", FilterCategory.Mode.ANY, mode,
            "任意：满足任一普通词条，且不匹配排除词条");
        modeButton(ALL, "且", FilterCategory.Mode.ALL, mode,
            "全部：满足全部普通词条，且不匹配排除词条");
        modeButton(DENY, "非", FilterCategory.Mode.DENY, mode,
            "黑名单：匹配普通词条的物品会被排除；排除词条始终排除");
        var fuzzyButton = button(FUZZY, fuzzy ? "模糊" : "精确",
            "模糊忽略附魔、耐久与组件；精确比较组件。沿用已有规则转换行为", this::toggleFuzzy);
        fuzzyButton.setSelected(fuzzy);
        button(ICON, "图标", "将参考物品设为当前分类图标", this::useSampleAsIcon)
            .active = hasCategory() && !sample.isEmpty();
        button(CLEAR_RULES, "清空", "清空当前分类的全部规则，保留分类与参考物品", this::clearRules)
            .active = hasCategory() && !current().rules().isEmpty();
        button(SAVE, "保存", "保存所有分类至过滤卡", this::saveCard);
        rebuildRulePanel();
    }

    private EditorButton button(Rect r, String label, String hint, Runnable action) {
        var button = new EditorButton(left() + r.x(), top() + r.y(), r.width(), r.height(),
            Component.literal(label), b -> action.run());
        button.setTooltip(Tooltip.create(Component.literal(hint)));
        return addRenderableWidget(button);
    }

    private void modeButton(Rect r, String label, FilterCategory.Mode mode,
                            FilterCategory.Mode selected, String hint) {
        var button = button(r, label, hint, () -> changeMode(mode));
        button.setSelected(mode == selected);
        button.active = hasCategory();
    }

    private void copy(String text) {
        if (minecraft != null) {
            minecraft.keyboardHandler.setClipboard(text);
            message("已复制", false);
        }
    }

    private Component ruleTooltip(FilterRule.Kind kind, String label, String value, Boolean excluded) {
        return Component.literal(String.join("\n", RuleClipboard.tooltip(kind, label, value, excluded)));
    }

    private void addSelected(boolean exclude) {
        if (attributeIndex >= 0 && attributeIndex < attributes.size())
            addRule(attributes.get(attributeIndex), exclude);
    }

    private void updateAddButtons() {
        if (includeButton == null || excludeButton == null) return;
        boolean ready = hasCategory() && attributeIndex >= 0 && attributeIndex < attributes.size();
        includeButton.active = ready;
        excludeButton.active = ready;
        if (ready) {
            var option = attributes.get(attributeIndex);
            includeButton.active = !hasRule(option, false);
            excludeButton.active = !hasRule(option, true);
        }
    }

    private boolean hasRule(AttributeOption option, boolean exclude) {
        return current().rules().stream().anyMatch(rule -> rule.kind() == option.kind()
            && rule.value().equals(option.value()) && rule.exclude() == exclude);
    }

    private void clearRules() {
        if (!hasCategory() || !commitName()) return;
        var old = current();
        replace(new FilterCategory(old.name(), old.iconItemId(), List.of(), old.mode()));
        ruleOffset = 0;
        message("已清空当前分类规则", false);
        rebuildWidgets();
    }

    private void rebuildRulePanel() {
        ruleWidgets.clear();
        ruleArea = new Rect(left() + RULE_LIST.x(), top() + RULE_LIST.y(), RULE_LIST.width(), RULE_LIST.height());
        if (!hasCategory()) return;
        var rules = current().rules();
        ruleOffset = Math.max(0, Math.min(ruleOffset, Math.max(0, rules.size() - RULE_ROWS)));
        for (int row = 0; row < RULE_ROWS && ruleOffset + row < rules.size(); row++) {
            int target = ruleOffset + row;
            FilterRule rule = rules.get(target);
            String title = ruleDescription(rule);
            int yy = ruleArea.y() + row * RULE_ROW_HEIGHT;
            var entry = new RuleEntryButton(ruleArea.x(), yy, ruleArea.width() - 16, 13,
                Component.literal((rule.exclude() ? "− " : "+ ") + title), b -> {},
                RuleClipboard.describe(rule.kind(), title, rule.value(), rule.exclude()), this::copy);
            entry.setCompact(true);
            entry.setAccent(rule.exclude() ? 0xFF9A6852 : 0xFF348C56);
            ruleWidgets.add(entry);
            var delete = new EditorButton(ruleArea.x() + ruleArea.width() - 14, yy, 13, 13,
                Component.literal("×"), b -> deleteRule(target));
            delete.setCompact(true);
            ruleWidgets.add(delete);
        }
    }

    List<Component> candidateTooltipAt(double mx, double my, int visibleEntries) {
        if (selector == null || !selector.isMouseOver(mx, my)) return List.of();
        return selector.hoverLines(sample.isEmpty() ? Component.literal("参考物品") : sample.getHoverName(), visibleEntries);
    }

    private boolean hasCategory() {
        return category >= 0 && category < working.categories().size();
    }
    private FilterCategory current() { return working.categories().get(category); }
    private void message(String text, boolean error) { notice = text; isError = error; noticeTicks = 40; }
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
        ruleOffset = 0;
        displayedCategory = -2;
        message("从背包点选样本，滚轮选择属性，再点击添加", false);
        rebuildWidgets();
    }
    private void deleteCategory() {
        if (!hasCategory()) return;
        List<FilterCategory> list = new ArrayList<>(working.categories());
        list.remove(category);
        working = new FilterCard(FilterCard.VERSION, list);
        category = list.isEmpty() ? -1 : Math.min(category, list.size()-1);
        ruleOffset = 0;
        displayedCategory = -2;
        rebuildWidgets();
    }
    private void moveCategory(int direction) {
        if (!commitName() || working.categories().isEmpty()) return;
        category = Math.floorMod(category + direction, working.categories().size());
        ruleOffset = 0;
        displayedCategory = -2;
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
        attributeIndex = 0;
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
        ruleOffset = Math.max(0, list.size() - RULE_ROWS);
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
    private static ResourceLocation itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private void setSample(ItemStack next) {
        if (ItemStack.matches(sample, next)) return;
        String pendingName = name == null ? null : name.getValue();
        sample = next.isEmpty() ? ItemStack.EMPTY : next.copyWithCount(1);
        attributeIndex = 0;
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
        if (noticeTicks > 0) noticeTicks--;
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
        if (ruleMouseDown) {
            ruleMouseDown = false;
            for (var widget : ruleWidgets) widget.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        boolean result = super.mouseReleased(mouseX, mouseY, button);
        dragged = ItemStack.EMPTY;
        return result;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (new Rect(left() + RULE_PANEL.x(), top() + RULE_PANEL.y(), RULE_PANEL.width(), RULE_PANEL.height()).contains(mx, my)) {
            ruleMouseDown = true;
            for (var widget : ruleWidgets) if (widget.mouseClicked(mx, my, button)) return true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return ruleMouseDown || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (ruleArea != null && ruleArea.contains(mx, my) && dy != 0) {
            int max = hasCategory() ? Math.max(0, current().rules().size() - RULE_ROWS) : 0;
            int next = Math.max(0, Math.min(max, ruleOffset + (dy < 0 ? 1 : -1)));
            if (next != ruleOffset) {
                ruleOffset = next;
                rebuildRulePanel();
            }
            return true;
        }
        return selector != null && selector.mouseScrolled(mx, my, dx, dy);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (name != null && name.isFocused() && keyCode != 256)
            return name.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {
        g.fill(0, 0, width, height, 0xA8000000);
        renderBg(g, tick, mx, my);
    }

    @Override protected void renderBg(GuiGraphics g, float tick, int mx, int my) {
        int x = left(), y = top();
        EditorButton.frame(g, x, y, W, CONFIG_HEIGHT, 0xFFC7B99A);
        for (int yy = y + 15; yy < y + 84; yy += 2)
            for (int xx = x + 3; xx < x + W - 3; xx += 2)
                if (((xx - x + yy - y) / 2 & 1) == 0)
                    g.fill(xx, yy, xx + 1, yy + 1, 0xFFB5A88D);
        EditorButton.frame(g, x, y, W, 15, 0xFFE6B95E);
        String title = "属性过滤卡" + (hasCategory() ? " · " + (category + 1) + "/" + working.categories().size() : "");
        g.drawCenteredString(font, title, x + W / 2, y + 4, 0xFF593A27);
        if (hasCategory()) {
            ResourceLocation icon = ResourceLocation.tryParse(current().iconItemId());
            ItemStack stack = icon != null && BuiltInRegistries.ITEM.containsKey(icon)
                ? new ItemStack(BuiltInRegistries.ITEM.get(icon)) : new ItemStack(Items.PAPER);
            g.pose().pushPose();
            g.pose().translate(x + 5, y + 2, 0);
            g.pose().scale(.625f, .625f, .625f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }
        EditorButton.frame(g, x + 1, y + 84, W - 2, CONFIG_HEIGHT - 85, 0xFFC6C6C4);
        g.fill(x + 84, y + 92, x + 85, y + 112, 0xFF858580);
        g.fill(x + 191, y + 92, x + 192, y + 112, 0xFF858580);
        slotFrame(g, x + REFERENCE_X, y + REFERENCE_Y);
        if (noticeTicks > 0)
            g.drawString(font, EditorText.fit(notice, W - 52, font::width), x + 42, y + 75,
                isError ? 0xFF9A2222 : 0xFF504B42, false);

        EditorButton.frame(g, x + INVENTORY.x(), y + INVENTORY.y(), INVENTORY.width(), INVENTORY.height(), 0xFFC6C6C6);
        g.drawString(font, "物品栏", x + INVENTORY.x() + 8, y + INVENTORY.y() + 6, 0xFF484848, false);
        for (int i = 0; i < CardEditorMenu.PLAYER_SLOT_COUNT; i++) {
            Slot slot = menu.getSlot(i);
            slotFrame(g, x + slot.x, y + slot.y);
        }
        EditorButton.frame(g, x + RULE_PANEL.x(), y + RULE_PANEL.y(), RULE_PANEL.width(), RULE_PANEL.height(), 0xFFE4DDC8);
        int count = hasCategory() ? current().rules().size() : 0;
        g.drawString(font, "已选规则 " + count, x + RULE_PANEL.x() + 5, y + RULE_PANEL.y() + 6, 0xFF504B42, false);
        if (count == 0) g.drawString(font, "尚无规则", x + RULE_LIST.x() + 5, y + RULE_LIST.y() + 4, 0xFF787369, false);
        else g.drawString(font, (ruleOffset + 1) + "–" + Math.min(count, ruleOffset + RULE_ROWS) + "/" + count,
            x + RULE_PANEL.x() + 5, y + RULE_PANEL.y() + 96, 0xFF777166, false);
    }

    private static void slotFrame(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFFF3F1E9);
        g.fill(x - 1, y - 1, x + 17, y, 0xFF4A4944);
        g.fill(x - 1, y - 1, x, y + 17, 0xFF4A4944);
        g.fill(x, y, x + 16, y + 16, 0xFF8B8B85);
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {}

    private java.util.List<net.minecraft.util.FormattedCharSequence> wrapTooltip(List<Component> lines) {
        int pixels = Math.max(80, Math.min(340, width - 24));
        return lines.stream().flatMap(line -> font.split(line, pixels).stream()).toList();
    }

    @Override protected void renderTooltip(GuiGraphics g, int mx, int my) {
        List<Component> candidates = candidateTooltipAt(mx, my, 16);
        if (!candidates.isEmpty()) {
            var wrapped = wrapTooltip(candidates);
            for (int entries = 15; wrapped.size() * 9 > height - 20 && entries >= 3; entries--)
                wrapped = wrapTooltip(candidateTooltipAt(mx, my, entries));
            g.renderTooltip(font, wrapped, mx, my);
            return;
        }
        for (int i = 0; i < ruleWidgets.size(); i++) {
            var widget = ruleWidgets.get(i);
            if (!widget.isMouseOver(mx, my)) continue;
            if ((i & 1) == 1) g.renderComponentTooltip(font, List.of(Component.literal("删除这条规则")), mx, my);
            else if (hasCategory()) {
                var rule = current().rules().get(ruleOffset + i / 2);
                var lines = RuleClipboard.tooltip(rule.kind(), ruleDescription(rule), rule.value(), rule.exclude())
                    .stream().map(Component::literal).map(Component.class::cast).toList();
                g.renderTooltip(font, wrapTooltip(lines), mx, my);
            }
            return;
        }
        if (new Rect(left() + REFERENCE_X - 1, top() + REFERENCE_Y - 1, 18, 18).contains(mx, my)) {
            List<Component> lines = new ArrayList<>();
            if (!sample.isEmpty()) lines.add(sample.getHoverName());
            lines.add(Component.literal("从背包点选样本；点击此槽清空"));
            lines.add(Component.literal("样本不消耗物品，也不永久保存"));
            g.renderComponentTooltip(font, lines, mx, my);
        } else super.renderTooltip(g, mx, my);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        super.render(g, mx, my, tick);
        for (var widget : ruleWidgets) widget.render(g, mx, my, tick);
        if (!dragged.isEmpty()) g.renderItem(dragged, mx - 8, my - 8);
        renderTooltip(g, mx, my);
    }

    @Override public boolean isPauseScreen() { return false; }
}
