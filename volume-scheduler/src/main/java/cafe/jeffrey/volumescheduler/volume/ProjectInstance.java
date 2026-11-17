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

package cafe.jeffrey.volumescheduler.volume;

import java.time.Instant;
import java.util.Optional;

/**
 * An instance as its directory on the volume says at the moment of reading.
 *
 * @param id           the instance's directory name, typically the pod's hostname
 * @param createdAt    when its directory was created; empty when the filesystem could not say,
 *                     which keeps the instance: nothing is deleted on a guess
 * @param sessionCount how many sessions it holds; none while the provisioner registered it and
 *                     nothing has started yet
 */
public record ProjectInstance(String id, Optional<Instant> createdAt, int sessionCount) {
}
