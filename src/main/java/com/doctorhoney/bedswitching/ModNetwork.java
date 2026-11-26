package com.doctorhoney.bedswitching;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Registers the mod's custom payloads.
 */
public final class ModNetwork {
    static boolean accept(net.minecraft.server.level.ServerPlayer player, RequestLimits.Action action) {
        if (player.connection.player != player) return false;
        var limits = player.getData(ModAttachments.REQUEST_LIMITS);
        long now = System.nanoTime();
        if (limits.accept(action, now)) return true;
        if (limits.canReportBusy(now)) MenuSync.reject(player, MenuSync.Failure.BUSY);
        return false;
    }

    static void notify(net.minecraft.server.level.ServerPlayer player, String key) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("bedrespawn.ui." + key));
    }

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3")
                .playToClient(MenuSync.Points.TYPE, MenuSync.Points.CODEC)
                .playToClient(MenuSync.History.TYPE, MenuSync.History.CODEC)
                .playToClient(MenuSync.Failure.TYPE, MenuSync.Failure.CODEC)
                .playToServer(HistoryAction.TYPE, HistoryAction.CODEC, HistoryAction::handle)
                .playToServer(RespawnAtBedPayload.TYPE, RespawnAtBedPayload.STREAM_CODEC, RespawnAtBedPayload::handle)
                .playToServer(RemoveBedPayload.TYPE, RemoveBedPayload.STREAM_CODEC, RemoveBedPayload::handle)
                .playToServer(RenameBedPayload.TYPE, RenameBedPayload.STREAM_CODEC, RenameBedPayload::handle);
    }
}
