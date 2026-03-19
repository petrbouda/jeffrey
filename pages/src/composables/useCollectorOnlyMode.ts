import { ref } from 'vue';

const STORAGE_KEY = 'collectorOnlyMode';

function loadFromStorage(): Map<string, boolean> {
  const stored = sessionStorage.getItem(STORAGE_KEY);
  if (!stored) return new Map();
  try {
    return new Map(Object.entries(JSON.parse(stored)) as [string, boolean][]);
  } catch {
    return new Map();
  }
}

const modeMap = loadFromStorage();
const collectorOnlyMode = ref<boolean>(false);

export function useCollectorOnlyMode() {
  function setCollectorOnlyMode(projectId: string, enabled: boolean) {
    modeMap.set(projectId, enabled);
    collectorOnlyMode.value = enabled;
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(Object.fromEntries(modeMap)));
  }

  function getCollectorOnlyMode(projectId: string): boolean {
    return modeMap.get(projectId) ?? false;
  }

  function activateCollectorOnlyMode(projectId: string) {
    collectorOnlyMode.value = modeMap.get(projectId) ?? false;
  }

  return { collectorOnlyMode, setCollectorOnlyMode, getCollectorOnlyMode, activateCollectorOnlyMode };
}
