package com.badlyac.flattrans.client;

import com.badlyac.flattrans.network.TeleporterListPayload;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 只會在客戶端被載入，不要從共用程式碼直接參照這個類別的方法參照。 */
public class ClientPayloadHandler {
    public static void handleTeleporterList(TeleporterListPayload payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new TeleporterScreen(payload.source(), payload.sourceName(), payload.destinations()));
    }
}
