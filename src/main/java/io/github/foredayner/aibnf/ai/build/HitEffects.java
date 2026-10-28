package io.github.foredayner.aibnf.ai.build;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Older versions drew the magic circle hit effect with glowing display pieces tagged
 * {@value #TAG}. Effects are now colour dots drawn by each player's game; any such pieces
 * left in a world are removed when they load.
 */
public final class HitEffects {

    public static final String TAG = "aib_fx";

    private HitEffects() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (!event.getLevel().isClientSide() && event.getEntity().getTags().contains(TAG)) {
                event.setCanceled(true);
            }
        });
    }
}
