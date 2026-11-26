package com.doctorhoney.bedswitching;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public record DeathHistory(List<Entry> entries) {
    public record Entry(long time, ResourceKey<Level> dimension, BlockPos pos, ResourceKey<Biome> biome, Component cause) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("time").forGetter(Entry::time),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Entry::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                ResourceKey.codec(Registries.BIOME).fieldOf("biome").forGetter(Entry::biome),
                ComponentSerialization.CODEC.fieldOf("cause").forGetter(Entry::cause)
        ).apply(i, Entry::new));
    }

    static final MapCodec<DeathHistory> CODEC = Entry.CODEC.listOf().fieldOf("entries")
            .xmap(DeathHistory::new, DeathHistory::entries);

    public DeathHistory {
        entries = List.copyOf(entries.subList(0, Math.min(20, entries.size())));
    }

    public DeathHistory() {
        this(List.of());
    }

    DeathHistory record(ServerPlayer player) {
        long time = System.currentTimeMillis();
        if (!entries.isEmpty()) time = Math.max(time, entries.getFirst().time() + 1);
        BlockPos pos = player.blockPosition();
        var updated = new ArrayList<Entry>();
        updated.add(new Entry(time, player.level().dimension(), pos,
                player.level().getBiome(pos).unwrapKey().orElse(BedPoint.UNKNOWN_BIOME),
                player.getCombatTracker().getDeathMessage()));
        updated.addAll(entries);
        return new DeathHistory(updated);
    }
}
