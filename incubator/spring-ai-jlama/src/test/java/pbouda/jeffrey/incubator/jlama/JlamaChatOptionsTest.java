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

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JlamaChatOptionsTest {

    @Test
    void shouldCreateOptionsWithBuilder() {
        JlamaChatOptions options = JlamaChatOptions.builder()
                .model("test-model")
                .temperature(0.5)
                .maxTokens(100)
                .topP(0.9)
                .topK(40)
                .stopSequences(List.of("STOP", "END"))
                .build();

        assertThat(options.getModel()).isEqualTo("test-model");
        assertThat(options.getTemperature()).isEqualTo(0.5);
        assertThat(options.getMaxTokens()).isEqualTo(100);
        assertThat(options.getTopP()).isEqualTo(0.9);
        assertThat(options.getTopK()).isEqualTo(40);
        assertThat(options.getStopSequences()).containsExactly("STOP", "END");
    }

    @Test
    void shouldReturnNullForUnsupportedOptions() {
        JlamaChatOptions options = JlamaChatOptions.builder().build();

        assertThat(options.getFrequencyPenalty()).isNull();
        assertThat(options.getPresencePenalty()).isNull();
    }

    @Test
    void shouldCopyOptions() {
        JlamaChatOptions original = JlamaChatOptions.builder()
                .model("original-model")
                .temperature(0.8)
                .maxTokens(200)
                .stopSequences(List.of("STOP"))
                .build();

        ChatOptions copy = original.copy();

        assertThat(copy.getModel()).isEqualTo("original-model");
        assertThat(copy.getTemperature()).isEqualTo(0.8);
        assertThat(copy.getMaxTokens()).isEqualTo(200);
        assertThat(copy.getStopSequences()).containsExactly("STOP");
        assertThat(copy).isNotSameAs(original);
    }

    @Test
    void shouldCreateEmptyOptions() {
        JlamaChatOptions options = JlamaChatOptions.builder().build();

        assertThat(options.getModel()).isNull();
        assertThat(options.getTemperature()).isNull();
        assertThat(options.getMaxTokens()).isNull();
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
        JlamaChatOptions options1 = JlamaChatOptions.builder()
                .model("model")
                .temperature(0.7)
                .build();

        JlamaChatOptions options2 = JlamaChatOptions.builder()
                .model("model")
                .temperature(0.7)
                .build();

        assertThat(options1).isEqualTo(options2);
        assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
    }

    @Test
    void shouldHaveReadableToString() {
        JlamaChatOptions options = JlamaChatOptions.builder()
                .model("test-model")
                .temperature(0.5)
                .build();

        String toString = options.toString();

        assertThat(toString).contains("test-model");
        assertThat(toString).contains("0.5");
    }
}
