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

import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;
import java.util.Objects;

/**
 * Options for Jlama chat model configuration.
 * <p>
 * <b>Supported options (used by Jlama):</b>
 * <ul>
 *   <li>{@code temperature} - Controls randomness in generation (0.0 - 2.0)</li>
 *   <li>{@code maxTokens} - Maximum number of tokens to generate</li>
 * </ul>
 * <p>
 * <b>Partially supported options (stored but not used by Jlama core):</b>
 * <ul>
 *   <li>{@code topP} - Nucleus sampling parameter (captured for API compatibility)</li>
 *   <li>{@code topK} - Top-k sampling parameter (captured for API compatibility)</li>
 *   <li>{@code stopSequences} - Stop sequences (captured for API compatibility)</li>
 * </ul>
 * <p>
 * <b>Unsupported options (always return null):</b>
 * <ul>
 *   <li>{@code frequencyPenalty} - Not supported by Jlama</li>
 *   <li>{@code presencePenalty} - Not supported by Jlama</li>
 * </ul>
 */
public class JlamaChatOptions implements ChatOptions {

    /** Model identifier (for API compatibility, not used for model selection). */
    private String model;

    /** Temperature for response generation (0.0 - 2.0). Supported by Jlama. */
    private Double temperature;

    /** Maximum number of tokens to generate. Supported by Jlama. */
    private Integer maxTokens;

    /** Top-p (nucleus) sampling. Captured but not used by Jlama core. */
    private Double topP;

    /** Top-k sampling. Captured but not used by Jlama core. */
    private Integer topK;

    /** Stop sequences. Captured but not used by Jlama core. */
    private List<String> stopSequences;

    public JlamaChatOptions() {
    }

    private JlamaChatOptions(Builder builder) {
        this.model = builder.model;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.stopSequences = builder.stopSequences;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    @Override
    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    @Override
    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    @Override
    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    @Override
    public List<String> getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
    }

    @Override
    public Double getFrequencyPenalty() {
        return null; // Not supported by Jlama
    }

    @Override
    public Double getPresencePenalty() {
        return null; // Not supported by Jlama
    }

    @Override
    public ChatOptions copy() {
        return builder()
                .model(this.model)
                .temperature(this.temperature)
                .maxTokens(this.maxTokens)
                .topP(this.topP)
                .topK(this.topK)
                .stopSequences(this.stopSequences != null ? List.copyOf(this.stopSequences) : null)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JlamaChatOptions that = (JlamaChatOptions) o;
        return Objects.equals(model, that.model) &&
                Objects.equals(temperature, that.temperature) &&
                Objects.equals(maxTokens, that.maxTokens) &&
                Objects.equals(topP, that.topP) &&
                Objects.equals(topK, that.topK) &&
                Objects.equals(stopSequences, that.stopSequences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, temperature, maxTokens, topP, topK, stopSequences);
    }

    @Override
    public String toString() {
        return "JlamaChatOptions{" +
                "model='" + model + '\'' +
                ", temperature=" + temperature +
                ", maxTokens=" + maxTokens +
                ", topP=" + topP +
                ", topK=" + topK +
                ", stopSequences=" + stopSequences +
                '}';
    }

    public static class Builder {

        private String model;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Integer topK;
        private List<String> stopSequences;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public JlamaChatOptions build() {
            return new JlamaChatOptions(this);
        }
    }
}
