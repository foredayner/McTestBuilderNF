package io.github.foredayner.aibnf.ai;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Loads Minecraft's registries once so block names and states can be checked in plain JUnit. */
public final class Boot {
    private static boolean done;

    private Boot() {
    }

    public static synchronized void minecraft() {
        if (!done) {
            SharedConstants.tryDetectVersion();
            try {
                Bootstrap.bootStrap();
            } catch (ExceptionInInitializerError | RuntimeException exception) {
                // Forge's bootstrap ends by setting up its network code, which needs the full mod
                // loader. Vanilla's registries (blocks, items, states) are all ready by then.
            }
            done = true;
        }
    }
}
