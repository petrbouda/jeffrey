<template>
  <div class="scoped-config-editor">
    <CommandDisplay
      :command="currentValue"
      :label="valueLabel"
      :deletable="!readonly && currentEntry !== null"
      :deleting="isDeleting"
      :empty-message="emptyMessage"
      @delete="emit('delete', TYPE)"
    >
      <template #header-left>
        <SettingsBreadcrumbs :items="breadcrumbItems" />
      </template>
    </CommandDisplay>

    <p v-if="currentEntry" class="config-provenance">
      Updated {{ formattedUpdatedAt }} · file
      <code>{{ shortDigest }}</code>
    </p>

    <ProfilerSettingsPanel
      v-if="!readonly"
      v-model="activeTab"
      v-model:manual-command="draftCommand"
      :tabs="TABS"
      @manual-accept="save"
      @manual-clear="draftCommand = ''"
      @builder-accept="acceptFromBuilder"
      @builder-cancel="activeTab = 'manual'"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import CommandDisplay from '@/components/settings/CommandDisplay.vue';
import SettingsBreadcrumbs, {
  type BreadcrumbItem
} from '@/components/settings/SettingsBreadcrumbs.vue';
import ProfilerSettingsPanel from '@/components/profiler-settings/ProfilerSettingsPanel.vue';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import FormattingService from '@shared/services/FormattingService';
import ScopedConfig, {
  entryOf,
  type ConfigScope,
  type ConfigType
} from '@/services/api/model/ScopedConfig';

/**
 * Edits the configuration of one scope.
 *
 * The Hub stores typed values and renders them into the file the Provisioner reads, so nothing
 * here writes or shows HOCON: the operator edits a command, and the Hub owns the file's shape.
 * One card per known type, so the page stays correct as the catalogue grows without becoming a
 * free-text field today.
 */
interface Props {
  scope: ConfigScope;
  /** What the scope holds, or null while it holds nothing. */
  config: ScopedConfig | null;
  /** The global scope is the Hub's own baseline and is shown without editing controls. */
  readonly?: boolean;
  isDeleting?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false,
  isDeleting: false
});

const emit = defineEmits<{
  save: [type: ConfigType, value: string];
  delete: [type: ConfigType];
}>();

/** The only type in the catalogue; a second one becomes a second card rather than a branch here. */
const TYPE: ConfigType = 'ASPROF_SETTINGS';

const TABS: TabBarItem[] = [
  { id: 'manual', label: 'Manual' },
  { id: 'builder', label: 'Visual Builder' }
];

/** Enough of the digest to compare two versions by eye, which is all it is used for here. */
const DIGEST_PREFIX_LENGTH = 12;

const activeTab = ref<string>('manual');
const draftCommand = ref('');

const currentEntry = computed(() => entryOf(props.config, TYPE));
const currentValue = computed(() => currentEntry.value?.value ?? null);

const valueLabel = computed(() =>
  props.scope === 'PROJECT' ? 'Project Command' : 'Profiler Command'
);

const emptyMessage = computed(() =>
  props.readonly
    ? 'Nothing set at this scope'
    : 'Nothing set here yet — anything below inherits from the scope above'
);

const formattedUpdatedAt = computed(() =>
  currentEntry.value ? FormattingService.formatRelativeTime(currentEntry.value.updatedAt) : ''
);

const shortDigest = computed(() => props.config?.digest.slice(0, DIGEST_PREFIX_LENGTH) ?? '');

const breadcrumbItems = computed<BreadcrumbItem[]>(() => [
  { icon: 'bi-globe2', label: 'Global', active: props.scope === 'GLOBAL' },
  { icon: 'bi-folder-fill', label: 'Workspace', active: props.scope === 'WORKSPACE' },
  { icon: 'bi-diagram-3-fill', label: 'Project', active: props.scope === 'PROJECT' }
]);

function save(command: string) {
  const trimmed = command?.trim();
  if (!trimmed) {
    return;
  }
  emit('save', TYPE, trimmed);
  draftCommand.value = '';
}

/** The builder already emits exactly the string this type stores, so it needs no translation. */
function acceptFromBuilder(command: string) {
  draftCommand.value = command;
  activeTab.value = 'manual';
}
</script>

<style scoped>
.scoped-config-editor {
  min-width: 0;
}

.config-provenance {
  margin: var(--spacing-sm) 0 var(--spacing-md);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.config-provenance code {
  color: var(--text-secondary);
}
</style>
