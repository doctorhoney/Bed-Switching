package com.doctorhoney.bedswitching;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by the client when a player chooses one of the saved beds on the death screen.
 */
public record RespawnAtBedPayload(ResourceKey<Level> dimension, BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RespawnAtBedPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BedSwitching.MODID, "respawn_at_bed"));

    public static final StreamCodec<ByteBuf, RespawnAtBedPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), RespawnAtBedPayload::dimension,
            BlockPos.STREAM_CODEC, RespawnAtBedPayload::pos,
            RespawnAtBedPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static boolean safe(ServerPlayer player, ServerLevel level, BedPoint point, BlockState state) {
        var pos = point.pos();
        return point.anchor()
                ? state.getBlock() instanceof RespawnAnchorBlock && state.getValue(RespawnAnchorBlock.CHARGE) > 0
                    && RespawnAnchorBlock.canSetSpawn(level, pos)
                    && RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, pos).isPresent()
                : state.getBlock() instanceof BedBlock && level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos).canSetSpawn(level)
                    && state.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
                    && BedBlock.findStandUpPosition(EntityType.PLAYER, level, pos, state.getValue(BedBlock.FACING), player.getYRot()).isPresent();
    }

    public static void handle(RespawnAtBedPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.isDeadOrDying() || !ModNetwork.accept(player, RequestLimits.Action.RESPAWN) || player.level().getLevelData().isHardcore()) {
            return;
        }

        MinecraftServer server = player.level().getServer();
        var point = player.getData(ModAttachments.SAVED_BEDS).points().stream()
                .filter(p -> p.matches(payload.dimension(), payload.pos())).findFirst().orElse(null);
        if (point == null || server.getPlayerList().getPlayer(player.getUUID()) != player) return;
        ServerLevel level = server.getLevel(point.dimension());
        var pos = point.pos();
        if (level == null || !level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) return;
        BlockState state = level.getBlockState(pos);
        boolean exists = point.anchor() ? state.getBlock() instanceof RespawnAnchorBlock : state.getBlock() instanceof BedBlock;
        var saved = player.getData(ModAttachments.SAVED_BEDS);
        if (!exists) {
            MenuSync.update(player, saved.remove(point));
            ModNetwork.notify(player, point.anchor() ? "missing.anchor" : "missing.bed");
            return;
        }
        if (!safe(player, level, point, state)) {
            var fresh = BedPoint.of(level, pos);
            if (fresh.pos().equals(pos)) MenuSync.update(player, saved.add(fresh));

            ModNetwork.notify(player, point.anchor() && state.getValue(RespawnAnchorBlock.CHARGE) == 0 ? "nocharges" : "unsafe");
            return;
        }
        player.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(
                                payload.dimension(), payload.pos().immutable(), player.getYRot(), player.getXRot()),
                        false),
                false);

        var config = player.getRespawnConfig();
        if (config == null || config.forced() || !config.respawnData().pos().equals(pos)
                || !config.respawnData().dimension().equals(point.dimension())) return;

        player.connection.handleClientCommand(new ServerboundClientCommandPacket(
                ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
    }
}
