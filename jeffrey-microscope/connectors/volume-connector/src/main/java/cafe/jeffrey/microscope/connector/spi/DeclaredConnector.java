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

package cafe.jeffrey.microscope.connector.spi;

import cafe.jeffrey.microscope.connector.volume.VolumeSource;
import cafe.jeffrey.microscope.model.connector.ConnectorAddress;

/**
 * A connector as its configuration declares it: where the volume lies, and how to open it. A kind
 * that signs in with a secret keeps it here, behind {@link #open()} — never in the address, which is
 * logged and shown.
 */
public interface DeclaredConnector {

    /** Where the volume lies; never carries a credential. */
    ConnectorAddress address();

    /** What the connector is called when its configuration does not name it: the share, the directory. */
    String defaultName();

    /** Opens the volume. Called once; the clients over it are kept for as long as Microscope runs. */
    VolumeSource open();
}
