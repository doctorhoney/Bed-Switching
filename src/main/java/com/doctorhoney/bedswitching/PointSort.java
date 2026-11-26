package com.doctorhoney.bedswitching;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

enum PointSort {
    NEAREST, FARTHEST, NAME, DATE;

    void apply(List<BedPoint> view, ResourceKey<Level> dimension, BlockPos origin) {
        if (this == NAME) {
            view.sort(Comparator.comparing(BedPoint::displayName, String.CASE_INSENSITIVE_ORDER));
        } else if (this != DATE) {
            Comparator<BedPoint> distances = Comparator.comparingDouble(p -> origin.distSqr(p.pos()));
            var order = this == NEAREST ? distances : distances.reversed();
            view.sort((a, b) -> {
                boolean local = dimension.equals(a.dimension());
                if (local != dimension.equals(b.dimension())) return local ? -1 : 1;
                return local ? order.compare(a, b) : 0;
            });
        }
    }
}
