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

import java.util.Map;

/**
 * Project metadata persisted as {@code .project-info.json} in the project directory. It carries
 * only what the hub reads: the project's id, name, creation time and attributes. Where the
 * project lies is never written down — the hub reads it from the directory the file sits in.
 * Files written by older provisioners carry more fields, which are ignored.
 */
public record RemoteProject(
        String projectId,
        String projectName,
        long createdAt,
        Map<String, String> attributes) {
}
