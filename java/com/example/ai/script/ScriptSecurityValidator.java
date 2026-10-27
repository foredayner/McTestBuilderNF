package com.example.ai.script;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Fast pre-parse rejection layer. Runs before Groovy compiles the script so dangerous
 * source is discarded without invoking SecureASTCustomizer or GroovyShell.evaluate().
 *
 * <p>Test checklist (expected results):
 * <ul>
 *   <li>Allowed: ai.placeBlock(...), for loops, if statements, local variables</li>
 *   <li>Rejected: System.getenv(), Runtime.getRuntime().exec(...), new File(...),
 *       new Socket(...), Class.forName(...), new Thread(...), import java.io.File,
 *       class Evil {}</li>
 * </ul>
 */
public final class ScriptSecurityValidator {

    private static final Pattern CLASS_DEFINITION = Pattern.compile("(?m)^\\s*class\\s+[A-Za-z_][\\w]*");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^\\s*package\\s+");
    private static final Pattern IMPORT_DECLARATION = Pattern.compile("(?m)^\\s*import\\s+");

    private static final String[] FORBIDDEN_FRAGMENTS = {
        "system.getenv",
        "system.getproperties",
        "system.exit",
        "runtime.getruntime",
        "processbuilder",
        "class.forname",
        "synchronized",
        "new file(",
        "new socket(",
        "new url(",
        "new thread(",
        "groovy.lang.groovyshell",
        "groovy.lang.binding",
        "getbinding(",
        ".binding",
        "java.io.",
        "java.net.",
        "java.nio.",
        "java.lang.reflect.",
        "javax.script.",
        "eval.me(",
        "eval(",
        "methodpointer",
        "@grab",
        "@groovy.transform"
    };

    private ScriptSecurityValidator() {
    }

    /**
     * @return empty if the script passes the pre-parse scan, otherwise a player-safe reason
     */
    public static Optional<String> validateSource(String script) {
        if (script == null || script.isBlank()) {
            return Optional.empty();
        }

        String normalized = script.toLowerCase(Locale.ROOT);

        if (IMPORT_DECLARATION.matcher(script).find()) {
            return Optional.of("Imports are not allowed in AI build scripts.");
        }
        if (PACKAGE_DECLARATION.matcher(script).find()) {
            return Optional.of("Package declarations are not allowed in AI build scripts.");
        }
        if (CLASS_DEFINITION.matcher(script).find()) {
            return Optional.of("Class definitions are not allowed in AI build scripts.");
        }

        for (String fragment : FORBIDDEN_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                return Optional.of("Disallowed construct detected: " + fragment);
            }
        }

        return Optional.empty();
    }
}
