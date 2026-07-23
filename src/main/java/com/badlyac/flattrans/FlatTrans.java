package com.badlyac.flattrans;

import com.badlyac.flattrans.block.TeleporterBlock;
import com.badlyac.flattrans.blockentity.TeleporterBlockEntity;
import com.badlyac.flattrans.client.ClientPayloadHandler;
import com.badlyac.flattrans.network.ServerPayloadHandler;
import com.badlyac.flattrans.network.TeleportRequestPayload;
import com.badlyac.flattrans.network.TeleporterDeletePayload;
import com.badlyac.flattrans.network.TeleporterListPayload;
import com.badlyac.flattrans.network.TeleporterRenamePayload;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(FlatTrans.MODID)
public class FlatTrans {
    public static final String MODID = "flattrans";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<TeleporterBlock> TELEPORTER = BLOCKS.register("teleporter",
            () -> new TeleporterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredItem<BlockItem> TELEPORTER_ITEM =
            ITEMS.registerSimpleBlockItem("teleporter", TELEPORTER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeleporterBlockEntity>> TELEPORTER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("teleporter",
                    () -> BlockEntityType.Builder.of(TeleporterBlockEntity::new, TELEPORTER.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLAT_TRANS_TAB = CREATIVE_MODE_TABS.register("flat_trans",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.flattrans"))
                    .icon(() -> new ItemStack(TELEPORTER_ITEM.get()))
                    .displayItems((parameters, output) -> output.accept(TELEPORTER_ITEM.get()))
                    .build());

    public FlatTrans(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(FlatTrans::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(TeleporterListPayload.TYPE, TeleporterListPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleTeleporterList(payload, context));

        registrar.playToServer(TeleportRequestPayload.TYPE, TeleportRequestPayload.STREAM_CODEC,
                ServerPayloadHandler::handleTeleportRequest);

        registrar.playToServer(TeleporterRenamePayload.TYPE, TeleporterRenamePayload.STREAM_CODEC,
                ServerPayloadHandler::handleTeleporterRename);

        registrar.playToServer(TeleporterDeletePayload.TYPE, TeleporterDeletePayload.STREAM_CODEC,
                ServerPayloadHandler::handleTeleporterDelete);
    }
}
