# Spring AI Jlama

Spring AI integration for [Jlama](https://github.com/tjake/Jlama) - a pure Java LLM inference engine.

## Features

- **In-process inference** - Run LLMs directly in the JVM without external dependencies
- **No API calls** - Complete data privacy, all processing happens locally
- **Spring AI compatible** - Works with `ChatClient`, `ChatModel`, and all Spring AI abstractions
- **Auto-configuration** - Just add the dependency and configure via `application.yml`
- **Streaming support** - Full support for streaming responses via `Flux`

## Requirements

- **Java 21+** (uses Vector API for SIMD acceleration)
- **JVM arguments**: `--add-modules jdk.incubator.vector --enable-preview`
- **RAM**: 1.5-4GB depending on model size

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>pbouda.jeffrey</groupId>
    <artifactId>spring-ai-jlama</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure JVM

Add to your JVM arguments:

```bash
--add-modules jdk.incubator.vector --enable-preview
```

For Maven:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <jvmArguments>--enable-preview --add-modules jdk.incubator.vector</jvmArguments>
    </configuration>
</plugin>
```

### 3. Configure Application

```yaml
spring:
  ai:
    jlama:
      enabled: true
      model: tjake/Llama-3.2-1B-Instruct-JQ4
      working-directory: ./models
      working-memory-type: F32    # F32, F16, or BF16
      quantized-memory-type: I8   # I8, Q4, or Q5
      chat:
        temperature: 0.7
        max-tokens: 256
```

### 4. Use in Your Application

```java
@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @PostMapping("/chat")
    public String chat(@RequestBody String message) {
        return chatClient.prompt()
            .user(message)
            .call()
            .content();
    }

    @GetMapping("/chat/stream")
    public Flux<String> streamChat(@RequestParam String message) {
        return chatClient.prompt()
            .user(message)
            .stream()
            .content();
    }
}
```

## Available Models

Pre-quantized models from [huggingface.co/tjake](https://huggingface.co/tjake):

| Model | Size | RAM | Speed | Use Case |
|-------|------|-----|-------|----------|
| `tjake/TinyLlama-1.1B-Chat-v1.0-Jlama-Q4` | ~600MB | ~1.5GB | Very Fast | Simple tasks |
| `tjake/Llama-3.2-1B-Instruct-JQ4` | ~700MB | ~2GB | Fast | General use |
| `tjake/Qwen2.5-3B-Instruct-JQ4` | ~2GB | ~4GB | Medium | Better quality |
| `tjake/Llama-3.2-3B-Instruct-JQ4` | ~2GB | ~4GB | Medium | Better quality |

## Configuration Properties

| Property | Description | Default |
|----------|-------------|---------|
| `spring.ai.jlama.enabled` | Enable/disable Jlama | `true` |
| `spring.ai.jlama.model` | Model name (HuggingFace format) | `tjake/Llama-3.2-1B-Instruct-JQ4` |
| `spring.ai.jlama.working-directory` | Model storage directory | `./models` |
| `spring.ai.jlama.working-memory-type` | Working memory precision (F32, F16, BF16) | `F32` |
| `spring.ai.jlama.quantized-memory-type` | Model weights precision (I8, Q4, Q5) | `I8` |
| `spring.ai.jlama.chat.temperature` | Response creativity (0.0-2.0) | `0.7` |
| `spring.ai.jlama.chat.max-tokens` | Maximum response length | `256` |
| `spring.ai.jlama.chat.top-p` | Nucleus sampling (not used by Jlama) | `null` |
| `spring.ai.jlama.chat.top-k` | Top-k sampling (not used by Jlama) | `null` |

## Programmatic Usage

```java
JlamaChatModel chatModel = JlamaChatModel.builder()
    .modelName("tjake/Llama-3.2-1B-Instruct-JQ4")
    .workingDirectory("./models")
    .workingMemoryType("F32")   // or DType.F32
    .quantizedMemoryType("I8")  // or DType.I8
    .defaultOptions(JlamaChatOptions.builder()
        .temperature(0.7f)
        .maxTokens(256)
        .build())
    .build();

// Simple call
String response = chatModel.call("What is the capital of France?");

// With prompt
ChatResponse response = chatModel.call(new Prompt(
    List.of(
        new SystemMessage("You are a helpful assistant."),
        new UserMessage("What is 2 + 2?")
    )
));

// Streaming
chatModel.stream(new Prompt("Count from 1 to 10"))
    .map(r -> r.getResult().getOutput().getText())
    .subscribe(System.out::print);
```

## Notes

- **First run**: Model download may take several minutes depending on model size and internet speed
- **Memory**: Models are loaded entirely into RAM
- **CPU only**: Jlama uses SIMD (Vector API) acceleration but no GPU support
- **Performance**: Expect 15-40 tokens/second on modern CPUs

## License

GNU Affero General Public License v3.0 (AGPL-3.0)
