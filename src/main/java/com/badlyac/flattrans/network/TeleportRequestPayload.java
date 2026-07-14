package com.badlyac.flattrans.network;

import com.badlyac.flattrans.FlatTrans;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S：玩家在畫面中選好目的地後送出的傳送請求。 */
public record TeleportRequestPayload(BlockPos source, BlockPos target) implements CustomPacketPayload {
    public static final Type<TeleportRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FlatTrans.MODID, "teleport_request"));

    public static final StreamCodec<ByteBuf, TeleportRequestPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TeleportRequestPayload::source,
            BlockPos.STREAM_CODEC, TeleportRequestPayload::target,
            TeleportRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
