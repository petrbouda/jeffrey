import { ref } from 'vue';
import WorkspaceType from '@/services/api/model/WorkspaceType';

const STORAGE_KEY = 'workspaceTypes';

function loadFromStorage(): Map<string, WorkspaceType> {
  const stored = sessionStorage.getItem(STORAGE_KEY);
  if (!stored) return new Map();
  try {
    return new Map(Object.entries(JSON.parse(stored)) as [string, WorkspaceType][]);
  } catch {
    return new Map();
  }
}

const typeMap = loadFromStorage();
const workspaceType = ref<WorkspaceType | null>(null);

export function useWorkspaceType() {
  function setWorkspaceType(workspaceId: string, type: WorkspaceType) {
    typeMap.set(workspaceId, type);
    workspaceType.value = type;
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(Object.fromEntries(typeMap)));
  }

  function getWorkspaceType(workspaceId: string): WorkspaceType | null {
    return typeMap.get(workspaceId) ?? null;
  }

  function activateWorkspaceType(workspaceId: string) {
    workspaceType.value = typeMap.get(workspaceId) ?? null;
  }

  return { workspaceType, setWorkspaceType, getWorkspaceType, activateWorkspaceType };
}
