package dev.rs2multifilter.minecraft;

import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class CreativeTabTest {
    @Test void addsTheRealUpgradeOnlyToTheNativeRefinedStorageTab() {
        var event = mock(BuildCreativeModeTabContentsEvent.class);
        when(event.getTabKey()).thenReturn(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            RefinedStorageApi.INSTANCE.getCreativeModeTabId()));
        MultiFilterMod.onCreativeTab(event);
        verify(event).accept(MultiFilterMod.FILTER_CARD.get());
    }
    @Test void leavesOtherCreativeTabsUnchanged() {
        var event = mock(BuildCreativeModeTabContentsEvent.class);
        when(event.getTabKey()).thenReturn(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.parse("minecraft:tools_and_utilities")));
        MultiFilterMod.onCreativeTab(event);
        verify(event, never()).accept(MultiFilterMod.FILTER_CARD.get());
    }
}
