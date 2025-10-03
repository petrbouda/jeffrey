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