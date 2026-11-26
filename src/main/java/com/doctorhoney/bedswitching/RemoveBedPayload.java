package com.doctorhoney.bedswitching;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by the client when a respawn point is removed in the management screen.
 */
public record RemoveBedPayload(ResourceKey<Level> dimension, BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RemoveBedPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BedSwitching.MODID, "remove_bed"));

    public static final StreamCodec<ByteBuf, RemoveBedPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), RemoveBedPayload::dimension,
            BlockPos.STREAM_CODEC, RemoveBedPayload::pos,
            RemoveBedPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoveBedPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !ModNetwork.accept(player, RequestLimits.Action.REMOVE)) {
            return;
        }

        BedPoints beds = player.getData(ModAttachments.SAVED_BEDS);
        beds.points().stream().filter(p -> p.matches(payload.dimension(), payload.pos())).findFirst()
                .ifPresentOrElse(point -> {
                    MenuSync.update(player, beds.remove(point));
                    ModNetwork.notify(player, "removed");
                }, () -> MenuSync.reject(player, MenuSync.Failure.MISSING_POINT));
    }
}
