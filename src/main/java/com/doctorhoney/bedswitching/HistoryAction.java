package com.doctorhoney.bedswitching;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HistoryAction(long time) implements CustomPacketPayload {
    static final Type<HistoryAction> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BedSwitching.MODID, "delete_history"));
    static final StreamCodec<ByteBuf, HistoryAction> CODEC = ByteBufCodecs.LONG.map(HistoryAction::new, HistoryAction::time);
    public Type<HistoryAction> type() { return TYPE; }

    static void handle(HistoryAction action, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !ModNetwork.accept(player, RequestLimits.Action.HISTORY)) return;
        var history = player.getData(ModAttachments.HISTORY);
        var updated = history.entries().stream().filter(e -> action.time != 0 && e.time() != action.time).toList();
        if (action.time == 0 || updated.size() != history.entries().size()) {
            MenuSync.update(player, new DeathHistory(updated));
            ModNetwork.notify(player, action.time == 0 ? "cleared" : "deleted");
        } else {
            MenuSync.reject(player, MenuSync.Failure.MISSING_RECORD);
        }
    }
}
