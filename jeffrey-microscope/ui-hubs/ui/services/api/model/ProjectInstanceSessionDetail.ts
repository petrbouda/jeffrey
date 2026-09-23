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

import ProjectInstanceSession from '@hubs/services/api/model/ProjectInstanceSession';

/**
 * Raw JFR environment JSON passed through from the server, keyed by JFR
 * event-type name (e.g. {@code "jdk.JVMInformation"}). The inner objects
 * are the field maps emitted by {@code EventFieldsToJsonMapper}; the UI
 * renders them dynamically so new JFR fields appear without a schema
 * change. {@code null} when no finished recording chunk is available yet.
 */
export type InstanceEnvironmentJson = Record<string, Record<string, unknown>>;

export default class ProjectInstanceSessionDetail {
  constructor(
    public session: ProjectInstanceSession,
    public environment: InstanceEnvironmentJson | null
  ) {}
}
