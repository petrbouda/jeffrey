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
 * Which storage the one configured connector reads: one constant per configuration block under
 * {@code jeffrey.microscope.connector.*}. On the wire (REST and MCP) it is the constant's name.
 */
public enum ConnectorKind {

    /** An Azure file share, read over the Azure Files REST API. */
    AZURE_FILES("azure-files"),

    /** A directory on the machine Microscope runs on. */
    LOCAL("local");

    private final String block;

    ConnectorKind(String block) {
        this.block = block;
    }

    /** The configuration block that declares it, e.g. {@code azure-files}; also its identity prefix. */
    public String block() {
        return block;
    }
}
