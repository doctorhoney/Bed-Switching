package com.doctorhoney.bedswitching;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Runs only in the isolated quick-play client started with -PmenuChecks. */
@EventBusSubscriber(modid = BedSwitching.MODID, value = Dist.CLIENT)
public final class MenuChecks {
    private static int phase;
    private static int ticks;
    private static String lastScreen;
    private static BedPoint target;
    private static BedPointsScreen list;
    private static LocationScreen detail;
    private static LocationScreen history;

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        String current = mc.screen == null ? "game" : mc.screen.getClass().getSimpleName();
        if (!current.equals(lastScreen)) { BedSwitching.LOGGER.info("Menu check {} screen {}", phase, current); lastScreen = current; }
        if (++ticks > 3600) throw new AssertionError("Timed out at " + phase + " screen " + mc.screen);
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (Boolean.getBoolean("bedswitching.checkRestart")) {
            if (!mc.player.hasData(ModAttachments.HISTORY) || !mc.player.hasData(ModAttachments.SAVED_BEDS)) return;
            if (phase == 0) {
                phase++;
                var server = mc.getSingleplayerServer();
                var uuid = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    require(player.getData(ModAttachments.HISTORY).entries().isEmpty());
                    require(player.getData(ModAttachments.SAVED_BEDS).points().isEmpty());
                    mc.execute(() -> {
                        require(mc.player.getData(ModAttachments.HISTORY).entries().isEmpty());
                        require(mc.player.getData(ModAttachments.SAVED_BEDS).points().isEmpty());
                        BedSwitching.LOGGER.info("CLIENT RESTART CHECK PASSED");
                        mc.stop();
                    });
                });
            }
            return;
        }
        switch (phase) {
            case 0 -> {
                if (mc.screen != null) return;
                var server = mc.getSingleplayerServer();
                var uuid = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    var pos = player.blockPosition().above(3);
                    var level = player.level();
                    var state = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState();
                    level.setBlock(pos, state.setValue(net.minecraft.world.level.block.BedBlock.PART,
                            net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
                    MenuSync.update(player, new BedPoints(List.of(BedPoint.of(level, pos).withCustomName("Before"))));
                    player.setData(ModAttachments.HISTORY, new DeathHistory().record(player).record(player));
                    player.setHealth(0);
                    player.die(player.damageSources().genericKill());
                });
                phase++;
            }
            case 1 -> {
                if (!(mc.screen instanceof DeathScreen death) || mc.player.getData(ModAttachments.SAVED_BEDS).points().isEmpty()) return;
                target = mc.player.getData(ModAttachments.SAVED_BEDS).points().getFirst();
                list = new BedPointsScreen(death);
                mc.setScreen(list);
                press(mc, LocationScreen.label("sort.date").getString());
                rename("First");
                phase++;
            }
            case 2 -> {
                if (!has(mc, "First")) return;
                require(mc.screen == list);
                press(mc, "First");
                detail = (LocationScreen) mc.screen;
                rename("Second");
                phase++;
            }
            case 3 -> {
                if (!detailText(detail).contains("Second")) return;
                require(mc.screen == detail);
                detail.onClose();
                require(has(mc, "Second"));
                press(mc, LocationScreen.label("history").getString());
                history = (LocationScreen) mc.screen;
                var entries = mc.player.getData(ModAttachments.HISTORY).entries();
                require(entries.size() == 3);
                ClientPacketDistributor.sendToServer(new HistoryAction(entries.getFirst().time()));
                phase++;
            }
            case 4 -> {
                if (mc.player.getData(ModAttachments.HISTORY).entries().size() != 2) return;
                require(mc.screen == history);
                ClientPacketDistributor.sendToServer(new HistoryAction(0));
                phase++;
            }
            case 5 -> {
                if (!detailText(history).contains(LocationScreen.label("history.empty").getString())) return;
                require(mc.screen == history && !has(mc, LocationScreen.label("delete").getString()));
                history.onClose();
                ClientPacketDistributor.sendToServer(new RemoveBedPayload(target.dimension(), target.pos()));
                phase++;
            }
            case 6 -> {
                if (has(mc, "Second")) return;
                require(mc.screen == list && has(mc, LocationScreen.label("sort.nearest").getString()));
                BedSwitching.LOGGER.info("OPEN MENU REGRESSION CHECKS PASSED");
                phase++;
                mc.stop();
            }
        }
    }

    private static String detailText(LocationScreen screen) {
        try {
            var field = LocationScreen.class.getDeclaredField("lines");
            field.setAccessible(true);
            var text = new StringBuilder();
            for (var line : (List<?>) field.get(screen)) {
                ((net.minecraft.util.FormattedCharSequence) line).accept((i, style, cp) -> {
                    text.appendCodePoint(cp);
                    return true;
                });
            }
            return text.toString();
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void rename(String name) {
        ClientPacketDistributor.sendToServer(new RenameBedPayload(target.dimension(), target.pos(), name));
    }

    private static boolean has(Minecraft mc, String label) {
        return mc.screen.children().stream().anyMatch(w -> w instanceof Button b && b.getMessage().getString().equals(label));
    }

    private static void press(Minecraft mc, String label) {
        mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(label)).findFirst().orElseThrow().onPress(null);
    }

    private static void require(boolean test) { if (!test) throw new AssertionError("Menu check phase " + phase); }
}
