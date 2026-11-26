package com.doctorhoney.bedswitching;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by the client after a player renames (or resets the name of) a saved bed.
 */
public record RenameBedPayload(ResourceKey<Level> dimension, BlockPos pos, String name) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RenameBedPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BedSwitching.MODID, "rename_bed"));

    public static final StreamCodec<ByteBuf, RenameBedPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), RenameBedPayload::dimension,
            BlockPos.STREAM_CODEC, RenameBedPayload::pos,
            ByteBufCodecs.stringUtf8(BedPoint.MAX_NAME_LENGTH), RenameBedPayload::name,
            RenameBedPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RenameBedPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !ModNetwork.accept(player, RequestLimits.Action.RENAME)) {
            return;
        }

        String name = payload.name() == null ? "" : payload.name().trim();
        if (name.length() > BedPoint.MAX_NAME_LENGTH) {
            name = name.substring(0, BedPoint.MAX_NAME_LENGTH);
        }

        BedPoints beds = player.getData(ModAttachments.SAVED_BEDS);
        if (name.codePoints().anyMatch(Character::isISOControl)) {
            MenuSync.reject(player, MenuSync.Failure.INVALID_NAME);
            return;
        }
        if (beds.points().stream().noneMatch(p -> p.matches(payload.dimension(), payload.pos()))) {
            MenuSync.reject(player, MenuSync.Failure.MISSING_POINT);
            return;
        }
        MenuSync.update(player, beds.rename(payload.dimension(), payload.pos(), name));
        ModNetwork.notify(player, "renamed");
    }
}
