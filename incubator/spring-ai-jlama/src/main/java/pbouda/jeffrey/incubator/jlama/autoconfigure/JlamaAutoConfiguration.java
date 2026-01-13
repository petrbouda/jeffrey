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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import pbouda.jeffrey.incubator.jlama.JlamaChatModel;

/**
 * Spring Boot auto-configuration for Jlama.
 * <p>
 * This configuration is enabled by default when Jlama classes are on the classpath.
 * It can be disabled by setting {@code spring.ai.jlama.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnClass(JlamaChatModel.class)
@ConditionalOnProperty(prefix = "spring.ai.jlama", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JlamaProperties.class)
public class JlamaAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(JlamaAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public JlamaChatModel jlamaChatModel(JlamaProperties properties) {
        logger.info("Creating JlamaChatModel model={} workingMemoryType={} quantizedMemoryType={}",
                properties.getModel(), properties.getWorkingMemoryType(), properties.getQuantizedMemoryType());

        return JlamaChatModel.builder()
                .modelName(properties.getModel())
                .workingDirectory(properties.getWorkingDirectory())
                .workingMemoryType(properties.getWorkingMemoryType())
                .quantizedMemoryType(properties.getQuantizedMemoryType())
                .defaultOptions(properties.getChat().toJlamaChatOptions())
                .build();
    }
}
