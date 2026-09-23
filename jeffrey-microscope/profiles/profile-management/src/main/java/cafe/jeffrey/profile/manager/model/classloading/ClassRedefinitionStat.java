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

package cafe.jeffrey.profile.manager.model.classloading;

/**
 * A single class redefinition, derived from a {@code jdk.ClassRedefinition} event. Redefinitions are
 * driven by bytecode-instrumentation agents (JFR retransformation, APM agents, mocking frameworks).
 *
 * @param className         binary name of the redefined class
 * @param modificationCount number of times the class has been changed
 * @param redefinitionId    identifier of the redefinition batch this class belongs to
 */
public record ClassRedefinitionStat(
        String className,
        int modificationCount,
        long redefinitionId) {
}
