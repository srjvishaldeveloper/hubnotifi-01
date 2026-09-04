# Complete Laravel AI Inventory

## Routes & Controllers
- `GET /app/ai/providers` -> `AiProviderController@index` (Inertia: `AI/Providers/Index`)
- `PUT /app/ai/providers/{provider}` -> `AiProviderController@update`
- `GET /app/ai/knowledge-bases` -> `AiKnowledgeBaseController@index` (Inertia: `AI/KnowledgeBases/Index`)
- `POST /app/ai/knowledge-bases` -> `AiKnowledgeBaseController@store`
- `GET /app/ai/knowledge-bases/{kb}` -> `AiKnowledgeBaseController@show` (Inertia: `AI/KnowledgeBases/Show`)
- `POST /app/ai/knowledge-bases/{kb}/documents` -> `AiKnowledgeBaseController@addDocument`
- `POST /app/ai/documents/{document}/reindex` -> `AiKnowledgeBaseController@reindex`
- `DELETE /app/ai/documents/{document}` -> `AiKnowledgeBaseController@destroyDocument`
- `GET /app/ai/chatbots` -> `AiChatbotController@index` (Inertia: `AI/Chatbots/Index`)
- `POST /app/ai/chatbots` -> `AiChatbotController@store`
- `PUT /app/ai/chatbots/{chatbot}` -> `AiChatbotController@update`
- `DELETE /app/ai/chatbots/{chatbot}` -> `AiChatbotController@destroy`
- `POST /app/ai/chatbots/{chatbot}/playground` -> `AiChatbotController@playground`

## Models
- `AiProviderConfig` (`ai_provider_configs` table)
- `AiKnowledgeBase` (`ai_knowledge_bases` table)
- `AiKbDocument` (`ai_kb_documents` table)
- `AiKbChunk` (`ai_kb_chunks` table)
- `AiChatbot` (`ai_chatbots` table)
- `AiRun` (`ai_runs` table)

## Services & Jobs
- `LlmManager`: Resolves chat and embedding LLM providers.
- `LlmGateway`: Central entry point for LLM chat/embed calls and execution logging (`AiRun`).
- `OpenAiProvider`: OpenAI Chat API (`gpt-4o-mini`) & Embeddings API (`text-embedding-3-small`).
- `AnthropicProvider`: Anthropic Messages API (`claude-3-haiku-20240307`).
- `GeminiProvider`: Gemini GenerateContent API (`gemini-1.5-flash`) & BatchEmbedContents API (`text-embedding-004`).
- `EmbeddingStore`: Vector similarity search (MySQL Cosine fallback + Qdrant support).
- `ChatbotRunner`: RAG pipeline, context building, history retrieval.
- `WorkflowGenerator`: Natural-language automation workflow generation (`generate`, `normalise`, BFS `layout`).
- `IndexDocumentJob`: Text extraction (text, file, url, faq, sitemap), 800-word chunking, embedding generation.
