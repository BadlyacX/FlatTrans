package com.badlyac.flattrans.client;

import com.badlyac.flattrans.network.TeleportRequestPayload;
import com.badlyac.flattrans.network.TeleporterEntry;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** 傳送目的地選擇畫面：清單由伺服器提供，點選後送出傳送請求。 */
public class TeleporterScreen extends Screen {
    private static final int BUTTONS_PER_PAGE = 6;
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 24;
    private static final int LIST_TOP = 40;

    private final BlockPos source;
    private final List<TeleporterEntry> destinations;
    private int page = 0;

    public TeleporterScreen(BlockPos source, List<TeleporterEntry> destinations) {
        super(Component.translatable("screen.flattrans.teleporter.title"));
        this.source = source;
        this.destinations = destinations;
    }

    private int pageCount() {
        return Math.max(1, (destinations.size() + BUTTONS_PER_PAGE - 1) / BUTTONS_PER_PAGE);
    }

    @Override
    protected void init() {
        int start = page * BUTTONS_PER_PAGE;
        int end = Math.min(start + BUTTONS_PER_PAGE, destinations.size());
        for (int i = start; i < end; i++) {
            TeleporterEntry entry = destinations.get(i);
            int y = LIST_TOP + (i - start) * BUTTON_SPACING;
            addRenderableWidget(Button.builder(labelFor(entry), button -> teleportTo(entry.pos()))
                    .bounds((width - BUTTON_WIDTH) / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        }

        int bottomY = LIST_TOP + BUTTONS_PER_PAGE * BUTTON_SPACING + 8;
        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                page = Math.max(0, page - 1);
                rebuildWidgets();
            }).bounds(width / 2 - 140, bottomY, 20, BUTTON_HEIGHT).build());

            addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                page = Math.min(pageCount() - 1, page + 1);
                rebuildWidgets();
            }).bounds(width / 2 + 120, bottomY, 20, BUTTON_HEIGHT).build());
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 - 50, bottomY, 100, BUTTON_HEIGHT)
                .build());
    }

    private Component labelFor(TeleporterEntry entry) {
        BlockPos pos = entry.pos();
        String coords = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
        return entry.name().isEmpty()
                ? Component.literal(coords)
                : Component.literal(entry.name() + " " + coords);
    }

    private void teleportTo(BlockPos target) {
        PacketDistributor.sendToServer(new TeleportRequestPayload(source, target));
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);

        if (destinations.isEmpty()) {
            guiGraphics.drawCenteredString(font,
                    Component.translatable("screen.flattrans.teleporter.empty"),
                    width / 2, LIST_TOP + 40, 0xAAAAAA);
        }

        if (pageCount() > 1) {
            Component pageText = Component.translatable("screen.flattrans.teleporter.page",
                    page + 1, pageCount());
            int bottomY = LIST_TOP + BUTTONS_PER_PAGE * BUTTON_SPACING + 8;
            guiGraphics.drawCenteredString(font, pageText, width / 2, bottomY - 12, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
