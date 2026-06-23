<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader
          icon="bi bi-git"
          title="Version Control System"
        />
      </template>

      <LoadingState v-if="loading" message="Loading version control system..." />
      <ErrorState v-else-if="error" :message="error" />

      <template v-else>
        <!-- Platform panels: pick GitHub or GitLab to reveal its form below -->
        <div class="platform-grid">
          <button
            v-for="platform in PLATFORMS"
            :key="platform.code"
            type="button"
            class="platform-tile"
            :class="{ selected: selectedPlatform === platform.code }"
            @click="selectPlatform(platform.code)"
          >
            <i class="bi" :class="platform.icon"></i>
            <span class="platform-name">{{ platform.label }}</span>
            <Badge
              v-if="configuredPlatform === platform.code"
              variant="success"
              size="xs"
            >
              Connected
            </Badge>
          </button>
        </div>

        <!-- Platform-specific form -->
        <form v-if="selectedPlatform" class="version-control-system-form" @submit.prevent="save">
          <div class="form-card">
            <div class="form-card-header">
              <i class="bi" :class="selectedPlatformIcon"></i>
              <h6>{{ selectedPlatformLabel }} repository</h6>
            </div>

            <label class="field-label" for="repository-url">Repository URL</label>
            <input
              id="repository-url"
              v-model="url"
              type="text"
              class="field-input"
              placeholder="https://github.com/owner/repository.git"
              autocomplete="off"
            />

            <label class="field-label" for="access-token">Access Token</label>
            <input
              id="access-token"
              v-model="token"
              type="password"
              class="field-input"
              :placeholder="tokenPlaceholder"
              autocomplete="off"
            />
            <p class="field-hint">
              A personal access token used to clone private repositories. Leave blank for public
              repositories{{ hasCredentials ? ', or to keep the currently stored token' : '' }}.
            </p>

            <button
              type="submit"
              class="settings-btn settings-btn-primary"
              :disabled="!canSave"
            >
              <span
                v-if="saving"
                class="spinner-border spinner-border-sm"
                role="status"
                aria-hidden="true"
              ></span>
              <i v-else class="bi bi-link-45deg"></i>
              {{ saving ? 'Saving...' : 'Save & Link' }}
            </button>
          </div>
        </form>
      </template>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import Badge from '@shared/components/Badge.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import ToastService from '@shared/services/ToastService';
import { useNavigation } from '@/composables/useNavigation';
import VersionControlSystemClient from '@/services/api/VersionControlSystemClient';

interface PlatformOption {
  code: string;
  label: string;
  icon: string;
}

const PLATFORMS: PlatformOption[] = [
  { code: 'github', label: 'GitHub', icon: 'bi-github' },
  { code: 'gitlab', label: 'GitLab', icon: 'bi-gitlab' }
];

const { hubId, workspaceId, projectId } = useNavigation();
const client = new VersionControlSystemClient(hubId.value, workspaceId.value, projectId.value);

const loading = ref(true);
const error = ref<string | null>(null);
const saving = ref(false);

const selectedPlatform = ref<string | null>(null);
const configuredPlatform = ref<string | null>(null);
const url = ref('');
const token = ref('');
const hasCredentials = ref(false);

const selectedPlatformLabel = computed(
  () => PLATFORMS.find(p => p.code === selectedPlatform.value)?.label ?? ''
);
const selectedPlatformIcon = computed(
  () => PLATFORMS.find(p => p.code === selectedPlatform.value)?.icon ?? ''
);
const tokenPlaceholder = computed(() =>
  hasCredentials.value ? '•••••••• (stored — leave blank to keep)' : 'Optional personal access token'
);
const canSave = computed(() => !saving.value && !!selectedPlatform.value && url.value.trim().length > 0);

function selectPlatform(code: string) {
  selectedPlatform.value = code;
}

onMounted(async () => {
  try {
    const config = await client.load();
    if (config.configured) {
      configuredPlatform.value = config.platform;
      selectedPlatform.value = config.platform;
      url.value = config.url ?? '';
      hasCredentials.value = config.hasCredentials;
    }
  } catch (e) {
    console.error('Failed to load version control system:', e);
    error.value = 'Could not load the version control system configuration.';
  } finally {
    loading.value = false;
  }
});

async function save() {
  if (!canSave.value || !selectedPlatform.value) {
    return;
  }

  try {
    saving.value = true;
    const config = await client.save({
      platform: selectedPlatform.value,
      url: url.value.trim(),
      token: token.value
    });
    configuredPlatform.value = config.platform;
    hasCredentials.value = config.hasCredentials;
    token.value = '';
    ToastService.success('Version Control System saved', 'The repository has been linked to this project.');
  } catch (e) {
    console.error('Failed to save version control system:', e);
    ToastService.error('Error', 'Could not save the version control system configuration.');
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.platform-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.platform-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 16px;
  background: var(--color-bg-card);
  border: 2px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.platform-tile:hover {
  border-color: var(--color-primary-border);
  box-shadow: var(--shadow-base);
}

.platform-tile.selected {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-base);
}

.platform-tile i {
  font-size: 2rem;
  color: var(--color-dark);
}

.platform-tile.selected i {
  color: var(--color-primary);
}

.platform-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.form-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-base);
  padding: 20px;
}

.form-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.form-card-header i {
  font-size: 1rem;
  color: var(--color-primary);
}

.form-card-header h6 {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
  margin: 0;
}

.field-label {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 6px;
}

.field-input {
  width: 100%;
  padding: var(--spacing-2) var(--spacing-3);
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-base);
  background: var(--color-white);
  color: var(--color-text);
  outline: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
  margin-bottom: 12px;
}

.field-input:focus {
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
}

.field-hint {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  margin-bottom: 16px;
}

.settings-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  border: none;
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.settings-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.settings-btn-primary {
  background: var(--color-primary);
  color: var(--color-white);
}

.settings-btn-primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
}
</style>
