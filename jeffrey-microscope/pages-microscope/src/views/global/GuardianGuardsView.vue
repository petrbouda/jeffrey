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
                <td>
                  <Badge :value="guard.groupKind" variant="secondary" size="s" />
                </td>
                <td>
                  <Badge :value="guard.category" variant="info" size="s" />
                </td>
                <td>{{ guard.resultType }}</td>
                <td>
                  <Badge
                    :value="guard.enabled ? 'Enabled' : 'Disabled'"
                    :variant="guard.enabled ? 'success' : 'secondary'"
                    size="s"
                  />
                </td>
                <td>
                  <Badge
                    :value="guard.builtIn ? 'Built-in' : 'Custom'"
                    :variant="guard.builtIn ? 'primary' : 'warning'"
                    size="s"
                  />
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
    <GenericModal
      v-model:show="showEditor"
      modal-id="guardianGuardModal"
      :title="editorTitle"
      icon="bi bi-shield-check"
      size="xl"
      modal-dialog-class="modal-dialog-centered modal-dialog-scrollable"
    >
      <form id="guardianGuardForm" class="guard-form" @submit.prevent="save">
        <div class="guard-grid">
          <div class="field-group c4">
            <label class="field-label">Name <span class="field-required">*</span></label>
            <div class="field-wrap">
              <input
                v-model="form.name"
                class="field-input"
                type="text"
                placeholder="Guard name"
                required
              />
            </div>
          </div>
          <div class="field-group c2">
            <label class="field-label">Status</label>
            <label class="guard-switch">
              <input v-model="form.enabled" type="checkbox" />
              <span>{{ form.enabled ? 'Enabled' : 'Disabled' }}</span>
            </label>
          </div>

          <div class="field-group c2">
            <label class="field-label">Group</label>
            <div class="field-wrap">
              <select v-model="form.groupKind" class="field-input">
                <option v-for="g in GROUP_KINDS" :key="g" :value="g">{{ g }}</option>
              </select>
            </div>
          </div>
          <div class="field-group c2">
            <label class="field-label">Category</label>
            <div class="field-wrap">
              <select v-model="form.category" class="field-input">
                <option v-for="c in CATEGORIES" :key="c" :value="c">{{ c }}</option>
              </select>
            </div>
          </div>
          <div class="field-group c2">
            <label class="field-label">Result type</label>
            <div class="field-wrap">
              <select v-model="form.resultType" class="field-input">
                <option v-for="r in RESULT_TYPES" :key="r" :value="r">{{ r }}</option>
              </select>
            </div>
          </div>

          <div class="field-group c2">
            <label class="field-label">Target frame</label>
            <div class="field-wrap">
              <select v-model="form.targetFrame" class="field-input">
                <option v-for="t in TARGET_FRAMES" :key="t" :value="t">{{ t }}</option>
              </select>
            </div>
          </div>
          <div class="field-group c2">
            <label class="field-label">Matching</label>
            <div class="field-wrap">
              <select v-model="form.matchingType" class="field-input">
                <option v-for="m in MATCHING_TYPES" :key="m" :value="m">{{ m }}</option>
              </select>
            </div>
          </div>
          <div class="field-group c1">
            <label class="field-label">Info ≥</label>
            <div class="field-wrap">
              <input
                v-model.number="form.infoThreshold"
                class="field-input"
                type="number"
                step="0.01"
              />
            </div>
          </div>
          <div class="field-group c1">
            <label class="field-label">Warn ≥</label>
            <div class="field-wrap">
              <input
                v-model.number="form.warningThreshold"
                class="field-input"
                type="number"
                step="0.01"
              />
            </div>
          </div>

          <div class="field-group c6">
            <label class="field-label">Summary noun</label>
            <div class="field-wrap">
              <input
                v-model="form.summaryNoun"
                class="field-input"
                type="text"
                placeholder="e.g. the logging"
              />
            </div>
          </div>

          <div class="field-group c6">
            <label class="field-label">Matcher spec <span class="field-muted">(JSON)</span></label>
            <textarea v-model="form.matcherSpec" class="field-textarea is-mono" rows="6"></textarea>
            <p class="field-hint">
              e.g. <code>{"anchor":{"type":"Predicate","op":"PREFIX","value":"com.acme."}}</code> —
              combine with <code>AnyOf</code> / <code>AllOf</code> / <code>Not</code>; ops:
              <code>PREFIX</code>, <code>SUFFIX</code>, <code>CONTAINS</code>, <code>EQUALS</code>,
              <code>REGEX</code>.
            </p>
          </div>
          <div class="field-group c6">
            <label class="field-label"
              >Preconditions <span class="field-muted">(JSON, optional)</span></label
            >
            <textarea
              v-model="form.preconditions"
              class="field-textarea is-mono"
              rows="3"
            ></textarea>
          </div>
          <div class="field-group c6">
            <label class="field-label"
              >Explanation <span class="field-muted">(HTML, optional)</span></label
            >
            <textarea v-model="form.explanation" class="field-textarea" rows="4"></textarea>
          </div>
          <div class="field-group c6">
            <label class="field-label"
              >Solution <span class="field-muted">(HTML, optional)</span></label
            >
            <textarea v-model="form.solution" class="field-textarea" rows="4"></textarea>
          </div>
        </div>

        <p v-if="formError" class="field-error">
          <i class="bi bi-exclamation-circle"></i> {{ formError }}
        </p>
      </form>

      <template #footer>
        <button type="button" class="btn btn-secondary" @click="showEditor = false">Cancel</button>
        <button type="submit" form="guardianGuardForm" class="btn btn-primary" :disabled="saving">
          {{ saving ? 'Saving…' : 'Save guard' }}
        </button>
      </template>
    </GenericModal>

    <!-- Delete confirmation -->
    <GenericModal
      v-model:show="showDelete"
      modal-id="guardianGuardDeleteModal"
      title="Delete guard"
      icon="bi bi-trash"
      size="md"
      modal-dialog-class="modal-dialog-centered"
    >
      <p class="mb-0">
        Delete guard <strong>{{ deleteTarget?.name }}</strong
        >? This cannot be undone.
      </p>

      <template #footer>
        <button class="btn btn-secondary" @click="showDelete = false">Cancel</button>
        <button class="btn btn-danger" :disabled="saving" @click="doDelete">Delete</button>
      </template>
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
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load guards';
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
  } catch (e: unknown) {
    formError.value = e instanceof Error ? e.message : 'Failed to save guard';
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

<style scoped>
.guard-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* Responsive 6-column grid; fields span a subset via the c1..c6 helpers. */
.guard-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 1rem 1.25rem;
}

.c1 {
  grid-column: span 1;
}
.c2 {
  grid-column: span 2;
}
.c4 {
  grid-column: span 4;
}
.c6 {
  grid-column: span 6;
}

.field-muted {
  color: var(--color-text-muted);
  font-weight: var(--font-weight-normal);
}

/* Enable/disable toggle styled to line up with the bordered field controls. */
.guard-switch {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 46px;
  font-size: var(--font-size-base);
  color: var(--color-dark);
  cursor: pointer;
}

.guard-switch input {
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: var(--color-primary);
}

/* Multi-line companion to the shared .field-wrap / .field-input controls. */
.field-textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-neutral-bg);
  padding: 10px 14px;
  font-size: var(--font-size-base);
  color: var(--color-dark);
  line-height: 1.5;
  resize: vertical;
  transition: all var(--transition-base);
}

.field-textarea:hover {
  border-color: var(--color-primary-light);
  background: var(--color-white);
}

.field-textarea:focus {
  outline: none;
  background: var(--color-white);
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
}

.field-textarea.is-mono {
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  font-size: var(--font-size-sm);
}

@media (max-width: 768px) {
  .guard-grid {
    grid-template-columns: 1fr;
  }

  .guard-grid > * {
    grid-column: 1 / -1;
  }
}
</style>
