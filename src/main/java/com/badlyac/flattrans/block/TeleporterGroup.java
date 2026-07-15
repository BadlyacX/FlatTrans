package com.badlyac.flattrans.block;

import com.badlyac.flattrans.FlatTrans;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

/**
 * 偵測彼此邊對邊相連的 flat_trans 方塊群組，並判斷這個群組是否恰好填滿一個矩形
 * （沒有缺角、沒有空洞）。只有形成矩形的群組才會被視為單一傳送點，
 * 其錨點固定為群組內 X、Z 座標最小的那個角落。
 */
public final class TeleporterGroup {
    /** 群組大小上限，避免超大範圍的連通搜尋拖慢伺服器（64x64 已遠超實際使用情境）。 */
    private static final int MAX_SIZE = 4096;

    private TeleporterGroup() {
    }

    public static final class Group {
        private final Set<BlockPos> positions;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final int y;

        private Group(Set<BlockPos> positions, int minX, int maxX, int minZ, int maxZ, int y) {
            this.positions = positions;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.y = y;
        }

        public Set<BlockPos> positions() {
            return Collections.unmodifiableSet(positions);
        }

        /** 群組恰好填滿其邊界矩形（無缺角、無空洞）時才視為一個傳送點。 */
        public boolean isRectangle() {
            long area = (long) (maxX - minX + 1) * (long) (maxZ - minZ + 1);
            return area == positions.size();
        }

        /** 矩形群組的錨點：X、Z 座標最小的角落。 */
        public BlockPos anchor() {
            return new BlockPos(minX, y, minZ);
        }

        public double centerX() {
            return (minX + maxX + 1) / 2.0;
        }

        public double centerZ() {
            return (minZ + maxZ + 1) / 2.0;
        }
    }

    /**
     * 從 start 開始，收集所有透過水平方向（東西南北）邊對邊相連、且在同一 Y 層的
     * flat_trans 方塊。start 必須本身就是 flat_trans 方塊。
     */
    public static Group floodFill(BlockGetter level, BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);

        int minX = start.getX();
        int maxX = start.getX();
        int minZ = start.getZ();
        int maxZ = start.getZ();

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (visited.contains(next) || !level.getBlockState(next).is(FlatTrans.TELEPORTER.get())) {
                    continue;
                }
                visited.add(next);
                if (visited.size() > MAX_SIZE) {
                    return new Group(visited, start.getX(), start.getX(), start.getZ(), start.getZ(), start.getY());
                }
                minX = Math.min(minX, next.getX());
                maxX = Math.max(maxX, next.getX());
                minZ = Math.min(minZ, next.getZ());
                maxZ = Math.max(maxZ, next.getZ());
                queue.add(next);
            }
        }

        return new Group(visited, minX, maxX, minZ, maxZ, start.getY());
    }
}
