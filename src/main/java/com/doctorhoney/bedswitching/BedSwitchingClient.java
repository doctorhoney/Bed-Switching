package com.doctorhoney.bedswitching;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Client entry point: adds the respawn point button to the vanilla death screen.
 */
@Mod(value = BedSwitching.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BedSwitching.MODID, value = Dist.CLIENT)
public class BedSwitchingClient {
    @SubscribeEvent
    static void registerClientHandlers(net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent event) {
        event.register(MenuSync.Points.TYPE, (packet, context) -> {
            context.player().setData(ModAttachments.SAVED_BEDS, packet.value());
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof BedPointsScreen list) list.pointsChanged();
            if (screen instanceof LocationScreen location) location.dataChanged(false);
        });
        event.register(MenuSync.History.TYPE, (packet, context) -> {
            context.player().setData(ModAttachments.HISTORY, packet.value());
            if (Minecraft.getInstance().screen instanceof LocationScreen location) location.dataChanged(true);
        });
        event.register(MenuSync.Failure.TYPE, (reason, context) -> {
            var client = Minecraft.getInstance();
            net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(client.getToastManager(),
                    net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    LocationScreen.label("rejected"), LocationScreen.label("failure." + reason.name().toLowerCase(java.util.Locale.ROOT)));
        });
    }

    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof DeathScreen deathScreen)) {
            return;
        }

        event.addListener(Button.builder(
                        Component.translatable("bedrespawn.ui.respawn_point"),
                        button -> Minecraft.getInstance().setScreen(new BedPointsScreen(deathScreen)))
                .bounds(8, screen.height - 28, 160, 20)
                .build());
    }
}
