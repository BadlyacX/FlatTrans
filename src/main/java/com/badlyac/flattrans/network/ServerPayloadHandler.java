package com.badlyac.flattrans.network;

import com.badlyac.flattrans.FlatTrans;
import com.badlyac.flattrans.data.TeleporterSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ServerPayloadHandler {
    /** 玩家必須站在來源裝置附近（8 格內）才能傳送，防止封包偽造。 */
    private static final double MAX_USE_DISTANCE_SQ = 8.0 * 8.0;

    public static void handleTeleportRequest(TeleportRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        TeleporterSavedData data = TeleporterSavedData.get(level);
        BlockPos source = payload.source();
        BlockPos target = payload.target();

        if (player.distanceToSqr(Vec3.atCenterOf(source)) > MAX_USE_DISTANCE_SQ) {
            return;
        }
        if (!level.getBlockState(source).is(FlatTrans.TELEPORTER.get())) {
            return;
        }
        // 目的地必須仍然存在傳送裝置
        if (!data.contains(target) || !level.getBlockState(target).is(FlatTrans.TELEPORTER.get())) {
            data.remove(target);
            player.displayClientMessage(Component.translatable("message.flattrans.destination_missing"), true);
            return;
        }

        level.playSound(null, source, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.connection.teleport(
                target.getX() + 0.5, target.getY() + 0.25, target.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        level.playSound(null, target, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
