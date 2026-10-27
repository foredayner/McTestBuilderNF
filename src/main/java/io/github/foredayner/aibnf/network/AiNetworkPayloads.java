package io.github.foredayner.aibnf.network;

import io.github.foredayner.aibnf.AiBuildAssistantMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class AiNetworkPayloads {

    private AiNetworkPayloads() {
    }

    public record AiLogS2CPayload(String message) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AiLogS2CPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "ai_log"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AiLogS2CPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, AiLogS2CPayload::message, AiLogS2CPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ApprovalPendingS2CPayload(String description) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ApprovalPendingS2CPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "approval_pending"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApprovalPendingS2CPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ApprovalPendingS2CPayload::description, ApprovalPendingS2CPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ApprovalClearS2CPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ApprovalClearS2CPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "approval_clear"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApprovalClearS2CPayload> CODEC =
            StreamCodec.unit(new ApprovalClearS2CPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ApprovalAcceptC2SPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ApprovalAcceptC2SPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "approval_accept"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApprovalAcceptC2SPayload> CODEC =
            StreamCodec.unit(new ApprovalAcceptC2SPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ApprovalDenyC2SPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ApprovalDenyC2SPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "approval_deny"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApprovalDenyC2SPayload> CODEC =
            StreamCodec.unit(new ApprovalDenyC2SPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record AiSettingsSyncC2SPayload(
        String provider,
        String modelName,
        String apiUrl,
        String apiKey,
        int ticksPerBlock,
        String forbiddenBlocksPath,
        String forbiddenBlocksData,
        int debugLogEnabled,
        int openAiWebSearchEnabled,
        int autoRetries,
        int keepAliveMinutes
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AiSettingsSyncC2SPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "settings_sync"));
        // 1.21.4's PacketCodec.tuple stops at 6 fields, so the 9 fields are written by hand
        // in the same order and encodings the 1.21.11 tuple used.
        public static final StreamCodec<RegistryFriendlyByteBuf, AiSettingsSyncC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.provider());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.modelName());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.apiUrl());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.apiKey());
                ByteBufCodecs.VAR_INT.encode(buf, payload.ticksPerBlock());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.forbiddenBlocksPath());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.forbiddenBlocksData());
                ByteBufCodecs.VAR_INT.encode(buf, payload.debugLogEnabled());
                ByteBufCodecs.VAR_INT.encode(buf, payload.openAiWebSearchEnabled());
                ByteBufCodecs.VAR_INT.encode(buf, payload.autoRetries());
                ByteBufCodecs.VAR_INT.encode(buf, payload.keepAliveMinutes());
            },
            buf -> new AiSettingsSyncC2SPayload(
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf)
            )
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }

        public boolean isDebugLogEnabled() {
            return debugLogEnabled != 0;
        }

        public boolean isOpenAiWebSearchEnabled() {
            return openAiWebSearchEnabled != 0;
        }
    }

    /** Server -> client: a finished build (gzip'd BuildRecord JSON) for the history tab. */
    public record BuildCompleteS2CPayload(byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<BuildCompleteS2CPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "build_complete"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BuildCompleteS2CPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BYTE_ARRAY, BuildCompleteS2CPayload::data, BuildCompleteS2CPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Client -> server: one part of a saved build to rebuild (client packets are size-limited). */
    public record RebuildPartC2SPayload(int id, int index, int total, int mode, int scalePercent, byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RebuildPartC2SPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "rebuild_part"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RebuildPartC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RebuildPartC2SPayload::id,
            ByteBufCodecs.VAR_INT, RebuildPartC2SPayload::index,
            ByteBufCodecs.VAR_INT, RebuildPartC2SPayload::total,
            ByteBufCodecs.VAR_INT, RebuildPartC2SPayload::mode,
            ByteBufCodecs.VAR_INT, RebuildPartC2SPayload::scalePercent,
            ByteBufCodecs.BYTE_ARRAY, RebuildPartC2SPayload::data,
            RebuildPartC2SPayload::new
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Client -> server: I finished mining this object (by one of its hitbox entities). */
    public record BreakObjectC2SPayload(int entityId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<BreakObjectC2SPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "break_object"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BreakObjectC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BreakObjectC2SPayload::entityId,
            BreakObjectC2SPayload::new
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Client -> server: undo my last build. */
    public record UndoC2SPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UndoC2SPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "undo"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UndoC2SPayload> CODEC = StreamCodec.unit(new UndoC2SPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * Server -> client: play colour-dot effects (a JSON list of Spark) at a point, facing a
     * direction, at a scale. Each player's game draws the dots.
     */
    public record SparkS2CPayload(String sparks, double x, double y, double z, float dx, float dy, float dz, float scale)
        implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SparkS2CPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "spark"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SparkS2CPayload> CODEC = StreamCodec.ofMember(
            (p, buf) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, p.sparks());
                ByteBufCodecs.DOUBLE.encode(buf, p.x());
                ByteBufCodecs.DOUBLE.encode(buf, p.y());
                ByteBufCodecs.DOUBLE.encode(buf, p.z());
                ByteBufCodecs.FLOAT.encode(buf, p.dx());
                ByteBufCodecs.FLOAT.encode(buf, p.dy());
                ByteBufCodecs.FLOAT.encode(buf, p.dz());
                ByteBufCodecs.FLOAT.encode(buf, p.scale());
            },
            buf -> new SparkS2CPayload(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf),
                ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Client -> server: the player swung an AI weapon (attack key, charged): play its attack effect. */
    public record AttackSwingC2SPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AttackSwingC2SPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "attack_swing"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AttackSwingC2SPayload> CODEC = StreamCodec.unit(new AttackSwingC2SPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Client -> server: remove a placed object (by its hitbox entity id, or by tag from the list). */
    public record RemoveObjectC2SPayload(int entityId, String tag) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RemoveObjectC2SPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "remove_object"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveObjectC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RemoveObjectC2SPayload::entityId,
            ByteBufCodecs.STRING_UTF8, RemoveObjectC2SPayload::tag,
            RemoveObjectC2SPayload::new
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Client -> server: which objects are near me? */
    public record NearbyRequestC2SPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<NearbyRequestC2SPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "nearby_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NearbyRequestC2SPayload> CODEC = StreamCodec.unit(new NearbyRequestC2SPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Server -> client: nearby objects as JSON (ObjectService.Nearby list). */
    public record NearbyObjectsS2CPayload(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<NearbyObjectsS2CPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "nearby_objects"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NearbyObjectsS2CPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, NearbyObjectsS2CPayload::json, NearbyObjectsS2CPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
