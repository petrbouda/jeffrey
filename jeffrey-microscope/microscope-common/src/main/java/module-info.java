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

/**
 * The utilities every Microscope module shares and nothing else does. The volume scheduler and the
 * provisioner keep their own; what all three must agree on to read the same volume is
 * {@code cafe.jeffrey.shared.volume}.
 */
module cafe.jeffrey.microscope.common {
    requires cafe.jeffrey.shared.volume;

    exports cafe.jeffrey.microscope.common;
    exports cafe.jeffrey.microscope.common.exception;
    exports cafe.jeffrey.microscope.common.filesystem;
    exports cafe.jeffrey.microscope.common.measure;
    exports cafe.jeffrey.microscope.common.model;
    exports cafe.jeffrey.microscope.common.model.connector;
    exports cafe.jeffrey.microscope.common.model.repository;
    exports cafe.jeffrey.microscope.common.model.repository.matcher;
    exports cafe.jeffrey.microscope.common.model.workspace;
    // Microscope's store of imported recordings: the catalogue of file kinds (ManagedFile) and categories
    exports cafe.jeffrey.microscope.common.model.recording;
}
