package dev.rs2multifilter.minecraft;

import dev.rs2multifilter.minecraft.network.SaveCardPayload;
import dev.rs2multifilter.minecraft.recipe.CopyCardRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.mojang.serialization.Codec;

@Mod(MultiFilterMod.ID)
public final class MultiFilterMod {
    public static final String ID = "rs2multifilter";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, ID);

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
        modBus.addListener(this::onPayloadRegistration);
    }

    private void onPayloadRegistration(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SaveCardPayload.TYPE, SaveCardPayload.STREAM_CODEC, SaveCardPayload::handle);
    }
}
