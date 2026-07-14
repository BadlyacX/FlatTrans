package com.badlyac.flattrans.network;

import com.badlyac.flattrans.FlatTrans;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S：玩家在傳送裝置選單裡幫這個裝置改名。 */
public record TeleporterRenamePayload(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<TeleporterRenamePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FlatTrans.MODID, "teleporter_rename"));

    public static final StreamCodec<ByteBuf, TeleporterRenamePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TeleporterRenamePayload::pos,
            ByteBufCodecs.STRING_UTF8, TeleporterRenamePayload::name,
            TeleporterRenamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
