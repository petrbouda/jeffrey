# Training and Using Jlama for OQL Query Generation

This guide covers how to create an OQL (Object Query Language) assistant using Jlama - a pure Java LLM inference engine. We'll explore two approaches: using system prompts (recommended) and actual fine-tuning.

## Table of Contents

1. [Understanding Jlama](#understanding-jlama)
2. [Approach A: System Prompt Method (Recommended)](#approach-a-system-prompt-method-recommended)
3. [Approach B: Fine-Tuning with External Tools](#approach-b-fine-tuning-with-external-tools)
4. [Java Application Integration](#java-application-integration)
5. [Complete Working Example](#complete-working-example)
6. [Troubleshooting](#troubleshooting)

---

## Understanding Jlama

**Important**: Jlama is an **inference engine**, not a training framework. It runs pre-trained models but cannot train them directly. For fine-tuning, you need external tools (Python-based), then import the resulting model into Jlama.

For domain-specific tasks like OQL generation, the **system prompt approach** is often sufficient and much simpler.

### Requirements

- Java 21+ (for Vector API)
- JVM arguments: `--add-modules jdk.incubator.vector --enable-preview`
- ~2-4GB RAM for small models (1-3B parameters)

---

## Approach A: System Prompt Method (Recommended)

This approach embeds OQL knowledge directly into the system prompt. No training required, works immediately.

### Step 1: Prepare the System Prompt

Create a comprehensive system prompt with OQL syntax reference:

```java
public class OqlSystemPrompt {
    
    public static final String NETBEANS_OQL_SYSTEM_PROMPT = """
        You are an OQL (Object Query Language) assistant for NetBeans/VisualVM heap analysis.
        Generate OQL queries based on user requests. Return ONLY the OQL query, no explanations.
        
        ## OQL Syntax Reference (NetBeans/VisualVM - JavaScript-based)
        
        ### Heap Object Functions
        - heap.findClass("className") - Find a class by name
        - heap.findObject("objectId") - Find object by ID
        - heap.objects("className") - Iterate all instances of a class
        - heap.objects("className", true) - Include subclass instances
        - heap.classes() - Iterate all classes
        - heap.roots() - Get GC roots
        - heap.finalizables() - Objects pending finalization
        - heap.livepaths(obj) - Paths from GC roots to object
        
        ### Object Functions
        - sizeof(obj) - Shallow size in bytes
        - rsizeof(obj) - Retained size in bytes
        - classof(obj) - Get object's class
        - objectid(obj) - Get object's unique ID
        - referrers(obj) - Objects referencing this object
        - referees(obj) - Objects this object references
        - reachables(obj) - All objects reachable from this object
        - refers(obj, target) - Check if obj refers to target
        - root(obj) - Get GC root for object
        - toHtml(obj) - HTML representation
        
        ### Utility Functions
        - filter(collection, "expr") - Filter elements
        - map(collection, "expr") - Transform elements
        - sort(collection, "expr") - Sort elements
        - top(collection, "expr", n) - Top N elements
        - count(collection) - Count elements
        - sum(collection, "expr") - Sum values
        - max(collection, "expr") - Maximum value
        - min(collection, "expr") - Minimum value
        - unique(collection) - Remove duplicates
        - toArray(collection) - Convert to array
        - concat(arr1, arr2) - Concatenate arrays
        
        ### Common Patterns
        
        Find all instances of a class:
        select obj from java.util.HashMap obj
        
        Filter by field value:
        select s from java.lang.String s where s.count > 100
        
        Find large objects:
        select obj from java.util.ArrayList obj where sizeof(obj) > 1024
        
        Find by retained size:
        select obj from java.util.HashMap obj where rsizeof(obj) > 1000000
        
        Count instances:
        select count(heap.objects("java.lang.String"))
        
        Find objects referencing target:
        select referrers(obj) from java.util.HashMap obj
        
        Top N by size:
        select top(heap.objects("java.lang.String"), "rsizeof(it)", 10)
        
        Filter with regex:
        select s from java.lang.String s where /.*Exception.*/.test(s.toString())
        
        Sum field values:
        select sum(map(heap.objects("java.util.ArrayList"), "it.size"))
        
        Group and aggregate:
        select { class: classof(obj).name, count: count(remove) } 
        from java.lang.Object obj
        """;
}
```

### Step 2: Maven Dependencies

```xml
<dependencies>
    <!-- Jlama Core -->
    <dependency>
        <groupId>com.github.tjake</groupId>
        <artifactId>jlama-core</artifactId>
        <version>0.8.2</version>
    </dependency>
    
    <!-- Native libraries for all platforms -->
    <dependency>
        <groupId>com.github.tjake</groupId>
        <artifactId>jlama-native</artifactId>
        <version>0.8.2</version>
        <classifier>linux-x86_64</classifier>
    </dependency>
    <dependency>
        <groupId>com.github.tjake</groupId>
        <artifactId>jlama-native</artifactId>
        <version>0.8.2</version>
        <classifier>linux-aarch_64</classifier>
    </dependency>
    <dependency>
        <groupId>com.github.tjake</groupId>
        <artifactId>jlama-native</artifactId>
        <version>0.8.2</version>
        <classifier>osx-x86_64</classifier>
    </dependency>
    <dependency>
        <groupId>com.github.tjake</groupId>
        <artifactId>jlama-native</artifactId>
        <version>0.8.2</version>
        <classifier>osx-aarch_64</classifier>
    </dependency>
    <dependency>
        <groupId>com.github.tjake</groupId>
        <artifactId>jlama-native</artifactId>
        <version>0.8.2</version>
        <classifier>windows-x86_64</classifier>
    </dependency>
    
    <!-- LangChain4j Integration (optional, but recommended) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-jlama</artifactId>
        <version>0.36.2</version>
    </dependency>
</dependencies>
```

### Step 3: JVM Configuration

Add to your run configuration or `MAVEN_OPTS`:

```bash
# For Maven
export MAVEN_OPTS="--add-modules jdk.incubator.vector --enable-preview"

# For direct Java execution
java --add-modules jdk.incubator.vector --enable-preview -jar your-app.jar
```

For IntelliJ IDEA, add to VM options in Run Configuration:
```
--add-modules jdk.incubator.vector --enable-preview
```

---

## Approach B: Fine-Tuning with External Tools

If you need better quality or have a large training dataset, you can fine-tune a model externally and import it into Jlama.

### Step 1: Prepare Training Data

The training dataset should be in JSONL format:

```json
{"instruction": "Find all HashMap instances", "input": "", "output": "select obj from java.util.HashMap obj"}
{"instruction": "Find strings larger than 1KB", "input": "", "output": "select s from java.lang.String s where sizeof(s) > 1024"}
{"instruction": "Count ArrayList instances", "input": "", "output": "select count(heap.objects(\"java.util.ArrayList\"))"}
```

Or in chat format:

```json
{"messages": [{"role": "system", "content": "You are an OQL assistant..."}, {"role": "user", "content": "Find all HashMap instances"}, {"role": "assistant", "content": "select obj from java.util.HashMap obj"}]}
```

### Step 2: Fine-Tune with Unsloth (Python)

Unsloth is the fastest and most memory-efficient option for fine-tuning.

```bash
# Install unsloth
pip install unsloth
```

Create `finetune.py`:

```python
from unsloth import FastLanguageModel
from datasets import load_dataset
from trl import SFTTrainer
from transformers import TrainingArguments
import torch

# Load base model (macOS compatible settings)
model, tokenizer = FastLanguageModel.from_pretrained(
    model_name="unsloth/Llama-3.2-1B-Instruct",
    max_seq_length=2048,
    dtype=None,
    load_in_4bit=False,  # Disabled for macOS
)

# Add LoRA adapters
model = FastLanguageModel.get_peft_model(
    model,
    r=16,
    target_modules=["q_proj", "k_proj", "v_proj", "o_proj",
                    "gate_proj", "up_proj", "down_proj"],
    lora_alpha=16,
    lora_dropout=0,
    bias="none",
    use_gradient_checkpointing="unsloth",
)

# Load your OQL dataset
dataset = load_dataset("json", data_files="netbeans-oql-training-dataset.jsonl")

# Format prompt
def format_prompt(example):
    return f"""### Instruction:
{example['instruction']}

### Response:
{example['output']}"""

# Training (macOS compatible)
trainer = SFTTrainer(
    model=model,
    tokenizer=tokenizer,
    train_dataset=dataset["train"],
    formatting_func=format_prompt,
    max_seq_length=2048,
    args=TrainingArguments(
        per_device_train_batch_size=1,  # Reduced for macOS
        gradient_accumulation_steps=8,  # Increased to compensate
        warmup_steps=5,
        max_steps=100,
        learning_rate=2e-4,
        fp16=False,  # Disabled for macOS
        logging_steps=10,
        output_dir="outputs",
        use_cpu=torch.backends.mps.is_available() == False,  # Use MPS if available
    ),
)

trainer.train()

# Save
model.save_pretrained_merged("oql-model", tokenizer, save_method="merged_16bit")
```

### Step 3: Convert to GGUF Format

```bash
# Clone llama.cpp
git clone https://github.com/ggerganov/llama.cpp
cd llama.cpp

# Install requirements
pip install -r requirements.txt

# Convert to GGUF
python convert_hf_to_gguf.py ../oql-model --outfile oql-model.gguf --outtype q4_k_m
```

### Step 4: Use Fine-Tuned Model with Jlama

Place the GGUF file in your models directory and load it:

```java
import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.safetensors.DType;

import java.io.File;

public class FineTunedOqlModel {
    
    public static void main(String[] args) throws Exception {
        // Load your fine-tuned GGUF model
        File modelPath = new File("./models/oql-model.gguf");
        
        AbstractModel model = ModelSupport.loadModel(
            modelPath,
            DType.F32,   // Working precision
            DType.Q4_0   // Quantization type
        );
        
        // Generate query
        String prompt = "Find all strings larger than 1MB";
        
        var promptContext = model.promptSupport()
            .orElseThrow()
            .builder()
            .addUserMessage(prompt)
            .build();
        
        StringBuilder response = new StringBuilder();
        model.generate(
            java.util.UUID.randomUUID(),
            promptContext,
            0.1f,    // temperature (low for deterministic output)
            256,     // max tokens
            (token, timing) -> response.append(token)
        );
        
        System.out.println("Generated OQL: " + response);
    }
}
```

---

## Java Application Integration

### Option 1: Using Jlama Directly

```java
import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.net.Downloader;

import java.io.File;
import java.util.UUID;

public class JlamaOqlService {
    
    private final AbstractModel model;
    private final String systemPrompt;
    
    public JlamaOqlService(String modelName) throws Exception {
        // Download model from HuggingFace if not present
        File modelPath = new Downloader("./models", modelName)
            .huggingFaceModel();
        
        // Load model
        this.model = ModelSupport.loadModel(
            modelPath,
            DType.F32,
            DType.I8
        );
        
        this.systemPrompt = OqlSystemPrompt.NETBEANS_OQL_SYSTEM_PROMPT;
    }
    
    public String generateOqlQuery(String userRequest) {
        var promptContext = model.promptSupport()
            .orElseThrow(() -> new IllegalStateException("Model doesn't support prompts"))
            .builder()
            .addSystemMessage(systemPrompt)
            .addUserMessage(userRequest)
            .build();
        
        StringBuilder response = new StringBuilder();
        
        model.generate(
            UUID.randomUUID(),
            promptContext,
            0.3f,    // temperature
            256,     // max tokens
            (token, timing) -> response.append(token)
        );
        
        return response.toString().trim();
    }
    
    public static void main(String[] args) throws Exception {
        // Use a small, fast model
        JlamaOqlService service = new JlamaOqlService("tjake/Llama-3.2-1B-Instruct-JQ4");
        
        // Test queries
        String[] testRequests = {
            "Find all HashMap instances",
            "Find strings larger than 1MB",
            "Count all ArrayList objects",
            "Find objects that reference a specific HashMap",
            "Get top 10 largest objects by retained size"
        };
        
        for (String request : testRequests) {
            System.out.println("Request: " + request);
            System.out.println("OQL: " + service.generateOqlQuery(request));
            System.out.println("---");
        }
    }
}
```

### Option 2: Using LangChain4j (Recommended)

LangChain4j provides a cleaner API and better abstraction:

```java
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.jlama.JlamaChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.nio.file.Path;
import java.util.List;

public class LangChain4jOqlService {
    
    private final ChatLanguageModel model;
    private final String systemPrompt;
    
    public LangChain4jOqlService() {
        this.model = JlamaChatModel.builder()
            .modelName("tjake/Llama-3.2-1B-Instruct-JQ4")
            .modelCachePath(Path.of("./models"))
            .temperature(0.3f)
            .maxTokens(256)
            .build();
        
        this.systemPrompt = OqlSystemPrompt.NETBEANS_OQL_SYSTEM_PROMPT;
    }
    
    public String generateOqlQuery(String userRequest) {
        var response = model.generate(List.of(
            SystemMessage.from(systemPrompt),
            UserMessage.from(userRequest)
        ));
        
        return response.content().text().trim();
    }
    
    public static void main(String[] args) {
        LangChain4jOqlService service = new LangChain4jOqlService();
        
        String query = service.generateOqlQuery("Find all strings larger than 1MB");
        System.out.println("Generated OQL: " + query);
    }
}
```

---

## Complete Working Example

Here's a complete, self-contained example you can copy and run:

### pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.example</groupId>
    <artifactId>jlama-oql-demo</artifactId>
    <version>1.0-SNAPSHOT</version>
    
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <jlama.version>0.8.2</jlama.version>
        <langchain4j.version>0.36.2</langchain4j.version>
    </properties>
    
    <dependencies>
        <!-- Jlama Core -->
        <dependency>
            <groupId>com.github.tjake</groupId>
            <artifactId>jlama-core</artifactId>
            <version>${jlama.version}</version>
        </dependency>
        
        <!-- Native libraries - all platforms -->
        <dependency>
            <groupId>com.github.tjake</groupId>
            <artifactId>jlama-native</artifactId>
            <version>${jlama.version}</version>
            <classifier>linux-x86_64</classifier>
        </dependency>
        <dependency>
            <groupId>com.github.tjake</groupId>
            <artifactId>jlama-native</artifactId>
            <version>${jlama.version}</version>
            <classifier>linux-aarch_64</classifier>
        </dependency>
        <dependency>
            <groupId>com.github.tjake</groupId>
            <artifactId>jlama-native</artifactId>
            <version>${jlama.version}</version>
            <classifier>osx-x86_64</classifier>
        </dependency>
        <dependency>
            <groupId>com.github.tjake</groupId>
            <artifactId>jlama-native</artifactId>
            <version>${jlama.version}</version>
            <classifier>osx-aarch_64</classifier>
        </dependency>
        <dependency>
            <groupId>com.github.tjake</groupId>
            <artifactId>jlama-native</artifactId>
            <version>${jlama.version}</version>
            <classifier>windows-x86_64</classifier>
        </dependency>
        
        <!-- LangChain4j Jlama Integration -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-jlama</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                    <compilerArgs>
                        <arg>--enable-preview</arg>
                        <arg>--add-modules</arg>
                        <arg>jdk.incubator.vector</arg>
                    </compilerArgs>
                </configuration>
            </plugin>
            
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
                <configuration>
                    <mainClass>com.example.OqlAssistantDemo</mainClass>
                    <arguments>
                        <argument>--add-modules</argument>
                        <argument>jdk.incubator.vector</argument>
                        <argument>--enable-preview</argument>
                    </arguments>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### OqlAssistantDemo.java

```java
package com.example;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.jlama.JlamaChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;

import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class OqlAssistantDemo {
    
    private static final String SYSTEM_PROMPT = """
        You are an OQL (Object Query Language) assistant for NetBeans/VisualVM heap analysis.
        Generate OQL queries based on user requests. Return ONLY the OQL query, no explanations.
        
        Key syntax:
        - heap.objects("className") - Get all instances
        - sizeof(obj) - Shallow size
        - rsizeof(obj) - Retained size  
        - referrers(obj) - Objects pointing to obj
        - referees(obj) - Objects obj points to
        - filter(collection, "expr") - Filter results
        - map(collection, "expr") - Transform results
        - top(collection, "expr", n) - Top N by expression
        - count(collection) - Count elements
        
        Examples:
        - "Find all HashMaps" → select obj from java.util.HashMap obj
        - "Strings > 1KB" → select s from java.lang.String s where sizeof(s) > 1024
        - "Count ArrayLists" → select count(heap.objects("java.util.ArrayList"))
        - "Top 10 by size" → select top(heap.objects("java.lang.Object"), "rsizeof(it)", 10)
        """;
    
    public static void main(String[] args) {
        System.out.println("Loading model... (this may take a minute on first run)");
        
        // Initialize model
        ChatLanguageModel model = JlamaChatModel.builder()
            .modelName("tjake/Llama-3.2-1B-Instruct-JQ4")  // ~700MB, fast
            .modelCachePath(Path.of("./models"))
            .temperature(0.2f)  // Low for consistent output
            .maxTokens(200)
            .build();
        
        System.out.println("Model loaded! Type your query request (or 'quit' to exit):\n");
        
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("You: ");
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                break;
            }
            
            if (input.isEmpty()) {
                continue;
            }
            
            // Generate OQL query
            Response<AiMessage> response = model.generate(List.of(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(input)
            ));
            
            String oqlQuery = response.content().text().trim();
            System.out.println("OQL: " + oqlQuery);
            System.out.println();
        }
        
        System.out.println("Goodbye!");
        scanner.close();
    }
}
```

### Running the Example

```bash
# Build
mvn clean compile

# Run (make sure to use Java 21+)
mvn exec:java -Dexec.mainClass="com.example.OqlAssistantDemo" \
    -Dexec.args="--add-modules jdk.incubator.vector --enable-preview"

# Or run directly
java --add-modules jdk.incubator.vector --enable-preview \
    -cp target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout) \
    com.example.OqlAssistantDemo
```

### Expected Output

```
Loading model... (this may take a minute on first run)
Model loaded! Type your query request (or 'quit' to exit):

You: Find all HashMap instances with more than 100 entries
OQL: select obj from java.util.HashMap obj where obj.size > 100

You: Get the top 10 largest strings by retained size
OQL: select top(heap.objects("java.lang.String"), "rsizeof(it)", 10)

You: Count all Thread objects
OQL: select count(heap.objects("java.lang.Thread"))

You: quit
Goodbye!
```

---

## Troubleshooting

### Common Issues

**1. `UnsupportedClassVersionError`**
```
Ensure you're using Java 21+:
java --version
```

**2. `Module jdk.incubator.vector not found`**
```bash
# Add JVM arguments
--add-modules jdk.incubator.vector --enable-preview
```

**3. `OutOfMemoryError`**
```bash
# Increase heap size
java -Xmx4g --add-modules jdk.incubator.vector --enable-preview -jar app.jar
```

**4. Model download fails**
```bash
# Download manually from HuggingFace
# Place in ./models/tjake/Llama-3.2-1B-Instruct-JQ4/
```

**5. Slow inference**
- Use a smaller model (1B parameters)
- Use quantized models (Q4 or Q8)
- Ensure Vector API is enabled (check for "Using Vector API" in logs)

### Model Recommendations

| Model | Size | RAM | Speed | Quality |
|-------|------|-----|-------|---------|
| `tjake/TinyLlama-1.1B-Chat-v1.0-Jlama-Q4` | ~600MB | ~1.5GB | Very Fast | Basic |
| `tjake/Llama-3.2-1B-Instruct-JQ4` | ~700MB | ~2GB | Fast | Good |
| `tjake/Qwen2.5-3B-Instruct-JQ4` | ~2GB | ~4GB | Medium | Better |
| `tjake/Llama-3.2-3B-Instruct-JQ4` | ~2GB | ~4GB | Medium | Better |

For OQL generation, the 1B model is usually sufficient since:
- OQL syntax is well-defined and limited
- Output is short (single query)
- Speed matters for interactive use

---

## Next Steps

1. **Enhance the system prompt** with your specific use cases
2. **Add context** from the heap dump (available classes, top memory consumers)
3. **Implement query validation** before execution
4. **Add conversation history** for follow-up queries
5. **Consider caching** generated queries for common requests

---

## Resources

- [Jlama GitHub](https://github.com/tjake/Jlama)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [HuggingFace Jlama Models](https://huggingface.co/tjake)
- [Unsloth Fine-tuning](https://github.com/unslothai/unsloth)
- [NetBeans OQL Documentation](https://visualvm.github.io/documentation.html)
