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

    /** NeoForge provides this setting; no vanilla renderer injection is needed. */
    public static final class NameField extends EditBox {
        public NameField(net.minecraft.client.gui.Font font, int x, int y, int w, int h, Component label) {
            super(font, x, y, w, h, label);
            setTextShadow(false);
        }
    }

    private int left() { return leftPos; }
    private int top() { return topPos; }

    @Override protected void init() {
        String draft = name != null && displayedCategory == category ? name.getValue()
            : hasCategory() ? current().name() : "";
        super.init();
        name = new NameField(font, left() + NAME.x() + 4, top() + NAME.y() + 5, NAME.width() - 8, 9,
            Component.literal("分类名称"));
        name.setBordered(false);
        name.setTextColor(0xFF404648);
        name.setTextColorUneditable(0xFF858580);
        name.setTooltip(Tooltip.create(Component.literal("编辑分类名称；切换、添加规则或保存时应用")));
        name.setMaxLength(32);
        name.setValue(draft);
        name.setEditable(hasCategory());
        addRenderableWidget(name);
        displayedCategory = category;
        iconButton(PREVIOUS, EditorIcon.PREVIOUS, "上一个分类", () -> moveCategory(-1)).active = hasCategory();
        iconButton(NEXT, EditorIcon.NEXT, "下一个分类", () -> moveCategory(1)).active = hasCategory();
        iconButton(NEW_CATEGORY, EditorIcon.FOLDER, "新建分类", this::addCategory);
        iconButton(DELETE_CATEGORY, EditorIcon.TRASH, "删除当前分类及其规则", this::deleteCategory).active = hasCategory();

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
        includeButton.setIcon(EditorIcon.ADD);
        excludeButton.setIcon(EditorIcon.EXCLUDE);
        updateAddButtons();

        FilterCategory.Mode mode = hasCategory() ? current().mode() : FilterCategory.Mode.ANY;
        modeButton(ANY, "或", FilterCategory.Mode.ANY, mode,
            "任意：满足任一普通词条，且不匹配排除词条");
        modeButton(ALL, "且", FilterCategory.Mode.ALL, mode,
            "全部：满足全部普通词条，且不匹配排除词条");
        modeButton(DENY, "非", FilterCategory.Mode.DENY, mode,
            "黑名单：匹配普通词条的物品会被排除；排除词条始终排除");
        var fuzzyButton = iconButton(FUZZY, fuzzy ? EditorIcon.FUZZY : EditorIcon.EXACT,
            (fuzzy ? "当前：模糊匹配\n" : "当前：精确匹配\n")
                + "模糊：忽略附魔、耐久等数据；精确：这些数据必须相同。切换会转换已有物品规则", this::toggleFuzzy);
        fuzzyButton.setSelected(fuzzy);
        iconButton(ICON, EditorIcon.PICTURE, "将参考物品设为当前分类图标", this::useSampleAsIcon)
            .active = hasCategory() && !sample.isEmpty();
        iconButton(CLEAR_RULES, EditorIcon.CLEAR, "清空当前分类的全部规则，保留分类与参考物品", this::clearRules)
            .active = hasCategory() && !current().rules().isEmpty();
        iconButton(SAVE, EditorIcon.CONFIRM, "保存所有分类至过滤升级", this::saveCard);
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
        button.setIcon(switch (mode) {
            case ANY -> EditorIcon.ANY;
            case ALL -> EditorIcon.ALL;
            case DENY -> EditorIcon.DENY;
        });
        button.setSelected(mode == selected);
        button.active = hasCategory();
    }

    private EditorButton iconButton(Rect r, EditorIcon icon, String hint, Runnable action) {
        var button = button(r, hint, hint, action);
        button.setIcon(icon);
        return button;
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
            delete.setIcon(EditorIcon.TRASH);
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
            message("一个过滤升级最多 64 个分类", true); return;
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
        if (sam…2659 tokens truncated…n    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        super.render(g, mx, my, tick);
        for (var widget : ruleWidgets) widget.render(g, mx, my, tick);
        if (!dragged.isEmpty()) g.renderItem(dragged, mx - 8, my - 8);
        renderTooltip(g, mx, my);
    }

    @Override public boolean isPauseScreen() { return false; }
}
