package com.badlyac.flattrans.block;

import com.badlyac.flattrans.blockentity.TeleporterBlockEntity;
import com.badlyac.flattrans.data.TeleporterSavedData;
import com.badlyac.flattrans.network.TeleporterEntry;
import com.badlyac.flattrans.network.TeleporterListPayload;

import java.util.Comparator;
import java.util.List;

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
 * 放置 / 破壞時會自動向 {@link TeleporterSavedData} 註冊 / 註銷。
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
            TeleporterSavedData.get(serverLevel).add(GlobalPos.of(serverLevel.dimension(), pos), "");
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // 用鐵砧改過名的物品，放下時把名稱記進註冊表
        if (level instanceof ServerLevel serverLevel) {
            Component customName = stack.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                TeleporterSavedData.get(serverLevel).setName(GlobalPos.of(serverLevel.dimension(), pos), customName.getString());
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            TeleporterSavedData.get(serverLevel).remove(GlobalPos.of(serverLevel.dimension(), pos));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            TeleporterSavedData data = TeleporterSavedData.get(serverLevel);
            GlobalPos sourceGlobalPos = GlobalPos.of(serverLevel.dimension(), pos);
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
