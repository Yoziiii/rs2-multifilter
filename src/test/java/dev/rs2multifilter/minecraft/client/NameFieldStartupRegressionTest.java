package dev.rs2multifilter.minecraft.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Calls the native EditBox renderer as well as checking the startup mixin manifest. */
class NameFieldStartupRegressionTest {
    @Test void noRegisteredMixinTargetsTheGlobalEditBox() throws Exception {
        try (var input = getClass().getResourceAsStream("/rs2multifilter.mixins.json")) {
            assertNotNull(input);
            var config = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String group : List.of("client", "mixins")) for (var item : config.getAsJsonArray(group)) {
                String name = config.get("package").getAsString() + "." + item.getAsString();
                try (var bytes = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
                    assertNotNull(bytes, name);
                    var node = new ClassNode();
                    new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
                    if (node.invisibleAnnotations == null) continue;
                    for (var annotation : node.invisibleAnnotations) {
                        if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                        assertFalse(annotation.values.toString().contains(Type.getDescriptor(EditBox.class)), name);
                    }
                }
            }
        }
    }

    private Font font() {
        var font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> {
            String value = call.getArgument(0);
            return value.substring(0, Math.min(value.length(), Math.max(0, call.<Integer>getArgument(1) / 6)));
        });
        return font;
    }

    @Test void realNameRendererKeepsNativeCursorSelectionAndUsesPlainText() {
        var font = font();
        var field = new CardEditorScreen.NameField(font, 10, 20, 120, 9, Component.literal("name"));
        field.setBordered(false);
        field.setValue("category");
        field.setCursorPosition(3);
        field.setHighlightPos(6);
        var graphics = mock(GuiGraphics.class);
        try (var minecraft = mockStatic(Minecraft.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            field.renderWidget(graphics, 0, 0, 0);
        }
        verify(graphics, times(2)).drawString(eq(font), any(FormattedCharSequence.class), anyInt(), eq(20), anyInt(), eq(false));
        verify(graphics, never()).drawString(eq(font), any(FormattedCharSequence.class), anyInt(), anyInt(), anyInt());
        verify(graphics).fill(any(RenderType.class), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        assertEquals("category", field.getValue());
        assertEquals(3, field.getCursorPosition());
        assertEquals("ego", field.getHighlighted());
    }

    @Test void ordinaryVanillaTextFieldsDoNotUseTheAdapter() {
        var font = font();
        var field = new EditBox(font, 10, 20, 120, 9, Component.literal("ordinary"));
        field.setBordered(false);
        field.setValue("ordinary");
        var graphics = mock(GuiGraphics.class);
        field.renderWidget(graphics, 0, 0, 0);
        verify(graphics).drawString(eq(font), any(FormattedCharSequence.class), anyInt(), anyInt(), anyInt());
        verify(graphics, never()).drawString(eq(font), any(FormattedCharSequence.class), anyInt(), anyInt(), anyInt(), eq(false));
    }

    @Test void textOverloadsKeepFullContentAndNativeReturnWidth() {
        var font = font();
        var graphics = mock(GuiGraphics.class);
        var text = FormattedCharSequence.forward("完整名称", Style.EMPTY);
        when(graphics.drawString(font, text, 10, 20, 123, false)).thenReturn(34);
        when(graphics.drawString(font, "_", 10, 20, 123, false)).thenReturn(16);
        try (var minecraft = mockStatic(Minecraft.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var adapter = new NameFieldGraphics(graphics);
            assertEquals(35, adapter.drawString(font, text, 10, 20, 123));
            assertEquals(17, adapter.drawString(font, "_", 10, 20, 123));
            adapter.drawString(font, Component.literal("完整提示"), 10, 20, 123);
        }
        verify(graphics, times(2)).drawString(eq(font), any(FormattedCharSequence.class), eq(10), eq(20), eq(123), eq(false));
        verify(graphics).drawString(font, "_", 10, 20, 123, false);
    }

    @Test void borderSelectionAndScissorUseTheOriginalDrawingContext() {
        var graphics = mock(GuiGraphics.class);
        var pose = new PoseStack();
        when(graphics.pose()).thenReturn(pose);
        var sprite = ResourceLocation.withDefaultNamespace("widget/text_field");
        try (var minecraft = mockStatic(Minecraft.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var adapter = new NameFieldGraphics(graphics);
            assertSame(pose, adapter.pose());
            adapter.blitSprite(sprite, 10, 20, 100, 9);
            adapter.fill(10, 20, 30, 40, 123);
            adapter.fill(RenderType.guiTextHighlight(), 10, 20, 30, 40, 123);
            adapter.enableScissor(10, 20, 30, 40);
            adapter.disableScissor();
        }
        verify(graphics).blitSprite(sprite, 10, 20, 100, 9);
        verify(graphics).fill(10, 20, 30, 40, 123);
        verify(graphics).fill(RenderType.guiTextHighlight(), 10, 20, 30, 40, 123);
        verify(graphics).enableScissor(10, 20, 30, 40);
        verify(graphics).disableScissor();
    }
}
