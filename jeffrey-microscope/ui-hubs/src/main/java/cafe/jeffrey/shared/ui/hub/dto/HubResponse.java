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

package cafe.jeffrey.shared.ui.hub.dto;

/**
 * @param source {@code "CONFIG"} for a hub declared in configuration — the UI marks it read-only,
 *               because the next startup would recreate anything deleted here — or {@code "USER"}
 *               for one added through the UI. Carried as the enum name so the DTO stays a plain
 *               record over JSON-friendly types.
 */
public record HubResponse(
        String id,
        String name,
        String hostname,
        int port,
        boolean plaintext,
        long createdAt,
        String source) {
}
