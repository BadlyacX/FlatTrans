package com.badlyac.flattrans.integration.journeymap;

import com.badlyac.flattrans.FlatTrans;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.server.IServerAPI;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 橋接 JourneyMap 伺服端 API 的橋接類別。呼叫端必須先確認 journeymap 模組已載入
 * （見 {@code ModList.get().isLoaded("journeymap")}）才能參照本類別，
 * 否則在 JourneyMap 不存在時載入到 journeymap.* 類別會直接出錯。
 * <p>
 * 注意：不要使用 {@code WaypointGroup}／{@code IServerAPI.addGlobalGroup} 之類的群組 API——
 * JourneyMap 1.21.1-6.0.1 的 {@code WaypointGroupImpl.addWaypoint} 內部會意外載入到
 * 客戶端專屬的 {@code journeymap.client.Constants}，在真正的專用伺服器（非單人整合伺服器）
 * 上會直接讓伺服器當機（RuntimeDistCleaner 阻擋載入 {@code net.minecraft.client.Minecraft}）。
 */
public final class JourneyMapIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger("FlatTrans/JourneyMap");
    private static final String DEFAULT_NAME = "Teleporter";

    @Nullable
    private static IServerAPI serverApi;

    private JourneyMapIntegration() {
    }

    static void setServerApi(@Nullable IServerAPI api) {
        serverApi = api;
        LOGGER.info("JourneyMap server plugin {}", api == null ? "detached" : "initialized");
    }

    /** 在錨點座標新增或更新一個全域標記，代表這裡有一個（矩形群組的）傳送裝置。 */
    public static void addOrUpdateWaypoint(ServerLevel level, BlockPos anchor, String name) {
        if (serverApi == null) {
            return;
        }
        try {
            String displayName = name.isEmpty() ? DEFAULT_NAME : name;
            Waypoint waypoint = findWaypoint(level, anchor)
                    .orElseGet(() -> WaypointFactory.createWaypoint(FlatTrans.MODID, anchor, displayName, level.dimension(), true));
            waypoint.setName(displayName);
            waypoint.setEnabled(true);

            serverApi.addGlobalWaypoint(waypoint);
            LOGGER.info("Added/updated JourneyMap waypoint '{}' at {} in {}", displayName, anchor, level.dimension().location());
        } catch (RuntimeException e) {
            LOGGER.error("Failed to add/update JourneyMap waypoint at {}", anchor, e);
        }
    }

    /** 移除指定座標的全域標記（如果有的話）。 */
    public static void removeWaypoint(ServerLevel level, BlockPos pos) {
        if (serverApi == null) {
            return;
        }
        try {
            Optional<Waypoint> existing = findWaypoint(level, pos);
            if (existing.isEmpty()) {
                LOGGER.info("No JourneyMap waypoint found to remove at {} in {}", pos, level.dimension().location());
                return;
            }
            serverApi.deleteGlobalWaypoint(existing.get().getGuid());
            LOGGER.info("Removed JourneyMap waypoint at {} in {}", pos, level.dimension().location());
        } catch (RuntimeException e) {
            LOGGER.error("Failed to remove JourneyMap waypoint at {}", pos, e);
        }
    }

    private static Optional<Waypoint> findWaypoint(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        List<Waypoint> waypoints = serverApi.getGlobalWaypoints();
        return waypoints.stream()
                .filter(waypoint -> FlatTrans.MODID.equals(waypoint.getModId()))
                .filter(waypoint -> pos.equals(waypoint.getBlockPos()))
                .filter(waypoint -> dimension.equals(waypoint.getPrimaryDimension()))
                .findFirst();
    }
}
