package com.doctorhoney.bedswitching;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class SavedDataChecks {
    public static void main(String[] args) {
        var limits = new RequestLimits();
        for (int i = 0; i < 12; i++) {
            check(limits.accept(RequestLimits.Action.HISTORY, 0));
            check(limits.accept(RequestLimits.Action.REMOVE, 0));
        }
        check(!limits.accept(RequestLimits.Action.REMOVE, 99_999_999));
        check(limits.accept(RequestLimits.Action.REMOVE, 100_000_000));
        check(!limits.accept(RequestLimits.Action.REMOVE, 100_000_001));
        check(limits.accept(RequestLimits.Action.RESPAWN, 0));
        check(limits.accept(RequestLimits.Action.RESPAWN, 0));
        check(!limits.accept(RequestLimits.Action.RESPAWN, 0));
        check(limits.accept(RequestLimits.Action.RENAME, 0));
        var legacy = JsonParser.parseString("""
                {"beds":[{"dimension":"minecraft:overworld","x":3,"y":64,"z":4,"name":"Camp"},
                         {"dimension":"minecraft:the_nether","x":1,"y":33,"z":2,"name":"Old",
                          "biome":"minecraft:basalt_deltas","custom_name":"Шахта"}]}
                """);
        var saved = BedPoints.CODEC.codec().parse(JsonOps.INSTANCE, legacy).getOrThrow();
        var camp = saved.points().getFirst();
        var mine = saved.points().getLast();
        check(!camp.anchor() && camp.icon().equals("red_bed") && camp.biome().equals(BedPoint.UNKNOWN_BIOME));
        check(mine.customName().equals("Шахта") && mine.biome().identifier().getPath().equals("basalt_deltas"));
        check(Math.round(Math.sqrt(new BlockPos(0, 64, 0).distSqr(camp.pos()))) == 5);
        check(!mine.matches(camp.dimension(), mine.pos()));
        check(saved.add(mine.withCustomName("replacement")).equals(saved));
        var codec = BedPoints.CODEC.codec();
        check(codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, saved).getOrThrow()).getOrThrow().equals(saved));
        var distant = new BedPoint(camp.dimension(), new BlockPos(300, 64, 0), "Z", camp.biome(), "", "blue_bed", -1, true);
        for (var sort : PointSort.values()) {
            var view = new ArrayList<>(java.util.List.of(mine, distant, camp));
            sort.apply(view, camp.dimension(), new BlockPos(0, 64, 0));
            switch (sort) {
                case NEAREST -> check(view.equals(java.util.List.of(camp, distant, mine)));
                case FARTHEST -> check(view.equals(java.util.List.of(distant, camp, mine)));
                case NAME -> check(view.equals(java.util.List.of(camp, distant, mine)));
                case DATE -> check(view.equals(java.util.List.of(mine, distant, camp)));
            }
        }
        var broken = JsonParser.parseString("{\"unexpected\": 17}");
        legacy.getAsJsonObject().getAsJsonArray("beds").add(broken);
        var recovered = codec.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        check(recovered.points().size() == 2 && recovered.unreadable().size() == 1);
        check(codec.encodeStart(JsonOps.INSTANCE, recovered.rename(camp.dimension(), camp.pos(), "New"))
                .getOrThrow().getAsJsonObject().getAsJsonArray("beds").get(2).equals(broken));
        var entries = new ArrayList<DeathHistory.Entry>();
        for (int i = 21; i > 0; i--) entries.add(new DeathHistory.Entry(i, camp.dimension(), camp.pos(),
                mine.biome(), Component.translatable("death.attack.fall", "Alex")));
        var history = new DeathHistory(entries);
        check(history.entries().size() == 20 && history.entries().getLast().time() == 2);
        System.out.println("Legacy fields, point identity, distance, round trip and history limit passed.");
    }

    private static void check(boolean result) {
        if (!result) throw new AssertionError();
    }
}
