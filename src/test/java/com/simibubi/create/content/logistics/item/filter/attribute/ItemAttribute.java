package com.simibubi.create.content.logistics.item.filter.attribute;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.function.BiPredicate;

/** TEST ONLY: the public reflection signatures of Create 6.0.10, not Create's codec/implementation. */
public interface ItemAttribute {
    class Fixture {
        public static final Map<String,ItemAttribute> registered=new HashMap<>();
        public static Error dependencyFailure;
        public static List<ItemAttribute> discovered=List.of();
        public static int loads;
        public static void reset() { registered.clear(); dependencyFailure=null; discovered=List.of(); loads=0; }
    }
    static ItemAttribute loadStatic(CompoundTag data, HolderLookup.Provider provider) {
        Fixture.loads++;
        if(Fixture.dependencyFailure!=null)throw Fixture.dependencyFailure;
        return Fixture.registered.get(data.getCompound("attribute").getString("type"));
    }
    static List<ItemAttribute> getAllAttributes(ItemStack stack,Level level) { return Fixture.discovered; }
    static CompoundTag saveStatic(ItemAttribute attribute,HolderLookup.Provider provider) {
        var tag=new CompoundTag();var data=new CompoundTag();
        Fixture.registered.entrySet().stream().filter(e->e.getValue()==attribute).findFirst()
            .ifPresent(e->data.putString("type",e.getKey()));
        tag.put("attribute",data);return tag;
    }
    boolean appliesTo(ItemStack stack,Level level);
    default Component format(boolean inverse) { return Component.literal("测试属性"); }
    record TestAttribute(BiPredicate<ItemStack,Level> predicate) implements ItemAttribute {
        @Override public boolean appliesTo(ItemStack stack,Level level) { return predicate.test(stack,level); }
    }
}
