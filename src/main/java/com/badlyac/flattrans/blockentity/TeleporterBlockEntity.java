package com.badlyac.flattrans.blockentity;

import com.badlyac.flattrans.FlatTrans;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 純粹作為渲染錨點，讓 {@link com.badlyac.flattrans.client.TeleporterBlockEntityRenderer}
 * 可以疊加繪製 flat_trans_glow 模型（不受環境光影響的發光條紋）。不儲存任何資料。
 */
public class TeleporterBlockEntity extends BlockEntity {
    public TeleporterBlockEntity(BlockPos pos, BlockState state) {
        super(FlatTrans.TELEPORTER_BLOCK_ENTITY.get(), pos, state);
    }
}
