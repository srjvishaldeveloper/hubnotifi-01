# AI Provider Mapping

| Provider | Chat Model | Embed Model | Authentication | Endpoints |
|---|---|---|---|---|
| **OpenAI** | `gpt-4o-mini` | `text-embedding-3-small` | Bearer Token (`api_key`), optional `OpenAI-Organization` header | `https://api.openai.com/v1/chat/completions`<br>`https://api.openai.com/v1/embeddings` |
| **Anthropic** | `claude-3-haiku-20240307` | N/A (Not supported) | `x-api-key` header, `anthropic-version: 2023-06-01` | `https://api.anthropic.com/v1/messages` |
| **Gemini** | `gemini-1.5-flash` | `text-embedding-004` | `key` query parameter | `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`<br>`https://generativelanguage.googleapis.com/v1beta/models/{model}:batchEmbedContents` |

## Resolution Logic (`LlmManager`)
1. Workspace configurations (`ai_provider_configs` table) prioritized by `openai` -> `anthropic` -> `gemini`.
2. Fallback to system environment variables (`AI_OPENAI_API_KEY`, `AI_ANTHROPIC_API_KEY`, `AI_GEMINI_API_KEY`).
3. For embeddings, Anthropic is automatically skipped as it does not natively support embeddings.
