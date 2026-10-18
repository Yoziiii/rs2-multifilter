package dev.rs2multifilter.minecraft;

import dev.rs2multifilter.minecraft.network.SaveCardPayload;
import dev.rs2multifilter.minecraft.network.SwapInstalledCardPayload;
import dev.rs2multifilter.minecraft.network.InstalledCardDataPayload;
import dev.rs2multifilter.minecraft.recipe.CopyCardRecipe;
import dev.rs2multifilter.minecraft.menu.CardEditorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import com.mojang.serialization.Codec;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(MultiFilterMod.ID)
public final class MultiFilterMod {
    public static final String ID = "rs2multifilter";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);

    public static final java.util.function.Supplier<MenuType<CardEditorMenu>> CARD_EDITOR_MENU = MENUS.register(
        "card_editor", () -> IMenuTypeExtension.create(CardEditorMenu::new)
    );

    public static final java.util.function.Supplier<net.minecraft.core.component.DataComponentType<String>> CARD_DATA = COMPONENTS.registerComponentType("filter_card_config", b ->
        b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
    );
    public static final DeferredItem<FilterCardItem> FILTER_CARD = ITEMS.registerItem(
        "filter_card", FilterCardItem::new, new Item.Properties().stacksTo(1)
    );
    public static final java.util.function.Supplier<RecipeSerializer<?>> COPY_RECIPE = RECIPES.register("copy_filter_card", () ->
        new SimpleCraftingRecipeSerializer<>(CopyCardRecipe::new)
    );

    public MultiFilterMod(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        RECIPES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(this::onPayloadRegistration);
        modBus.addListener(MultiFilterMod::onCreativeTab);
    }

    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(RefinedStorageApi.INSTANCE.getCreativeModeTabId()))
            event.accept(FILTER_CARD.get());
    }

    private void onPayloadRegistration(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(SaveCardPayload.TYPE, SaveCardPayload.STREAM_CODEC, SaveCardPayload::handle);
        registrar.playToServer(SwapInstalledCardPayload.TYPE, SwapInstalledCardPayload.STREAM_CODEC, SwapInstalledCardPayload::handle);
        registrar.playToClient(InstalledCardDataPayload.TYPE, InstalledCardDataPayload.STREAM_CODEC, InstalledCardDataPayload::handle);
    }
}
