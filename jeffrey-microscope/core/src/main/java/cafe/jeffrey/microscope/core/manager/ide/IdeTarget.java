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

package cafe.jeffrey.microscope.core.manager.ide;

/**
 * The cached, stable IDE-window choice for a profile: which discovered instance ({@code port}) and
 * which project ({@code projectId} = the IDE's {@code locationHash}) to navigate to. The {@code port}
 * and {@code pid} are the volatile parts — re-resolved by discovery when the IDE restarts — but the
 * project choice is what the user picked. The display fields ({@code ideName}, {@code projectName})
 * are captured at selection time so the UI can show the linked window without re-scanning.
 *
 * <p>{@code basePath} is the checkout on disk. It is here rather than looked up on demand because it
 * is what makes the link mean something outside the IDE conversation: it is the directory an AI
 * analysis is allowed to read, and it is captured at selection time so that permission is anchored
 * to the window the user actually chose.
 */
public record IdeTarget(
        int port, String projectId, String ideName, String projectName, String basePath, long pid) {
}
