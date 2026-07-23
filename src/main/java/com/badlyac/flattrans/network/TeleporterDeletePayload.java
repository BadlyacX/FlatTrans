package com.badlyac.flattrans.network;

import com.badlyac.flattrans.FlatTrans;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S：玩家在傳送裝置選單裡刪除清單中的一個目的地（長按 X 觸發）。 */
public record TeleporterDeletePayload(BlockPos source, GlobalPos target) implements CustomPacketPayload {
    public static final Type<TeleporterDeletePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FlatTrans.MODID, "teleporter_delete"));

    public static final StreamCodec<ByteBuf, TeleporterDeletePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TeleporterDeletePayload::source,
            GlobalPos.STREAM_CODEC, TeleporterDeletePayload::target,
            TeleporterDeletePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
