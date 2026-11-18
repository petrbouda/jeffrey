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

package cafe.jeffrey.microscope.model.connector;

/**
 * Which storage the one configured connector reads, as the provider of that kind names it.
 *
 * @param name  what REST and MCP answers carry, e.g. {@code AZURE_FILES}
 * @param block the configuration block under {@code jeffrey.microscope.connector.*} that declares
 *              it, e.g. {@code azure-files}; also the prefix of the address's identity
 */
public record ConnectorKind(String name, String block) {

    public ConnectorKind {
        if (name == null || name.isBlank() || block == null || block.isBlank()) {
            throw new IllegalArgumentException("A connector kind needs a name and its configuration block");
        }
    }

    /** The prefix of an address's identity: the block and a colon, e.g. {@code azure-files:}. */
    public String identityPrefix() {
        return block + ":";
    }
}
