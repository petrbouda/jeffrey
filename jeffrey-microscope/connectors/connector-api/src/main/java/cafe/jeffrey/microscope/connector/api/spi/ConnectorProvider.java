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

package cafe.jeffrey.microscope.connector.api.spi;

import cafe.jeffrey.microscope.common.model.connector.ConnectorKind;

/**
 * One kind of connector — where its source lies and how it is opened — found by
 * {@link java.util.ServiceLoader}: a module that brings a kind declares it with {@code provides} in its
 * {@code module-info} and in {@code META-INF/services} (the classpath, where Microscope runs, reads
 * only the latter). Nothing above this interface names a kind.
 *
 * @param <S> the kind's settings: a plain record, bound from its block under
 *            {@code jeffrey.microscope.connector.<block>.*} by whoever reads the configuration
 */
public interface ConnectorProvider<S> {

    /** Which kind this is, and so which configuration block declares it. */
    ConnectorKind kind();

    /** The record the kind's configuration block is bound to. */
    Class<S> settingsType();

    /**
     * The connector the settings declare.
     *
     * @throws IllegalArgumentException when the settings are unusable, saying which property and why
     */
    DeclaredConnector declare(S settings);
}
