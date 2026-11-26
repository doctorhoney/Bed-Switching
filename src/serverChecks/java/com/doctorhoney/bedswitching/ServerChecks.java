package com.doctorhoney.bedswitching;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;

@EventBusSubscriber(modid = BedSwitching.MODID)
public final class ServerChecks {
    private static boolean ran;

    @SubscribeEvent
    static void run(ServerTickEvent.Pre event) {
        if (ran || !(event.getServer() instanceof GameTestServer server)) return;
        ran = true;
        var level = server.overworld();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "Regression"), false);
        var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        // Apply the actual outgoing sync bytes to a separate holder as the client decoder does.
        var mirror = new AttachmentHolder() {};
        var connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void send(Packet<?> packet, ChannelFutureListener callback, boolean flush) {
                if (packet instanceof ClientboundCustomPayloadPacket message) {
                    var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                    try {
                        if (message.payload() instanceof MenuSync.Points points) {
                            MenuSync.Points.CODEC.encode(buf, points);
                            mirror.setData(ModAttachments.SAVED_BEDS, MenuSync.Points.CODEC.decode(buf).value());
                        } else if (message.payload() instanceof MenuSync.History history) {
                            MenuSync.History.CODEC.encode(buf, history);
                            mirror.setData(ModAttachments.HISTORY, MenuSync.History.CODEC.decode(buf).value());
                        }
                    } finally {
                        buf.release();
                    }
                }
                super.send(packet, callback, flush);
            }
        };
        new EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.ChannelAttributes.getOrCreateAdHocChannels(connection)
                .addAll(java.util.List.of(MenuSync.Points.TYPE.id(), MenuSync.History.TYPE.id(), MenuSync.Failure.TYPE.id(),
                        net.neoforged.neoforge.network.payload.SyncAttachmentsPayload.TYPE.id()));
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        var listener = player.connection;
        var context = new ServerPayloadContext(listener, RespawnAtBedPayload.TYPE.id());
        var beds = new ArrayList<BedPoint>();
        for (int i = 0; i < 10; i++) {
            var pos = new BlockPos(16 + i * 8, 120, 16);
            platform(level, pos);
            var state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            level.setBlock(pos.south(), state.setValue(BedBlock.PART, BedPart.FOOT), 2);
            level.setBlock(pos, state.setValue(BedBlock.PART, BedPart.HEAD), 2);
            beds.add(BedPoint.of(level, pos));
        }
        player.setData(ModAttachments.SAVED_BEDS, new BedPoints(beds));
        for (int i = 0; i < 10; i++) {
            var target = beds.get(i < 5 ? 0 : i);
            player.setHealth(0);
            player.die(player.damageSources().genericKill());
            var request = new RespawnAtBedPayload(level.dimension(), target.pos());
            RespawnAtBedPayload.handle(request, context);
            require(listener.player != player && listener.player.isAlive(), "respawn " + i);
            require(!ModNetwork.accept(player, RequestLimits.Action.REMOVE), "stale player");
            player = listener.player;
            require(player.blockPosition().distSqr(target.pos()) < 10, "selected bed");
            RespawnAtBedPayload.handle(request, context);
            require(listener.player == player, "duplicate request while alive");
        }
        for (int i = 0; i < 4; i++) {
            var point = beds.get(i < 3 ? 0 : 1);
            var name = "Base " + i;
            RenameBedPayload.handle(new RenameBedPayload(level.dimension(), point.pos(), name), context);
            require(mirror.getData(ModAttachments.SAVED_BEDS).points().stream().anyMatch(
                    b -> b.matches(level.dimension(), point.pos()) && b.displayName().equals(name)), "rename snapshot");
        }
        var nether = server.getLevel(Level.NETHER);
        var anchor = new BlockPos(16, 160, 16);
        platform(nether, anchor);
        nether.setBlock(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 2), 2);
        player.setData(ModAttachments.SAVED_BEDS, player.getData(ModAttachments.SAVED_BEDS).add(BedPoint.of(nether, anchor)));
        player.setHealth(0);
        player.die(player.damageSources().genericKill());
        var request = new RespawnAtBedPayload(Level.NETHER, anchor);
        RespawnAtBedPayload.handle(request, context);
        require(listener.player != player && listener.player.level() == nether, "anchor respawn");
        RespawnAtBedPayload.handle(request, context);
        require(nether.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE) == 1, "single anchor charge");
        player = listener.player;
        player.setHealth(0);
        player.die(player.damageSources().genericKill());
        player.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
        var bad = new RespawnAtBedPayload(Level.OVERWORLD, new BlockPos(240000, 120, 240000));
        RespawnAtBedPayload.handle(bad, context);
        require(listener.player == player && !level.hasChunkAt(bad.pos()), "unregistered destination");
        PointSort.FARTHEST.apply(beds, level.dimension(), beds.getFirst().pos());
        for (var target : beds) {
            var history = player.getData(ModAttachments.HISTORY);
            long id = history.entries().getFirst().time();
            HistoryAction.handle(new HistoryAction(id), context);
            var updated = player.getData(ModAttachments.HISTORY);
            require(updated.entries().size() == history.entries().size() - 1 && updated.entries().stream().noneMatch(e -> e.time() == id), "history deletion");
            require(updated.equals(mirror.getData(ModAttachments.HISTORY)), "history sync");
            var previous = player.getData(ModAttachments.SAVED_BEDS);
            RemoveBedPayload.handle(new RemoveBedPayload(target.dimension(), target.pos()), context);
            var remaining = player.getData(ModAttachments.SAVED_BEDS);
            require(remaining.points().size() == previous.points().size() - 1 && remaining.points().stream().noneMatch(p -> p.matches(target.dimension(), target.pos())), "sorted deletion");
            require(remaining.equals(mirror.getData(ModAttachments.SAVED_BEDS)), "point sync");
        }
        RenameBedPayload.handle(new RenameBedPayload(Level.NETHER, anchor, "Anchor"), context);
        require("Anchor".equals(mirror.getData(ModAttachments.SAVED_BEDS).points().getFirst().customName()), "anchor rename");
        HistoryAction.handle(new HistoryAction(0), context);
        require(mirror.getData(ModAttachments.HISTORY).entries().isEmpty(), "clear history snapshot");
        var expectedHistory = player.getData(ModAttachments.HISTORY);
        var expectedPoints = player.getData(ModAttachments.SAVED_BEDS);
        server.getPlayerList().remove(player);
        var rejoined = new ServerPlayer(server, nether, cookie.gameProfile(), cookie.clientInformation());
        var saved = server.getPlayerList().loadPlayerData(player.nameAndId()).orElseThrow();
        rejoined.load(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, server.registryAccess(), saved));
        require(!rejoined.hasData(ModAttachments.REQUEST_LIMITS), "rate limits persisted on disk");
        require(rejoined.getData(ModAttachments.HISTORY).equals(expectedHistory), "saved history");
        require(rejoined.getData(ModAttachments.SAVED_BEDS).points().size() == expectedPoints.points().size()
                && rejoined.getData(ModAttachments.SAVED_BEDS).points().getFirst().matches(Level.NETHER, anchor), "saved anchor");
        BedSwitching.LOGGER.info("SERVER REGRESSION CHECKS PASSED");
    }

    private static void platform(ServerLevel level, BlockPos center) {
        for (var pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 2, 3))) {
            level.setBlock(pos, pos.getY() < center.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
