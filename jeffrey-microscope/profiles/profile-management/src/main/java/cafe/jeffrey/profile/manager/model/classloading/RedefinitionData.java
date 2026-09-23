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

import java.util.List;

/**
 * Bytecode-instrumentation activity for a profile: the retransformation batches and the individual
 * class redefinitions they produced.
 *
 * @param redefinitions individual {@code jdk.ClassRedefinition} entries (one per redefined class)
 * @param retransforms  {@code jdk.RetransformClasses} batches (one per agent retransformation call)
 */
public record RedefinitionData(
        List<ClassRedefinitionStat> redefinitions,
        List<RetransformBatch> retransforms) {
}
