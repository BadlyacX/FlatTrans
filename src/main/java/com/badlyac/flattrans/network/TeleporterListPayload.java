package com.badlyac.flattrans.network;

import com.badlyac.flattrans.FlatTrans;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C：玩家打開傳送裝置時，伺服器送來可選目的地清單。 */
public record TeleporterListPayload(BlockPos source, List<TeleporterEntry> destinations) implements CustomPacketPayload {
    public static final Type<TeleporterListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FlatTrans.MODID, "teleporter_list"));

    public static final StreamCodec<ByteBuf, TeleporterListPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TeleporterListPayload::source,
            TeleporterEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), TeleporterListPayload::destinations,
            TeleporterListPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
