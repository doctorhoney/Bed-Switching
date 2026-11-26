package com.doctorhoney.bedswitching;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The list of beds stored in a player attachment.
 */
public record BedPoints(List<BedPoint> points, List<Dynamic<?>> unreadable) {
    private static final Codec<Either<BedPoint, Dynamic<?>>> ENTRY = Codec.either(BedPoint.CODEC, Codec.PASSTHROUGH);
    public static final MapCodec<BedPoints> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ENTRY.listOf().fieldOf("beds").forGetter(BedPoints::encodeEntries)
    ).apply(instance, BedPoints::decodeEntries));

    private static BedPoints decodeEntries(List<Either<BedPoint, Dynamic<?>>> entries) {
        var points = new ArrayList<BedPoint>();
        var unreadable = new ArrayList<Dynamic<?>>();
        for (var entry : entries) {
            entry.ifLeft(points::add).ifRight(unreadable::add);
        }
        if (!unreadable.isEmpty()) BedSwitching.LOGGER.warn("Preserving {} unreadable respawn point records", unreadable.size());
        return new BedPoints(points, unreadable);
    }

    private List<Either<BedPoint, Dynamic<?>>> encodeEntries() {
        var entries = new ArrayList<Either<BedPoint, Dynamic<?>>>(points.size() + unreadable.size());
        points.forEach(point -> entries.add(Either.left(point)));
        unreadable.forEach(raw -> entries.add(Either.right(raw)));
        return entries;
    }

    public static final StreamCodec<ByteBuf, List<BedPoint>> LIST_STREAM_CODEC =
            ByteBufCodecs.collection(ArrayList::new, BedPoint.STREAM_CODEC);

    public static final StreamCodec<ByteBuf, BedPoints> STREAM_CODEC =
            LIST_STREAM_CODEC.map(BedPoints::new, BedPoints::points);

    public BedPoints {
        points = List.copyOf(points);
        unreadable = List.copyOf(unreadable);
    }

    public BedPoints(List<BedPoint> points) {
        this(points, List.of());
    }

    public BedPoints() {
        this(List.of());
    }

    /** Adds a bed, or refreshes an existing one while keeping its custom name. */
    public BedPoints add(BedPoint point) {
        List<BedPoint> updated = new ArrayList<>(points);
        for (int i = 0; i < updated.size(); i++) {
            BedPoint saved = updated.get(i);
            if (saved.matches(point.dimension(), point.pos())) {
                updated.set(i, saved.withDataFrom(point));
                return new BedPoints(updated, unreadable);
            }
        }
        updated.add(point);
        return new BedPoints(updated, unreadable);
    }

    public BedPoints remove(BedPoint point) {
        List<BedPoint> updated = new ArrayList<>(points);
        updated.removeIf(saved -> saved.matches(point.dimension(), point.pos()));
        return new BedPoints(updated, unreadable);
    }

    public BedPoints rename(ResourceKey<Level> dimension, BlockPos pos, String customName) {
        List<BedPoint> updated = new ArrayList<>(points);
        for (int i = 0; i < updated.size(); i++) {
            BedPoint saved = updated.get(i);
            if (saved.matches(dimension, pos)) {
                updated.set(i, saved.withCustomName(customName));
                break;
            }
        }
        return new BedPoints(updated, unreadable);
    }

    public BedPoints replace(List<BedPoint> newPoints) {
        return new BedPoints(newPoints, unreadable);
    }
}
