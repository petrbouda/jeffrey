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
        <MainCardHeader icon="bi bi-shield-check" title="Guardian Guards" />
      </template>

      <p class="text-muted mb-3">
        Guardian guards are loaded from the central database. Built-in guards ship as defaults; you
        can edit any of them or add your own custom guard.
      </p>

      <LoadingState v-if="loading" />
      <ErrorState v-else-if="error" :message="error" />
      <template v-else>
        <div class="d-flex justify-content-between align-items-center mb-3 gap-2">
          <input
            v-model="search"
            type="text"
            class="form-control form-control-sm"
            style="max-width: 320px"
            placeholder="Search guards…"
          />
          <button class="btn btn-sm btn-primary" @click="openCreate">
            <i class="bi bi-plus-lg"></i> New Guard
          </button>
        </div>

        <EmptyState
          v-if="filtered.length === 0"
          icon="bi-shield-slash"
          title="No guards"
          description="No Guardian guards match the current search."
        />
        <div v-else class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Name</th>
                <th>Group</th>
                <th>Category</th>
                <th>Result</th>
                <th>Status</th>
                <th>Origin</th>
                <th class="text-end">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="guard in filtered" :key="guard.guardId">
                <td>{{ guard.name }}</td>
                <td><Badge variant="secondary" size="s">{{ guard.groupKind }}</Badge></td>
                <td><Badge variant="info" size="s">{{ guard.category }}</Badge></td>
                <td>{{ guard.resultType }}</td>
                <td>
                  <Badge :variant="guard.enabled ? 'success' : 'secondary'" size="s">
                    {{ guard.enabled ? 'Enabled' : 'Disabled' }}
                  </Badge>
                </td>
                <td>
                  <Badge :variant="guard.builtIn ? 'primary' : 'warning'" size="s">
                    {{ guard.builtIn ? 'Built-in' : 'Custom' }}
                  </Badge>
                </td>
                <td class="text-end">
                  <button class="btn btn-sm btn-outline-secondary me-1" @click="openEdit(guard)">
                    <i class="bi bi-pencil"></i>
                  </button>
                  <button class="btn btn-sm btn-outline-danger" @click="confirmDelete(guard)">
                    <i class="bi bi-trash"></i>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </MainCard>

    <!-- Create / Edit modal -->
    <GenericModal v-model:show="showEditor" :title="editorTitle" size="lg" :show-footer="false">
      <form @submit.prevent="save">
        <div class="row g-3">
          <div class="col-md-8">
            <label class="form-label">Name</label>
            <input v-model="form.name" type="text" class="form-control form-control-sm" required />
          </div>
          <div class="col-md-4 d-flex align-items-end">
            <div class="form-check">
              <input id="guard-enabled" v-model="form.enabled" class="form-check-input" type="checkbox" />
              <label class="form-check-label" for="guard-enabled">Enabled</label>
            </div>
          </div>

          <div class="col-md-4">
            <label class="form-label">Group</label>
            <select v-model="form.groupKind" class="form-select form-select-sm">
              <option v-for="g in GROUP_KINDS" :key="g" :value="g">{{ g }}</option>
            </select>
          </div>
          <div class="col-md-4">
            <label class="form-label">Category</label>
            <select v-model="form.category" class="form-select form-select-sm">
              <option v-for="c in CATEGORIES" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div class="col-md-4">
            <label class="form-label">Result type</label>
            <select v-model="form.resultType" class="form-select form-select-sm">
              <option v-for="r in RESULT_TYPES" :key="r" :value="r">{{ r }}</option>
            </select>
          </div>

          <div class="col-md-4">
            <label class="form-label">Target frame</label>
            <select v-model="form.targetFrame" class="form-select form-select-sm">
              <option v-for="t in TARGET_FRAMES" :key="t" :value="t">{{ t }}</option>
            </select>
          </div>
          <div class="col-md-4">
            <label class="form-label">Matching</label>
            <select v-model="form.matchingType" class="form-select form-select-sm">
              <option v-for="m in MATCHING_TYPES" :key="m" :value="m">{{ m }}</option>
            </select>
          </div>
          <div class="col-md-2">
            <label class="form-label">Info ≥</label>
            <input v-model.number="form.infoThreshold" type="number" step="0.01" class="form-control form-control-sm" />
          </div>
          <div class="col-md-2">
            <label class="form-label">Warn ≥</label>
            <input v-model.number="form.warningThreshold" type="number" step="0.01" class="form-control form-control-sm" />
          </div>

          <div class="col-12">
            <label class="form-label">Summary noun</label>
            <input v-model="form.summaryNoun" type="text" class="form-control form-control-sm" />
          </div>
          <div class="col-12">
            <label class="form-label">
              Matcher spec (JSON) —
              <span class="text-muted">e.g. {"anchor":{"type":"Predicate","op":"PREFIX","value":"com.acme."}}</span>
            </label>
            <textarea v-model="form.matcherSpec" rows="4" class="form-control form-control-sm font-monospace"></textarea>
          </div>
          <div class="col-12">
            <label class="form-label">Preconditions (JSON, optional)</label>
            <textarea v-model="form.preconditions" rows="2" class="form-control form-control-sm font-monospace"></textarea>
          </div>
          <div class="col-12">
            <label class="form-label">Explanation (HTML, optional)</label>
            <textarea v-model="form.explanation" rows="3" class="form-control form-control-sm"></textarea>
          </div>
          <div class="col-12">
            <label class="form-label">Solution (HTML, optional)</label>
            <textarea v-model="form.solution" rows="3" class="form-control form-control-sm"></textarea>
          </div>
        </div>

        <div v-if="formError" class="text-danger small mt-2">{{ formError }}</div>

        <div class="d-flex justify-content-end gap-2 mt-3">
          <button type="button" class="btn btn-sm btn-outline-secondary" @click="showEditor = false">
            Cancel
          </button>
          <button type="submit" class="btn btn-sm btn-primary" :disabled="saving">
            {{ saving ? 'Saving…' : 'Save' }}
          </button>
        </div>
      </form>
    </GenericModal>

    <!-- Delete confirmation -->
    <GenericModal v-model:show="showDelete" title="Delete guard" size="sm" :show-footer="false">
      <p>Delete guard <strong>{{ deleteTarget?.name }}</strong>? This cannot be undone.</p>
      <div class="d-flex justify-content-end gap-2">
        <button class="btn btn-sm btn-outline-secondary" @click="showDelete = false">Cancel</button>
        <button class="btn btn-sm btn-danger" :disabled="saving" @click="doDelete">Delete</button>
      </div>
    </GenericModal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
import Badge from '@/components/Badge.vue';
import GenericModal from '@/components/GenericModal.vue';
import EmptyState from '@/components/EmptyState.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import GuardianGuardsClient from '@/services/api/GuardianGuardsClient';
import type GuardianGuard from '@/services/api/model/GuardianGuard';
import type { GuardianGuardRequest } from '@/services/api/model/GuardianGuard';

const GROUP_KINDS = ['EXECUTION_SAMPLE', 'CPU_TIME_SAMPLE', 'ALLOCATION', 'WALL_CLOCK', 'BLOCKING'];
const CATEGORIES = ['PREREQUISITES', 'GARBAGE_COLLECTION', 'JIT', 'APPLICATION', 'OTHERS'];
const RESULT_TYPES = ['SAMPLES', 'WEIGHT', 'SELF_SAMPLES', 'SELF_WEIGHT'];
const TARGET_FRAMES = ['JAVA', 'JVM', 'ALL'];
const MATCHING_TYPES = ['FULL_MATCH', 'SINGLE_MATCH'];

const client = new GuardianGuardsClient();

const loading = ref(true);
const error = ref<string | null>(null);
const guards = ref<GuardianGuard[]>([]);
const search = ref('');

const showEditor = ref(false);
const editingId = ref<string | null>(null);
const saving = ref(false);
const formError = ref<string | null>(null);
const form = ref<GuardianGuardRequest>(emptyForm());

const showDelete = ref(false);
const deleteTarget = ref<GuardianGuard | null>(null);

const editorTitle = computed(() => (editingId.value ? 'Edit guard' : 'New guard'));

const filtered = computed(() => {
  const term = search.value.trim().toLowerCase();
  if (!term) {
    return guards.value;
  }
  return guards.value.filter(
    g =>
      g.name.toLowerCase().includes(term) ||
      g.groupKind.toLowerCase().includes(term) ||
      g.category.toLowerCase().includes(term)
  );
});

function emptyForm(): GuardianGuardRequest {
  return {
    name: '',
    enabled: true,
    groupKind: 'EXECUTION_SAMPLE',
    category: 'APPLICATION',
    resultType: 'SAMPLES',
    targetFrame: 'JAVA',
    matchingType: 'FULL_MATCH',
    infoThreshold: 0.03,
    warningThreshold: 0.05,
    matcherSpec: '{"anchor":{"type":"Predicate","op":"PREFIX","value":""}}',
    preconditions: null,
    summaryNoun: '',
    explanation: '',
    solution: ''
  };
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    guards.value = await client.list();
  } catch (e: any) {
    error.value = e?.message ?? 'Failed to load guards';
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  editingId.value = null;
  form.value = emptyForm();
  formError.value = null;
  showEditor.value = true;
}

function openEdit(guard: GuardianGuard): void {
  editingId.value = guard.guardId;
  form.value = {
    name: guard.name,
    enabled: guard.enabled,
    groupKind: guard.groupKind,
    category: guard.category,
    resultType: guard.resultType,
    targetFrame: guard.targetFrame,
    matchingType: guard.matchingType,
    infoThreshold: guard.infoThreshold,
    warningThreshold: guard.warningThreshold,
    matcherSpec: guard.matcherSpec,
    preconditions: guard.preconditions,
    summaryNoun: guard.summaryNoun,
    explanation: guard.explanation,
    solution: guard.solution
  };
  formError.value = null;
  showEditor.value = true;
}

function validate(): boolean {
  if (!form.value.name.trim()) {
    formError.value = 'Name is required';
    return false;
  }
  try {
    JSON.parse(form.value.matcherSpec);
  } catch {
    formError.value = 'Matcher spec must be valid JSON';
    return false;
  }
  if (form.value.preconditions && form.value.preconditions.trim()) {
    try {
      JSON.parse(form.value.preconditions);
    } catch {
      formError.value = 'Preconditions must be valid JSON';
      return false;
    }
  } else {
    form.value.preconditions = null;
  }
  return true;
}

async function save(): Promise<void> {
  formError.value = null;
  if (!validate()) {
    return;
  }
  saving.value = true;
  try {
    if (editingId.value) {
      await client.update(editingId.value, form.value);
    } else {
      await client.create(form.value);
    }
    showEditor.value = false;
    await load();
  } catch (e: any) {
    formError.value = e?.message ?? 'Failed to save guard';
  } finally {
    saving.value = false;
  }
}

function confirmDelete(guard: GuardianGuard): void {
  deleteTarget.value = guard;
  showDelete.value = true;
}

async function doDelete(): Promise<void> {
  if (!deleteTarget.value) {
    return;
  }
  saving.value = true;
  try {
    await client.remove(deleteTarget.value.guardId);
    showDelete.value = false;
    deleteTarget.value = null;
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>
