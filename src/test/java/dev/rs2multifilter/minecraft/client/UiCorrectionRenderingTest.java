package dev.rs2multifilter.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Draw-command checks, not screenshots or real client acceptance. */
class UiCorrectionRenderingTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void editorButtonUsesOnlyAOnePixelBorderWithoutTheTexturedPanel() {
        var button = new EditorButton(10, 20, 18, 18, Component.literal("添加"), b -> {});
        button.setIcon(EditorIcon.ADD);
        var g = mock(GuiGraphics.class);
        button.renderWidget(g, -1, -1, 0);
        verify(g).fill(10, 20, 28, 38, 0xFF747671);
        verify(g).fill(11, 21, 27, 37, 0xFFC6C6C6);
        verify(g, never()).blitSprite(any(ResourceLocation.class), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test void correctedEditorIconsUseTheExistingSmallPixelMasksWithoutSpriteEnlargement() {
        for (EditorIcon icon : new EditorIcon[]{EditorIcon.CONFIRM, EditorIcon.CLEAR, EditorIcon.FUZZY, EditorIcon.EXACT}) {
            var g = mock(GuiGraphics.class);
            icon.render(g, 20, 20, 0xFF454A4C);
            verify(g, atLeastOnce()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(0xFF454A4C));
            verify(g, never()).blitSprite(any(ResourceLocation.class), anyInt(), anyInt(), anyInt(), anyInt());
        }
    }

    @Test void memoryIconDrawsOpaqueCharcoalAboveTheNativeBaseInBothStatesWithoutAnUnderline() {
        var preferences = new GridPreferences(directory.resolve("client.properties"));
        var selection = new GridCategoryState(preferences, "world");
        var button = new RememberCategoryButton(selection);
        button.setPosition(8, 12);
        var g = mock(GuiGraphics.class);
        var pose = spy(new PoseStack());
        when(g.pose()).thenReturn(pose);
        button.renderWidget(g, -1, -1, 0);
        verify(g, atLeastOnce()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(0xFF363A3C));
        button.onPress();
        button.renderWidget(g, -1, -1, 0);
        verify(g, atLeastOnce()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(0xFF505456));
        verify(pose, times(2)).pushPose();
        verify(pose, times(2)).translate(0, 0, 1);
        verify(pose, times(2)).popPose();
        verify(g, never()).fill(12, 26, 22, 27, 0xFF318EB2);
    }
}
