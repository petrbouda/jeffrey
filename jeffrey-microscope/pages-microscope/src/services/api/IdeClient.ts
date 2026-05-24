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

import BasePlatformClient from './BasePlatformClient';

export interface IdeOpenResponse {
  success: boolean;
  message: string | null;
}

export interface IdeSourceResponse {
  success: boolean;
  content: string | null;
  message: string | null;
}

export interface IdeProjectView {
  id: string;
  name: string;
  basePath: string | null;
  vcsBranch: string | null;
  focused: boolean;
  hasClass: boolean;
}

export interface IdeInstanceView {
  port: number;
  ideName: string;
  ideVersion: string;
  pid: number;
  projects: IdeProjectView[];
}

export interface IdeTargetsResponse {
  selectedProjectId: string | null;
  instances: IdeInstanceView[];
}

export interface IdeTargetResponse {
  success: boolean;
}

export default class IdeClient extends BasePlatformClient {
  constructor() {
    super('/ide');
  }

  open(profileId: string, fqn: string, method: string, line: number): Promise<IdeOpenResponse> {
    return this.post<IdeOpenResponse>('/open', { profileId, fqn, method, line }, { suppressToast: true });
  }

  fetchSource(profileId: string, fqn: string, method: string): Promise<IdeSourceResponse> {
    return this.get<IdeSourceResponse>('/source', { profileId, fqn, method }, { suppressToast: true });
  }

  discoverTargets(profileId: string, fqn: string): Promise<IdeTargetsResponse> {
    return this.get<IdeTargetsResponse>('/targets', { profileId, fqn }, { suppressToast: true });
  }

  selectTarget(profileId: string, port: number, projectId: string): Promise<IdeTargetResponse> {
    return this.post<IdeTargetResponse>('/target', { profileId, port, projectId }, { suppressToast: true });
  }
}
