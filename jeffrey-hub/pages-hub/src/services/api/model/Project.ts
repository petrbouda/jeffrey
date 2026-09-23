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

import RecordingStatus from "@/services/api/model/RecordingStatus.ts";

export default class Project {
    constructor(
        public id: string,
        public name: string,
        public label: string | null,
        // UTC epoch millis — format with FormattingService, never by parsing date strings
        public createdAt: number,
        public workspaceId: string,
        public status: RecordingStatus) {
    }

    static displayName(project: Project): string {
        return project.label?.trim() ? project.label : project.name;
    }
}
