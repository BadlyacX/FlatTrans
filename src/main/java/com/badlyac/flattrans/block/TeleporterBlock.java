package com.badlyac.flattrans.block;

import com.badlyac.flattrans.FlatTrans;
import com.badlyac.flattrans.blockentity.TeleporterBlockEntity;
import com.badlyac.flattrans.data.TeleporterSavedData;
import com.badlyac.flattrans.integration.journeymap.JourneyMapIntegration;
import com.badlyac.flattrans.network.TeleporterEntry;
import com.badlyac.flattrans.network.TeleporterListPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 傳送裝置方塊：放在地板上，右鍵開啟目的地選擇畫面。
 * 多個方塊邊對邊相連、且恰好填滿一個矩形（無缺角、無空洞）時，會被視為單一傳送點，
 * 註冊在群組中 X、Z 座標最小的那個角落（見 {@link TeleporterGroup}）；不成矩形的連通群組則不會被註冊。
 * 放置 / 破壞時會重新計算受影響的群組，自動向 {@link TeleporterSavedData} 註冊 / 註銷。
 * 附帶一個沒有資料的 BlockEntity，僅供客戶端疊加繪製發光條紋（見 TeleporterBlockEntityRenderer）。
 */
public class TeleporterBlock extends Block implements EntityBlock {
    // 對應 flat_trans 模型的主體高度（裝飾用的外圈溢出部分不計入碰撞箱，避免影響鄰近方塊）
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    public TeleporterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeleporterBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportRigidBlock(level, pos.below());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!state.is(oldState.getBlock()) && level instanceof ServerLevel serverLevel) {
            // 接上新方塊後還沒湊成矩形之前，保留現有登記項不動，避免中途把已有的名字洗掉
            refreshGroup(serverLevel, TeleporterSavedData.get(serverLevel), pos, true);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // 用鐵砧改過名的物品，放下時把名稱記到這個方塊所屬矩形群組的錨點上
        if (level instanceof ServerLevel serverLevel) {
            Component customName = stack.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                TeleporterGroup.Group group = TeleporterGroup.floodFill(serverLevel, pos);
                if (group.isRectangle()) {
                    TeleporterSavedData.get(serverLevel)
                            .setName(GlobalPos.of(serverLevel.dimension(), group.anchor()), customName.getString());
                }
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            TeleporterSavedData data = TeleporterSavedData.get(serverLevel);
            // pos 自己可能就是（單一方塊或矩形群組的）錨點，一律先移除
            // 注意：挖掉方塊不會動到 JourneyMap 上的標記，標記只能透過選單裡的 X 按鈕手動刪除
            data.remove(GlobalPos.of(serverLevel.dimension(), pos));

            // 剩下的鄰居可能因此分裂成數個群組、或不再是矩形，逐一重新計算
            Set<BlockPos> processed = new HashSet<>();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = pos.relative(direction);
                if (processed.contains(neighborPos) || !level.getBlockState(neighborPos).is(FlatTrans.TELEPORTER.get())) {
                    continue;
                }
                // 破壞後的殘餘形狀若不再是矩形，就不該繼續視為有效傳送點，因此這裡一律清掉舊登記；
                // 但傳 placing=false 讓 JourneyMap 標記完全不受影響
                processed.addAll(refreshGroup(serverLevel, data, neighborPos, false).positions());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * 重新計算 start 所在的連通群組：先移除群組內所有既有的登記項（可能是先前各自獨立的方塊，
     * 也可能是先前已合併的矩形），再依目前的形狀決定是否要用群組錨點重新登記一筆。
     * 名稱優先取自錨點原本的登記，沒有的話才退而求其次取群組內任一個非空名稱。
     * <p>
     * {@code placing} 為 true 時（放置方塊觸發）：若目前形狀還不是矩形（例如正在接新方塊、尚未湊滿），
     * 不會直接清掉登記、讓原本的名字/註冊維持原樣，等真的接成矩形時才統一清掉重登；
     * 但如果這個連通區塊剛好同時併入了不只一筆既有登記（例如把兩個各自獨立、各自已命名的傳送裝置
     * 直接接在一起），仍會立刻只保留其中一筆（優先留有名字的那筆），其餘的登記與地圖標記都會清掉，
     * 避免同一塊連通區域在地圖上同時留下好幾個標記。這個情境下也會同步更新 JourneyMap 標記。
     * <p>
     * {@code placing} 為 false 時（破壞方塊觸發）：一律照舊清掉 {@link TeleporterSavedData} 的登記，
     * 維持「非矩形一律不算有效傳送點」的規則；但完全不會動 JourneyMap 上的標記——
     * 標記只能由玩家在傳送裝置選單裡按住 X 手動刪除，破壞方塊不會自動移除。
     */
    private static TeleporterGroup.Group refreshGroup(ServerLevel level, TeleporterSavedData data, BlockPos start,
                                                       boolean placing) {
        TeleporterGroup.Group group = TeleporterGroup.floodFill(level, start);
        boolean journeyMapLoaded = placing && ModList.get().isLoaded("journeymap");

        if (placing && !group.isRectangle()) {
            List<BlockPos> registered = group.positions().stream()
                    .filter(memberPos -> data.contains(GlobalPos.of(level.dimension(), memberPos)))
                    .sorted(Comparator.comparingLong(BlockPos::asLong))
                    .toList();
            if (registered.size() > 1) {
                BlockPos keep = registered.stream()
                        .filter(memberPos -> !data.getName(GlobalPos.of(level.dimension(), memberPos)).isEmpty())
                        .findFirst()
                        .orElse(registered.get(0));
                for (BlockPos memberPos : registered) {
                    if (memberPos.equals(keep)) {
                        continue;
                    }
                    data.remove(GlobalPos.of(level.dimension(), memberPos));
                    if (journeyMapLoaded) {
                        JourneyMapIntegration.removeWaypoint(level, memberPos);
                    }
                }
            }
            return group;
        }

        BlockPos anchor = group.anchor();

        List<BlockPos> members = new ArrayList<>(group.positions());
        members.sort(Comparator.comparingLong(BlockPos::asLong));

        String anchorName = "";
        String fallbackName = "";
        for (BlockPos memberPos : members) {
            GlobalPos memberGlobalPos = GlobalPos.of(level.dimension(), memberPos);
            String existing = data.getName(memberGlobalPos);
            if (!existing.isEmpty()) {
                if (memberPos.equals(anchor)) {
                    anchorName = existing;
                } else if (fallbackName.isEmpty()) {
                    fallbackName = existing;
                }
            }
            data.remove(memberGlobalPos);
            if (journeyMapLoaded) {
                JourneyMapIntegration.removeWaypoint(level, memberPos);
            }
        }

        if (group.isRectangle()) {
            String finalName = anchorName.isEmpty() ? fallbackName : anchorName;
            data.add(GlobalPos.of(level.dimension(), anchor), finalName);
            if (journeyMapLoaded) {
                JourneyMapIntegration.addOrUpdateWaypoint(level, anchor, finalName);
            }
        }
        return group;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            TeleporterSavedData data = TeleporterSavedData.get(serverLevel);
            // 玩家可能點的是矩形群組裡的任一個方塊，要先找出群組錨點才能對到正確的登記項
            TeleporterGroup.Group group = TeleporterGroup.floodFill(serverLevel, pos);
            BlockPos anchor = group.isRectangle() ? group.anchor() : pos;
            GlobalPos sourceGlobalPos = GlobalPos.of(serverLevel.dimension(), anchor);
            // 同維度的目的地優先，並依距離排序；跨維度的目的地排在後面，用維度 ID 分組
            List<TeleporterEntry> destinations = data.getEntries().stream()
                    .filter(entry -> !entry.pos().equals(sourceGlobalPos))
                    .sorted(Comparator.<TeleporterEntry>comparingInt(entry -> entry.pos().dimension().equals(serverLevel.dimension()) ? 0 : 1)
                            .thenComparing(entry -> entry.pos().dimension().location().toString())
                            .thenComparingDouble(entry -> entry.pos().pos().distSqr(pos)))
                    .toList();
            PacketDistributor.sendToPlayer(serverPlayer, new TeleporterListPayload(pos, data.getName(sourceGlobalPos), destinations));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
