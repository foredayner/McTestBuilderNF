package io.github.foredayner.aibnf.client.network;

import io.github.foredayner.aibnf.ai.build.BuildRecord;
import io.github.foredayner.aibnf.ai.build.RebuildService;
import io.github.foredayner.aibnf.client.AiClientState;
import io.github.foredayner.aibnf.client.BuildHistory;
import io.github.foredayner.aibnf.config.AiBuilderConfig;
import io.github.foredayner.aibnf.config.ForbiddenBlockRegistry;
import io.github.foredayner.aibnf.network.AiNetworkPayloads;
import io.github.foredayner.aibnf.network.AiNetworking;
import java.util.function.Consumer;

public final class AiClientNetworking {

    private AiClientNetworking() {
    }

    /** Messages are handled on the game thread already (see AiNetworking). */
    @SuppressWarnings("unchecked")
    private static <M extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void on(
        net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<M> type, Consumer<M> handler) {
        AiNetworking.CLIENT_HANDLERS.put(type, msg -> handler.accept((M) msg));
    }

    public static void registerReceivers() {
        on(AiNetworkPayloads.AiLogS2CPayload.ID, payload -> AiClientState.addLog(payload.message()));

        on(AiNetworkPayloads.ApprovalPendingS2CPayload.ID, payload -> {
            AiClientState.setPendingApproval(payload.description());
            AiClientState.addLog("[AI] Approval required.");
            for (String line : payload.description().split("\n")) {
                if (!line.isEmpty()) {
                    AiClientState.addLog("  " + line);
                }
            }
        });

        on(AiNetworkPayloads.ApprovalClearS2CPayload.ID, payload -> AiClientState.clearPendingApproval());

        on(AiNetworkPayloads.SparkS2CPayload.ID, payload -> io.github.foredayner.aibnf.client.SparkRenderer.play(payload.sparks(),
            new net.minecraft.world.phys.Vec3(payload.x(), payload.y(), payload.z()),
            new net.minecraft.world.phys.Vec3(payload.dx(), payload.dy(), payload.dz()), payload.scale()));

        on(AiNetworkPayloads.NearbyObjectsS2CPayload.ID, payload -> AiClientState.setNearby(payload.json()));

        on(AiNetworkPayloads.BuildCompleteS2CPayload.ID, payload -> {
            try {
                BuildRecord record = BuildRecord.fromGzip(payload.data());
                BuildHistory.save(record);
                AiClientState.addLog("[History] 보관함에 추가했어요. 보관함에서 \"손에 받기\"로 꺼내 쓰세요.");
            } catch (RuntimeException exception) {
                AiClientState.addLog("[History] Could not save this build: " + exception.getMessage());
            }
        });
    }

    public static void sendApprovalAction(boolean approve) {
        if (approve) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AiNetworkPayloads.ApprovalAcceptC2SPayload());
        } else {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AiNetworkPayloads.ApprovalDenyC2SPayload());
        }
    }

    public static void sendRemoveObject(int entityId, String tag) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AiNetworkPayloads.RemoveObjectC2SPayload(entityId, tag == null ? "" : tag));
    }

    public static void requestNearby() {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AiNetworkPayloads.NearbyRequestC2SPayload());
    }

    public static void sendBreak(int entityId) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AiNetworkPayloads.BreakObjectC2SPayload(entityId));
    }

    public static void sendAttackSwing() {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(AiNetworkPayloads.AttackSwingC2SPayload.ID)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AiNetworkPayloads.AttackSwingC2SPayload());
        }
    }

    public static void sendUndo() {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AiNetworkPayloads.UndoC2SPayload());
    }

    /** Send a saved build to the server in parts small enough for client packets. */
    public static void sendRebuild(BuildRecord record) {
        sendRebuild(record, RebuildService.MODE_PLACE);
    }

    /** mode: RebuildService.MODE_PLACE (build in front of me) or MODE_GIVE (blueprint paper). */
    public static void sendRebuild(BuildRecord record, int mode) {
        sendRebuild(record, mode, 100);
    }

    /** scalePercent: 100 = as designed (objects can be placed bigger or smaller). */
    public static void sendRebuild(BuildRecord record, int mode, int scalePercent) {
        byte[] data = record.toGzip();
        int part = RebuildService.PART_SIZE;
        int total = Math.max(1, (data.length + part - 1) / part);
        int id = (int) (System.nanoTime() & 0x7fffffff);
        for (int i = 0; i < total; i++) {
            int from = i * part;
            byte[] chunk = java.util.Arrays.copyOfRange(data, from, Math.min(data.length, from + part));
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AiNetworkPayloads.RebuildPartC2SPayload(id, i, total, mode, scalePercent, chunk));
        }
    }

    public static void sendSettingsSync(AiBuilderConfig config) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AiNetworkPayloads.AiSettingsSyncC2SPayload(
            config.provider.name(),
            config.modelName,
            config.apiUrl,
            config.apiKey,
            config.ticksPerBlock,
            config.forbiddenBlocksPath,
            ForbiddenBlockRegistry.encodeForNetwork(config.forbiddenBlocks),
            config.debugLogEnabled ? 1 : 0,
            config.openAiWebSearchEnabled ? 1 : 0,
            config.autoRetries,
            config.keepAliveMinutes
        ));
    }
}
