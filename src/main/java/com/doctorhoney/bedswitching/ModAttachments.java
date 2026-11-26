package com.doctorhoney.bedswitching;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registration and configuration of the player data attachment.
 */
public final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, BedSwitching.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BedPoints>> SAVED_BEDS =
            ATTACHMENTS.register("saved_beds", () -> AttachmentType.<BedPoints>builder(() -> new BedPoints())
                    .serialize(BedPoints.CODEC)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DeathHistory>> HISTORY =
            ATTACHMENTS.register("death_history", () -> AttachmentType.builder(() -> new DeathHistory())
                    .serialize(DeathHistory.CODEC).copyOnDeath()
                    .build());

    // No serialization, synchronization or copyOnDeath: each new player starts with fresh limits.
    static final DeferredHolder<AttachmentType<?>, AttachmentType<RequestLimits>> REQUEST_LIMITS =
            ATTACHMENTS.register("request_limits", () -> AttachmentType.builder(RequestLimits::new).build());

    private ModAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
