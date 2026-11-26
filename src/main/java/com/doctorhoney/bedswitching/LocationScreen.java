package com.doctorhoney.bedswitching;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

final class LocationScreen extends Screen {
    private final BedPointsScreen parent;
    private final BedPoint selected;
    private DeathHistory history;
    private int index;
    private java.util.List<net.minecraft.util.FormattedCharSequence> lines;
    private int offset;
    private long copiedUntil;

    LocationScreen(BedPointsScreen parent, BedPoint selected) {
        super(label(selected == null ? "history" : "details"));
        this.parent = parent;
        this.selected = selected;
    }

    static Component label(String suffix, Object... args) {
        return Component.translatable("bedrespawn.ui." + suffix, args);
    }

    static Locale locale() {
        return Locale.forLanguageTag(Minecraft.getInstance().getLanguageManager().getSelected().replace('_', '-'));
    }

    private DeathHistory.Entry entry() {
        return history.entries().get(index);
    }

    protected void init() {
        int y = height - 26;
        int center = width / 2;
        button(label("back"), center + 2, y, 120, this::onClose);
        Component message = label("failure.missing_point");
        BlockPos coords = null;
        if (selected != null) {
            var current = minecraft.player.getData(ModAttachments.SAVED_BEDS).points().stream()
                    .filter(p -> p.matches(selected.dimension(), selected.pos())).findFirst();
            if (current.isPresent()) {
                message = parent.infoTooltip(current.get());
                coords = current.get().pos();
            }
        } else {
            history = minecraft.player.getData(ModAttachments.HISTORY);
            int count = history.entries().size();
            index = Math.clamp(index, 0, Math.max(0, count - 1));
            message = label("history.empty");
            if (count > 0) {
                var death = entry();
                coords = death.pos();
                String date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale())
                        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(death.time()));
                message = label("history.record", date, death.cause(), BedPoint.dimensionComponent(death.dimension()),
                        BedPoint.biomeComponent(death.biome()), coordinates(coords));
                button(Component.literal("‹"), center - 121, 28, 30, () -> page(-1)).active = index > 0;
                button(Component.literal("›"), center + 91, 28, 30, () -> page(1)).active = index + 1 < count;
                button(label("delete"), center - 122, y - 24, 120, () -> confirm(death.time()));
                button(label("clear"), center + 2, y - 24, 120, () -> confirm(0));
            }
        }
        if (coords != null) {
            String value = coordinates(coords);
            button(label("copy"), center - 122, y, 120, () -> {
                minecraft.keyboardHandler.setClipboard(value);
                copiedUntil = System.currentTimeMillis() + 1800;
            });
        }
        lines = font.split(message, width - 32);
        offset = Math.clamp(offset, 0, Math.max(0, lines.size() - visibleLines()));
    }

    private static String coordinates(BlockPos pos) {
        return "X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ();
    }

    private Button button(Component text, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(text, b -> action.run()).bounds(x, y, width, 20).build());
    }

    private void page(int delta) {
        index += delta;
        offset = 0;
        rebuildWidgets();
    }

    private void confirm(long time) {
        minecraft.setScreen(new ConfirmScreen(ok -> {
            if (ok) ClientPacketDistributor.sendToServer(new HistoryAction(time));
            minecraft.setScreen(this);
        }, title, label(time == 0 ? "confirm.clear" : "confirm.death")));
    }

    public boolean isPauseScreen() { return false; }

    void dataChanged(boolean historyChanged) {
        if (historyChanged != (selected == null)) return;
        if (historyChanged && history != null && !history.entries().isEmpty()) {
            long visible = entry().time();
            var entries = minecraft.player.getData(ModAttachments.HISTORY).entries();
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).time() == visible) { index = i; break; }
            }
        }
        rebuildWidgets();
    }

    public void onClose() { minecraft.setScreen(parent); }

    private int visibleLines() { return Math.max(1, (height - 125) / 11); }

    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        offset = Math.clamp(offset - (int)Math.signum(dy), 0, Math.max(0, lines.size() - visibleLines()));
        return true;
    }

    public void render(GuiGraphics g, int x, int y, float tick) {
        super.render(g, x, y, tick);
        g.drawCenteredString(font, title, width / 2, 12, -1);
        if (selected == null && !history.entries().isEmpty())
            g.drawCenteredString(font, (index + 1) + " / " + history.entries().size(), width / 2, 34, -1);
        for (int i = offset; i < Math.min(lines.size(), offset + visibleLines()); i++)
            g.drawString(font, lines.get(i), 16, 54 + (i - offset) * 11, -1);
        if (System.currentTimeMillis() < copiedUntil)
            g.drawCenteredString(font, label("copied"), width / 2, height - 64, 0xff99ff99);
    }
}
