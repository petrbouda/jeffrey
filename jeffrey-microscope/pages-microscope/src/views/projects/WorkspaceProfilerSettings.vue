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
  <div class="workspace-profiler-settings">
    <AsyncProfilerHelpPanel />

    <div class="tab-bar">
      <button
        type="button"
        :class="{ active: activeTab === 'current' }"
        @click="activeTab = 'current'"
      >
        Current
        <span
          v-if="effectiveLevel !== 'none'"
          class="tab-level-badge"
          :class="`tab-level-${effectiveLevel}`"
        >
          {{ effectiveLevel === 'workspace' ? 'WORKSPACE' : 'GLOBAL' }}
        </span>
      </button>
      <button
        type="button"
        :class="{ active: activeTab === 'builder' }"
        @click="activeTab = 'builder'"
      >
        Visual Builder
      </button>
      <button
        type="button"
        :class="{ active: activeTab === 'manual' }"
        @click="activeTab = 'manual'"
      >
        Manual
      </button>
    </div>

    <div class="tab-content">
      <template v-if="activeTab === 'current'">
        <div v-if="loadingCurrent" class="current-loading">
          <span class="spinner-border spinner-border-sm me-2" role="status"></span>
          Loading current configuration…
        </div>

        <template v-else>
          <div v-if="workspaceCommand" class="level-card active">
            <div class="level-card-head">
              <span class="level-card-icon"><i class="bi bi-folder2"></i></span>
              <span class="level-card-title">
                Workspace
                <small>Override applied to <b>{{ workspaceName || 'this workspace' }}</b></small>
              </span>
              <span class="level-card-pill">ACTIVE</span>
            </div>
            <div class="level-card-body">
              <pre class="cmd-text">{{ workspaceCommand }}</pre>
            </div>
            <div class="level-card-foot">
              <button class="btn btn-ghost" @click="copyCommandText(workspaceCommand)">
                <i class="bi bi-clipboard"></i> Copy
              </button>
              <button class="btn btn-danger" :disabled="deleting" @click="confirmRemoveOverride = true">
                <span v-if="deleting" class="spinner-border spinner-border-sm me-1" role="status"></span>
                <i v-else class="bi bi-trash"></i>
                Remove workspace override
              </button>
            </div>
          </div>

          <div v-if="workspaceCommand" class="level-arrow">
            <span class="level-arrow-line"></span>
            <i class="bi bi-arrow-down"></i>
            <span>falls back to</span>
            <i class="bi bi-arrow-down"></i>
            <span class="level-arrow-line"></span>
          </div>

          <div :class="['level-card', workspaceCommand ? 'fallback' : 'active']">
            <div class="level-card-head">
              <span class="level-card-icon"><i class="bi bi-globe2"></i></span>
              <span class="level-card-title">
                Global
                <small>Server-wide default</small>
              </span>
              <span class="level-card-pill">{{ workspaceCommand ? 'DEFAULT' : 'ACTIVE' }}</span>
            </div>
            <div :class="['level-card-body', { muted: !!workspaceCommand }]">
              <pre v-if="globalCommand" class="cmd-text">{{ globalCommand }}</pre>
              <div v-else class="empty-current">
                No global profiler settings configured. The cluster admin can seed a default via
                <code>jeffrey.server.profiler.global-settings.command</code>.
              </div>
            </div>
            <div v-if="globalCommand && !workspaceCommand" class="level-card-foot">
              <button class="btn btn-ghost" @click="copyCommandText(globalCommand)">
                <i class="bi bi-clipboard"></i> Copy
              </button>
            </div>
          </div>
        </template>

        <ConfirmationDialog
          v-model:show="confirmRemoveOverride"
          title="Remove workspace override?"
          :message="`Remove the workspace-level profiler settings for ${workspaceName || 'this workspace'}?`"
          sub-message="The workspace will fall back to the global default."
          confirm-label="Remove override"
          confirm-button-class="btn-danger"
          @confirm="removeWorkspaceOverride"
        />
      </template>

      <template v-else-if="activeTab === 'manual'">
        <ConfigureCommand
          v-model="manualCommand"
          hide-actions
          @accept-command="onManualAccept"
        />
        <div class="manual-actions">
          <button
            type="button"
            class="btn btn-primary"
            :disabled="!manualCommand.trim() || applying"
            @click="onManualAccept(manualCommand)"
          >
            <span v-if="applying" class="spinner-border spinner-border-sm me-2" role="status"></span>
            <i v-else class="bi bi-check-circle-fill me-1"></i>
            Apply to workspace
          </button>
        </div>
      </template>

      <template v-else>
        <template v-if="hasCommand">
          <div
            class="warning-panel clickable-cmd"
            title="Click to copy command"
            @click="copyCommand"
          >
            <div class="step-header-status header-success">
              <div class="step-type-info">
                <i class="bi bi-terminal-fill"></i>
                <span>COMMAND TO APPLY</span>
                <span class="header-description">FULL COMMAND + ACTIVE PARAMETERS</span>
              </div>
              <div class="configure-icon">
                <i class="bi bi-clipboard"></i>
              </div>
            </div>
            <div class="cmd-block">
              <pre class="cmd-text">{{ livePreview.command }}</pre>
            </div>
            <div v-if="livePreview.tokens.length > 0" class="param-rows">
              <div
                v-for="t in livePreview.tokens"
                :key="t.key"
                class="param-row"
              >
                <span class="param-key">
                  <i class="bi bi-circle-fill"></i>
                  {{ t.label }}
                </span>
                <span class="param-val">{{ t.value }}</span>
              </div>
            </div>
          </div>

          <div
            v-for="(w, idx) in livePreview.warnings"
            :key="idx"
            class="warning-panel clickable-warning"
            @click="w.action"
          >
            <div class="step-header-status header-warning">
              <div class="step-type-info">
                <i class="bi bi-exclamation-triangle-fill"></i>
                <span>RECOMMENDATION</span>
                <span class="header-description">{{ w.title.toUpperCase() }}</span>
              </div>
              <div class="configure-icon">
                <i class="bi bi-gear-fill"></i>
              </div>
            </div>
            <div class="warning-content">
              <div class="warning-message">
                <span class="warning-text">{{ w.body }}</span>
              </div>
            </div>
          </div>

          <div class="live-preview-actions">
            <button
              type="button"
              class="btn btn-primary"
              :disabled="applying"
              @click="applyToWorkspace"
            >
              <span v-if="applying" class="spinner-border spinner-border-sm me-2" role="status"></span>
              <i v-else class="bi bi-check-circle-fill me-1"></i>
              Apply to workspace
            </button>
          </div>
          <div class="section-delimiter" role="separator">
            <span class="section-delimiter-line"></span>
            <span class="section-delimiter-caption">
              <i class="bi bi-sliders"></i>
              Configure profiler
            </span>
            <span class="section-delimiter-line"></span>
          </div>
        </template>

        <CommandBuilder
          ref="builderRef"
          hide-live-preview
          hide-help-header
          agent-mode="jeffrey"
          @accept-command="onBuilderAccept"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import ConfigureCommand from '@/components/settings/ConfigureCommand.vue';
import CommandBuilder from '@/components/settings/CommandBuilder.vue';
import AsyncProfilerHelpPanel from '@/components/settings/AsyncProfilerHelpPanel.vue';
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import WorkspaceProfilerSettingsClient from '@/services/api/WorkspaceProfilerSettingsClient';
import ToastService from '@/services/ToastService';

const props = defineProps<{
  serverId: string;
  workspaceId: string;
  workspaceName?: string;
}>();

type TabKey = 'current' | 'builder' | 'manual';
const activeTab = ref<TabKey>('current');

const manualCommand = ref('');
const applying = ref(false);
const deleting = ref(false);
const confirmRemoveOverride = ref(false);
const workspaceCommand = ref<string | null>(null);
const globalCommand = ref<string | null>(null);
const loadingCurrent = ref(false);

const effectiveLevel = computed<'workspace' | 'global' | 'none'>(() => {
  if (workspaceCommand.value) return 'workspace';
  if (globalCommand.value) return 'global';
  return 'none';
});

const builderRef = ref<InstanceType<typeof CommandBuilder> | null>(null);

const fetchCurrent = async () => {
  if (!props.serverId || !props.workspaceId) {
    workspaceCommand.value = null;
    globalCommand.value = null;
    return;
  }
  loadingCurrent.value = true;
  try {
    const client = new WorkspaceProfilerSettingsClient(props.serverId, props.workspaceId);
    const response = await client.fetchCurrent();
    workspaceCommand.value = response.workspaceAgentSettings;
    globalCommand.value = response.globalAgentSettings;
  } catch (error) {
    console.error('Failed to load current workspace profiler settings:', error);
    workspaceCommand.value = null;
    globalCommand.value = null;
  } finally {
    loadingCurrent.value = false;
  }
};

onMounted(fetchCurrent);
watch(() => [props.serverId, props.workspaceId] as const, fetchCurrent);

interface Recommendation {
  title: string;
  body: string;
  action: () => void;
}

const livePreview = computed(() => {
  const r = builderRef.value;
  if (!r) {
    return {
      command: '',
      tokens: [] as Array<{ key: string; label: string; value: string }>,
      warnings: [] as Recommendation[],
    };
  }

  const warnings: Recommendation[] = [];
  if (r.shouldShowChunkSizeWarning) {
    warnings.push({
      title: 'Chunk size not configured',
      body: 'Chunk Size helps with parallelization of JFR processing with multiple threads',
      action: () => r.enableChunkSizeConfiguration({ scroll: false }),
    });
  }
  if (r.shouldShowJfrSyncWarning) {
    warnings.push({
      title: 'JFR synchronization not configured',
      body: 'JFR Synchronization merges AsyncProfiler events with JDK\'s JFR recording for richer profiling data',
      action: () => r.enableJfrSyncConfiguration({ scroll: false }),
    });
  }

  return {
    command: r.generatedConfig || '',
    tokens: r.builderTokens || [],
    warnings,
  };
});

const hasCommand = computed(() => livePreview.value.command.length > 0);

const applyCommand = async (command: string) => {
  if (!command) return;
  applying.value = true;
  try {
    const client = new WorkspaceProfilerSettingsClient(props.serverId, props.workspaceId);
    await client.upsert(command);
    ToastService.success(
      'Configuration Applied',
      `Profiler configuration was applied to ${props.workspaceName || 'this workspace'}.`,
    );
    await fetchCurrent();
    activeTab.value = 'current';
  } catch (error) {
    console.error('Failed to apply workspace profiler settings:', error);
    ToastService.error(
      'Application Failed',
      'Could not apply profiler settings to this workspace.',
    );
  } finally {
    applying.value = false;
  }
};

const onManualAccept = (command: string) => {
  manualCommand.value = command;
  applyCommand(command);
};

const onBuilderAccept = (command: string) => {
  if (command) {
    applyCommand(command);
  }
};

const applyToWorkspace = async () => {
  await applyCommand(livePreview.value.command);
};

const copyCommand = async () => {
  await copyCommandText(livePreview.value.command);
};

const copyCommandText = async (cmd: string | null | undefined) => {
  if (!cmd) return;
  try {
    await navigator.clipboard.writeText(cmd);
    ToastService.success('Copied!', 'Profiler command copied to clipboard', 1500);
  } catch (error) {
    console.error('Failed to copy:', error);
    ToastService.error('Copy failed', 'Could not copy command to clipboard', 1500);
  }
};

const removeWorkspaceOverride = async () => {
  deleting.value = true;
  try {
    const client = new WorkspaceProfilerSettingsClient(props.serverId, props.workspaceId);
    await client.delete();
    ToastService.success(
      'Workspace Override Removed',
      `${props.workspaceName || 'This workspace'} now uses the global default.`,
    );
    await fetchCurrent();
  } catch (error) {
    console.error('Failed to remove workspace override:', error);
    ToastService.error(
      'Removal Failed',
      'Could not remove the workspace-level profiler settings.',
    );
  } finally {
    deleting.value = false;
    confirmRemoveOverride.value = false;
  }
};
</script>

<style scoped>
.workspace-profiler-settings {
  padding: var(--spacing-5);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.live-preview-actions {
  display: flex;
  justify-content: flex-start;
  gap: 8px;
}

.section-delimiter {
  display: flex;
  align-items: center;
  gap: 12px;
  /* CommandBuilder below has pt-3 (16px). Adding margin-top: 16px here
     keeps the visual gap symmetric on both sides of the delimiter. */
  margin: 16px 0 0;
}
.section-delimiter-line {
  flex: 1;
  height: 4px;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}
.section-delimiter-caption {
  font-size: 10px;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: 1px;
  text-transform: uppercase;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.section-delimiter-caption i {
  color: var(--color-primary);
  font-size: 10px;
}
.manual-actions {
  display: flex;
  justify-content: flex-start;
  gap: 8px;
  margin-top: 8px;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  font-size: 12.5px;
  font-weight: 600;
  border: 1px solid transparent;
  border-radius: var(--radius-base);
  cursor: pointer;
}
.btn-primary {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}
.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}
.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.tab-bar {
  display: flex;
  border-bottom: 1px solid var(--color-border);
}
.tab-bar button {
  padding: 9px 16px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.12s ease, border-color 0.12s ease;
}
.tab-bar button:hover {
  color: var(--color-text);
}
.tab-bar button.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
}

.tab-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Merged Command + Active Parameters block — uses the same .warning-panel skeleton
   as the recommendation panels, but with a green header and a hairline-row body. */
.cmd-block {
  padding: 14px 18px;
  background: var(--color-white);
  border-bottom: 1px solid var(--color-border);
}
.cmd-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  color: var(--color-text-dark);
  font-weight: 500;
  word-break: break-all;
  line-height: 1.55;
  margin: 0;
  white-space: pre-wrap;
}
.param-rows {
  background: var(--color-white);
}
.param-row {
  display: grid;
  grid-template-columns: 130px 1fr;
  gap: 16px;
  padding: 10px 18px;
  align-items: center;
}
.param-row + .param-row {
  border-top: 1px solid var(--color-border);
}
.param-key {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.param-key i {
  color: var(--color-success);
  font-size: 6px;
}
.param-val {
  color: var(--color-text-muted);
  font-size: 0.8rem;
  line-height: 1.4;
  word-break: break-all;
  margin: 0;
}

/* Recommendation panels — mirrors CommandBuilder's .warning-panel styles */
.warning-panel {
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.1),
    0 1px 2px rgba(0, 0, 0, 0.06);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.clickable-warning {
  cursor: pointer;
  user-select: none;
}
.clickable-warning:hover {
  border-color: rgba(245, 158, 11, 0.3);
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.15);
  transform: translateY(-1px);
}
.clickable-warning:hover .step-header-status {
  background: linear-gradient(135deg, var(--color-amber-highlight), var(--color-amber-darkest));
}
.clickable-warning:hover .configure-icon {
  transform: rotate(15deg);
}

.clickable-cmd {
  cursor: pointer;
  user-select: none;
}
.clickable-cmd:hover {
  border-color: rgba(0, 210, 122, 0.3);
  box-shadow: 0 2px 8px rgba(0, 210, 122, 0.15);
  transform: translateY(-1px);
}
.clickable-cmd:hover .header-success {
  background: linear-gradient(135deg, var(--color-success-hover), var(--color-success-hover));
}
.clickable-cmd:active {
  transform: translateY(0);
}

.step-header-status {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 13px;
  font-size: 10.5px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: white;
  transition: all 0.2s ease;
}

.header-warning {
  background: linear-gradient(135deg, var(--color-amber), var(--color-amber-highlight));
}

.header-success {
  background: linear-gradient(135deg, var(--color-success), var(--color-success-hover));
}

.header-current {
  background: linear-gradient(135deg, var(--color-info), var(--color-info-hover));
}

.empty-current {
  font-style: italic;
  color: var(--color-text-muted);
  font-size: 0.85rem;
  font-family: inherit;
}

/* Current tab — Mockup A: stacked level cards with inheritance arrow */
.current-loading {
  display: flex;
  align-items: center;
  padding: 16px;
  font-size: 13px;
  color: var(--color-text-muted);
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.level-card {
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm, 0 1px 2px rgba(20, 25, 60, 0.05));
}

.level-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border-soft, var(--color-border));
  background: var(--color-light);
}
.level-card.active .level-card-head {
  background: linear-gradient(90deg, rgba(0, 210, 122, 0.1), transparent);
  border-bottom-color: rgba(0, 210, 122, 0.18);
}
.level-card.fallback .level-card-head {
  opacity: 0.85;
}

.level-card-icon {
  width: 38px;
  height: 38px;
  border-radius: var(--radius-base);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: 18px;
  flex-shrink: 0;
}
.level-card.active .level-card-icon {
  background: var(--color-success);
  color: white;
}

.level-card-title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  color: var(--color-text-dark);
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
}
.level-card-title small {
  font-weight: 500;
  font-size: 12.5px;
  color: var(--color-text);
  letter-spacing: 0;
  text-transform: none;
}

.level-card-pill {
  margin-left: auto;
  font-size: 9.5px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 999px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.level-card.active .level-card-pill {
  background: var(--color-success);
  color: white;
}
.level-card.fallback .level-card-pill {
  background: var(--color-lighter);
  color: var(--color-text-muted);
}

.level-card-body {
  padding: 14px 18px;
}
.level-card-body.muted .cmd-text {
  color: var(--color-text-muted);
}
.level-card-body code {
  background: var(--color-lighter);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 0.9em;
}

.level-card-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 14px;
  border-top: 1px solid var(--color-border-soft, var(--color-border));
  background: var(--color-light);
}

.level-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 10.5px;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.6px;
  padding: 8px 0;
}
.level-arrow i { color: var(--color-text-light); }
.level-arrow-line {
  height: 16px;
  width: 1px;
  background: linear-gradient(to bottom, var(--color-border), transparent);
}

.btn-ghost {
  background: transparent;
  border: 1px solid transparent;
  color: var(--color-text-muted);
}
.btn-ghost:hover { color: var(--color-text); background: var(--color-light); }

.btn-danger {
  background: white;
  color: var(--color-danger);
  border: 1px solid rgba(230, 55, 87, 0.25);
}
.btn-danger:hover:not(:disabled) {
  background: rgba(230, 55, 87, 0.08);
  border-color: var(--color-danger);
}
.btn-danger:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.tab-level-badge {
  font-size: 8.5px;
  font-weight: 700;
  letter-spacing: 0.5px;
  padding: 1px 5px;
  border-radius: 999px;
  margin-left: 6px;
  text-transform: uppercase;
}
.tab-level-badge.tab-level-workspace {
  background: var(--color-info-light);
  color: var(--color-info);
  border: 1px solid var(--color-info);
}
.tab-level-badge.tab-level-global {
  background: var(--color-lighter);
  color: var(--color-text-muted);
  border: 1px solid var(--color-border);
}

.step-type-info {
  display: flex;
  align-items: center;
  gap: 5px;
}
.step-type-info i {
  font-size: 11.5px;
}

.header-description {
  font-weight: 400;
  font-style: italic;
  opacity: 0.85;
  font-size: 10px;
  margin-left: 9px;
  letter-spacing: 0.3px;
}

.configure-icon {
  display: flex;
  align-items: center;
  font-size: 11px;
  transition: transform 0.2s ease;
  opacity: 0.85;
}

.warning-content {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
}

.warning-message {
  flex: 1;
}

.warning-text {
  color: var(--color-text-muted);
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.4;
}
</style>
