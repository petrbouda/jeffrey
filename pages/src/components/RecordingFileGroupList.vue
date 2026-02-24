<script setup lang="ts">
import {ref} from 'vue';
import RecordingFileRow from '@/components/RecordingFileRow.vue';
import RecordingFileType from '@/services/api/model/RecordingFileType';
import RecordingFile from '@/services/api/model/RecordingFile';

interface Props {
  recordingId: string;
  files: RecordingFile[];
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'download', recordingId: string, fileId: string): void
}>();

// --- Type grouping constants ---
type ArtifactTypeGroup = 'JFR_RECORDING' | 'HEAP_DUMP' | 'PERF_COUNTERS' | 'JVM_LOG' | 'HS_JVM_ERROR_LOG' | 'UNKNOWN';

const TYPE_GROUP_ORDER: ArtifactTypeGroup[] = [
  'JFR_RECORDING', 'HEAP_DUMP', 'PERF_COUNTERS', 'JVM_LOG', 'HS_JVM_ERROR_LOG', 'UNKNOWN',
];

const FILE_TYPE_TO_GROUP: Record<string, ArtifactTypeGroup> = {
  [RecordingFileType.JFR]: 'JFR_RECORDING',
  [RecordingFileType.JFR_LZ4]: 'JFR_RECORDING',
  [RecordingFileType.ASPROF]: 'JFR_RECORDING',
  [RecordingFileType.HEAP_DUMP]: 'HEAP_DUMP',
  [RecordingFileType.HEAP_DUMP_GZ]: 'HEAP_DUMP',
  [RecordingFileType.PERF_COUNTERS]: 'PERF_COUNTERS',
  [RecordingFileType.JVM_LOG]: 'JVM_LOG',
  [RecordingFileType.HS_JVM_ERROR_LOG]: 'HS_JVM_ERROR_LOG',
  [RecordingFileType.UNKNOWN]: 'UNKNOWN',
};

const TYPE_GROUP_DISPLAY: Record<ArtifactTypeGroup, { name: string; variant: string; fileType: string }> = {
  'JFR_RECORDING':    { name: 'JFR Recordings',     variant: 'primary', fileType: 'JFR' },
  'HEAP_DUMP':        { name: 'Heap Dumps',          variant: 'purple',  fileType: 'HEAP_DUMP' },
  'PERF_COUNTERS':    { name: 'Perf Counters',       variant: 'blue',    fileType: 'PERF_COUNTERS' },
  'JVM_LOG':          { name: 'JVM Logs',            variant: 'teal',    fileType: 'JVM_LOG' },
  'HS_JVM_ERROR_LOG': { name: 'HotSpot Error Logs',  variant: 'red',     fileType: 'HS_JVM_ERROR_LOG' },
  'UNKNOWN':          { name: 'Other Files',          variant: 'grey',    fileType: 'UNKNOWN' },
};

interface TypeGroupPanel {
  groupKey: ArtifactTypeGroup;
  display: { name: string; variant: string; fileType: string };
  files: RecordingFile[];
  fileCount: number;
  totalSize: number;
}

// --- Expansion state ---
const expandedTypePanels = ref<{ [key: string]: boolean }>({});

const toggleTypePanel = (groupKey: ArtifactTypeGroup) => {
  const key = `${props.recordingId}:${groupKey}`;
  expandedTypePanels.value[key] = !expandedTypePanels.value[key];
};

const isTypePanelExpanded = (groupKey: ArtifactTypeGroup): boolean => {
  return !!expandedTypePanels.value[`${props.recordingId}:${groupKey}`];
};

// --- Grouping logic ---
const getGroupMap = (files: RecordingFile[]): Map<ArtifactTypeGroup, RecordingFile[]> => {
  const groupMap = new Map<ArtifactTypeGroup, RecordingFile[]>();
  for (const file of files) {
    const groupKey = FILE_TYPE_TO_GROUP[file.type] || 'UNKNOWN';
    if (!groupMap.has(groupKey)) groupMap.set(groupKey, []);
    groupMap.get(groupKey)!.push(file);
  }
  return groupMap;
};

const getTypeGroupPanels = (files: RecordingFile[]): TypeGroupPanel[] => {
  const groupMap = getGroupMap(files);
  const panels: TypeGroupPanel[] = [];
  for (const groupKey of TYPE_GROUP_ORDER) {
    const groupFiles = groupMap.get(groupKey);
    if (!groupFiles || groupFiles.length <= 1) continue;
    panels.push({
      groupKey,
      display: TYPE_GROUP_DISPLAY[groupKey],
      files: groupFiles,
      fileCount: groupFiles.length,
      totalSize: groupFiles.reduce((sum, f) => sum + f.sizeInBytes, 0),
    });
  }
  return panels;
};

const getStandaloneFiles = (files: RecordingFile[]): RecordingFile[] => {
  const groupMap = getGroupMap(files);
  const standalone: RecordingFile[] = [];
  for (const groupKey of TYPE_GROUP_ORDER) {
    const groupFiles = groupMap.get(groupKey);
    if (groupFiles && groupFiles.length === 1) {
      standalone.push(groupFiles[0]);
    }
  }
  return standalone;
};
</script>

<template>
  <!-- Type Group Panels (2+ files of same type) -->
  <div v-for="panel in getTypeGroupPanels(props.files)" :key="panel.groupKey" class="type-panel mb-2">
    <div class="type-panel-header-wrapper" @click="toggleTypePanel(panel.groupKey)">
      <RecordingFileRow
        :filename="panel.display.name"
        :fileType="panel.display.fileType"
        :sizeInBytes="panel.totalSize"
      >
        <template #before>
          <i class="bi me-1 type-panel-chevron"
             :class="isTypePanelExpanded(panel.groupKey) ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
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
  color: #6c757d;
  width: 16px;
  text-align: center;
}

.type-panel-body {
  border-left: 1px solid #e9ecef;
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
