/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import ProjectInstanceSession from '@/services/api/model/ProjectInstanceSession';

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
