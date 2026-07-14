package com.badlyac.flattrans.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 一個傳送目的地：位置 + 名稱（名稱可為空字串，客戶端會改用座標顯示）。 */
public record TeleporterEntry(BlockPos pos, String name) {
    public static final StreamCodec<ByteBuf, TeleporterEntry> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TeleporterEntry::pos,
            ByteBufCodecs.STRING_UTF8, TeleporterEntry::name,
            TeleporterEntry::new);
}
