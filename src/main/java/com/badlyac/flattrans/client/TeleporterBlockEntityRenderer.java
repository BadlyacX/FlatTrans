package com.badlyac.flattrans.client;

import com.badlyac.flattrans.FlatTrans;
import com.badlyac.flattrans.blockentity.TeleporterBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * 疊加繪製 flat_trans_glow 模型（light_1~4 及外圈疊層），
 * 用固定的 {@link LightTexture#FULL_BRIGHT} 亮度渲染，讓這些元件不受周遭環境光影響，永遠呈現自發光效果。
 */
public class TeleporterBlockEntityRenderer implements BlockEntityRenderer<TeleporterBlockEntity> {
    public static final ModelResourceLocation GLOW_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(FlatTrans.MODID, "block/flat_trans_glow"));

    public TeleporterBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TeleporterBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                        MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BakedModel glowModel = Minecraft.getInstance().getModelManager().getModel(GLOW_MODEL);
        ModelBlockRenderer modelRenderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        modelRenderer.renderModel(poseStack.last(), bufferSource.getBuffer(RenderType.translucent()),
                null, glowModel, 1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, packedOverlay);
    }
}
