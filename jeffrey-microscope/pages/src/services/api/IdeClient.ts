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

import BasePlatformClient from '@shared/services/api/BasePlatformClient';

export interface IdeOpenResponse {
  success: boolean;
  message: string | null;
  reason: IdeFailureReason;
}

export type IdeFailureReason = 'NONE' | 'DISABLED' | 'NO_TARGET' | 'UNREACHABLE' | 'NOT_RESOLVED';

export interface IdeSourceResponse {
  success: boolean;
  content: string | null;
  message: string | null;
  decompiled: boolean;
}

export interface IdeHasResponse {
  found: boolean;
}

export interface IdeProjectView {
  id: string;
  name: string;
  basePath: string | null;
  vcsBranch: string | null;
  headCommit: string | null;
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

/** Cache-only view of a profile's IDE link, backing the profile-wide nav control. */
export interface IdeTargetStatusResponse {
  selectable: boolean;
  linked: boolean;
  ideName: string | null;
  projectName: string | null;
  basePath: string | null;
  port: number;
  pid: number;
}

/** The window the user picked, with display fields cached server-side at selection time. */
export interface IdeTargetSelection {
  port: number;
  projectId: string;
  ideName: string;
  projectName: string;
  basePath: string | null;
  pid: number;
}

export default class IdeClient extends BasePlatformClient {
  constructor() {
    super('/ide');
  }

  open(profileId: string, fqn: string, method: string, line: number): Promise<IdeOpenResponse> {
    return this.post<IdeOpenResponse>(
      '/open',
      { profileId, fqn, method, line },
      { suppressToast: true }
    );
  }

  fetchSource(profileId: string, fqn: string, method: string): Promise<IdeSourceResponse> {
    return this.get<IdeSourceResponse>(
      '/source',
      { profileId, fqn, method },
      { suppressToast: true }
    );
  }

  discoverTargets(profileId: string, fqn: string): Promise<IdeTargetsResponse> {
    return this.get<IdeTargetsResponse>('/targets', { profileId, fqn }, { suppressToast: true });
  }

  hasClass(profileId: string, fqn: string): Promise<IdeHasResponse> {
    return this.get<IdeHasResponse>('/has', { profileId, fqn }, { suppressToast: true });
  }

  getStatus(profileId: string): Promise<IdeTargetStatusResponse> {
    return this.get<IdeTargetStatusResponse>('/status', { profileId }, { suppressToast: true });
  }

  selectTarget(profileId: string, target: IdeTargetSelection): Promise<IdeTargetResponse> {
    return this.post<IdeTargetResponse>(
      '/target',
      { profileId, ...target },
      { suppressToast: true }
    );
  }

  clearTarget(profileId: string): Promise<IdeTargetResponse> {
    return this.del<IdeTargetResponse>(`/target?profileId=${encodeURIComponent(profileId)}`, {
      suppressToast: true
    });
  }
}
