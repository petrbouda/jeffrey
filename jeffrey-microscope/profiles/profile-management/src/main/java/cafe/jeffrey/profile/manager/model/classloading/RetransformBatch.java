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
 * A class-retransformation batch, derived from a {@code jdk.RetransformClasses} event. One batch
 * typically corresponds to a single agent {@code retransformClasses} call covering several classes.
 *
 * @param redefinitionId identifier shared with the {@code jdk.ClassRedefinition} events of this batch
 * @param classCount     number of classes retransformed in the batch
 * @param durationNanos  wall-clock time the retransformation took, in nanoseconds
 */
public record RetransformBatch(
        long redefinitionId,
        int classCount,
        long durationNanos) {
}
