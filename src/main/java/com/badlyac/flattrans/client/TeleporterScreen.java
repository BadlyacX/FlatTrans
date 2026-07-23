package com.badlyac.flattrans.client;

import com.badlyac.flattrans.network.TeleportRequestPayload;
import com.badlyac.flattrans.network.TeleporterDeletePayload;
import com.badlyac.flattrans.network.TeleporterEntry;
import com.badlyac.flattrans.network.TeleporterRenamePayload;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** 傳送目的地選擇畫面：清單由伺服器提供，點選後送出傳送請求；也可以在這裡幫目前的裝置改名。 */
public class TeleporterScreen extends Screen {
    private static final int BUTTONS_PER_PAGE = 6;
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 24;
    private static final int NAME_ROW_Y = 36;
    private static final int LIST_TOP = 66;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int DELETE_BUTTON_SIZE = 20;
    private static final int DELETE_BUTTON_GAP = 4;
    private static final int DESTINATION_BUTTON_WIDTH = BUTTON_WIDTH - DELETE_BUTTON_SIZE - DELETE_BUTTON_GAP;

    private final BlockPos source;
    private final String initialSourceName;
    private final List<TeleporterEntry> destinations;
    private int page = 0;
    private EditBox nameBox;
    // 需要延後到下一輪 render 開頭才能執行的動作（例如刪除後要 rebuildWidgets），
    // 不能在 renderWidget 當下直接執行：Minecraft.execute 在算繪執行緒呼叫時是同步立即跑，
    // 若在 super.render() 的 widget 疊代過程中改動 widget 清單會拋 ConcurrentModificationException。
    private Runnable pendingAction;

    public TeleporterScreen(BlockPos source, String sourceName, List<TeleporterEntry> destinations) {
        super(Component.translatable("screen.flattrans.teleporter.title"));
        this.source = source;
        this.initialSourceName = sourceName;
        this.destinations = new ArrayList<>(destinations);
    }

    private int pageCount() {
        return Math.max(1, (destinations.size() + BUTTONS_PER_PAGE - 1) / BUTTONS_PER_PAGE);
    }

    @Override
    protected void init() {
        // 換頁時保留玩家已輸入但尚未送出的名字，避免重建畫面把它蓋掉
        String currentValue = nameBox != null ? nameBox.getValue() : initialSourceName;
        nameBox = new EditBox(font, width / 2 - 100, NAME_ROW_Y, 200, 20,
                Component.translatable("screen.flattrans.teleporter.name_field"));
        nameBox.setMaxLength(MAX_NAME_LENGTH);
        nameBox.setValue(currentValue);
        addRenderableWidget(nameBox);

        int start = page * BUTTONS_PER_PAGE;
        int end = Math.min(start + BUTTONS_PER_PAGE, destinations.size());
        int left = (width - BUTTON_WIDTH) / 2;
        for (int i = start; i < end; i++) {
            TeleporterEntry entry = destinations.get(i);
            int y = LIST_TOP + (i - start) * BUTTON_SPACING;
            addRenderableWidget(Button.builder(labelFor(entry), button -> teleportTo(entry.pos()))
                    .bounds(left, y, DESTINATION_BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            addRenderableWidget(new HoldToConfirmButton(left + DESTINATION_BUTTON_WIDTH + DELETE_BUTTON_GAP, y,
                    DELETE_BUTTON_SIZE, BUTTON_HEIGHT, () -> schedulePendingAction(() -> deleteDestination(entry))));
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
        BlockPos pos = entry.pos().pos();
        String coords = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
        String name = entry.name().isEmpty()
                ? Component.translatable("screen.flattrans.teleporter.default_name").getString()
                : entry.name();

        // 跨維度的目的地額外標示所在維度，避免玩家搞混座標所屬的世界
        boolean sameDimension = Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.dimension().equals(entry.pos().dimension());
        if (sameDimension) {
            return Component.literal(name + " " + coords);
        }
        return Component.literal(name + " " + coords + " [" + entry.pos().dimension().location().getPath() + "]");
    }

    private void teleportTo(GlobalPos target) {
        PacketDistributor.sendToServer(new TeleportRequestPayload(source, target));
        onClose();
    }

    private void deleteDestination(TeleporterEntry entry) {
        destinations.remove(entry);
        PacketDistributor.sendToServer(new TeleporterDeletePayload(source, entry.pos()));
        page = Math.min(page, pageCount() - 1);
        rebuildWidgets();
    }

    private void schedulePendingAction(Runnable action) {
        pendingAction = action;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 在 super.render() 疊代 widget 清單「之前」執行，避免在疊代過程中改動清單造成 ConcurrentModificationException
        if (pendingAction != null) {
            Runnable action = pendingAction;
            pendingAction = null;
            action.run();
        }

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
    public void onClose() {
        if (nameBox != null) {
            String newName = nameBox.getValue().trim();
            if (!newName.equals(initialSourceName)) {
                PacketDistributor.sendToServer(new TeleporterRenamePayload(source, newName));
            }
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 需要按住 1 秒才會觸發的「X」刪除鈕，放開或按住時間不足都不會執行動作。 */
    private static final class HoldToConfirmButton extends AbstractWidget {
        private static final int HOLD_MILLIS = 1000;

        private final Runnable onConfirm;
        private long pressStart = -1;
        private boolean triggered;

        HoldToConfirmButton(int x, int y, int width, int height, Runnable onConfirm) {
            super(x, y, width, height, Component.literal("X"));
            this.onConfirm = onConfirm;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (triggered || button != 0 || !clicked(mouseX, mouseY)) {
                return false;
            }
            pressStart = Util.getMillis();
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            pressStart = -1;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int background = isHoveredOrFocused() ? 0xFFAA3333 : 0xFF802020;
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                    getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFF);

            if (pressStart >= 0 && !triggered) {
                long elapsed = Util.getMillis() - pressStart;
                int filled = Math.round(getWidth() * Math.min(1.0F, elapsed / (float) HOLD_MILLIS));
                guiGraphics.fill(getX(), getY() + getHeight() - 3, getX() + filled, getY() + getHeight(), 0xFFFFFFFF);
                if (elapsed >= HOLD_MILLIS) {
                    triggered = true;
                    pressStart = -1;
                    this.active = false;
                    onConfirm.run();
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
