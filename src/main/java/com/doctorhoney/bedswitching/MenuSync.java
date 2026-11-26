package com.doctorhoney.bedswitching;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Menu data belongs to the connection's player, even after their entity leaves world tracking. */
final class MenuSync {
    record Points(BedPoints value) implements CustomPacketPayload {
        static final Type<Points> TYPE = id("points_snapshot");
        static final StreamCodec<RegistryFriendlyByteBuf, Points> CODEC = BedPoints.STREAM_CODEC.map(Points::new, Points::value).cast();
        public Type<Points> type() { return TYPE; }
    }

    record History(DeathHistory value) implements CustomPacketPayload {
        static final Type<History> TYPE = id("history_snapshot");
        static final StreamCodec<RegistryFriendlyByteBuf, History> CODEC =
                ByteBufCodecs.fromCodecWithRegistries(DeathHistory.CODEC.codec()).map(History::new, History::value);
        public Type<History> type() { return TYPE; }
    }

    enum Failure implements CustomPacketPayload {
        BUSY, MISSING_POINT, MISSING_RECORD, INVALID_NAME;
        static final Type<Failure> TYPE = id("menu_rejected");
        static final StreamCodec<RegistryFriendlyByteBuf, Failure> CODEC =
                ByteBufCodecs.idMapper(i -> values()[i], Failure::ordinal).cast();
        public Type<Failure> type() { return TYPE; }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BedSwitching.MODID, path));
    }

    static void update(ServerPlayer player, BedPoints points) {
        player.setData(ModAttachments.SAVED_BEDS, points);
        PacketDistributor.sendToPlayer(player, new Points(points));
    }

    static void update(ServerPlayer player, DeathHistory history) {
        player.setData(ModAttachments.HISTORY, history);
        PacketDistributor.sendToPlayer(player, new History(history));
    }

    static void sendCurrent(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Points(player.getData(ModAttachments.SAVED_BEDS)));
        PacketDistributor.sendToPlayer(player, new History(player.getData(ModAttachments.HISTORY)));
    }

    static void reject(ServerPlayer player, Failure reason) {
        PacketDistributor.sendToPlayer(player, reason);
    }
}
