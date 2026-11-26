package com.doctorhoney.bedswitching;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Scrollable management screen for the beds saved by the mod.
 */
public class BedPointsScreen extends Screen {
    private static final int ROW_HEIGHT = 34;
    private static final int LIST_TOP = 54;
    private static final int GAP = 4;
    private static final int DEFAULT_RESPAWN_WIDTH = 64;
    private static final int RUSSIAN_EXTRA_WIDTH = 18;
    private static final int INFO_WIDTH = 20;
    private static final int EDIT_WIDTH = 20;
    private static final int DELETE_WIDTH = 22;

    private final Screen previousScreen;
    private final List<BedPoint> points;
    private static PointSort sort = PointSort.DATE;
    private BedPoints snapshot;
    private final net.minecraft.core.BlockPos origin;
    private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    private BedPoint nearest;
    private record Row(net.minecraft.world.item.ItemStack icon, Component caption) {}
    private final List<Row> rows = new ArrayList<>();
    private int scrollOffset;

    public BedPointsScreen(Screen previousScreen) {
        super(Component.translatable("bedrespawn.ui.title"));
        this.previousScreen = previousScreen;
        var player = Objects.requireNonNull(Minecraft.getInstance().player);
        var deaths = player.getData(ModAttachments.HISTORY).entries();
        origin = deaths.isEmpty() ? player.blockPosition() : deaths.getFirst().pos();
        dimension = deaths.isEmpty() ? player.level().dimension() : deaths.getFirst().dimension();
        points = new ArrayList<>();
        refresh();
    }

    private double distanceSquared(BedPoint point) { return origin.distSqr(point.pos()); }

    private Component distance(BedPoint point) {
        if (!dimension.equals(point.dimension())) return LocationScreen.label("other");
        var format = java.text.NumberFormat.getIntegerInstance(LocationScreen.locale());
        return LocationScreen.label("blocks", format.format(Math.round(Math.sqrt(distanceSquared(point)))));
    }

    private void refresh() {
        snapshot = Minecraft.getInstance().player.getData(ModAttachments.SAVED_BEDS);
        points.clear();
        points.addAll(snapshot.points());
        var distance = java.util.Comparator.comparingDouble(this::distanceSquared);
        nearest = points.stream().filter(p -> dimension.equals(p.dimension()) && p.available())
                .min(distance).orElse(null);
        sort.apply(points, dimension, origin);
    }

    public boolean isPauseScreen() { return false; }

    public void tick() {
        if (minecraft.player == null || !minecraft.player.isDeadOrDying()) { minecraft.setScreen(null); return; }
    }

    void pointsChanged() {
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        if (snapshot != minecraft.player.getData(ModAttachments.SAVED_BEDS)) refresh();

        int listWidth = Math.min(420, this.width - 20);
        int listX = (this.width - listWidth) / 2;
        int right = listX + listWidth;
        int respawnWidth = respawnWidth();

        int deleteX = right - GAP - DELETE_WIDTH;
        int editX = deleteX - GAP - EDIT_WIDTH;
        int infoX = editX - GAP - INFO_WIDTH;
        int respawnX = infoX - GAP - respawnWidth;
        int nameWidth = Math.max(40, respawnX - GAP - listX);

        scrollOffset = Math.clamp(scrollOffset, 0, Math.max(0, points.size() - visibleRowCount()));
        addRenderableWidget(Button.builder(LocationScreen.label("sort." + sort.name().toLowerCase(java.util.Locale.ROOT)), b -> {
            sort = PointSort.values()[(sort.ordinal() + 1) % PointSort.values().length];
            scrollOffset = 0;
            refresh();
            rebuildWidgets();
        }).bounds(width / 2 - 127, 28, 126, 20).build());
        addRenderableWidget(Button.builder(LocationScreen.label("history"), b ->
                minecraft.setScreen(new LocationScreen(this, null)))
                .bounds(width / 2 + 1, 28, 126, 20).build());

        rows.clear();
        int visibleRows = Math.min(visibleRowCount(), Math.max(0, this.points.size() - this.scrollOffset));
        for (int index = 0; index < visibleRows; index++) {
            BedPoint point = this.points.get(this.scrollOffset + index);
            int y = LIST_TOP + index * ROW_HEIGHT;
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(point.icon()));
            var caption = distance(point).copy();
            if (point == nearest) caption.append(" · ").append(LocationScreen.label("nearest"));
            rows.add(new Row(item.getDefaultInstance(), caption));

            this.addRenderableWidget(Button.builder(Component.literal(point.displayName()), button -> details(point))
                    .bounds(listX + 20, y, nameWidth - 20, 20)
                    .build());
            this.addRenderableWidget(Button.builder(Component.translatable("bedrespawn.ui.respawn"), button -> respawn(point))
                    .bounds(respawnX, y, respawnWidth, 22)
                    .tooltip(Tooltip.create(point.charges() == 0 ? LocationScreen.label("nocharges") : LocationScreen.label("respawn")))
                    .build());
            this.addRenderableWidget(Button.builder(Component.translatable("bedrespawn.ui.info.icon"), button -> details(point))
                    .tooltip(Tooltip.create(infoTooltip(point)))
                    .bounds(infoX, y, INFO_WIDTH, 22)
                    .build());
            this.addRenderableWidget(Button.builder(Component.translatable("bedrespawn.ui.edit.icon"), button -> edit(point))
                    .bounds(editX, y, EDIT_WIDTH, 22)
                    .build());
            this.addRenderableWidget(Button.builder(Component.literal("X"), button -> minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ok -> {
                        if (ok) remove(point);
                        minecraft.setScreen(this);
                    }, title, LocationScreen.label("confirm.point"))))
                    .bounds(deleteX, y, DELETE_WIDTH, 22)
                    .build());
        }

        this.addRenderableWidget(Button.builder(Component.translatable("bedrespawn.ui.back"), button -> back())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFFFF);

        int left = (width - Math.min(420, width - 20)) / 2;
        for (int i = 0; i < rows.size(); i++) {
            int y = LIST_TOP + i * ROW_HEIGHT;
            guiGraphics.renderItem(rows.get(i).icon(), left, y + 2);
            guiGraphics.drawString(font, rows.get(i).caption(), left + 20, y + 22, 0xffcccccc);
        }
        if (this.points.isEmpty()) {
            guiGraphics.drawCenteredString(
                    this.font, Component.translatable("bedrespawn.ui.empty"), this.width / 2, this.height / 2, 0xFFAAAAAA);
        } else if (this.points.size() > visibleRowCount()) {
            int listWidth = Math.min(420, this.width - 20);
            int listX = (this.width - listWidth) / 2 + listWidth + 4;
            int trackHeight = visibleRowCount() * ROW_HEIGHT;
            guiGraphics.fill(listX, LIST_TOP, listX + 4, LIST_TOP + trackHeight, 0x55FFFFFF);

            int thumbHeight = Math.max(12, trackHeight * visibleRowCount() / this.points.size());
            int maxScroll = this.points.size() - visibleRowCount();
            int thumbY = LIST_TOP + (trackHeight - thumbHeight) * this.scrollOffset / Math.max(1, maxScroll);
            guiGraphics.fill(listX, thumbY, listX + 4, thumbY + thumbHeight, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, this.points.size() - visibleRowCount());
        int newOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (scrollY > 0 ? 1 : -1)));
        if (newOffset != this.scrollOffset) {
            this.scrollOffset = newOffset;
            this.rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            back();
            return true;
        }
        return super.keyPressed(event);
    }

    private int respawnWidth() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        return "ru_ru".equalsIgnoreCase(language) ? DEFAULT_RESPAWN_WIDTH + RUSSIAN_EXTRA_WIDTH : DEFAULT_RESPAWN_WIDTH;
    }

    private void details(BedPoint point) {
        minecraft.setScreen(new LocationScreen(this, point));
    }

    Component infoTooltip(BedPoint point) {
        return Component.empty()
                .append(Component.translatable("bedrespawn.ui.info.name", point.displayName()))
                .append(Component.literal("\n"))
                .append(Component.translatable("bedrespawn.ui.info.dimension", point.dimensionComponent()))
                .append(Component.literal("\n"))
                .append(Component.translatable("bedrespawn.ui.info.coords",
                        point.pos().getX(), point.pos().getY(), point.pos().getZ()))
                .append(Component.literal("\n"))
                .append(Component.translatable("bedrespawn.ui.info.biome", point.biomeComponent()))
                .append("\n").append(LocationScreen.label("type", LocationScreen.label(point.anchor() ? "anchor" : "bed")))
                .append("\n").append(LocationScreen.label("distance", distance(point)))
                .append(point.anchor() ? Component.literal("\n").append(point.charges() < 0 ? LocationScreen.label("unknowncharges")
                        : LocationScreen.label("charges", point.charges())) : Component.empty());
    }

    private int visibleRowCount() {
        return Math.max(1, (this.height - LIST_TOP - 50) / ROW_HEIGHT);
    }

    private void respawn(BedPoint point) {
        ClientPacketDistributor.sendToServer(new RespawnAtBedPayload(point.dimension(), point.pos()));

    }

    private void remove(BedPoint point) {
        ClientPacketDistributor.sendToServer(new RemoveBedPayload(point.dimension(), point.pos()));
    }

    private void edit(BedPoint point) {
        Minecraft.getInstance().setScreen(new BedPointEditScreen(this, point));
    }

    private void back() {
        Minecraft.getInstance().setScreen(this.previousScreen);
    }
}
