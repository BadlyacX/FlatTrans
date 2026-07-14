package com.badlyac.flattrans.network;

import com.badlyac.flattrans.FlatTrans;
import com.badlyac.flattrans.data.TeleporterSavedData;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ServerPayloadHandler {
    /** 玩家必須站在來源裝置附近（8 格內）才能傳送/改名，防止封包偽造。 */
    private static final double MAX_USE_DISTANCE_SQ = 8.0 * 8.0;
    private static final int MAX_NAME_LENGTH = 32;

    public static void handleTeleportRequest(TeleportRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        TeleporterSavedData data = TeleporterSavedData.get(level);
        BlockPos source = payload.source();
        GlobalPos target = payload.target();

        if (player.distanceToSqr(Vec3.atCenterOf(source)) > MAX_USE_DISTANCE_SQ) {
            return;
        }
        if (!level.getBlockState(source).is(FlatTrans.TELEPORTER.get())) {
            return;
        }

        ServerLevel targetLevel = player.server.getLevel(target.dimension());
        // 目的地必須仍然存在傳送裝置，且所在維度仍然存在
        if (targetLevel == null || !data.contains(target) || !targetLevel.getBlockState(target.pos()).is(FlatTrans.TELEPORTER.get())) {
            data.remove(target);
            player.displayClientMessage(Component.translatable("message.flattrans.destination_missing"), true);
            return;
        }

        level.playSound(null, source, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.0F);
        BlockPos targetPos = target.pos();
        player.teleportTo(targetLevel, targetPos.getX() + 0.5, targetPos.getY() + 0.25, targetPos.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
        targetLevel.playSound(null, targetPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    public static void handleTeleporterRename(TeleporterRenamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();

        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_USE_DISTANCE_SQ) {
            return;
        }
        if (!level.getBlockState(pos).is(FlatTrans.TELEPORTER.get())) {
            return;
        }

        String name = payload.name().trim();
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        TeleporterSavedData.get(level).setName(GlobalPos.of(level.dimension(), pos), name);
    }
}
