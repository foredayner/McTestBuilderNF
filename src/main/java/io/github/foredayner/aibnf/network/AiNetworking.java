package io.github.foredayner.aibnf.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
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

    /**
     * What the player's game does with each server -> client message. Filled in by the client
     * code (AiClientNetworking) so this class never loads client-only classes on a server.
     */
    public static final Map<CustomPacketPayload.Type<?>, Consumer<Object>> CLIENT_HANDLERS = new ConcurrentHashMap<>();

    private AiNetworking() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AiNetworking::registerPayloads);
    }

    private static <M extends CustomPacketPayload> void toClient(PayloadRegistrar registrar, CustomPacketPayload.Type<M> type,
                                                               StreamCodec<RegistryFriendlyByteBuf, M> codec) {
        registrar.playToClient(type, codec, (msg, context) -> {
            Consumer<Object> handler = CLIENT_HANDLERS.get(type);
            if (handler != null) {
                handler.accept(msg);
            }
        });
    }

    private static <M extends CustomPacketPayload> void toServer(PayloadRegistrar registrar, CustomPacketPayload.Type<M> type,
                                                               StreamCodec<RegistryFriendlyByteBuf, M> codec,
                                                               BiConsumer<M, ServerPlayer> handler) {
        registrar.playToServer(type, codec, (msg, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                handler.accept(msg, player);
            }
        });
    }

    /** Registers every message (both directions); handlers run on the game thread. */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);

        toClient(registrar, AiLogS2CPayload.ID, AiLogS2CPayload.CODEC);
        toClient(registrar, ApprovalPendingS2CPayload.ID, ApprovalPendingS2CPayload.CODEC);
        toClient(registrar, ApprovalClearS2CPayload.ID, ApprovalClearS2CPayload.CODEC);
        toClient(registrar, BuildCompleteS2CPayload.ID, BuildCompleteS2CPayload.CODEC);
        toClient(registrar, SparkS2CPayload.ID, SparkS2CPayload.CODEC);
        toClient(registrar, NearbyObjectsS2CPayload.ID, NearbyObjectsS2CPayload.CODEC);

        toServer(registrar, ApprovalAcceptC2SPayload.ID, ApprovalAcceptC2SPayload.CODEC, (payload, player) -> {
            if (allowed(player)) {
                BuildApproval.approveFor(player);
            }
        });
        toServer(registrar, ApprovalDenyC2SPayload.ID, ApprovalDenyC2SPayload.CODEC, (payload, player) -> {
            if (allowed(player)) {
                BuildApproval.denyFor(player);
            }
        });
        // The server's AI settings (and API key) are only changed by operators.
        toServer(registrar, AiSettingsSyncC2SPayload.ID, AiSettingsSyncC2SPayload.CODEC, (payload, player) -> {
            if (allowed(player)) {
                applySettings(payload);
            }
        });
        toServer(registrar, RemoveObjectC2SPayload.ID, RemoveObjectC2SPayload.CODEC, (payload, player) -> {
            if (!allowed(player)) {
                return;
            }
            if (payload.tag().isEmpty()) {
                ObjectService.removeByEntity(player, payload.entityId());
            } else {
                ObjectService.removeByTag(player, payload.tag());
            }
        });
        toServer(registrar, NearbyRequestC2SPayload.ID, NearbyRequestC2SPayload.CODEC, (payload, player) -> {
            if (allowed(player)) {
                ObjectService.sendNearby(player);
            }
        });
        toServer(registrar, BreakObjectC2SPayload.ID, BreakObjectC2SPayload.CODEC,
            (payload, player) -> io.github.foredayner.aibnf.ai.build.ObjectItems.breakObject(player, payload.entityId()));
        // Using what was made is open to everyone: swinging an AI weapon plays its effect.
        toServer(registrar, AttackSwingC2SPayload.ID, AttackSwingC2SPayload.CODEC,
            (payload, player) -> io.github.foredayner.aibnf.ai.build.GearEffects.onSwing(player));
        toServer(registrar, UndoC2SPayload.ID, UndoC2SPayload.CODEC, (payload, player) -> {
            if (allowed(player)) {
                UndoService.undo(player);
            }
        });
        toServer(registrar, RebuildPartC2SPayload.ID, RebuildPartC2SPayload.CODEC, (payload, player) -> {
            if (allowed(player)) {
                RebuildService.receivePart(player, payload.id(), payload.index(), payload.total(),
                    payload.mode(), payload.scalePercent(), payload.data());
            }
        });
    }

    /** Send a message to one player's game (skipped for players without a connection, like test players). */
    public static void send(ServerPlayer player, CustomPacketPayload message) {
        if (canSend(player, message.type())) {
            PacketDistributor.sendToPlayer(player, message);
        }
    }

    /** True if this player's game has this mod and can receive this message. */
    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && player.connection.getConnection() != null
            && player.connection.getConnection().isConnected() && player.connection.hasChannel(type);
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
