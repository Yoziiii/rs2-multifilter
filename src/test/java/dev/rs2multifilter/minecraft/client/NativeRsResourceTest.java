package dev.rs2multifilter.minecraft.client;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class NativeRsResourceTest {
    @Test void originalUpgradeTextureHasFourOpaqueGrayInteriorsAtTheUnchangedSlotCoordinates() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("assets/refinedstorage/textures/gui/generic_filter.png")) {
            assertNotNull(input);
            var image = ImageIO.read(input);
            for (int slot = 0; slot < 4; slot++)
                for (int dx = 0; dx < 16; dx++)
                    for (int dy = 0; dy < 16; dy++)
                        assertEquals(0xFF8B8B8B, image.getRGB(187 + dx, 6 + slot * 18 + dy));
            assertEquals(0, image.getRGB(180, 0) >>> 24, "Native panel has shaped transparent corners, not a black box");
        }
    }
    @Test void reusedSlotAndActionSpritesExistInTheActualRsDependency() throws Exception {
        for (String sprite : new String[]{"slot", "set", "reset", "widget/side_button/base", "widget/side_button/hovered",
                "widget/side_button/fuzzy_mode/on", "widget/side_button/fuzzy_mode/off"}) {
            try (var resource = getClass().getClassLoader().getResourceAsStream("assets/refinedstorage/textures/gui/sprites/" + sprite + ".png")) {
                assertNotNull(resource, sprite);
                assertNotNull(ImageIO.read(resource), sprite);
            }
        }
    }
}
