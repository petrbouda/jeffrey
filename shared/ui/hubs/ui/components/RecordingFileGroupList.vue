<script setup lang="ts">
import { ref } from 'vue';
import RecordingFileRow from '@hubs/components/RecordingFileRow.vue';
import SupportedFileType from '@hubs/services/api/model/SupportedFileType';
import type { Variant } from '@shared/types/ui';
import RecordingFile from '@hubs/services/api/model/RecordingFile';

interface Props {
  recordingId: string;
  files: RecordingFile[];
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'download', recordingId: string, fileId: string): void;
}>();

// --- Type grouping constants ---
type FileTypeGroup =
  | 'JFR_RECORDING'
  | 'HEAP_DUMP'
  | 'PERF_COUNTERS'
  | 'JVM_LOG'
  | 'APP_LOG'
  | 'HS_JVM_ERROR_LOG'
  | 'UNKNOWN';

const TYPE_GROUP_ORDER: FileTypeGroup[] = [
  'JFR_RECORDING',
  'HEAP_DUMP',
  'PERF_COUNTERS',
  'JVM_LOG',
  'APP_LOG',
  'HS_JVM_ERROR_LOG',
  'UNKNOWN'
];

const FILE_TYPE_TO_GROUP: Record<string, FileTypeGroup> = {
  [SupportedFileType.JFR]: 'JFR_RECORDING',
  [SupportedFileType.JFR_LZ4]: 'JFR_RECORDING',
  [SupportedFileType.ASPROF]: 'JFR_RECORDING',
  [SupportedFileType.HEAP_DUMP]: 'HEAP_DUMP',
  [SupportedFileType.HEAP_DUMP_GZ]: 'HEAP_DUMP',
  [SupportedFileType.PERF_COUNTERS]: 'PERF_COUNTERS',
  [SupportedFileType.JVM_LOG]: 'JVM_LOG',
  [SupportedFileType.APP_LOG]: 'APP_LOG',
  [SupportedFileType.HS_JVM_ERROR_LOG]: 'HS_JVM_ERROR_LOG',
  [SupportedFileType.UNKNOWN]: 'UNKNOWN'
};

const TYPE_GROUP_DISPLAY: Record<
  FileTypeGroup,
  { name: string; variant: Variant; fileType: string }
> = {
  JFR_RECORDING: { name: 'JFR Recordings', variant: 'primary', fileType: 'JFR' },
  HEAP_DUMP: { name: 'Heap Dumps', variant: 'purple', fileType: 'HEAP_DUMP' },
  PERF_COUNTERS: { name: 'Perf Counters', variant: 'blue', fileType: 'PERF_COUNTERS' },
  JVM_LOG: { name: 'JVM Logs', variant: 'green', fileType: 'JVM_LOG' },
  APP_LOG: { name: 'Application Logs', variant: 'brown', fileType: 'APP_LOG' },
  HS_JVM_ERROR_LOG: { name: 'HotSpot Error Logs', variant: 'red', fileType: 'HS_JVM_ERROR_LOG' },
  UNKNOWN: { name: 'Other Files', variant: 'grey', fileType: 'UNKNOWN' }
};

interface TypeGroupPanel {
  groupKey: FileTypeGroup;
  display: { name: string; variant: string; fileType: string };
  files: RecordingFile[];
  fileCount: number;
  totalSize: number;
}

// --- Expansion state ---
const expandedTypePanels = ref<{ [key: string]: boolean }>({});

const toggleTypePanel = (groupKey: FileTypeGroup) => {
  const key = `${props.recordingId}:${groupKey}`;
  expandedTypePanels.value[key] = !expandedTypePanels.value[key];
};

const isTypePanelExpanded = (groupKey: FileTypeGroup): boolean => {
  return !!expandedTypePanels.value[`${props.recordingId}:${groupKey}`];
};

// Groups that always show as a panel, even with a single file
const ALWAYS_GROUPED: Set<FileTypeGroup> = new Set(['JVM_LOG', 'APP_LOG']);

// --- Grouping logic ---
const getGroupMap = (files: RecordingFile[]): Map<FileTypeGroup, RecordingFile[]> => {
  const groupMap = new Map<FileTypeGroup, RecordingFile[]>();
  for (const file of files) {
    const groupKey = FILE_TYPE_TO_GROUP[file.type] || 'UNKNOWN';
    if (!groupMap.has(groupKey)) {groupMap.set(groupKey, []);}
    groupMap.get(groupKey)!.push(file);
  }
  return groupMap;
};

const getTypeGroupPanels = (files: RecordingFile[]): TypeGroupPanel[] => {
  const groupMap = getGroupMap(files);
  const panels: TypeGroupPanel[] = [];
  for (const groupKey of TYPE_GROUP_ORDER) {
    const groupFiles = groupMap.get(groupKey);
    if (!groupFiles) {continue;}
    if (groupFiles.length <= 1 && !ALWAYS_GROUPED.has(groupKey)) {continue;}
    panels.push({
      groupKey,
      display: TYPE_GROUP_DISPLAY[groupKey],
      files: groupFiles,
      fileCount: groupFiles.length,
      totalSize: groupFiles.reduce((sum, f) => sum + f.sizeInBytes, 0)
    });
  }
  return panels;
};

const getStandaloneFiles = (files: RecordingFile[]): RecordingFile[] => {
  const groupMap = getGroupMap(files);
  const standalone: RecordingFile[] = [];
  for (const groupKey of TYPE_GROUP_ORDER) {
    const groupFiles = groupMap.get(groupKey);
    if (groupFiles && groupFiles.length === 1 && !ALWAYS_GROUPED.has(groupKey)) {
      standalone.push(groupFiles[0]);
    }
  }
  return standalone;
};
</script>

<template>
  <!-- Type Group Panels (2+ files of same type) -->
  <div
    v-for="panel in getTypeGroupPanels(props.files)"
    :key="panel.groupKey"
    class="type-panel mb-2"
  >
    <div class="type-panel-header-wrapper" @click="toggleTypePanel(panel.groupKey)">
      <RecordingFileRow
        :filename="panel.display.name"
        :fileType="panel.display.fileType"
        :sizeInBytes="panel.totalSize"
      >
        <template #before>
          <i
            class="bi me-1 type-panel-chevron"
            :class="isTypePanelExpanded(panel.groupKey) ? 'bi-chevron-down' : 'bi-chevron-right'"
          ></i>
        </template>
        <template #extra-badges>
          <span class="recording-file-size ms-2">
            <i class="bi bi-files me-1"></i>{{ panel.fileCount }} files
          </span>
        </template>
      </RecordingFileRow>
    </div>

    <div v-if="isTypePanelExpanded(panel.groupKey)" class="type-panel-body">
      <RecordingFileRow
        v-for="file in panel.files"
        :key="file.id"
        :filename="file.filename"
        :fileType="file.type"
        :sizeInBytes="file.sizeInBytes"
        :description="file.description"
        class="mb-2"
      >
        <template #actions>
          <button
            class="btn btn-sm btn-outline-secondary download-file-btn"
            @click="emit('download', props.recordingId, file.id)"
            title="Download file"
          >
            <i class="bi bi-download"></i>
          </button>
        </template>
      </RecordingFileRow>
    </div>
  </div>

  <!-- Standalone Files (1 file per type) -->
  <RecordingFileRow
    v-for="file in getStandaloneFiles(props.files)"
    :key="file.id"
    :filename="file.filename"
    :fileType="file.type"
    :sizeInBytes="file.sizeInBytes"
    :description="file.description"
    class="mb-2"
  >
    <template #actions>
      <button
        class="btn btn-sm btn-outline-secondary download-file-btn"
        @click="emit('download', props.recordingId, file.id)"
        title="Download file"
      >
        <i class="bi bi-download"></i>
      </button>
    </template>
  </RecordingFileRow>
</template>

<style scoped>
.type-panel-header-wrapper {
  cursor: pointer;
  user-select: none;
}

.type-panel-chevron {
  font-size: 0.65rem;
  color: var(--color-text-muted);
  width: 16px;
  text-align: center;
}

.type-panel-body {
  border-left: 1px solid var(--color-border);
  margin-left: 32px;
  padding-left: 20px;
  padding-top: 6px;
}

.download-file-btn {
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  opacity: 0.6;
  transition: opacity 0.15s ease;
}

.download-file-btn:hover {
  opacity: 1;
}
</style>
