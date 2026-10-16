package dev.rs2multifilter.minecraft.client;

import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
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
        field.renderWidget(graphics, 0, 0, 0);
        verify(graphics, times(2)).drawString(eq(font), any(FormattedCharSequence.class), anyInt(), eq(20), anyInt(), eq(false));
        verify(graphics, never()).drawString(eq(font), any(FormattedCharSequence.class), anyInt(), anyInt(), anyInt());
        verify(graphics).fill(any(RenderType.class), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        assertEquals("category", field.getValue());
        assertEquals(3, field.getCursorPosition());
        assertEquals("ego", field.getHighlighted());
    }

    @Test void ordinaryVanillaTextFieldsKeepTheirDefaultShadow() {
        var font = font();
        var field = new EditBox(font, 10, 20, 120, 9, Component.literal("ordinary"));
        field.setBordered(false);
        field.setValue("ordinary");
        var graphics = mock(GuiGraphics.class);
        field.renderWidget(graphics, 0, 0, 0);
        assertTrue(field.getTextShadow());
        verify(graphics).drawString(eq(font), any(FormattedCharSequence.class), anyInt(), anyInt(), anyInt(), eq(true));
        verify(graphics, never()).drawString(eq(font), any(FormattedCharSequence.class), anyInt(), anyInt(), anyInt(), eq(false));
    }

    @Test void nativeEditingReplacesOnlyTheSelectedTextAndKeepsTheCursor() {
        var field = new CardEditorScreen.NameField(font(), 10, 20, 120, 9, Component.literal("name"));
        field.setValue("category");
        field.setCursorPosition(3);
        field.setHighlightPos(6);
        field.insertText("X");
        assertEquals("catXry", field.getValue());
        assertEquals(4, field.getCursorPosition());
        assertEquals("", field.getHighlighted());
        assertFalse(field.getTextShadow());
    }

    @Test void nativeHintAndSuggestionAlsoUseTheSameShadowSetting() {
        var font = font();
        var graphics = mock(GuiGraphics.class);
        var field = new CardEditorScreen.NameField(font, 10, 20, 120, 9, Component.literal("name"));
        field.setBordered(false);
        var hint = Component.literal("完整分类名称");
        field.setHint(hint);
        field.renderWidget(graphics, 0, 0, 0);
        verify(graphics).drawString(eq(font), eq(hint), anyInt(), anyInt(), anyInt(), eq(false));
        field.setValue("category");
        field.setSuggestion("完整建议");
        field.renderWidget(graphics, 0, 0, 0);
        verify(graphics).drawString(eq(font), eq("完整建议"), anyInt(), anyInt(), anyInt(), eq(false));
    }
}
