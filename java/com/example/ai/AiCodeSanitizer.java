package com.example.ai;

import com.example.ai.script.AiScriptApi;

import java.util.regex.Pattern;

public final class AiCodeSanitizer {

    private AiCodeSanitizer() {
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }

        String code = raw.trim();
        if (code.isEmpty()) {
            return "";
        }

        if (code.startsWith("```")) {
            int firstLineEnd = code.indexOf('\n');
            if (firstLineEnd >= 0) {
                code = code.substring(firstLineEnd + 1);
            } else {
                code = code.substring(3);
            }
        }

        int closingFence = code.lastIndexOf("```");
        if (closingFence >= 0) {
            code = code.substring(0, closingFence);
        }

        return prefixAiApiCalls(code.trim());
    }

    private static String prefixAiApiCalls(String code) {
        String result = code;
        for (String method : AiScriptApi.METHOD_NAMES) {
            result = result.replaceAll(
                "(?m)(?<!ai\\.)(?<![\\w.])" + Pattern.quote(method) + "\\s*\\(",
                "ai." + method + "("
            );
        }
        return result;
    }
}
