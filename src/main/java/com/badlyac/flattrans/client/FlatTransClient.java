package com.badlyac.flattrans.client;

import com.badlyac.flattrans.FlatTrans;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = FlatTrans.MODID, value = Dist.CLIENT)
public class FlatTransClient {
    @SubscribeEvent
    static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(TeleporterBlockEntityRenderer.GLOW_MODEL);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FlatTrans.TELEPORTER_BLOCK_ENTITY.get(), TeleporterBlockEntityRenderer::new);
    }
}
