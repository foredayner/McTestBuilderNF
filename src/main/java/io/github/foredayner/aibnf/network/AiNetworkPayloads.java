package io.github.foredayner.aibnf.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Messages between the game and the server. Each one writes itself to a buffer and has a
 * matching {@code read}; {@link AiNetworking} registers them on the mod's network channel.
 */
public final class AiNetworkPayloads {

    /** Long text fields (JSON lists, forbidden block lists) may be bigger than the default 32767 chars. */
    static final int LONG_TEXT = 1 << 20;

    private AiNetworkPayloads() {
    }

    /** Every message can write itself. */
    public interface Msg {
        void write(FriendlyByteBuf buf);
    }

    public record AiLogS2CPayload(String message) implements Msg {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(message, LONG_TEXT);
        }

        public static AiLogS2CPayload read(FriendlyByteBuf buf) {
            return new AiLogS2CPayload(buf.readUtf(LONG_TEXT));
        }
    }

    public record ApprovalPendingS2CPayload(String description) implements Msg {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(description, LONG_TEXT);
        }

        public static ApprovalPendingS2CPayload read(FriendlyByteBuf buf) {
            return new ApprovalPendingS2CPayload(buf.readUtf(LONG_TEXT));
        }
    }

    public record ApprovalClearS2CPayload() implements Msg {
        public void write(FriendlyByteBuf buf) {
        }

        public static ApprovalClearS2CPayload read(FriendlyByteBuf buf) {
            return new ApprovalClearS2CPayload();
        }
    }

    public record ApprovalAcceptC2SPayload() implements Msg {
        public void write(FriendlyByteBuf buf) {
        }

        public static ApprovalAcceptC2SPayload read(FriendlyByteBuf buf) {
            return new ApprovalAcceptC2SPayload();
        }
    }

    public record ApprovalDenyC2SPayload() implements Msg {
        public void write(FriendlyByteBuf buf) {
        }

        public static ApprovalDenyC2SPayload read(FriendlyByteBuf buf) {
            return new ApprovalDenyC2SPayload();
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
    ) implements Msg {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(provider);
            buf.writeUtf(modelName);
            buf.writeUtf(apiUrl);
            buf.writeUtf(apiKey);
            buf.writeVarInt(ticksPerBlock);
            buf.writeUtf(forbiddenBlocksPath);
            buf.writeUtf(forbiddenBlocksData, LONG_TEXT);
            buf.writeVarInt(debugLogEnabled);
            buf.writeVarInt(openAiWebSearchEnabled);
            buf.writeVarInt(autoRetries);
            buf.writeVarInt(keepAliveMinutes);
        }

        public static AiSettingsSyncC2SPayload read(FriendlyByteBuf buf) {
            return new AiSettingsSyncC2SPayload(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(),
                buf.readUtf(), buf.readUtf(LONG_TEXT), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        public boolean isDebugLogEnabled() {
            return debugLogEnabled != 0;
        }

        public boolean isOpenAiWebSearchEnabled() {
            return openAiWebSearchEnabled != 0;
        }
    }

    /** Server -> client: a finished build (gzip'd BuildRecord JSON) for the history tab. */
    public record BuildCompleteS2CPayload(byte[] data) implements Msg {
        public void write(FriendlyByteBuf buf) {
            buf.writeByteArray(data);
        }

        public static BuildCompleteS2CPayload read(FriendlyByteBuf buf) {
            return new BuildCompleteS2CPayload(buf.readByteArray());
        }
    }

    /** Client -> server: one part of a saved build to rebuild (client packets are size-limited). */
    public record RebuildPartC2SPayload(int id, int index, int total, int mode, int scalePercent, byte[] data) implements Msg {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeVarInt(index);
            buf.writeVarInt(total);
            buf.writeVarInt(mode);
            buf.writeVarInt(scalePercent);
            buf.writeByteArray(data);
        }

        public static RebuildPartC2SPayload read(FriendlyByteBuf buf) {
            return new RebuildPartC2SPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readByteArray(io.github.foredayner.aibnf.ai.build.RebuildService.PART_SIZE));
        }
    }

    /** Client -> server: I finished mining this object (by one of its hitbox entities). */
    public record BreakObjectC2SPayload(int entityId) implements Msg {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
        }

        public static BreakObjectC2SPayload read(FriendlyByteBuf buf) {
            return new BreakObjectC2SPayload(buf.readVarInt());
        }
    }

    /** Client -> server: undo my last build. */
    public record UndoC2SPayload() implements Msg {
        public void write(FriendlyByteBuf buf) {
        }

        public static UndoC2SPayload read(FriendlyByteBuf buf) {
            return new UndoC2SPayload();
        }
    }

    /**
     * Server -> client: play colour-dot effects (a JSON list of Spark) at a point, facing a
     * direction, at a scale. Each player's game draws the dots.
     */
    public record SparkS2CPayload(String sparks, double x, double y, double z, float dx, float dy, float dz, float scale)
        implements Msg {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(sparks, LONG_TEXT);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(dx);
            buf.writeFloat(dy);
            buf.writeFloat(dz);
            buf.writeFloat(scale);
        }

        public static SparkS2CPayload read(FriendlyByteBuf buf) {
            return new SparkS2CPayload(buf.readUtf(LONG_TEXT), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }
    }

    /** Client -> server: the player swung an AI weapon (attack key, charged): play its attack effect. */
    public record AttackSwingC2SPayload() implements Msg {
        public void write(FriendlyByteBuf buf) {
        }

        public static AttackSwingC2SPayload read(FriendlyByteBuf buf) {
            return new AttackSwingC2SPayload();
        }
    }

    /** Client -> server: remove a placed object (by its hitbox entity id, or by tag from the list). */
    public record RemoveObjectC2SPayload(int entityId, String tag) implements Msg {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeUtf(tag);
        }

        public static RemoveObjectC2SPayload read(FriendlyByteBuf buf) {
            return new RemoveObjectC2SPayload(buf.readVarInt(), buf.readUtf());
        }
    }

    /** Client -> server: which objects are near me? */
    public record NearbyRequestC2SPayload() implements Msg {
        public void write(FriendlyByteBuf buf) {
        }

        public static NearbyRequestC2SPayload read(FriendlyByteBuf buf) {
            return new NearbyRequestC2SPayload();
        }
    }

    /** Server -> client: nearby objects as JSON (ObjectService.Nearby list). */
    public record NearbyObjectsS2CPayload(String json) implements Msg {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, LONG_TEXT);
        }

        public static NearbyObjectsS2CPayload read(FriendlyByteBuf buf) {
            return new NearbyObjectsS2CPayload(buf.readUtf(LONG_TEXT));
        }
    }
}
