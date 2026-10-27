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
    private static <M> void on(Class<M> type, Consumer<M> handler) {
        AiNetworking.CLIENT_HANDLERS.put(type, msg -> handler.accept((M) msg));
    }

    public static void registerReceivers() {
        on(AiNetworkPayloads.AiLogS2CPayload.class, payload -> AiClientState.addLog(payload.message()));

        on(AiNetworkPayloads.ApprovalPendingS2CPayload.class, payload -> {
            AiClientState.setPendingApproval(payload.description());
            AiClientState.addLog("[AI] Approval required.");
            for (String line : payload.description().split("\n")) {
                if (!line.isEmpty()) {
                    AiClientState.addLog("  " + line);
                }
            }
        });

        on(AiNetworkPayloads.ApprovalClearS2CPayload.class, payload -> AiClientState.clearPendingApproval());

        on(AiNetworkPayloads.SparkS2CPayload.class, payload -> io.github.foredayner.aibnf.client.SparkRenderer.play(payload.sparks(),
            new net.minecraft.world.phys.Vec3(payload.x(), payload.y(), payload.z()),
            new net.minecraft.world.phys.Vec3(payload.dx(), payload.dy(), payload.dz()), payload.scale()));

        on(AiNetworkPayloads.NearbyObjectsS2CPayload.class, payload -> AiClientState.setNearby(payload.json()));

        on(AiNetworkPayloads.BuildCompleteS2CPayload.class, payload -> {
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
            AiNetworking.CHANNEL.sendToServer(new AiNetworkPayloads.ApprovalAcceptC2SPayload());
        } else {
            AiNetworking.CHANNEL.sendToServer(new AiNetworkPayloads.ApprovalDenyC2SPayload());
        }
    }

    public static void sendRemoveObject(int entityId, String tag) {
        AiNetworking.CHANNEL.sendToServer(new AiNetworkPayloads.RemoveObjectC2SPayload(entityId, tag == null ? "" : tag));
    }

    public static void requestNearby() {
        AiNetworking.CHANNEL.sendToServer(new AiNetworkPayloads.NearbyRequestC2SPayload());
    }

    public static void sendBreak(int entityId) {
        AiNetworking.CHANNEL.sendToServer(new AiNetworkPayloads.BreakObjectC2SPayload(entityId));
    }

    public static void sendAttackSwing() {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection != null && AiNetworking.CHANNEL.isRemotePresent(connection.getConnection())) {
            AiNetworking.CHANNEL.sendToServer(new AiNetworkPayloads.AttackSwingC2SPayload());
        }
    }

    public static void sendUndo() {
        AiNetworking.CHANNEL.sendToServer(new AiNetworkPayloads.UndoC2SPayload());
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
            AiNetworking.CHANNEL.sendToServer(new AiNetworkPayloads.RebuildPartC2SPayload(id, i, total, mode, scalePercent, chunk));
        }
    }

    public static void sendSettingsSync(AiBuilderConfig config) {
        AiNetworking.CHANNEL.sendToServer(new AiNetworkPayloads.AiSettingsSyncC2SPayload(
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
