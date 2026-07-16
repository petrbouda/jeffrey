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
  <div class="profiler-settings-panel">
    <TabBar v-model="activeTab" :tabs="tabs" />

    <div class="tab-content">
      <!-- Scope-specific "current configuration" pane (only rendered when the caller declares the tab) -->
      <template v-if="activeTab === 'current'">
        <slot name="current"></slot>
      </template>

      <!-- Manual command pane -->
      <template v-else-if="activeTab === 'manual'">
        <ConfigureCommand
          :model-value="manualCommand"
          :hide-actions="hideManualActions"
          @update:model-value="emit('update:manualCommand', $event)"
          @accept-command="emit('manual-accept', $event)"
          @clear="emit('manual-clear')"
        />
        <slot name="manual-footer"></slot>
      </template>

      <!-- Visual builder pane -->
      <template v-else-if="activeTab === 'builder'">
        <slot name="builder-prelude"></slot>
        <CommandBuilder
          ref="builderRef"
          :agent-mode="builderAgentMode"
          :hide-live-preview="builderHideLivePreview"
          :hide-help-header="builderHideHelpHeader"
          @cancel="emit('builder-cancel')"
          @accept-command="emit('builder-accept', $event)"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import TabBar, { type TabBarItem } from '@shared/components/TabBar.vue';
import ConfigureCommand from '@/components/settings/ConfigureCommand.vue';
import CommandBuilder from '@/components/settings/CommandBuilder.vue';

interface Props {
  /** Tabs to render; ids 'current', 'manual' and 'builder' switch the built-in panes. */
  tabs: TabBarItem[];
  /** Command shown in the manual tab's ConfigureCommand (v-model:manual-command). */
  manualCommand: string;
  /** Hide ConfigureCommand's built-in accept/clear actions (caller renders its own via #manual-footer). */
  hideManualActions?: boolean;
  /** Forwarded to CommandBuilder. */
  builderAgentMode?: 'jeffrey' | 'custom';
  builderHideLivePreview?: boolean;
  builderHideHelpHeader?: boolean;
}

withDefaults(defineProps<Props>(), {
  hideManualActions: false,
  builderAgentMode: 'jeffrey',
  builderHideLivePreview: false,
  builderHideHelpHeader: false
});

const emit = defineEmits<{
  'update:manualCommand': [value: string];
  'manual-accept': [command: string];
  'manual-clear': [];
  'builder-accept': [command: string];
  'builder-cancel': [];
}>();

/** Active tab id (v-model). */
const activeTab = defineModel<string>({ required: true });

const builderRef = ref<InstanceType<typeof CommandBuilder> | null>(null);

defineExpose({
  /** The mounted CommandBuilder instance (null while the builder tab is closed). */
  builder: builderRef
});
</script>

<style scoped>
/*
 * Layout of `.tab-content` (spacing, overflow handling) is intentionally left to the
 * callers via `:deep(.tab-content)` so each scope keeps its exact original look.
 */
.profiler-settings-panel {
  min-width: 0;
}
</style>
