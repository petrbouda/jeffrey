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

package cafe.jeffrey.jib.payload;

/**
 * The GraalVM-compiled provisioner and async-profiler, each for both Linux architectures.
 *
 * <p>This class carries no behaviour. It exists so the module publishes a non-empty sources and
 * javadoc jar, which Maven Central requires of every artifact; the payload itself travels beside it
 * under {@code jeffrey-payload/}, described by {@code jeffrey-payload/payload.properties}.
 */
public abstract class NativeProvisionerPayload {
}
