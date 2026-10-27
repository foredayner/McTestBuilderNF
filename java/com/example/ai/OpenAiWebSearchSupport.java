package com.example.ai;

public final class OpenAiWebSearchSupport {

    private OpenAiWebSearchSupport() {
    }

    public static boolean isModelSupported(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }

        String model = modelName.trim().toLowerCase();

        if (model.contains("gpt-3.5")
            || model.contains("gpt-4-turbo")
            || model.contains("gpt-4-32k")
            || model.contains("gpt-4-1106")
            || model.contains("gpt-4-0125")) {
            return false;
        }

        return model.startsWith("gpt-4o")
            || model.startsWith("gpt-4.1")
            || model.startsWith("gpt-5")
            || model.startsWith("o3")
            || model.startsWith("o4");
    }

    public static String unsupportedModelMessage(String modelName) {
        return "Web search is not supported with model '"
            + modelName
            + "'. Change Settings > Model to gpt-4o, gpt-4.1, or gpt-5.x.";
    }
}
