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
package pbouda.jeffrey.incubator.jlama.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import pbouda.jeffrey.incubator.jlama.JlamaChatOptions;

/**
 * Configuration properties for Jlama integration.
 */
@ConfigurationProperties(prefix = "spring.ai.jlama")
public class JlamaProperties {

    /**
     * Enable/disable Jlama integration.
     */
    private boolean enabled = true;

    /**
     * Model name in HuggingFace format (owner/model-name).
     * Pre-quantized models are available at https://huggingface.co/tjake
     */
    private String model = "tjake/Llama-3.2-1B-Instruct-JQ4";

    /**
     * Working directory for model storage.
     * Models are downloaded to this directory on first use.
     */
    private String workingDirectory = "./models";

    /**
     * Working memory data type for model inference.
     * Controls the precision of intermediate calculations.
     * Options: F32 (default, highest precision), F16, BF16 (lower precision, less memory).
     */
    private String workingMemoryType = "F32";

    /**
     * Quantized memory data type for model weights.
     * Controls how model weights are stored in memory.
     * Options: I8 (default), Q4, Q5 (lower precision uses less memory).
     */
    private String quantizedMemoryType = "I8";

    /**
     * Chat model options.
     */
    @NestedConfigurationProperty
    private ChatOptions chat = new ChatOptions();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getWorkingMemoryType() {
        return workingMemoryType;
    }

    public void setWorkingMemoryType(String workingMemoryType) {
        this.workingMemoryType = workingMemoryType;
    }

    public String getQuantizedMemoryType() {
        return quantizedMemoryType;
    }

    public void setQuantizedMemoryType(String quantizedMemoryType) {
        this.quantizedMemoryType = quantizedMemoryType;
    }

    public ChatOptions getChat() {
        return chat;
    }

    public void setChat(ChatOptions chat) {
        this.chat = chat;
    }

    /**
     * Chat-specific options.
     */
    public static class ChatOptions {

        /**
         * Temperature for response generation (0.0 - 2.0).
         * Lower values are more deterministic, higher values more creative.
         */
        private Double temperature = 0.7;

        /**
         * Maximum number of tokens to generate.
         */
        private Integer maxTokens = 256;

        /**
         * Top-p (nucleus) sampling parameter.
         */
        private Double topP;

        /**
         * Top-k sampling parameter.
         */
        private Integer topK;

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Double getTopP() {
            return topP;
        }

        public void setTopP(Double topP) {
            this.topP = topP;
        }

        public Integer getTopK() {
            return topK;
        }

        public void setTopK(Integer topK) {
            this.topK = topK;
        }

        /**
         * Convert to JlamaChatOptions.
         */
        public JlamaChatOptions toJlamaChatOptions() {
            return JlamaChatOptions.builder()
                    .temperature(this.temperature)
                    .maxTokens(this.maxTokens)
                    .topP(this.topP)
                    .topK(this.topK)
                    .build();
        }
    }
}
