package com.createarmor;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Create: Steam Armor — паровий обладунок у стилі Create. Повний сет дає +4 до шкоди й відкриває слот імпланта
 * (будь-який нагрудник, Extendo Grip, парові крила, наручна гармата).
 */
@Mod(CreateArmor.MOD_ID)
public class CreateArmor {
    public static final String MOD_ID = "createarmor";
    public static final Logger LOG = com.mojang.logging.LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<Item> HELMET = ITEMS.register("steam_helmet", () -> new SteamArmorItem(ArmorItem.Type.HELMET));
    public static final RegistryObject<Item> CHESTPLATE = ITEMS.register("steam_chestplate", () -> new SteamArmorItem(ArmorItem.Type.CHESTPLATE));
    public static final RegistryObject<Item> LEGGINGS = ITEMS.register("steam_leggings", () -> new SteamArmorItem(ArmorItem.Type.LEGGINGS));
    public static final RegistryObject<Item> BOOTS = ITEMS.register("steam_boots", () -> new SteamArmorItem(ArmorItem.Type.BOOTS));
    public static final RegistryObject<Item> ELYTRA = ITEMS.register("steam_elytra", SteamElytraItem::new);
    public static final RegistryObject<Item> ELYTRA_MODULE = ITEMS.register("steam_elytra_module", WingsModuleItem::new);
    public static final RegistryObject<Item> EXTENDO_MODULE = ITEMS.register("steam_extendo_module",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> ARM_CANNON = ITEMS.register("steam_arm_cannon",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> CANNON = ITEMS.register("steam_cannon", SteamCannonItem::new);
    /** Проміжний виріб для автоматизації (sequenced assembly): з нього роблять усю броню. */
    public static final RegistryObject<Item> PLATE = ITEMS.register("steam_plate", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> INCOMPLETE_PLATE = ITEMS.register("incomplete_steam_plate", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Block> WINGS_BLOCK = BLOCKS.register("steam_wings",
            () -> new SteamWingsBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.COPPER).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SteamWingsBlockEntity>> WINGS_BE = BLOCK_ENTITIES.register("steam_wings",
            () -> BlockEntityType.Builder.of(SteamWingsBlockEntity::new, WINGS_BLOCK.get()).build(null));

    public static final RegistryObject<MenuType<ImplantMenu>> IMPLANT_MENU = MENUS.register("implant",
            () -> IForgeMenuType.create((id, inv, buf) -> new ImplantMenu(id, inv)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.createarmor"))
            .icon(() -> new ItemStack(CHESTPLATE.get()))
            .displayItems((p, out) -> {
                for (RegistryObject<Item> i : ITEMS.getEntries()) {
                    if (i != INCOMPLETE_PLATE) {
                        out.accept(i.get());
                    }
                }
            }).build());

    public CreateArmor() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(LiveImplant::registerBacktankSupplier));
        MENUS.register(bus);
        TABS.register(bus);
        Net.register();
    }
}
