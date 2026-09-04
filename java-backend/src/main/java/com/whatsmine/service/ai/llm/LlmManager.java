package com.whatsmine.service.ai.llm;

import com.whatsmine.model.AiProviderConfig;
import com.whatsmine.repository.AiProviderConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LlmManager {

    private static final List<String> EMBED_CAPABLE = List.of("openai", "gemini");

    @Autowired
    private AiProviderConfigRepository providerConfigRepository;

    public LlmProviderInterface forWorkspace(Long workspaceId) {
        List<AiProviderConfig> configs = providerConfigRepository.findByWorkspaceIdAndEnabledTrue(workspaceId);

        // Sort preference: openai -> anthropic -> gemini
        configs.sort((a, b) -> Integer.compare(providerPriority(a.getProvider()), providerPriority(b.getProvider())));

        for (AiProviderConfig config : configs) {
            String apiKey = getApiKeyFromConfig(config);
            if (!apiKey.isEmpty()) {
                return build(config.getProvider(), apiKey, config.getDefaultModelChat(), config.getDefaultModelEmbed());
            }
        }

        // System-level environment variable fallbacks
        String openaiKey = getEnv("AI_OPENAI_API_KEY", "OPENAI_API_KEY");
        if (!openaiKey.isEmpty()) return build("openai", openaiKey, null, null);

        String anthropicKey = getEnv("AI_ANTHROPIC_API_KEY", "ANTHROPIC_API_KEY");
        if (!anthropicKey.isEmpty()) return build("anthropic", anthropicKey, null, null);

        String geminiKey = getEnv("AI_GEMINI_API_KEY", "GEMINI_API_KEY");
        if (!geminiKey.isEmpty()) return build("gemini", geminiKey, null, null);

        throw new RuntimeException("No AI provider configured for workspace " + workspaceId);
    }

    public LlmProviderInterface forWorkspaceEmbed(Long workspaceId) {
        List<AiProviderConfig> configs = providerConfigRepository.findByWorkspaceIdAndEnabledTrue(workspaceId);

        for (String provider : EMBED_CAPABLE) {
            for (AiProviderConfig config : configs) {
                if (provider.equals(config.getProvider())) {
                    String apiKey = getApiKeyFromConfig(config);
                    if (!apiKey.isEmpty()) {
                        return build(config.getProvider(), apiKey, config.getDefaultModelChat(), config.getDefaultModelEmbed());
                    }
                }
            }
        }

        // System-level environment variable fallbacks (embed-capable only)
        String openaiKey = getEnv("AI_OPENAI_API_KEY", "OPENAI_API_KEY");
        if (!openaiKey.isEmpty()) return build("openai", openaiKey, null, null);

        String geminiKey = getEnv("AI_GEMINI_API_KEY", "GEMINI_API_KEY");
        if (!geminiKey.isEmpty()) return build("gemini", geminiKey, null, null);

        throw new RuntimeException("No embedding-capable AI provider (OpenAI or Gemini) configured for workspace " + workspaceId);
    }

    public LlmProviderInterface build(String provider, String apiKey, String chatModel, String embedModel) {
        return switch (provider.toLowerCase()) {
            case "openai" -> new OpenAiProvider(apiKey, chatModel, embedModel, null);
            case "anthropic" -> new AnthropicProvider(apiKey, chatModel);
            case "gemini" -> new GeminiProvider(apiKey, chatModel, embedModel);
            default -> throw new RuntimeException("Unknown LLM provider: " + provider);
        };
    }

    private int providerPriority(String p) {
        if ("openai".equalsIgnoreCase(p)) return 1;
        if ("anthropic".equalsIgnoreCase(p)) return 2;
        if ("gemini".equalsIgnoreCase(p)) return 3;
        return 99;
    }

    private String getApiKeyFromConfig(AiProviderConfig config) {
        if (config.getCredentials() != null && config.getCredentials().get("api_key") != null) {
            return String.valueOf(config.getCredentials().get("api_key")).trim();
        }
        return "";
    }

    private String getEnv(String... keys) {
        for (String key : keys) {
            String val = System.getenv(key);
            if (val != null && !val.isBlank()) return val.trim();
        }
        return "";
    }
}
