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

// Global type declarations

declare global {
  interface Window {
    bootstrap: {
      Toast: any;
      Tooltip: any;
      Popover: any;
      Modal: any;
    };
  }
}

// Route parameter types for better type safety
export interface WorkspaceParams {
  workspaceId: string;
}

export interface ProjectParams extends WorkspaceParams {
  projectId: string;
}

export interface ProfileParams extends ProjectParams {
  profileId: string;
}

// Navigation context type
export interface NavigationContext {
  workspaceId: string;
  workspaceName?: string;
  projectId?: string;
  projectName?: string;
  profileId?: string;
  profileName?: string;
}

export {};
