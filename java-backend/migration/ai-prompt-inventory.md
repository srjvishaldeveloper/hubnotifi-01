# AI Prompt Inventory

## 1. Chatbot System Prompt (`ChatbotRunner`)
Default prompt: `"You are a helpful assistant."` (customizable per chatbot).
Append Context Format:
```text
<system_prompt>

Relevant context:
<chunk_1>

---

<chunk_2>
```

## 2. Conversation History Context (`ChatbotRunner`)
Appends last 20 messages from `messages` table for the conversation:
- `direction = 'out'` -> `role: 'assistant'`
- `direction = 'in'` -> `role: 'user'`

## 3. Workflow Generator Prompt (`WorkflowGenerator`)
System prompt instructs the LLM to output a single automation graph JSON structure containing:
- `name`: Max 60 chars title
- `trigger_type`: One of valid trigger types
- `nodes`: List of action nodes with valid types and data objects
- `edges`: List of edge objects with `source`, `target`, and optional `sourceHandle` ("true"/"false")
