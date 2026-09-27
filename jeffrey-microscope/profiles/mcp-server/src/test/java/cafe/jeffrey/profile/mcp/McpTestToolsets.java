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

package cafe.jeffrey.profile.mcp;

import cafe.jeffrey.microscope.mcp.protocol.McpToolAnnotations;
import java.util.function.Function;

/**
 * Profile-scoped toolsets for tests whose tool object holds nothing that needs releasing: each call
 * resolves a target and has nothing to close afterwards. Shipped in the test-jar, so core-microscope's
 * tool tests build them the same way.
 */
public final class McpTestToolsets {

    private McpTestToolsets() {
    }

    /** A read-only family, the default every profile-scoped family starts from. */
    public static <T> ProfileScopedToolset<T> unscoped(
            Class<T> targetType, String prefix, Function<String, T> targetResolver) {
        return unscoped(targetType, prefix, targetResolver, McpToolAnnotations.READ_ONLY);
    }

    /** A family with annotations other than the read-only default. */
    public static <T> ProfileScopedToolset<T> unscoped(
            Class<T> targetType,
            String prefix,
            Function<String, T> targetResolver,
            McpToolAnnotations defaultAnnotations) {
        ProfileScopedToolset.ScopedTargetResolver<T> resolver = profileId -> new ProfileScopedToolset.ScopedTarget<>() {
            @Override
            public T target() {
                return targetResolver.apply(profileId);
            }

            @Override
            public void close() {
            }
        };
        return ProfileScopedToolset.leased(targetType, prefix, resolver, defaultAnnotations);
    }
}
