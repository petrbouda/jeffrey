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

package cafe.jeffrey.jib.maven;

/**
 * Marks the native flavour of the Jeffrey JIB extension for Maven: the extension plus a payload
 * jar carrying the GraalVM provisioner and async-profiler.
 *
 * <p>This class carries no behaviour. It exists so the module publishes a non-empty sources and
 * javadoc jar, which Maven Central requires of every artifact; the module is its two dependencies.
 */
public abstract class NativeFlavour {
}
