package com.doctorhoney.bedswitching;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Compact dialog for changing the custom name of one saved bed.
 */
public class BedPointEditScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 136;

    private final BedPointsScreen parent;
    private final BedPoint point;
    private EditBox nameField;
    private int panelX;
    private int panelY;

    public BedPointEditScreen(BedPointsScreen parent, BedPoint point) {
        super(Component.translatable("bedrespawn.ui.edit.title"));
        this.parent = parent;
        this.point = point;
    }

    @Override
    protected void init() {
        super.init();

        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;

        this.nameField = new EditBox(this.font, this.panelX + 16, this.panelY + 40,
                PANEL_WIDTH - 32, 20, Component.translatable("bedrespawn.ui.edit.hint"));
        this.nameField.setMaxLength(BedPoint.MAX_NAME_LENGTH);
        this.nameField.setValue(this.point.customName());
        this.addRenderableWidget(this.nameField);
        this.setInitialFocus(this.nameField);

        int buttonY = this.panelY + PANEL_HEIGHT - 30;
        this.addRenderableWidget(Button.builder(Component.translatable("bedrespawn.ui.edit.save"), button -> save())
                .bounds(this.panelX + 16, buttonY, 130, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("bedrespawn.ui.edit.cancel"), button -> cancel())
                .bounds(this.panelX + PANEL_WIDTH - 146, buttonY, 130, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(this.panelX - 4, this.panelY - 4,
                this.panelX + PANEL_WIDTH + 4, this.panelY + PANEL_HEIGHT + 4, 0xC0000000);
        guiGraphics.fill(this.panelX, this.panelY,
                this.panelX + PANEL_WIDTH, this.panelY + PANEL_HEIGHT, 0xFF202028);
        guiGraphics.fill(this.panelX, this.panelY,
                this.panelX + PANEL_WIDTH, this.panelY + 1, 0xFF808090);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.panelY + 12, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                font.plainSubstrByWidth(Component.translatable("bedrespawn.ui.edit.base", this.point.displayName()).getString(), PANEL_WIDTH - 16),
                this.width / 2, this.panelY + 66, 0xFFAAAAAA);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("bedrespawn.ui.edit.note"),
                this.width / 2, this.panelY + 80, 0xFF888888);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            cancel();
            return true;
        }
        if (event.isConfirmation()) {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    private void save() {
        String name = this.nameField.getValue().trim();
        if (name.length() > BedPoint.MAX_NAME_LENGTH) {
            name = name.substring(0, BedPoint.MAX_NAME_LENGTH);
        }

        ClientPacketDistributor.sendToServer(
                new RenameBedPayload(this.point.dimension(), this.point.pos(), name));

        Minecraft.getInstance().setScreen(this.parent);
    }

    private void cancel() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
