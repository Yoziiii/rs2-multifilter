package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.*;
import dev.rs2multifilter.minecraft.network.SaveCardPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sample-based category editor:
 * choose any inventory item -> add its mod, exact ID or an item tag.
 * One category can contain as many inclusive mod IDs as needed.
 */
public final class CardEditorScreen extends Screen {
    private static final int WIDTH = 414;
    private static final int HEIGHT = 304;
    private static final int RULES_PAGE_SIZE = 5;

    private FilterCard working;
    private int selectedCategory = -1;
    private int rulesPage = 0;
    private ItemStack sample = ItemStack.EMPTY;
    private EditBox nameField;
    private String feedback = "";
    private boolean error = false;
    private boolean invertNextRule = false;

    public CardEditorScreen(FilterCard initial) {
        super(Component.literal("多分类过滤卡"));
        working = initial;
        if (!initial.categories().isEmpty()) selectedCategory = 0;
    }

    private int left() { return (width - WIDTH) / 2; }
    private int top() { return (height - HEIGHT) / 2; }

    @Override
    protected void init() {
        int x = left();
        int y = top();
        nameField = new EditBox(font, x + 57, y + 46, 123, 18, Component.literal("分类名称"));
        nameField.setMaxLength(32);
        addRenderableWidget(nameField);

        addRenderableWidget(Button.builder(Component.literal("‹"), b -> changeCategory(-1))
            .bounds(x + 183, y + 46, 22, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> changeCategory(1))
            .bounds(x + 207, y + 46, 22, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+ 新建分类"), b -> addCategory())
            .bounds(x + 234, y + 46, 85, 18).build());
        addRenderableWidget(Button.builder(Component.literal("删除分类"), b -> removeCategory())
            .bounds(x + 321, y + 46, 82, 18).build());

        addRenderableWidget(Button.builder(Component.literal("模式: " + (selectedCategory < 0 ? "—" : modeName(working.categories().get(selectedCategory).mode()))), b -> cycleMode())
            .bounds(x + 198, y + 66, 201, 18)
            .tooltip(Tooltip.create(Component.literal("任意匹配白名单 / 全匹配白名单 / 黑名单"))).build());
        addRenderableWidget(Button.builder(Component.literal("添加所属模组"), b -> addFromSample(FilterRule.Kind.MOD))
            .bounds(x + 198, y + 88, 201, 19)
            .tooltip(Tooltip.create(Component.literal("包括该模组的所有物品，可在同一分类中连续加入多个模组"))).build());
        addRenderableWidget(Button.builder(Component.literal("仅添加此物品"), b -> addFromSample(FilterRule.Kind.ITEM))
            .bounds(x + 198, y + 111, 201, 19).build());
        addRenderableWidget(Button.builder(Component.literal("选择物品标签…"), b -> pickTag())
            .bounds(x + 198, y + 134, 201, 19).build());
        addRenderableWidget(Button.builder(Component.literal("用样本做分类图标"), b -> setIconFromSample())
            .bounds(x + 198, y + 157, 201, 19).build());

        addRenderableWidget(Button.builder(Component.literal("‹"), b -> {
            rulesPage = Math.max(0, rulesPage - 1);
            rebuildWidgets();
        }).bounds(x + 197, y + 271, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> {
            rulesPage++;
            rebuildWidgets();
        }).bounds(x + 229, y + 271, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal(invertNextRule ? "下一条：排除" : "下一条：包含"), b -> {
            invertNextRule = !invertNextRule; rebuildWidgets();
        }).bounds(x + 7, y + 266, 140, 18).build());
        addRenderableWidget(Button.builder(Component.literal("保存配置到卡片"), b -> save())
            .bounds(x + 265, y + 269, 135, 22).build());

        if (selectedCategory >= 0 && selectedCategory < working.categories().size()) {
            FilterCategory category = working.categories().get(selectedCategory);
            nameField.setValue(category.name());
            List<FilterRule> rules = category.rules();
            int maxPage = Math.max(0, (rules.size() - 1) / RULES_PAGE_SIZE);
            rulesPage = Math.min(rulesPage, maxPage);
            for (int i = 0; i < RULES_PAGE_SIZE; i++) {
                int idx = rulesPage * RULES_PAGE_SIZE + i;
                if (idx >= rules.size()) break;
                FilterRule rule = rules.get(idx);
                String text = (rule.exclude() ? "排除 " : "包含 ") + rule.kind().name().toLowerCase() + " " + rule.value();
                if (text.length() > 29) text = text.substring(0, 28) + "…";
                addRenderableWidget(Button.builder(Component.literal(text + "  ×"), b -> removeRule(idx))
                    .bounds(x + 198, y + 185 + i * 17, 201, 16)
                    .tooltip(Tooltip.create(Component.literal("点击移除规则： " + rule.value()))).build());
            }
        } else {
            nameField.setValue("");
            nameField.setEditable(false);
        }
    }

    private void notice(String message, boolean isError) {
        feedback = message;
        error = isError;
    }

    private boolean storeName() {
        if (selectedCategory < 0 || selectedCategory >= working.categories().size()) return true;
        FilterCategory current = working.categories().get(selectedCategory);
        String name = nameField.getValue().trim();
        try {
            var categories = new ArrayList<>(working.categories());
            categories.set(selectedCategory, new FilterCategory(name, current.iconItemId(), current.rules(), current.mode()));
            working = new FilterCard(FilterCard.VERSION, categories);
            return true;
        } catch (RuntimeException ex) {
            notice(ex.getMessage(), true);
            return false;
        }
    }

    private void changeCategory(int delta) {
        if (!storeName() || working.categories().isEmpty()) return;
        selectedCategory = Math.floorMod(selectedCategory + delta, working.categories().size());
        rulesPage = 0;
        rebuildWidgets();
    }

    private void addCategory() {
        if (!storeName()) return;
        if (working.categories().size() >= FilterCard.MAX_CATEGORIES) {
            notice("最多 64 个分类", true);
            return;
        }
        String icon = sample.isEmpty() ? "minecraft:paper" : itemId(sample).toString();
        working = working.withCategory(new FilterCategory("新分类" + (working.categories().size() + 1), icon, List.of()));
        selectedCategory = working.categories().size() - 1;
        rulesPage = 0;
        notice("新分类已创建：现在点击左侧样本，添加模组或标签", false);
        rebuildWidgets();
    }

    private void removeCategory() {
        if (selectedCategory < 0) return;
        var categories = new ArrayList<>(working.categories());
        categories.remove(selectedCategory);
        working = new FilterCard(FilterCard.VERSION, categories);
        selectedCategory = categories.isEmpty() ? -1 : Math.min(selectedCategory, categories.size() - 1);
        rulesPage = 0;
        rebuildWidgets();
    }

    private static ResourceLocation itemId(ItemStack item) {
        return BuiltInRegistries.ITEM.getKey(item.getItem());
    }

    private void addFromSample(FilterRule.Kind kind) {
        if (selectedCategory < 0) { notice("请先新建分类", true); return; }
        if (sample.isEmpty()) { notice("先点击下方背包里的一个样本物品", true); return; }
        if (!storeName()) return;
        var id = itemId(sample);
        String value = kind == FilterRule.Kind.MOD ? id.getNamespace() : id.toString();
        addRule(new FilterRule(kind, value, invertNextRule));
    }

    private void addRule(FilterRule rule) {
        FilterCategory category = working.categories().get(selectedCategory);
        var rules = new ArrayList<>(category.rules());
        if (rules.contains(rule)) {
            notice("当前分类已包含这条规则", false);
            return;
        }
        if (rules.size() >= 256) { notice("单个分类最多 256 条规则", true); return; }
        rules.add(rule);
        replaceCategory(new FilterCategory(category.name(), category.iconItemId(), rules, category.mode()));
        rulesPage = (rules.size() - 1) / RULES_PAGE_SIZE;
        notice("已添加 " + rule.kind().name().toLowerCase() + " : " + rule.value(), false);
        rebuildWidgets();
    }

    private void removeRule(int ruleIndex) {
        if (!storeName()) return;
        FilterCategory category = working.categories().get(selectedCategory);
        var rules = new ArrayList<>(category.rules());
        if (ruleIndex < 0 || ruleIndex >= rules.size()) return;
        rules.remove(ruleIndex);
        replaceCategory(new FilterCategory(category.name(), category.iconItemId(), rules));
        rebuildWidgets();
    }

    private void replaceCategory(FilterCategory category) {
        var categories = new ArrayList<>(working.categories());
        categories.set(selectedCategory, category);
        working = new FilterCard(FilterCard.VERSION, categories);
    }

    private void setIconFromSample() {
        if (selectedCategory < 0 || sample.isEmpty()) { notice("先选择分类和样本物品", true); return; }
        if (!storeName()) return;
        FilterCategory old = working.categories().get(selectedCategory);
        replaceCategory(new FilterCategory(old.name(), itemId(sample).toString(), old.rules(), old.mode()));
        notice("分类图标已改为 " + sample.getHoverName().getString(), false);
    }

    private void pickTag() {
        if (selectedCategory < 0 || sample.isEmpty()) { notice("先选择分类和样本物品", true); return; }
        if (!storeName()) return;
        List<String> tags = sample.getTags().map(t -> t.location().toString()).sorted().toList();
        if (tags.isEmpty()) { notice("当前样本没有物品标签，可以改用所属模组", true); return; }
        Minecraft.getInstance().setScreen(new TagPickerScreen(this, tags, tag -> {
            addRule(new FilterRule(FilterRule.Kind.TAG, tag, invertNextRule));
        }));
    }

    private static String modeName(FilterCategory.Mode mode) {
        return switch (mode) {
            case ANY -> "任意白名单";
            case ALL -> "全部白名单";
            case DENY -> "黑名单";
        };
    }

    private void cycleMode() {
        if (selectedCategory < 0 || !storeName()) return;
        FilterCategory old = working.categories().get(selectedCategory);
        FilterCategory.Mode[] modes = FilterCategory.Mode.values();
        replaceCategory(new FilterCategory(old.name(), old.iconItemId(), old.rules(),
            modes[(old.mode().ordinal() + 1) % modes.length]));
        rebuildWidgets();
    }

    private void save() {
        if (!storeName()) return;
        try {
            PacketDistributor.sendToServer(new SaveCardPayload(CardWireCodec.encode(working)));
            Minecraft.getInstance().setScreen(null);
        } catch (RuntimeException ex) { notice(ex.getMessage(), true); }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int startX = left() + 13;
        int startY = top() + 183;
        int cellX = (int) (mouseX - startX) / 18;
        int cellY = (int) (mouseY - startY) / 18;
        if (mouseX >= startX && mouseY >= startY && cellX >= 0 && cellX < 9 && cellY >= 0 && cellY < 4
            && mouseX < startX + 162 && mouseY < startY + 72) {
            var player = Minecraft.getInstance().player;
            if (player == null) return true;
            int index = cellY * 9 + cellX;
            ItemStack stack = player.getInventory().items.get(index);
            if (!stack.isEmpty()) {
                sample = stack.copy();
                notice("样本：" + stack.getHoverName().getString(), false);
            }
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        g.fill(x - 4, y - 4, x + WIDTH + 4, y + HEIGHT + 4, 0xDD181818);
        g.fill(x + 5, y + 179, x + 181, y + 260, 0xFF333333);
        g.drawCenteredString(font, "多分类过滤卡 · 物品样本编辑", width / 2, y + 5, 0xFFFFFF);
        g.drawString(font, "分类名", x + 9, y + 51, 0xFFFFFF);
        g.drawString(font, (selectedCategory < 0 ? 0 : selectedCategory + 1) + " / " + working.categories().size(), x + 181, y + 29, 0xAAAAAA);
        g.drawString(font, "选中样本", x + 11, y + 83, 0xFFFFFF);
        if (!sample.isEmpty()) {
            g.renderItem(sample, x + 12, y + 100);
            String id = itemId(sample).toString();
            g.drawString(font, truncate(sample.getHoverName().getString(), 23), x + 33, y + 100, 0xEEEEEE);
            g.drawString(font, truncate(id, 25), x + 12, y + 125, 0xAAAAAA);
            String modName = ModList.get().getModContainerById(itemId(sample).getNamespace())
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(itemId(sample).getNamespace());
            long tagCount = sample.getTags().count();
            g.drawString(font, "所属模组：" + truncate(modName, 24), x + 199, y + 76, 0xDDDDDD);
            g.drawString(font, "可选标签：" + tagCount, x + 12, y + 166, 0xAAAAAA);
        } else g.drawString(font, "点击下方背包物品", x + 12, y + 108, 0xBBBBBB);

        if (selectedCategory >= 0) {
            FilterCategory category = working.categories().get(selectedCategory);
            ResourceLocation iconId = ResourceLocation.tryParse(category.iconItemId());
            ItemStack icon = iconId != null && BuiltInRegistries.ITEM.containsKey(iconId)
                ? new ItemStack(BuiltInRegistries.ITEM.get(iconId)) : new ItemStack(Items.PAPER);
            g.renderItem(icon, x + 12, y + 145);
            g.drawString(font, "分类图标", x + 35, y + 150, 0xDDDDDD);
            g.drawString(font, "筛选规则 (" + category.rules().size() + ")", x + 199, y + 176, 0xEEEEEE);
            g.drawString(font, "第 " + (rulesPage + 1) + " 页", x + 262, y + 276, 0xCCCCCC);
        } else g.drawString(font, "点击『新建分类』开始", x + 12, y + 151, 0xFFAAAA);

        var player = Minecraft.getInstance().player;
        if (player != null) for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().items.get(i);
            int sx = x + 13 + (i % 9) * 18;
            int sy = y + 183 + (i / 9) * 18;
            g.fill(sx, sy, sx + 17, sy + 17, 0xFF4A4A4A);
            if (!item.isEmpty()) {
                g.renderItem(item, sx, sy);
                if (ItemStack.isSameItemSameComponents(item, sample))
                    g.renderOutline(sx - 1, sy - 1, 19, 19, 0xFF55DFFF);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (!feedback.isEmpty()) g.drawString(font, truncate(feedback, 57), x + 7, y + 295,
            error ? 0xFFFF7777 : 0xFF88DD99);
        if (player != null) {
            int sx = mouseX - x - 13;
            int sy = mouseY - y - 183;
            if (sx >= 0 && sy >= 0 && sx < 162 && sy < 72) {
                ItemStack hover = player.getInventory().items.get((sy / 18) * 9 + sx / 18);
                if (!hover.isEmpty()) g.renderTooltip(font, hover, mouseX, mouseY);
            }
        }
    }

    private static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, Math.max(0, limit - 1)) + "…";
    }

    @Override public boolean isPauseScreen() { return false; }
}
