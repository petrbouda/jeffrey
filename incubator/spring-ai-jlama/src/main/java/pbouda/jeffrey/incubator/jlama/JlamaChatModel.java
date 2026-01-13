/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package pbouda.jeffrey.incubator.jlama;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.safetensors.prompt.PromptSupport;
import com.github.tjake.jlama.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring AI {@link ChatModel} implementation for Jlama - a pure Java LLM inference engine.
 * <p>
 * Jlama allows running LLM inference directly in the JVM without external dependencies,
 * using Java's Vector API for SIMD acceleration.
 * <p>
 * Supported models include Llama, Mistral, Gemma, Qwen, and other models available
 * on HuggingFace in SafeTensors format.
 *
 * @see <a href="https://github.com/tjake/Jlama">Jlama GitHub</a>
 */
public class JlamaChatModel implements ChatModel, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(JlamaChatModel.class);

    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 256;
    private static final DType DEFAULT_WORKING_MEMORY_TYPE = DType.F32;
    private static final DType DEFAULT_QUANTIZED_MEMORY_TYPE = DType.I8;

    private final AbstractModel model;
    private final JlamaChatOptions defaultOptions;
    private final String modelName;

    /**
     * Create a new JlamaChatModel.
     *
     * @param modelName           the model name in HuggingFace format (owner/model-name)
     * @param workingDirectory    the directory to store downloaded models
     * @param defaultOptions      default chat options (can be null)
     * @param workingMemoryType   the data type for working memory (F32, F16, BF16)
     * @param quantizedMemoryType the data type for quantized memory (I8, Q4, Q5)
     */
    public JlamaChatModel(String modelName, String workingDirectory, JlamaChatOptions defaultOptions,
                          DType workingMemoryType, DType quantizedMemoryType) {
        Assert.hasText(modelName, "Model name must not be empty");
        Assert.hasText(workingDirectory, "Working directory must not be empty");

        this.modelName = modelName;
        this.defaultOptions = defaultOptions != null ? defaultOptions : createDefaultOptions();

        DType effectiveWorkingType = workingMemoryType != null ? workingMemoryType : DEFAULT_WORKING_MEMORY_TYPE;
        DType effectiveQuantizedType = quantizedMemoryType != null ? quantizedMemoryType : DEFAULT_QUANTIZED_MEMORY_TYPE;

        try {
            logger.info("Loading Jlama model: {} workingMemory={} quantizedMemory={} (this may take a while on first run)",
                    modelName, effectiveWorkingType, effectiveQuantizedType);

            // Download model from HuggingFace if not present
            File localModelPath = new Downloader(workingDirectory, modelName).huggingFaceModel();

            // Load the model with configurable memory types
            this.model = ModelSupport.loadModel(
                    localModelPath,
                    effectiveWorkingType,
                    effectiveQuantizedType
            );

            logger.info("Successfully loaded Jlama model: {}", modelName);
        } catch (IOException e) {
            throw new JlamaModelException("Failed to load Jlama model: " + modelName, e);
        }
    }

    /**
     * Create a new JlamaChatModel with default memory types (F32/I8).
     *
     * @param modelName        the model name in HuggingFace format (owner/model-name)
     * @param workingDirectory the directory to store downloaded models
     * @param defaultOptions   default chat options (can be null)
     */
    public JlamaChatModel(String modelName, String workingDirectory, JlamaChatOptions defaultOptions) {
        this(modelName, workingDirectory, defaultOptions, DEFAULT_WORKING_MEMORY_TYPE, DEFAULT_QUANTIZED_MEMORY_TYPE);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Assert.notNull(prompt, "Prompt must not be null");

        // Merge runtime options with defaults
        JlamaChatOptions options = mergeOptions(prompt.getOptions());

        // Convert Spring AI messages to Jlama PromptContext
        PromptContext promptContext = buildPromptContext(prompt.getInstructions());

        // Generate response with token counting
        StringBuilder responseText = new StringBuilder();
        AtomicInteger tokenCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        // Convert Double to float for Jlama API
        float temperature = options.getTemperature() != null
                ? options.getTemperature().floatValue()
                : (float) DEFAULT_TEMPERATURE;
        int maxTokens = options.getMaxTokens() != null ? options.getMaxTokens() : DEFAULT_MAX_TOKENS;

        model.generate(
                UUID.randomUUID(),
                promptContext,
                temperature,
                maxTokens,
                (token, timing) -> {
                    responseText.append(token);
                    tokenCount.incrementAndGet();
                }
        );

        long duration = System.currentTimeMillis() - startTime;
        logger.debug("Generated response in {}ms tokenCount={}", duration, tokenCount.get());

        return buildChatResponse(responseText.toString(), tokenCount.get());
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Assert.notNull(prompt, "Prompt must not be null");

        JlamaChatOptions options = mergeOptions(prompt.getOptions());
        PromptContext promptContext = buildPromptContext(prompt.getInstructions());

        // Convert Double to float for Jlama API
        float temperature = options.getTemperature() != null
                ? options.getTemperature().floatValue()
                : (float) DEFAULT_TEMPERATURE;
        int maxTokens = options.getMaxTokens() != null ? options.getMaxTokens() : DEFAULT_MAX_TOKENS;

        // Create a Sink for streaming tokens
        Sinks.Many<ChatResponse> sink = Sinks.many().unicast().onBackpressureBuffer();

        // Run generation in a virtual thread
        Thread.startVirtualThread(() -> {
            try {
                model.generate(
                        UUID.randomUUID(),
                        promptContext,
                        temperature,
                        maxTokens,
                        (token, timing) -> {
                            ChatResponse response = buildChatResponse(token);
                            sink.tryEmitNext(response);
                        }
                );
                sink.tryEmitComplete();
            } catch (Exception e) {
                logger.error("Error during streaming generation", e);
                sink.tryEmitError(e);
            }
        });

        return sink.asFlux();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return this.defaultOptions;
    }

    @Override
    public void destroy() {
        if (model != null) {
            try {
                model.close();
                logger.info("Closed Jlama model: {}", modelName);
            } catch (Exception e) {
                logger.warn("Error closing Jlama model", e);
            }
        }
    }

    private PromptContext buildPromptContext(List<Message> messages) {
        PromptSupport promptSupport = model.promptSupport()
                .orElseThrow(() -> new JlamaModelException("Model does not support chat prompts: " + modelName));

        PromptSupport.Builder builder = promptSupport.builder();

        for (Message message : messages) {
            String content = message.getText();

            switch (message.getMessageType()) {
                case SYSTEM -> builder.addSystemMessage(content);
                case USER -> builder.addUserMessage(content);
                case ASSISTANT -> builder.addAssistantMessage(content);
                default -> logger.warn("Unsupported message type: {}, skipping", message.getMessageType());
            }
        }

        return builder.build();
    }

    private ChatResponse buildChatResponse(String content) {
        return buildChatResponse(content, 0);
    }

    private ChatResponse buildChatResponse(String content, int completionTokens) {
        AssistantMessage assistantMessage = new AssistantMessage(content);

        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason("STOP")
                .build();

        Generation generation = new Generation(assistantMessage, generationMetadata);

        ChatResponseMetadata.Builder responseMetadataBuilder = ChatResponseMetadata.builder()
                .model(modelName);

        // Add usage metrics if we have completion tokens
        if (completionTokens > 0) {
            responseMetadataBuilder.usage(new DefaultUsage(null, completionTokens, null));
        }

        return new ChatResponse(List.of(generation), responseMetadataBuilder.build());
    }

    private JlamaChatOptions mergeOptions(ChatOptions runtimeOptions) {
        if (runtimeOptions == null) {
            return this.defaultOptions;
        }

        return JlamaChatOptions.builder()
                .model(selectValue(runtimeOptions.getModel(), defaultOptions.getModel()))
                .temperature(selectValue(runtimeOptions.getTemperature(), defaultOptions.getTemperature()))
                .maxTokens(selectValue(runtimeOptions.getMaxTokens(), defaultOptions.getMaxTokens()))
                .topP(selectValue(runtimeOptions.getTopP(), defaultOptions.getTopP()))
                .topK(selectValue(runtimeOptions.getTopK(), defaultOptions.getTopK()))
                .stopSequences(selectValue(runtimeOptions.getStopSequences(), defaultOptions.getStopSequences()))
                .build();
    }

    private <T> T selectValue(T runtimeValue, T defaultValue) {
        return runtimeValue != null ? runtimeValue : defaultValue;
    }

    private JlamaChatOptions createDefaultOptions() {
        return JlamaChatOptions.builder()
                .temperature(DEFAULT_TEMPERATURE)
                .maxTokens(DEFAULT_MAX_TOKENS)
                .build();
    }

    /**
     * Returns the name of the loaded model.
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * Creates a new builder for JlamaChatModel.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating JlamaChatModel instances.
     */
    public static class Builder {

        private String modelName = "tjake/Llama-3.2-1B-Instruct-JQ4";
        private String workingDirectory = "./models";
        private JlamaChatOptions defaultOptions;
        private DType workingMemoryType;
        private DType quantizedMemoryType;

        /**
         * Set the model name in HuggingFace format (owner/model-name).
         * <p>
         * Pre-quantized models are available at https://huggingface.co/tjake
         *
         * @param modelName the model name
         * @return this builder
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Set the working directory for model storage.
         * Models are downloaded to this directory on first use.
         *
         * @param workingDirectory the directory path
         * @return this builder
         */
        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        /**
         * Set default chat options.
         *
         * @param defaultOptions the default options
         * @return this builder
         */
        public Builder defaultOptions(JlamaChatOptions defaultOptions) {
            this.defaultOptions = defaultOptions;
            return this;
        }

        /**
         * Set the working memory data type.
         * <p>
         * Controls the precision of intermediate calculations during inference.
         * Higher precision (F32) is more accurate but uses more memory.
         * Lower precision (F16, BF16) uses less memory but may be slightly less accurate.
         *
         * @param workingMemoryType the data type (F32, F16, BF16)
         * @return this builder
         */
        public Builder workingMemoryType(DType workingMemoryType) {
            this.workingMemoryType = workingMemoryType;
            return this;
        }

        /**
         * Set the working memory data type from string.
         *
         * @param workingMemoryType the data type name (F32, F16, BF16)
         * @return this builder
         */
        public Builder workingMemoryType(String workingMemoryType) {
            if (workingMemoryType != null && !workingMemoryType.isBlank()) {
                this.workingMemoryType = DType.valueOf(workingMemoryType.toUpperCase());
            }
            return this;
        }

        /**
         * Set the quantized memory data type.
         * <p>
         * Controls the precision of model weights stored in memory.
         * Lower precision (Q4, Q5) uses less memory, higher precision (I8) is more accurate.
         *
         * @param quantizedMemoryType the data type (I8, Q4, Q5)
         * @return this builder
         */
        public Builder quantizedMemoryType(DType quantizedMemoryType) {
            this.quantizedMemoryType = quantizedMemoryType;
            return this;
        }

        /**
         * Set the quantized memory data type from string.
         *
         * @param quantizedMemoryType the data type name (I8, Q4, Q5)
         * @return this builder
         */
        public Builder quantizedMemoryType(String quantizedMemoryType) {
            if (quantizedMemoryType != null && !quantizedMemoryType.isBlank()) {
                this.quantizedMemoryType = DType.valueOf(quantizedMemoryType.toUpperCase());
            }
            return this;
        }

        /**
         * Build the JlamaChatModel.
         *
         * @return a new JlamaChatModel instance
         */
        public JlamaChatModel build() {
            return new JlamaChatModel(modelName, workingDirectory, defaultOptions,
                    workingMemoryType, quantizedMemoryType);
        }
    }
}
