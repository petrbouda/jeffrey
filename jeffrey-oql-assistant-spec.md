# Jeffrey OQL Assistant - Implementation Specification

## Overview

This document specifies the implementation of an AI-powered OQL (Object Query Language) assistant for Jeffrey, a Java Flight Recorder analysis tool. The assistant helps users generate OQL queries for heap dump analysis through natural language conversation.

**Repository:** https://github.com/petrbouda/jeffrey
**Tech Stack:** Java 21+, Spring Boot, Vue.js, DuckDB

---

## Feature Requirements

### Functional Requirements

1. **Natural Language to OQL**: Users describe what they want to find in plain language, AI generates valid OQL queries
2. **Conversational Interface**: Support iterative refinement through multi-turn conversation
3. **Context-Aware**: Include heap dump metadata (class histogram, top classes by size) in AI context
4. **Provider Agnostic**: Support multiple AI providers (Anthropic Claude, OpenAI, Ollama) via Spring AI
5. **Apply to Editor**: Generated queries can be applied directly to the OQL editor
6. **Run Directly**: Option to execute generated query immediately

### Non-Functional Requirements

1. **Optional Feature**: AI assistant is opt-in, Jeffrey works fully without it
2. **BYOK (Bring Your Own Key)**: Users provide their own API keys
3. **Privacy**: No heap dump data sent to AI except class names and basic metadata
4. **Offline Support**: Ollama option for fully local/offline usage

---

## Architecture

### High-Level Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Vue.js UI     │────▶│  Spring Boot    │────▶│   AI Provider   │
│  Chat Component │     │  OQL Assistant  │     │ (Claude/OpenAI/ │
│                 │◀────│    Service      │◀────│    Ollama)      │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                               │
                               ▼
                        ┌─────────────────┐
                        │   Heap Dump     │
                        │   Repository    │
                        │  (Class Info)   │
                        └─────────────────┘
```

### Component Responsibilities

| Component | Responsibility |
|-----------|----------------|
| `OqlAssistantController` | REST API endpoints for chat |
| `OqlAssistantService` | Orchestrates AI calls, manages conversation |
| `HeapDumpContextService` | Extracts relevant metadata from heap dumps |
| `OqlAssistantPanel.vue` | Chat UI component |
| `useOqlAssistant.js` | Composable for chat state management |

---

## Backend Implementation

### Dependencies (pom.xml)

```xml
<!-- Spring AI BOM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- AI Provider Starters - include all, activate via config -->
<dependencies>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-anthropic-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

### Configuration (application.yml)

```yaml
jeffrey:
  ai:
    enabled: ${AI_ENABLED:false}
    provider: ${AI_PROVIDER:none}  # none | anthropic | openai | ollama

spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
      chat:
        options:
          model: claude-sonnet-4-5-20250929
          max-tokens: 1024
    
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        options:
          model: gpt-4o
          max-tokens: 1024
    
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: ${OLLAMA_MODEL:llama3.1}
```

### Configuration Class

```java
package cz.hrabosch.jeffrey.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jeffrey.ai")
public record AiConfiguration(
    boolean enabled,
    String provider
) {
    public boolean isConfigured() {
        return enabled && provider != null && !provider.equals("none");
    }
}
```

### System Prompt

```java
package cz.hrabosch.jeffrey.assistant;

public final class OqlSystemPrompt {

    public static final String SYSTEM_PROMPT = """
        You are an OQL (Object Query Language) expert assistant for Java heap dump analysis.
        Your role is to help users generate OQL queries to analyze heap dumps and find memory issues.
        
        ## OQL Syntax Reference
        
        ### Basic SELECT
        ```
        select <expression> from <class> [<identifier>] [where <condition>]
        ```
        
        ### Examples by Category
        
        **Find instances:**
        ```sql
        select s from java.lang.String s
        select h from java.util.HashMap h where h.size > 100
        ```
        
        **Size functions:**
        - `sizeof(obj)` - shallow size in bytes
        - `rsizeof(obj)` - retained size (deep size)
        - `objectid(obj)` - unique object ID
        
        ```sql
        select s from java.lang.String s where sizeof(s) > 1024
        select {obj: s, size: rsizeof(s)} from java.lang.String s where rsizeof(s) > 10000
        ```
        
        **Reference traversal:**
        - `referrers(obj)` - objects that reference this object
        - `referees(obj)` - objects this object references  
        - `reachables(obj)` - all objects reachable from this object
        
        ```sql
        select referrers(s) from java.lang.String s where s.toString().contains("leak")
        select referees(h) from java.util.HashMap h
        ```
        
        **Class and type:**
        - `classof(obj)` - returns class of object
        - `heap.findClass("className")` - find class by name
        
        ```sql
        select classof(o).name from java.lang.Object o
        ```
        
        **Array access:**
        ```sql
        select s from java.lang.String s where s.value.length > 1000
        select a from java.lang.Object[] a where a.length > 100
        ```
        
        **Aggregation:**
        - `count(collection)` - count elements
        - `sum(collection, expression)` - sum values
        - `unique(collection)` - unique elements
        
        ```sql
        select count(filter(heap.objects('java.lang.String'), 'it.value.length > 100'))
        ```
        
        **Subselects and filtering:**
        ```sql
        select s from java.lang.String s where contains(referrers(s), 'classof(it).name.contains("Cache")')
        ```
        
        ## Response Guidelines
        
        1. Generate ONLY the OQL query unless the user asks for explanation
        2. If explaining, be concise - users are typically developers
        3. If the query might return many results, suggest adding a limit or filter
        4. If you're unsure about the exact class name, use the provided class list
        5. For memory leak investigation, suggest checking referrers
        6. Always prefer retained size (rsizeof) over shallow size for leak detection
        
        ## Common Memory Analysis Patterns
        
        **Find potential leaks (large retained size):**
        ```sql
        select {class: classof(o).name, retained: rsizeof(o)} from java.lang.Object o where rsizeof(o) > 1000000
        ```
        
        **String duplicates:**
        ```sql
        select unique(s.toString()) from java.lang.String s
        ```
        
        **Collection sizing issues:**
        ```sql
        select {map: h, size: h.size, retained: rsizeof(h)} from java.util.HashMap h where h.size > 1000
        ```
        
        **Find what's holding an object:**
        ```sql
        select referrers(o) from java.lang.Object o where objectid(o) == <id>
        ```
        """;
    
    private OqlSystemPrompt() {}
}
```

### DTOs

```java
package cz.hrabosch.jeffrey.assistant.dto;

import java.util.List;

public record OqlChatRequest(
    String message,
    List<ChatMessage> history
) {}

public record ChatMessage(
    String role,  // "user" or "assistant"
    String content,
    String oql    // nullable, only for assistant messages with queries
) {}

public record OqlChatResponse(
    String content,
    String oql,        // extracted OQL query, null if none
    List<String> suggestedFollowups
) {}

public record HeapDumpContext(
    List<ClassInfo> topClassesByCount,
    List<ClassInfo> topClassesByRetainedSize,
    long totalObjects,
    long totalSize
) {}

public record ClassInfo(
    String className,
    long instanceCount,
    long shallowSize,
    long retainedSize
) {}

public record AiStatusResponse(
    boolean enabled,
    String provider,
    boolean configured
) {}
```

### Heap Dump Context Service

```java
package cz.hrabosch.jeffrey.assistant.service;

import cz.hrabosch.jeffrey.assistant.dto.ClassInfo;
import cz.hrabosch.jeffrey.assistant.dto.HeapDumpContext;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class HeapDumpContextService {

    private static final int TOP_CLASSES_LIMIT = 50;

    /**
     * Extract relevant context from heap dump for AI prompt.
     * This should use Jeffrey's existing heap dump analysis capabilities.
     */
    public HeapDumpContext extractContext(String heapDumpId) {
        // TODO: Integrate with Jeffrey's heap dump repository/service
        // This is a placeholder - implement using actual heap dump access
        
        // Example of what this should return:
        // - Load heap dump by ID
        // - Get class histogram
        // - Sort by instance count and retained size
        // - Return top N classes
        
        throw new UnsupportedOperationException(
            "Implement using Jeffrey's heap dump access layer"
        );
    }

    /**
     * Format context as text for inclusion in AI prompt.
     */
    public String formatContextForPrompt(HeapDumpContext context) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("## Heap Dump Summary\n");
        sb.append("Total objects: ").append(context.totalObjects()).append("\n");
        sb.append("Total size: ").append(formatBytes(context.totalSize())).append("\n\n");
        
        sb.append("## Top Classes by Instance Count\n");
        for (ClassInfo cls : context.topClassesByCount()) {
            sb.append("- ").append(cls.className())
              .append(" (").append(cls.instanceCount()).append(" instances, ")
              .append(formatBytes(cls.retainedSize())).append(" retained)\n");
        }
        
        sb.append("\n## Top Classes by Retained Size\n");
        for (ClassInfo cls : context.topClassesByRetainedSize()) {
            sb.append("- ").append(cls.className())
              .append(" (").append(formatBytes(cls.retainedSize())).append(" retained, ")
              .append(cls.instanceCount()).append(" instances)\n");
        }
        
        return sb.toString();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
```

### OQL Assistant Service

```java
package cz.hrabosch.jeffrey.assistant.service;

import cz.hrabosch.jeffrey.assistant.OqlSystemPrompt;
import cz.hrabosch.jeffrey.assistant.config.AiConfiguration;
import cz.hrabosch.jeffrey.assistant.dto.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OqlAssistantService {

    private static final Pattern OQL_PATTERN = Pattern.compile(
        "(?:```(?:sql|oql)?\\s*)?\\s*(select\\s+.+?)\\s*(?:```)?\\s*$",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final ChatClient chatClient;
    private final HeapDumpContextService contextService;
    private final AiConfiguration aiConfig;

    public OqlAssistantService(
            ChatClient.Builder chatClientBuilder,
            HeapDumpContextService contextService,
            AiConfiguration aiConfig) {
        
        this.contextService = contextService;
        this.aiConfig = aiConfig;
        
        if (aiConfig.isConfigured()) {
            this.chatClient = chatClientBuilder
                .defaultSystem(OqlSystemPrompt.SYSTEM_PROMPT)
                .build();
        } else {
            this.chatClient = null;
        }
    }

    public boolean isAvailable() {
        return chatClient != null && aiConfig.isConfigured();
    }

    public OqlChatResponse chat(String heapDumpId, OqlChatRequest request) {
        if (!isAvailable()) {
            throw new IllegalStateException("AI assistant is not configured");
        }

        // Extract heap dump context
        HeapDumpContext context = contextService.extractContext(heapDumpId);
        String contextText = contextService.formatContextForPrompt(context);

        // Build conversation messages
        List<Message> messages = new ArrayList<>();
        
        // Add history
        if (request.history() != null) {
            for (ChatMessage msg : request.history()) {
                if ("user".equals(msg.role())) {
                    messages.add(new UserMessage(msg.content()));
                } else {
                    String content = msg.oql() != null 
                        ? msg.content() + "\n```sql\n" + msg.oql() + "\n```"
                        : msg.content();
                    messages.add(new AssistantMessage(content));
                }
            }
        }

        // Add current user message with context
        String userMessageWithContext = """
            ## Current Heap Dump Context
            %s
            
            ## User Request
            %s
            """.formatted(contextText, request.message());
        
        messages.add(new UserMessage(userMessageWithContext));

        // Call AI
        String response = chatClient.prompt()
            .messages(messages)
            .call()
            .content();

        // Extract OQL from response
        String extractedOql = extractOql(response);
        
        // Clean response text (remove code blocks if OQL was extracted)
        String cleanedContent = extractedOql != null 
            ? response.replaceAll("```(?:sql|oql)?\\s*" + Pattern.quote(extractedOql) + "\\s*```", "").trim()
            : response;

        return new OqlChatResponse(
            cleanedContent.isEmpty() ? "Here's a query for that:" : cleanedContent,
            extractedOql,
            generateFollowupSuggestions(extractedOql)
        );
    }

    private String extractOql(String response) {
        // Try to find OQL in code blocks first
        Pattern codeBlockPattern = Pattern.compile("```(?:sql|oql)?\\s*(select.+?)```", 
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = codeBlockPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // Try to find standalone select statement
        Pattern selectPattern = Pattern.compile("^\\s*(select\\s+.+)$", 
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        matcher = selectPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    private List<String> generateFollowupSuggestions(String oql) {
        if (oql == null) {
            return List.of();
        }
        
        List<String> suggestions = new ArrayList<>();
        
        if (oql.toLowerCase().contains("from java.lang.string")) {
            suggestions.add("Show what's holding these strings");
            suggestions.add("Find duplicate string values");
        }
        
        if (oql.toLowerCase().contains("hashmap") || oql.toLowerCase().contains("arraylist")) {
            suggestions.add("Show entries with largest retained size");
            suggestions.add("Find what references these collections");
        }
        
        if (!oql.toLowerCase().contains("rsizeof")) {
            suggestions.add("Add retained size to results");
        }
        
        if (!oql.toLowerCase().contains("referrers")) {
            suggestions.add("Show what's holding these objects");
        }
        
        return suggestions.stream().limit(3).toList();
    }
}
```

### REST Controller

```java
package cz.hrabosch.jeffrey.assistant.controller;

import cz.hrabosch.jeffrey.assistant.config.AiConfiguration;
import cz.hrabosch.jeffrey.assistant.dto.AiStatusResponse;
import cz.hrabosch.jeffrey.assistant.dto.OqlChatRequest;
import cz.hrabosch.jeffrey.assistant.dto.OqlChatResponse;
import cz.hrabosch.jeffrey.assistant.service.OqlAssistantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/heapdump/{heapDumpId}/oql-assistant")
public class OqlAssistantController {

    private final OqlAssistantService assistantService;
    private final AiConfiguration aiConfig;

    public OqlAssistantController(
            OqlAssistantService assistantService,
            AiConfiguration aiConfig) {
        this.assistantService = assistantService;
        this.aiConfig = aiConfig;
    }

    @GetMapping("/status")
    public AiStatusResponse getStatus() {
        return new AiStatusResponse(
            aiConfig.enabled(),
            aiConfig.provider(),
            assistantService.isAvailable()
        );
    }

    @PostMapping("/chat")
    public ResponseEntity<OqlChatResponse> chat(
            @PathVariable String heapDumpId,
            @RequestBody OqlChatRequest request) {
        
        if (!assistantService.isAvailable()) {
            return ResponseEntity.status(503)
                .body(new OqlChatResponse(
                    "AI assistant is not configured. Please set up an AI provider in settings.",
                    null,
                    List.of()
                ));
        }

        OqlChatResponse response = assistantService.chat(heapDumpId, request);
        return ResponseEntity.ok(response);
    }
}
```

---

## Frontend Implementation

### Directory Structure

```
src/
  components/
    heapdump/
      oql/
        OqlEditor.vue          # Existing OQL editor
        OqlAssistantPanel.vue  # New: Chat panel component
        OqlAssistantButton.vue # New: Toggle button for assistant
  composables/
    useOqlAssistant.js         # New: Chat state management
  api/
    oqlAssistant.js            # New: API client
```

### API Client (api/oqlAssistant.js)

```javascript
import { api } from './base'  // Your existing API client setup

export const oqlAssistantApi = {
  
  async getStatus(heapDumpId) {
    const response = await api.get(`/heapdump/${heapDumpId}/oql-assistant/status`)
    return response.data
  },

  async chat(heapDumpId, message, history = []) {
    const response = await api.post(`/heapdump/${heapDumpId}/oql-assistant/chat`, {
      message,
      history
    })
    return response.data
  }
}
```

### Composable (composables/useOqlAssistant.js)

```javascript
import { ref, computed } from 'vue'
import { oqlAssistantApi } from '@/api/oqlAssistant'

export function useOqlAssistant(heapDumpId) {
  const messages = ref([])
  const isLoading = ref(false)
  const error = ref(null)
  const isAvailable = ref(false)
  const provider = ref(null)

  // Initial welcome message
  const initializeChat = () => {
    messages.value = [{
      id: generateId(),
      role: 'assistant',
      content: 'Hi! I can help you generate OQL queries. Describe what you want to find in the heap dump.',
      oql: null,
      timestamp: new Date()
    }]
  }

  // Check if AI is available
  const checkAvailability = async () => {
    try {
      const status = await oqlAssistantApi.getStatus(heapDumpId.value)
      isAvailable.value = status.configured
      provider.value = status.provider
    } catch (e) {
      isAvailable.value = false
    }
  }

  // Send message
  const sendMessage = async (userMessage) => {
    if (!userMessage.trim() || isLoading.value) return

    // Add user message
    messages.value.push({
      id: generateId(),
      role: 'user',
      content: userMessage,
      oql: null,
      timestamp: new Date()
    })

    isLoading.value = true
    error.value = null

    try {
      // Prepare history (exclude welcome message)
      const history = messages.value
        .filter(m => m.role !== 'system')
        .slice(0, -1)  // Exclude the message we just added
        .map(m => ({
          role: m.role,
          content: m.content,
          oql: m.oql
        }))

      const response = await oqlAssistantApi.chat(
        heapDumpId.value,
        userMessage,
        history
      )

      // Add assistant response
      messages.value.push({
        id: generateId(),
        role: 'assistant',
        content: response.content,
        oql: response.oql,
        suggestions: response.suggestedFollowups,
        timestamp: new Date()
      })
    } catch (e) {
      error.value = e.message || 'Failed to get response'
      messages.value.push({
        id: generateId(),
        role: 'assistant',
        content: 'Sorry, I encountered an error. Please try again.',
        oql: null,
        timestamp: new Date()
      })
    } finally {
      isLoading.value = false
    }
  }

  // Clear chat
  const clearChat = () => {
    initializeChat()
    error.value = null
  }

  // Generate unique ID
  const generateId = () => {
    return `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
  }

  return {
    messages,
    isLoading,
    error,
    isAvailable,
    provider,
    sendMessage,
    clearChat,
    checkAvailability,
    initializeChat
  }
}
```

### Chat Panel Component (components/heapdump/oql/OqlAssistantPanel.vue)

```vue
<template>
  <div class="oql-assistant-panel" :class="{ 'is-open': isOpen }">
    <!-- Header -->
    <div class="assistant-header">
      <div class="header-title">
        <span class="icon">✨</span>
        <span>OQL Assistant</span>
        <span v-if="provider" class="provider-badge">{{ provider }}</span>
      </div>
      <div class="header-actions">
        <button @click="clearChat" class="btn-icon" title="Clear chat">
          🗑️
        </button>
        <button @click="$emit('close')" class="btn-icon" title="Close">
          ✕
        </button>
      </div>
    </div>

    <!-- Messages -->
    <div class="messages-container" ref="messagesContainer">
      <div
        v-for="message in messages"
        :key="message.id"
        class="message"
        :class="message.role"
      >
        <div class="message-content">
          <p>{{ message.content }}</p>
          
          <!-- OQL Code Block -->
          <div v-if="message.oql" class="oql-block">
            <pre><code>{{ message.oql }}</code></pre>
            <div class="oql-actions">
              <button @click="$emit('apply-query', message.oql)" class="btn-primary">
                Apply to Editor
              </button>
              <button @click="$emit('run-query', message.oql)" class="btn-secondary">
                Run Query
              </button>
              <button @click="copyToClipboard(message.oql)" class="btn-icon" title="Copy">
                📋
              </button>
            </div>
          </div>

          <!-- Suggested Follow-ups -->
          <div v-if="message.suggestions?.length" class="suggestions">
            <button
              v-for="suggestion in message.suggestions"
              :key="suggestion"
              @click="sendMessage(suggestion)"
              class="suggestion-chip"
            >
              {{ suggestion }}
            </button>
          </div>
        </div>
      </div>

      <!-- Loading indicator -->
      <div v-if="isLoading" class="message assistant">
        <div class="message-content">
          <div class="typing-indicator">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    </div>

    <!-- Input Area -->
    <div class="input-area">
      <textarea
        v-model="inputText"
        @keydown.enter.exact.prevent="handleSend"
        :disabled="isLoading"
        placeholder="Describe what you want to find..."
        rows="2"
      />
      <button
        @click="handleSend"
        :disabled="!inputText.trim() || isLoading"
        class="send-button"
      >
        <span v-if="isLoading">...</span>
        <span v-else>Send</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import { useOqlAssistant } from '@/composables/useOqlAssistant'

const props = defineProps({
  heapDumpId: {
    type: String,
    required: true
  },
  isOpen: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'apply-query', 'run-query'])

const heapDumpIdRef = ref(props.heapDumpId)

const {
  messages,
  isLoading,
  isAvailable,
  provider,
  sendMessage,
  clearChat,
  checkAvailability,
  initializeChat
} = useOqlAssistant(heapDumpIdRef)

const inputText = ref('')
const messagesContainer = ref(null)

onMounted(async () => {
  await checkAvailability()
  initializeChat()
})

watch(() => props.heapDumpId, (newId) => {
  heapDumpIdRef.value = newId
  clearChat()
  checkAvailability()
})

watch(messages, async () => {
  await nextTick()
  scrollToBottom()
}, { deep: true })

const handleSend = () => {
  if (!inputText.value.trim() || isLoading.value) return
  sendMessage(inputText.value)
  inputText.value = ''
}

const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
  } catch (e) {
    console.error('Failed to copy:', e)
  }
}
</script>

<style scoped>
.oql-assistant-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-secondary, #1e1e1e);
  border-left: 1px solid var(--border-color, #333);
}

.assistant-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color, #333);
  background: var(--bg-tertiary, #252525);
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.header-title .icon {
  font-size: 18px;
}

.provider-badge {
  font-size: 11px;
  padding: 2px 6px;
  background: var(--accent-color, #4a9eff);
  border-radius: 4px;
  text-transform: uppercase;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.btn-icon {
  background: none;
  border: none;
  padding: 4px 8px;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.2s;
}

.btn-icon:hover {
  opacity: 1;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message {
  display: flex;
  flex-direction: column;
}

.message.user {
  align-items: flex-end;
}

.message.assistant {
  align-items: flex-start;
}

.message-content {
  max-width: 85%;
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--bg-tertiary, #252525);
}

.message.user .message-content {
  background: var(--accent-color, #4a9eff);
  color: white;
}

.message-content p {
  margin: 0 0 8px 0;
}

.message-content p:last-child {
  margin-bottom: 0;
}

.oql-block {
  margin-top: 8px;
  background: var(--bg-code, #1a1a1a);
  border-radius: 8px;
  overflow: hidden;
}

.oql-block pre {
  margin: 0;
  padding: 12px;
  overflow-x: auto;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
}

.oql-actions {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid var(--border-color, #333);
  flex-wrap: wrap;
}

.btn-primary {
  background: var(--accent-color, #4a9eff);
  color: white;
  border: none;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.btn-primary:hover {
  filter: brightness(1.1);
}

.btn-secondary {
  background: transparent;
  color: var(--text-color, #ccc);
  border: 1px solid var(--border-color, #444);
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.btn-secondary:hover {
  background: var(--bg-tertiary, #252525);
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.suggestion-chip {
  background: var(--bg-tertiary, #333);
  border: 1px solid var(--border-color, #444);
  padding: 4px 10px;
  border-radius: 16px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.suggestion-chip:hover {
  background: var(--accent-color, #4a9eff);
  border-color: var(--accent-color, #4a9eff);
  color: white;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: var(--text-muted, #666);
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

@keyframes typing {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.input-area {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--border-color, #333);
  background: var(--bg-tertiary, #252525);
}

.input-area textarea {
  flex: 1;
  resize: none;
  padding: 10px 12px;
  border: 1px solid var(--border-color, #444);
  border-radius: 8px;
  background: var(--bg-secondary, #1e1e1e);
  color: var(--text-color, #eee);
  font-family: inherit;
  font-size: 14px;
}

.input-area textarea:focus {
  outline: none;
  border-color: var(--accent-color, #4a9eff);
}

.send-button {
  padding: 10px 20px;
  background: var(--accent-color, #4a9eff);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 500;
}

.send-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-button:not(:disabled):hover {
  filter: brightness(1.1);
}
</style>
```

### Toggle Button Component (components/heapdump/oql/OqlAssistantButton.vue)

```vue
<template>
  <button 
    v-if="isAvailable"
    @click="$emit('toggle')"
    class="assistant-toggle"
    :class="{ active: isOpen }"
    :title="isOpen ? 'Close AI Assistant' : 'Open AI Assistant'"
  >
    <span class="icon">✨</span>
    <span class="label">AI Assistant</span>
  </button>
</template>

<script setup>
defineProps({
  isOpen: Boolean,
  isAvailable: Boolean
})

defineEmits(['toggle'])
</script>

<style scoped>
.assistant-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s;
}

.assistant-toggle:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.assistant-toggle.active {
  background: linear-gradient(135deg, #764ba2 0%, #667eea 100%);
}

.icon {
  font-size: 16px;
}
</style>
```

### Integration with OQL Editor

Add to your existing OQL editor parent component:

```vue
<template>
  <div class="oql-workspace">
    <!-- Existing OQL Editor -->
    <div class="editor-section">
      <div class="editor-toolbar">
        <button @click="runQuery">Run Query</button>
        <OqlAssistantButton 
          :is-open="showAssistant"
          :is-available="aiAvailable"
          @toggle="showAssistant = !showAssistant"
        />
      </div>
      
      <OqlEditor 
        v-model="query"
        @run="runQuery"
      />
      
      <div class="results">
        <!-- Query results -->
      </div>
    </div>

    <!-- AI Assistant Panel (slide-in) -->
    <transition name="slide">
      <OqlAssistantPanel
        v-if="showAssistant"
        :heap-dump-id="heapDumpId"
        :is-open="showAssistant"
        @close="showAssistant = false"
        @apply-query="applyQuery"
        @run-query="runGeneratedQuery"
      />
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import OqlEditor from './OqlEditor.vue'
import OqlAssistantPanel from './OqlAssistantPanel.vue'
import OqlAssistantButton from './OqlAssistantButton.vue'
import { oqlAssistantApi } from '@/api/oqlAssistant'

const props = defineProps({
  heapDumpId: {
    type: String,
    required: true
  }
})

const query = ref('')
const showAssistant = ref(false)
const aiAvailable = ref(false)

onMounted(async () => {
  try {
    const status = await oqlAssistantApi.getStatus(props.heapDumpId)
    aiAvailable.value = status.configured
  } catch (e) {
    aiAvailable.value = false
  }
})

const applyQuery = (oql) => {
  query.value = oql
}

const runGeneratedQuery = (oql) => {
  query.value = oql
  runQuery()
}

const runQuery = () => {
  // Execute the query
}
</script>

<style scoped>
.oql-workspace {
  display: flex;
  height: 100%;
}

.editor-section {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.editor-toolbar {
  display: flex;
  gap: 8px;
  padding: 8px;
  border-bottom: 1px solid var(--border-color);
}

/* Slide transition for assistant panel */
.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>
```

---

## Settings UI for API Key Configuration

Add a settings section for users to configure their AI provider:

```vue
<!-- components/settings/AiSettings.vue -->
<template>
  <div class="ai-settings">
    <h3>AI Assistant Settings</h3>
    
    <div class="setting-group">
      <label>AI Provider</label>
      <select v-model="settings.provider">
        <option value="none">Disabled</option>
        <option value="anthropic">Anthropic (Claude)</option>
        <option value="openai">OpenAI (GPT-4)</option>
        <option value="ollama">Ollama (Local)</option>
      </select>
    </div>

    <template v-if="settings.provider === 'anthropic'">
      <div class="setting-group">
        <label>Anthropic API Key</label>
        <input 
          type="password" 
          v-model="settings.anthropicApiKey"
          placeholder="sk-ant-..."
        />
        <small>Get your API key from <a href="https://console.anthropic.com" target="_blank">console.anthropic.com</a></small>
      </div>
    </template>

    <template v-if="settings.provider === 'openai'">
      <div class="setting-group">
        <label>OpenAI API Key</label>
        <input 
          type="password" 
          v-model="settings.openaiApiKey"
          placeholder="sk-..."
        />
      </div>
    </template>

    <template v-if="settings.provider === 'ollama'">
      <div class="setting-group">
        <label>Ollama URL</label>
        <input 
          type="text" 
          v-model="settings.ollamaUrl"
          placeholder="http://localhost:11434"
        />
      </div>
      <div class="setting-group">
        <label>Model</label>
        <input 
          type="text" 
          v-model="settings.ollamaModel"
          placeholder="llama3.1"
        />
      </div>
    </template>

    <button @click="saveSettings" class="btn-primary">Save Settings</button>
  </div>
</template>
```

---

## Testing

### Backend Unit Tests

```java
@SpringBootTest
class OqlAssistantServiceTest {

    @Autowired
    private OqlAssistantService assistantService;

    @Test
    void shouldExtractOqlFromCodeBlock() {
        String response = """
            Here's a query for finding large strings:
            ```sql
            select s from java.lang.String s where sizeof(s) > 1024
            ```
            This will find all strings larger than 1KB.
            """;
        
        String oql = assistantService.extractOql(response);
        
        assertThat(oql).isEqualTo("select s from java.lang.String s where sizeof(s) > 1024");
    }

    @Test
    void shouldGenerateSuggestionsForStringQuery() {
        String oql = "select s from java.lang.String s";
        
        List<String> suggestions = assistantService.generateFollowupSuggestions(oql);
        
        assertThat(suggestions).contains("Show what's holding these strings");
    }
}
```

### Frontend Component Tests

```javascript
import { mount } from '@vue/test-utils'
import OqlAssistantPanel from './OqlAssistantPanel.vue'

describe('OqlAssistantPanel', () => {
  it('emits apply-query when Apply button clicked', async () => {
    const wrapper = mount(OqlAssistantPanel, {
      props: {
        heapDumpId: 'test-123',
        isOpen: true
      }
    })

    // Simulate receiving a response with OQL
    wrapper.vm.messages.push({
      role: 'assistant',
      content: 'Here is your query:',
      oql: 'select s from java.lang.String s'
    })
    
    await wrapper.vm.$nextTick()
    
    await wrapper.find('.btn-primary').trigger('click')
    
    expect(wrapper.emitted('apply-query')[0]).toEqual(['select s from java.lang.String s'])
  })
})
```

---

## Deployment Considerations

### Environment Variables

```bash
# Docker / Kubernetes
AI_ENABLED=true
AI_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-...

# Or for Ollama
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://ollama-service:11434
OLLAMA_MODEL=llama3.1
```

### Docker Compose Example

```yaml
services:
  jeffrey:
    image: jeffrey:latest
    environment:
      - AI_ENABLED=${AI_ENABLED:-false}
      - AI_PROVIDER=${AI_PROVIDER:-none}
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY:-}
      - OPENAI_API_KEY=${OPENAI_API_KEY:-}
      - OLLAMA_BASE_URL=http://ollama:11434
    ports:
      - "8080:8080"

  # Optional: Local Ollama for self-hosted AI
  ollama:
    image: ollama/ollama:latest
    volumes:
      - ollama-data:/root/.ollama
    profiles:
      - local-ai

volumes:
  ollama-data:
```

---

## Implementation Checklist

### Phase 1: Backend Foundation
- [ ] Add Spring AI dependencies to pom.xml
- [ ] Create AiConfiguration properties class
- [ ] Implement HeapDumpContextService (integrate with existing heap dump access)
- [ ] Create OqlAssistantService with Spring AI ChatClient
- [ ] Create OqlAssistantController REST endpoints
- [ ] Write unit tests

### Phase 2: Frontend Chat UI
- [ ] Create useOqlAssistant composable
- [ ] Create OqlAssistantPanel.vue component
- [ ] Create OqlAssistantButton.vue component
- [ ] Integrate with existing OQL editor
- [ ] Add CSS styling (adapt to Jeffrey's theme)

### Phase 3: Settings & Configuration
- [ ] Add AI settings to application.yml
- [ ] Create settings UI for API key configuration
- [ ] Implement settings persistence (localStorage or backend)
- [ ] Add status endpoint for UI availability check

### Phase 4: Polish & Testing
- [ ] Add error handling and user feedback
- [ ] Implement conversation history limits
- [ ] Add rate limiting / token counting
- [ ] E2E testing with Playwright/Cypress
- [ ] Documentation

---

## Notes for Implementation

1. **HeapDumpContextService Integration**: The placeholder needs to be connected to Jeffrey's existing heap dump analysis code. Look for existing services that provide class histogram data.

2. **Spring AI Version**: Use the latest stable version. The API may have minor changes - check the Spring AI documentation.

3. **Styling**: The CSS uses CSS variables - adapt to Jeffrey's existing theme system.

4. **State Management**: If Jeffrey uses Pinia/Vuex, consider moving the chat state there for better persistence.

5. **Token Limits**: For long conversations, implement history truncation to avoid exceeding context limits.
