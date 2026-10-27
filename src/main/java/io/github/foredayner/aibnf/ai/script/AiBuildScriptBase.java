package io.github.foredayner.aibnf.ai.script;

import groovy.lang.Script;

/**
 * Gives AI scripts a typed {@code ai} receiver so SecureASTCustomizer can allow
 * {@link AiScriptFacade} method calls instead of rejecting them as {@link Object} calls.
 */
public abstract class AiBuildScriptBase extends Script {

    protected final AiScriptFacade ai = new AiScriptFacade();
}
