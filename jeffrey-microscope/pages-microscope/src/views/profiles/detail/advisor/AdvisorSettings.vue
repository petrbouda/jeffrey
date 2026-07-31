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
  <LoadingState v-if="loading" message="Loading advisor settings..." />

  <ErrorState v-else-if="error" :message="error" />

  <PageHeader
    v-else
    title="Source & Settings"
    description="Where the Advisor reads your code, and how far it goes when checking a patch"
    icon="bi-folder2-open"
  >
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi-folder2-open" title="Project configuration" />
      </template>

      <form class="settings-form" @submit.prevent="save">
        <div class="field">
          <label for="advisor-source-path">Source folder</label>
          <input
            id="advisor-source-path"
            v-model="form.sourcePath"
            type="text"
            placeholder="/home/you/projects/order-service"
            spellcheck="false"
          />
          <small>
            An absolute path on the machine running Microscope. The Advisor reads it in place — it
            never writes to it, and never deletes it.
          </small>
        </div>

        <div class="field-row">
          <div class="field">
            <label for="advisor-prune">Prompt detail (% of samples)</label>
            <input
              id="advisor-prune"
              v-model.number="form.pruneThresholdPct"
              type="number"
              step="0.1"
              min="0.1"
              max="99.9"
            />
            <small>
              How much of the call tree reaches the model. Lower means a finer tree and a longer
              prompt.
            </small>
          </div>

          <div class="field">
            <label for="advisor-regression">Regression threshold (pp)</label>
            <input
              id="advisor-regression"
              v-model.number="form.regressionThresholdPp"
              type="number"
              step="0.1"
              min="0"
            />
            <small>
              How far a frame must move before a comparison calls it a regression. Below this,
              sampling noise fills the view.
            </small>
          </div>
        </div>

        <div class="field">
          <label for="advisor-compile">Compile command</label>
          <input
            id="advisor-compile"
            v-model="form.compileCommand"
            type="text"
            placeholder="mvn -q -o compile"
            spellcheck="false"
          />
        </div>

        <div class="field">
          <label for="advisor-test">Test command</label>
          <input
            id="advisor-test"
            v-model="form.testCommand"
            type="text"
            placeholder="mvn -q -o test"
            spellcheck="false"
          />
          <small>
            Both are blank by default and their checks report as skipped. Setting them lets Jeffrey
            run your build against a patched copy of the working tree — leave them empty unless you
            want that.
          </small>
        </div>

        <div class="form-actions">
          <button type="submit" class="save-btn" :disabled="saving">
            {{ saving ? 'Saving…' : 'Save settings' }}
          </button>
        </div>
      </form>
    </MainCard>
  </PageHeader>
</template>

<script setup lang="ts">
import '@shared/styles/shared-components.css';
import { onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import ToastService from '@shared/services/ToastService';
import AdvisorClient from '@/services/api/AdvisorClient';
import type { AdvisorSettings } from '@/services/api/model/Advisor';

const route = useRoute();
const client = new AdvisorClient(route.params.profileId as string);

const loading = ref(true);
const saving = ref(false);
const error = ref<string | null>(null);

const form = reactive<AdvisorSettings>({
  sourcePath: '',
  configured: false,
  pruneThresholdPct: 1,
  regressionThresholdPp: 2,
  compileCommand: '',
  testCommand: ''
});

const apply = (settings: AdvisorSettings): void => {
  Object.assign(form, settings);
};

const load = async (): Promise<void> => {
  loading.value = true;
  error.value = null;
  try {
    apply(await client.settings());
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Failed to load the advisor settings';
  } finally {
    loading.value = false;
  }
};

const save = async (): Promise<void> => {
  saving.value = true;
  try {
    apply(
      await client.saveSettings({
        sourcePath: form.sourcePath,
        pruneThresholdPct: form.pruneThresholdPct,
        regressionThresholdPp: form.regressionThresholdPp,
        compileCommand: form.compileCommand,
        testCommand: form.testCommand
      })
    );
    ToastService.success('Advisor', 'Settings saved');
  } catch (e: any) {
    ToastService.error('Advisor', e?.response?.data?.message ?? 'Could not save the settings');
  } finally {
    saving.value = false;
  }
};

onMounted(load);
</script>

<style scoped>
.settings-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  padding: 1.25rem;
}

.field-row {
  display: flex;
  gap: 1.25rem;
  flex-wrap: wrap;
}

.field-row .field {
  flex: 1 1 240px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text);
}

.field input {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.4rem 0.6rem;
  font-size: 0.85rem;
}

.field small {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

.save-btn {
  border: none;
  border-radius: var(--radius-sm);
  padding: 0.4rem 1rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-white);
  background: var(--color-primary);
  cursor: pointer;
}

.save-btn:disabled {
  background: var(--color-border);
  color: var(--color-text-muted);
  cursor: not-allowed;
}
</style>
