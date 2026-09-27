/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cafe.jeffrey.microscope.mcp.protocol;

import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestFeatures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What one endpoint offers. All three providers are required and resolved per request, so an endpoint
 * that cannot serve one of them is refused at construction rather than at the first call.
 */
class McpServerFeaturesTest {

    private static final McpToolProvider TOOLSET = StubToolset.of(StubToolset.answering("sample_ping", "pong"));

    @Test
    void carriesEveryCapabilityAnEndpointOffers() {
        McpServerFeatures features = McpTestFeatures.of(
                () -> TOOLSET,
                Prompts::new,
                Resources::new);

        assertEquals(1, features.tools().get().specs().size());
        assertNotNull(features.prompts().get());
        assertNotNull(features.resources().get());
    }

    /**
     * A features record missing any of the three describes an endpoint that cannot answer a method the
     * envelope will advertise, so it refuses to exist rather than failing at the first call.
     */
    @Test
    void refusesToDescribeAnEndpointMissingACapability() {
        assertThrows(IllegalArgumentException.class,
                () -> McpTestFeatures.of(null, Prompts::new, Resources::new));
        assertThrows(IllegalArgumentException.class,
                () -> McpTestFeatures.of(() -> TOOLSET, null, Resources::new));
        assertThrows(IllegalArgumentException.class,
                () -> McpTestFeatures.of(() -> TOOLSET, Prompts::new, null));
    }

    /**
     * The suppliers are resolved per request, not here: building the toolset can fail for a profile
     * that has gone, and that must not stop the endpoint answering server/discover.
     */
    @Test
    void doesNotResolveItsProvidersOnConstruction() {
        McpServerFeatures features = McpTestFeatures.of(
                () -> {
                    throw new IllegalStateException("resolved too early");
                },
                Prompts::new,
                Resources::new);

        assertThrows(IllegalStateException.class, () -> features.tools().get());
    }

    /** An endpoint that declares neither extension serves neither. */
    @Test
    void servesNoSkillsAndNoTasksUnlessGivenThem() {
        McpServerFeatures features = McpTestFeatures.of(() -> TOOLSET, Prompts::new, Resources::new);

        assertSame(McpSkillProvider.NONE, features.skills().get());
        assertSame(McpTaskProvider.NONE, features.tasks().get());
    }

    @Test
    void theWithersReplaceOnlyTheirOwnProvider() {
        McpSkillProvider skills = List::of;
        McpTaskProvider tasks = new McpTaskProvider() {
            @Override
            public McpTask get(String taskId) {
                throw new AssertionError("not asked");
            }

            @Override
            public void cancel(String taskId) {
                throw new AssertionError("not asked");
            }
        };
        McpServerFeatures base = McpTestFeatures.of(() -> TOOLSET, Prompts::new, Resources::new);

        McpServerFeatures extended = base.withSkills(() -> skills).withTasks(() -> tasks);

        assertSame(skills, extended.skills().get());
        assertSame(tasks, extended.tasks().get());
        assertSame(base.tools(), extended.tools());
        assertSame(base.prompts(), extended.prompts());
        assertSame(base.resources(), extended.resources());
        assertSame(base.instructions(), extended.instructions());
        assertSame(base.completions(), extended.completions());
        assertSame(base.resourceLinks(), extended.resourceLinks());
        assertSame(McpSkillProvider.NONE, base.skills().get());
    }

    @Test
    void refusesAMissingSkillOrTaskSupplier() {
        McpServerFeatures base = McpTestFeatures.of(() -> TOOLSET, Prompts::new, Resources::new);

        assertThrows(IllegalArgumentException.class, () -> base.withSkills(null));
        assertThrows(IllegalArgumentException.class, () -> base.withTasks(null));
    }

    /** Following no tasks, the default provider knows no id: {@code -32602}, as for an expired one. */
    @Test
    void theDefaultTaskProviderFollowsNoTask() {
        McpProtocolException get = assertThrows(McpProtocolException.class, () -> McpTaskProvider.NONE.get("task-1"));
        McpProtocolException cancel = assertThrows(McpProtocolException.class,
                () -> McpTaskProvider.NONE.cancel("task-1"));

        assertEquals(McpErrorCode.INVALID_PARAMS, get.code());
        assertEquals(McpErrorCode.INVALID_PARAMS, cancel.code());
    }

    private static final class Prompts implements McpPromptProvider {

        @Override
        public List<McpPrompt> prompts() {
            return List.of();
        }

        @Override
        public McpPrompt prompt(String name) {
            throw new IllegalArgumentException("Unknown prompt: " + name);
        }
    }

    private static final class Resources implements McpResourceProvider {

        @Override
        public List<McpResource> resources() {
            return List.of();
        }

        @Override
        public List<McpResource> templates() {
            return List.of();
        }

        @Override
        public Contents read(String uri) {
            throw new IllegalArgumentException("Unknown resource: " + uri);
        }
    }
}
