package io.github.foredayner.aibnf.network;

import io.github.foredayner.aibnf.AiBuildAssistantMod;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import io.github.foredayner.aibnf.ai.approval.BuildApproval;
import io.github.foredayner.aibnf.ai.build.BuildRecord;
import io.github.foredayner.aibnf.ai.build.RebuildService;
import io.github.foredayner.aibnf.ai.build.UndoService;
import io.github.foredayner.aibnf.ai.build.ObjectService;
import io.github.foredayner.aibnf.config.AiBuilderConfig;
import io.github.foredayner.aibnf.config.AiProvider;
import io.github.foredayner.aibnf.config.ForbiddenBlockRegistry;
import io.github.foredayner.aibnf.network.AiNetworkPayloads.*;

public final class AiNetworking {

    /** Bump when a message changes; both sides must have the same mod version. */
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(AiBuildAssistantMod.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /**
     * What the player's game does with each server -> client message. Filled in by the client
     * code (AiClientNetworking) so this class never loads client-only classes on a server.
     */
    public static final Map<Class<?>, Consumer<Object>> CLIENT_HANDLERS = new ConcurrentHashMap<>();

    private static boolean payloadsRegistered = false;
    private static int nextId = 0;

    private AiNetworking() {
    }

    private static <M extends Msg> void toClient(Class<M> type, Function<FriendlyByteBuf, M> read) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(Msg::write)
            .decoder(read)
            .consumerMainThread((msg, ctx) -> {
                Consumer<Object> handler = CLIENT_HANDLERS.get(type);
                if (handler != null) {
                    handler.accept(msg);
                }
            })
            .add();
    }

    private static <M extends Msg> void toServer(Class<M> type, Function<FriendlyByteBuf, M> read,
                                                 BiConsumer<M, ServerPlayer> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(Msg::write)
            .decoder(read)
            .consumerMainThread((M msg, Supplier<NetworkEvent.Context> ctx) -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    handler.accept(msg, player);
                }
            })
            .add();
    }

    /** Registers every message (both directions). Called once while the mod loads. */
    public static void registerPayloads() {
        if (payloadsRegistered) {
            return;
        }
        payloadsRegistered = true;

        toClient(AiLogS2CPayload.class, AiLogS2CPayload::read);
        toClient(ApprovalPendingS2CPayload.class, ApprovalPendingS2CPayload::read);
        toClient(ApprovalClearS2CPayload.class, ApprovalClearS2CPayload::read);
        toClient(BuildCompleteS2CPayload.class, BuildCompleteS2CPayload::read);
        toClient(SparkS2CPayload.class, SparkS2CPayload::read);
        toClient(NearbyObjectsS2CPayload.class, NearbyObjectsS2CPayload::read);

        toServer(ApprovalAcceptC2SPayload.class, ApprovalAcceptC2SPayload::read, (payload, player) -> {
            if (allowed(player)) {
                BuildApproval.approveFor(player);
            }
        });
        toServer(ApprovalDenyC2SPayload.class, ApprovalDenyC2SPayload::read, (payload, player) -> {
            if (allowed(player)) {
                BuildApproval.denyFor(player);
            }
        });
        // The server's AI settings (and API key) are only changed by operators.
        toServer(AiSettingsSyncC2SPayload.class, AiSettingsSyncC2SPayload::read, (payload, player) -> {
            if (allowed(player)) {
                applySettings(payload);
            }
        });
        toServer(RemoveObjectC2SPayload.class, RemoveObjectC2SPayload::read, (payload, player) -> {
            if (!allowed(player)) {
                return;
            }
            if (payload.tag().isEmpty()) {
                ObjectService.removeByEntity(player, payload.entityId());
            } else {
                ObjectService.removeByTag(player, payload.tag());
            }
        });
        toServer(NearbyRequestC2SPayload.class, NearbyRequestC2SPayload::read, (payload, player) -> {
            if (allowed(player)) {
                ObjectService.sendNearby(player);
            }
        });
        toServer(BreakObjectC2SPayload.class, BreakObjectC2SPayload::read,
            (payload, player) -> io.github.foredayner.aibnf.ai.build.ObjectItems.breakObject(player, payload.entityId()));
        // Using what was made is open to everyone: swinging an AI weapon plays its effect.
        toServer(AttackSwingC2SPayload.class, AttackSwingC2SPayload::read,
            (payload, player) -> io.github.foredayner.aibnf.ai.build.GearEffects.onSwing(player));
        toServer(UndoC2SPayload.class, UndoC2SPayload::read, (payload, player) -> {
            if (allowed(player)) {
                UndoService.undo(player);
            }
        });
        toServer(RebuildPartC2SPayload.class, RebuildPartC2SPayload::read, (payload, player) -> {
            if (allowed(player)) {
                RebuildService.receivePart(player, payload.id(), payload.index(), payload.total(),
                    payload.mode(), payload.scalePercent(), payload.data());
            }
        });
    }

    /** Send a message to one player's game. */
    public static void send(ServerPlayer player, Msg message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** Players in this world within {@code range} blocks of a point (who should see an effect there). */
    public static java.util.List<ServerPlayer> playersAround(net.minecraft.server.level.ServerLevel world,
                                                             net.minecraft.world.phys.Vec3 at, double range) {
        java.util.List<ServerPlayer> out = new java.util.ArrayList<>();
        for (ServerPlayer p : world.players()) {
            if (p.position().distanceToSqr(at) <= range * range) {
                out.add(p);
            }
        }
        return out;
    }

    /** True if this player's game has this mod (and can receive its messages). */
    public static boolean canSend(ServerPlayer player) {
        return player.connection != null && CHANNEL.isRemotePresent(player.connection.connection);
    }

    /** Permission level that may use the AI 공방 (requests, settings, 손에 받기, undo, removal). */
    public static final int OP_LEVEL = 2;
    public static final String OP_ONLY = "관리자(OP)만 사용할 수 있어요.";

    /**
     * Everything that talks to the AI or changes the server's settings is for operators only.
     * Using what was made (placing object items, wearing gear, mining objects) stays open to all.
     */
    public static boolean allowed(ServerPlayer player) {
        if (player != null && player.hasPermissions(OP_LEVEL)) {
            return true;
        }
        if (player != null) {
            player.displayClientMessage(Component.literal(OP_ONLY), true);
        }
        return false;
    }

    private static void applySettings(AiNetworkPayloads.AiSettingsSyncC2SPayload payload) {
        AiProvider provider;
        try {
            provider = AiProvider.valueOf(payload.provider());
        } catch (IllegalArgumentException exception) {
            provider = AiProvider.OLLAMA;
        }

        AiBuilderConfig.get().applyFrom(
            provider,
            payload.modelName(),
            payload.apiUrl(),
            payload.apiKey(),
            payload.ticksPerBlock(),
            payload.forbiddenBlocksPath(),
            ForbiddenBlockRegistry.decodeFromNetwork(payload.forbiddenBlocksData()),
            payload.isDebugLogEnabled(),
            payload.isOpenAiWebSearchEnabled()
        );
        AiBuilderConfig.get().autoRetries = payload.autoRetries();
        AiBuilderConfig.get().keepAliveMinutes = payload.keepAliveMinutes();
        AiBuilderConfig.save();
    }

    public static void sendLog(ServerPlayer player, String message) {
        sendLog(player, message, false);
    }

    public static void sendDebugLog(ServerPlayer player, String message) {
        sendLog(player, message, true);
    }

    public static void sendLog(ServerPlayer player, String message, boolean debugOnly) {
        if (debugOnly && !AiBuilderConfig.get().debugLogEnabled) {
            return;
        }

        runOnServerThread(player, () -> {
            if (player.connection != null) {
                send(player, new AiNetworkPayloads.AiLogS2CPayload(message));
            }
        });
    }

    public static void sendApprovalPending(ServerPlayer player, String description) {
        runOnServerThread(player, () -> {
            if (player.connection != null) {
                send(player, new AiNetworkPayloads.ApprovalPendingS2CPayload(description));
            }
        });
    }

    public static void sendBuildComplete(ServerPlayer player, BuildRecord record) {
        runOnServerThread(player, () -> {
            if (player.connection != null) {
                send(player, new AiNetworkPayloads.BuildCompleteS2CPayload(record.toGzip()));
            }
        });
    }

    public static void sendNearby(ServerPlayer player, String json) {
        runOnServerThread(player, () -> {
            if (player.connection != null) {
                send(player, new AiNetworkPayloads.NearbyObjectsS2CPayload(json));
            }
        });
    }

    public static void sendApprovalClear(ServerPlayer player) {
        runOnServerThread(player, () -> {
            if (player.connection != null) {
                send(player, new AiNetworkPayloads.ApprovalClearS2CPayload());
            }
        });
    }

    public static void notify(ServerPlayer player, String message) {
        sendLog(player, message);
        runOnServerThread(player, () -> player.sendSystemMessage(Component.literal(message)));
    }

    private static void runOnServerThread(ServerPlayer player, Runnable action) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                System.out.println("[AI] Could not send to " + player.getName().getString() + ": " + exception.getMessage());
            }
        };
        if (server.isSameThread()) {
            safe.run();
        } else {
            server.execute(safe);
        }
    }
}
