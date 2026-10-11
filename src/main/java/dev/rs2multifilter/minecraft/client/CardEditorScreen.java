package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.*;
import dev.rs2multifilter.minecraft.network.SaveCardPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CardEditorScreen extends Screen {
    private FilterCard working;
    private int index = -1;
    private EditBox name;
    private EditBox icon;
    private EditBox rules;
    private String error = "";

    public CardEditorScreen(FilterCard initial) {
        super(Component.literal("多分类过滤卡"));
        this.working = initial;
        if (!working.categories().isEmpty()) index = 0;
    }

    @Override protected void init() {
        int x = width / 2 - 170;
        int y = height / 2 - 94;
        name = new EditBox(font, x + 54, y + 12, 280, 20, Component.literal("分类名称"));
        name.setMaxLength(32);
        icon = new EditBox(font, x + 54, y + 44, 280, 20, Component.literal("物品图标 ID"));
        icon.setMaxLength(256);
        rules = new EditBox(font, x + 54, y + 76, 280, 20, Component.literal("规则"));
        rules.setMaxLength(4000);
        addRenderableWidget(name);
        addRenderableWidget(icon);
        addRenderableWidget(rules);
        loadIndex();
        addRenderableWidget(Button.builder(Component.literal("< 上一个"), b -> move(-1)).bounds(x + 3, y + 111, 77, 20).build());
        addRenderableWidget(Button.builder(Component.literal("下一个 >"), b -> move(1)).bounds(x + 83, y + 111, 77, 20).build());
        addRenderableWidget(Button.builder(Component.literal("应用编辑"), b -> apply()).bounds(x + 163, y + 111, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("新增分类"), b -> addCategory()).bounds(x + 250, y + 111, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("删除当前"), b -> remove()).bounds(x + 3, y + 140, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("保存到卡片"), b -> save()).bounds(x + 112, y + 140, 222, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+ 副手物品所属模组"), b -> addFromOffhand(true)).bounds(x + 3, y + 168, 164, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+ 副手具体物品"), b -> addFromOffhand(false)).bounds(x + 170, y + 168, 164, 20).build());
        addRenderableWidget(Button.builder(Component.literal("从副手物品选择标签..."), b -> openTagPicker()).bounds(x + 3, y + 193, 331, 20).build());
    }

    private void loadIndex() {
        error = "";
        if (index < 0 || index >= working.categories().size()) {
            name.setValue("");
            icon.setValue("minecraft:paper");
            rules.setValue("+mod:create");
        } else {
            FilterCategory c = working.categories().get(index);
            name.setValue(c.name());
            icon.setValue(c.iconItemId());
            rules.setValue(RuleText.format(c.rules()).replace('\n', ';'));
        }
    }

    private void move(int direction) {
        if (!applyIfExisting()) return;
        if (working.categories().isEmpty()) return;
        index = Math.floorMod(index + direction, working.categories().size());
        loadIndex();
    }
    private boolean applyIfExisting() { return index < 0 || apply(); }
    private boolean apply() {
        if (index < 0) { error = "请先点新增分类"; return false; }
        try {
            working = CardEditor.replace(working, index, name.getValue().trim(), icon.getValue().trim(), rules.getValue().replace(';', '\n'));
            error = "当前分类已暂存（请点保存到卡片）";
            return true;
        } catch (RuntimeException e) { error = e.getMessage(); return false; }
    }
    private void addCategory() {
        if (!applyIfExisting()) return;
        if (working.categories().size() >= FilterCard.MAX_CATEGORIES) { error = "已达到最多 64 个分类"; return; }
        try {
            working = CardEditor.add(working, "新分类" + (working.categories().size() + 1), "minecraft:paper", "+mod:create");
            index = working.categories().size() - 1;
            loadIndex();
        } catch (RuntimeException e) { error = e.getMessage(); }
    }
    private void remove() {
        if (index < 0) return;
        working = CardEditor.remove(working, index);
        index = working.categories().isEmpty() ? -1 : Math.min(index, working.categories().size() - 1);
        loadIndex();
    }
    private void addFromOffhand(boolean byMod) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack sample = player.getOffhandItem();
        if (sample.isEmpty()) { error = "请在副手放入样本物品"; return; }
        if (index < 0) { error = "先点击新增分类"; return; }
        var id = BuiltInRegistries.ITEM.getKey(sample.getItem());
        String entry = byMod ? "+mod:" + id.getNamespace() : "+item:" + id;
        String current = rules.getValue();
        java.util.List<String> existing = java.util.Arrays.stream(current.split(";")).map(String::trim).filter(t -> !t.isEmpty()).toList();
        if (!existing.contains(entry)) {
            rules.setValue(current.isBlank() ? entry : current + "; " + entry);
            error = "已添加：" + entry;
        } else error = "规则已存在";
    }

    private void openTagPicker() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack sample = player.getOffhandItem();
        if (sample.isEmpty()) { error = "请在副手拿一件样本物品"; return; }
        if (index < 0) { error = "请先创建分类"; return; }
        if (!applyIfExisting()) return;
        var tags = sample.getTags().map(key -> key.location().toString()).sorted().toList();
        if (tags.isEmpty()) { error = "这个物品没有物品标签"; return; }
        Minecraft.getInstance().setScreen(new TagPickerScreen(this, tags, this::addTagRule));
    }

    private void addTagRule(String tagId) {
        if (index < 0 || index >= working.categories().size()) return;
        var category = working.categories().get(index);
        String newRule = "+tag:" + tagId;
        String currentRules = RuleText.format(category.rules());
        if (java.util.Arrays.stream(currentRules.split("\\R")).noneMatch(s -> s.equals(newRule))) {
            working = CardEditor.replace(working, index, category.name(), category.iconItemId(), currentRules + "\n" + newRule);
        }
        rules.setValue(RuleText.format(working.categories().get(index).rules()).replace('\\n', ';'));
        error = "已加入标签 " + tagId;
    }

    private void save() {
        if (!applyIfExisting()) return;
        try {
            PacketDistributor.sendToServer(new SaveCardPayload(CardWireCodec.encode(working)));
            onClose();
        } catch (RuntimeException e) { error = e.getMessage(); }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - 170, y = height / 2 - 94;
        graphics.drawCenteredString(font, "过滤卡分类 " + (index < 0 ? 0 : index + 1) + " / " + working.categories().size(), width / 2, y - 10, 0xFFFFFF);
        graphics.drawString(font, "名称", x, y + 18, 0xFFFFFF);
        graphics.drawString(font, "图标", x, y + 50, 0xFFFFFF);
        graphics.drawString(font, "规则", x, y + 82, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "副手放样本物品，可一键添加模组、物品或标签", width / 2, y + 227, 0xAAAAAA);
        if (!error.isBlank()) graphics.drawCenteredString(font, error, width / 2, y + 244, 0xFF7777);
    }
    @Override public boolean isPauseScreen() { return false; }
}
