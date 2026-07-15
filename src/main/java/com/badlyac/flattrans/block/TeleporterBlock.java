package com.badlyac.flattrans.block;

import com.badlyac.flattrans.FlatTrans;
import com.badlyac.flattrans.blockentity.TeleporterBlockEntity;
import com.badlyac.flattrans.data.TeleporterSavedData;
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
            refreshGroup(serverLevel, TeleporterSavedData.get(serverLevel), pos);
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
            data.remove(GlobalPos.of(serverLevel.dimension(), pos));

            // 剩下的鄰居可能因此分裂成數個群組、或不再是矩形，逐一重新計算
            Set<BlockPos> processed = new HashSet<>();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = pos.relative(direction);
                if (processed.contains(neighborPos) || !level.getBlockState(neighborPos).is(FlatTrans.TELEPORTER.get())) {
                    continue;
                }
                processed.addAll(refreshGroup(serverLevel, data, neighborPos).positions());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * 重新計算 start 所在的連通群組：先移除群組內所有既有的登記項（可能是先前各自獨立的方塊，
     * 也可能是先前已合併的矩形），再依目前的形狀決定是否要用群組錨點重新登記一筆。
     * 名稱優先取自錨點原本的登記，沒有的話才退而求其次取群組內任一個非空名稱。
     */
    private static TeleporterGroup.Group refreshGroup(ServerLevel level, TeleporterSavedData data, BlockPos start) {
        TeleporterGroup.Group group = TeleporterGroup.floodFill(level, start);
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
        }

        if (group.isRectangle()) {
            data.add(GlobalPos.of(level.dimension(), anchor), anchorName.isEmpty() ? fallbackName : anchorName);
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
