/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.shared.common.model.repository;

/**
 * Instance metadata persisted as {@code .instance-info.json} in the instance directory. An
 * instance is a deployed pod or container that creates sessions; the hub reads its id from the
 * directory name and its creation time from here. The project and workspace are not repeated:
 * the directories above it already say them. Files written by older provisioners carry more
 * fields, which are ignored.
 *
 * @param instanceId identifier of the instance (typically HOSTNAME), equal to its directory name
 * @param createdAt  when the instance was first provisioned (epoch millis)
 */
public record RemoteProjectInstance(
        String instanceId,
        long createdAt) {
}
