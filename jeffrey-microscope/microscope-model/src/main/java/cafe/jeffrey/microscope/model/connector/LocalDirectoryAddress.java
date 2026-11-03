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

import java.nio.file.Path;

/**
 * The recording volume in a directory on this machine's filesystem — a scratch tree for testing, a
 * tree the provisioner and the hub keep on the same machine, or a copy of part of another volume.
 * The workspaces lie directly under {@code root}, read live on every call.
 *
 * @param root     the directory holding the workspaces; absolute, stored normalized
 * @param readOnly whether Microscope refuses every change to the volume
 */
public record LocalDirectoryAddress(Path root, boolean readOnly) implements ConnectorAddress {

    private static final String IDENTITY_PREFIX = ConnectorKind.LOCAL.block() + ":";
    private static final String READ_ONLY_SUFFIX = " (read-only)";

    public LocalDirectoryAddress {
        if (root == null) {
            throw new IllegalArgumentException("A local connector needs the directory holding the workspaces");
        }
        if (!root.isAbsolute()) {
            throw new IllegalArgumentException("A local connector's directory must be an absolute path: path=" + root);
        }
        root = root.normalize();
    }

    @Override
    public ConnectorKind kind() {
        return ConnectorKind.LOCAL;
    }

    @Override
    public String identity() {
        return IDENTITY_PREFIX + location();
    }

    @Override
    public String location() {
        return root.toString();
    }

    @Override
    public String toString() {
        return location() + (readOnly ? READ_ONLY_SUFFIX : "");
    }
}
