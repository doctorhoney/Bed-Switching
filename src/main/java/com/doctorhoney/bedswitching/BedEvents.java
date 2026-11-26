package com.doctorhoney.bedswitching;

import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

@EventBusSubscriber(modid = BedSwitching.MODID)
public final class BedEvents {
    @SubscribeEvent
    static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) MenuSync.sendCurrent(player);
    }

    @SubscribeEvent
    static void onRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) MenuSync.sendCurrent(player);
    }

    @SubscribeEvent
    static void onDimensionChange(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) MenuSync.sendCurrent(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onSetSpawn(PlayerSetSpawnEvent event) {
        var pos = event.getNewSpawn();
        if (event.isForced() || pos == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        var level = player.level().getServer().getLevel(event.getSpawnLevel());
        if (level == null || !level.hasChunkAt(pos)) return;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)
                && !(state.getBlock() instanceof RespawnAnchorBlock && state.getValue(RespawnAnchorBlock.CHARGE) > 0
                && RespawnAnchorBlock.canSetSpawn(level, pos))) return;
        var saved = player.getData(ModAttachments.SAVED_BEDS);
        var updated = saved.add(BedPoint.of(level, pos));
        if (!updated.equals(saved)) {
            MenuSync.update(player, updated);
            if (updated.points().size() > saved.points().size()) ModNetwork.notify(player, "saved");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MenuSync.update(player, player.getData(ModAttachments.HISTORY).record(player));
        var saved = player.getData(ModAttachments.SAVED_BEDS);
        var updated = new ArrayList<BedPoint>();
        for (var point : saved.points()) {
            var level = player.level().getServer().getLevel(point.dimension());
            if (level == null || !level.hasChunkAt(point.pos())) {
                updated.add(point.unverified());
                continue;
            }
            var block = level.getBlockState(point.pos()).getBlock();
            if (point.anchor() ? block instanceof RespawnAnchorBlock : block instanceof BedBlock) {
                var fresh = BedPoint.of(level, point.pos());
                if (fresh.pos().equals(point.pos())) {
                    var pos = point.pos();
                    // Stand-up checks may inspect nearby blocks across a chunk boundary.
                    boolean safe = level.hasChunksAt(pos.offset(-3, -3, -3), pos.offset(3, 3, 3))
                            && level.getWorldBorder().isWithinBounds(pos)
                            && RespawnAtBedPayload.safe(player, level, fresh, level.getBlockState(pos));
                    updated.add(point.withDataFrom(fresh.checked(safe)));
                }
            }
        }
        if (!updated.equals(saved.points())) MenuSync.update(player, saved.replace(updated));
    }
}
