package com.doctorhoney.bedswitching;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * A single saved bed. {@code pos} is always normalized to the head part of the bed.
 *
 * <p>The {@code biome}, {@code customName} fields are optional in the on-disk codec, so beds saved by
 * earlier versions of the mod stay valid ({@link #UNKNOWN_BIOME} and an empty custom name are used as
 * defaults).</p>
 */
public record BedPoint(
        ResourceKey<Level> dimension,
        BlockPos pos,
        String name,
        ResourceKey<Biome> biome,
        String customName, String icon, int charges, boolean available) {

    /** Maximum length of a player-defined bed name. */
    public static final int MAX_NAME_LENGTH = 48;

    /** Fallback used for beds saved before biomes were tracked. */
    public static final ResourceKey<Biome> UNKNOWN_BIOME =
            ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(BedSwitching.MODID, "unknown"));

    public static final Codec<BedPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(BedPoint::dimension),
            Codec.INT.fieldOf("x").forGetter(point -> point.pos().getX()),
            Codec.INT.fieldOf("y").forGetter(point -> point.pos().getY()),
            Codec.INT.fieldOf("z").forGetter(point -> point.pos().getZ()),
            Codec.STRING.fieldOf("name").forGetter(BedPoint::name),
            ResourceKey.codec(Registries.BIOME).optionalFieldOf("biome", UNKNOWN_BIOME).forGetter(BedPoint::biome),
            Codec.STRING.optionalFieldOf("custom_name", "").forGetter(BedPoint::customName),
            Codec.STRING.optionalFieldOf("icon", "red_bed").forGetter(BedPoint::icon)
    ).apply(instance, (dimension, x, y, z, name, biome, customName, icon) ->
            new BedPoint(dimension, new BlockPos(x, y, z), name, biome, customName, icon, -1, false)));

    public static final StreamCodec<ByteBuf, BedPoint> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), BedPoint::dimension,
            BlockPos.STREAM_CODEC, BedPoint::pos,
            ByteBufCodecs.STRING_UTF8, BedPoint::name,
            ResourceKey.streamCodec(Registries.BIOME), BedPoint::biome,
            ByteBufCodecs.STRING_UTF8, BedPoint::customName,
            ByteBufCodecs.STRING_UTF8, BedPoint::icon,
            ByteBufCodecs.VAR_INT, BedPoint::charges,
            ByteBufCodecs.BOOL, BedPoint::available,
            BedPoint::new
    );

    public BedPoint {
        pos = pos.immutable();
        if (!icon.equals("respawn_anchor") && (!icon.endsWith("_bed") || net.minecraft.resources.Identifier.tryParse("minecraft:" + icon) == null)) icon = "red_bed";
        name = name == null ? "" : name;
        biome = biome == null ? UNKNOWN_BIOME : biome;
        customName = customName == null ? "" : customName.trim();
    }

    /**
     * Creates a point from the clicked bed position, normalizing it to {@link BedPart#HEAD} and
     * remembering the biome at that position.
     */
    public static BedPoint of(Level level, BlockPos clicked) {
        BlockPos head = clicked.immutable();
        BlockState state = level.getBlockState(clicked);
        if (state.hasProperty(BedBlock.PART)
                && state.getValue(BedBlock.PART) == BedPart.FOOT
                && state.hasProperty(BedBlock.FACING)) {
            head = clicked.relative(state.getValue(BedBlock.FACING)).immutable();
        }
        ResourceKey<Biome> biome = level.getBiome(head).unwrapKey().orElse(UNKNOWN_BIOME);
        return new BedPoint(level.dimension(), head, makeName(level.dimension(), head), biome, "",
                state.getBlock() instanceof BedBlock bed ? bed.getColor().getName() + "_bed" : "respawn_anchor",
                state.hasProperty(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE)
                        ? state.getValue(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE) : -1, false);
    }

    /** The name shown in the list: the custom one if set, otherwise the generated base name. */
    public String displayName() {
        if (!customName.isBlank()) return customName;
        if (!name.equals(makeName(dimension, pos))) return name;
        return dimensionComponent().getString() + ": " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public BedPoint withCustomName(String newCustomName) {
        return new BedPoint(this.dimension, this.pos, this.name, this.biome, newCustomName, icon, charges, available);
    }

    /** Refreshes generated data (including the biome) while preserving the player's custom name. */
    public BedPoint withDataFrom(BedPoint fresh) {
        return new BedPoint(fresh.dimension, fresh.pos, fresh.name, fresh.biome, this.customName, fresh.icon, fresh.charges, fresh.available);
    }

    public BedPoint checked(boolean safe) {
        return new BedPoint(dimension, pos, name, biome, customName, icon, charges, safe);
    }

    public boolean anchor() {
        return "respawn_anchor".equals(icon);
    }

    public BedPoint unverified() {
        return new BedPoint(dimension, pos, name, biome, customName, icon, -1, false);
    }

    public boolean matches(ResourceKey<Level> dimension, BlockPos pos) {
        return this.dimension.equals(dimension) && this.pos.equals(pos);
    }

    /** Localized biome name, using the vanilla {@code biome.<namespace>.<path>} translation key. */
    public Component biomeComponent() {
        return biomeComponent(biome);
    }

    static Component biomeComponent(ResourceKey<Biome> biome) {
        if (UNKNOWN_BIOME.equals(biome)) {
            return Component.translatable("bedrespawn.ui.info.biome_unknown");
        }
        Identifier id = biome.identifier();
        return Component.translatableWithFallback(
                "biome." + id.getNamespace() + "." + id.getPath(), id.getPath());
    }

    /** Localized dimension name; modded dimensions fall back to the vanilla key / generated name. */
    public Component dimensionComponent() {
        return dimensionComponent(dimension);
    }

    static Component dimensionComponent(ResourceKey<Level> dimension) {
        if (dimension.identifier().toString().equals("minecraft:overworld")) {
            return Component.translatable("bedrespawn.dimension.overworld");
        }
        if (dimension.identifier().toString().equals("minecraft:the_nether")) {
            return Component.translatable("bedrespawn.dimension.nether");
        }
        if (dimension.identifier().toString().equals("minecraft:the_end")) {
            return Component.translatable("bedrespawn.dimension.end");
        }
        Identifier id = dimension.identifier();
        return Component.translatableWithFallback(
                "dimension." + id.getNamespace() + "." + id.getPath(), dimensionName(dimension));
    }

    private static String makeName(ResourceKey<Level> dimension, BlockPos pos) {
        return dimensionName(dimension) + ": " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String dimensionName(ResourceKey<Level> dimension) {
        if (dimension.identifier().toString().equals("minecraft:overworld")) {
            return "Overworld";
        }
        if (dimension.identifier().toString().equals("minecraft:the_nether")) {
            return "Nether";
        }
        if (dimension.identifier().toString().equals("minecraft:the_end")) {
            return "End";
        }

        String[] parts = dimension.identifier().getPath().split("[_/]");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return result.length() == 0 ? dimension.identifier().getPath() : result.toString();
    }
}
