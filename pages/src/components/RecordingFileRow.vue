<script setup lang="ts">
import Badge from '@/components/Badge.vue';
import FormattingService from '@/services/FormattingService';
import Utils from '@/services/Utils';

interface Props {
  filename: string;
  fileType: string;
  sizeInBytes?: number;
  description?: string;
  timestamp?: number;
  status?: string;
  compact?: boolean;
}

const props = defineProps<Props>();

const getRowClasses = (): string[] => {
  const classes: string[] = [];
  // Status-active overrides file-type styling
  if (props.status === 'ACTIVE') {
    classes.push('status-active');
  } else {
    classes.push(getFileTypeClass(props.fileType));
  }
  return classes;
};

const getFileTypeClass = (fileType: string): string => {
  switch (fileType) {
    case 'JFR':
    case 'JFR_LZ4':
      return 'file-type-jfr';
    case 'HEAP_DUMP_GZ':
      return 'file-type-heap-dump-gz';
    case 'HEAP_DUMP':
      return 'file-type-heap-dump';
    case 'PERF_COUNTERS':
      return 'file-type-perf-counters';
    case 'JVM_LOG':
      return 'file-type-jvm-log';
    case 'HS_JVM_ERROR_LOG':
      return 'file-type-hs-jvm-error-log';
    case 'ASPROF_TEMP':
      return 'file-type-asprof-temp';
    default:
      return 'file-type-unknown';
  }
};

const getFileTypeVariant = (fileType: string): string => {
  switch (fileType) {
    case 'JFR':
      return 'primary';
    case 'JFR_LZ4':
      return 'indigo';
    case 'HEAP_DUMP_GZ':
      return 'violet';
    case 'HEAP_DUMP':
      return 'purple';
    case 'ASPROF_TEMP':
      return 'orange';
    case 'PERF_COUNTERS':
      return 'blue';
    case 'JVM_LOG':
      return 'teal';
    case 'HS_JVM_ERROR_LOG':
      return 'red';
    case 'UNKNOWN':
    default:
      return 'grey';
  }
};

const getFileTypeIcon = (fileType: string): string => {
  switch (fileType) {
    case 'JFR':
      return 'bi-file-earmark-code';
    case 'JFR_LZ4':
      return 'bi-file-earmark-zip';
    case 'HEAP_DUMP_GZ':
      return 'bi-file-earmark-zip';
    case 'HEAP_DUMP':
      return 'bi-file-earmark-binary';
    case 'ASPROF_TEMP':
      return 'bi-hourglass-split';
    case 'PERF_COUNTERS':
      return 'bi-file-earmark-bar-graph';
    case 'JVM_LOG':
      return 'bi-file-earmark-text';
    case 'HS_JVM_ERROR_LOG':
      return 'bi-file-earmark-x';
    default:
      return 'bi-file-earmark';
  }
};

const formatTimestamp = (millis: number | null | undefined): string => {
  if (!millis) return '';
  return FormattingService.formatTimestampUTC(millis);
};
</script>

<template>
  <div class="recording-file-row" :class="[...getRowClasses(), props.compact ? 'recording-file-row-compact p-1' : 'p-2']">
    <div class="d-flex align-items-center justify-content-between">
      <div class="d-flex align-items-center">
        <slot name="before" />
        <div :class="props.compact ? 'recording-file-icon-small me-2' : 'recording-file-icon-medium me-2'">
          <i class="bi" :class="getFileTypeIcon(props.fileType)"></i>
        </div>
        <div>
          <div :class="props.compact ? 'recording-file-name-compact' : 'recording-file-name'">{{ props.filename }}</div>
          <div :class="['d-flex', 'align-items-center', props.compact ? 'mt-0' : 'mt-1']">
            <Badge
              :value="Utils.formatFileType(props.fileType)"
              :variant="getFileTypeVariant(props.fileType)"
              size="xxs"
            />
            <span class="recording-file-size ms-2" v-if="props.sizeInBytes !== undefined">
              <i class="bi bi-hdd me-1"></i>{{ FormattingService.formatBytes(props.sizeInBytes) }}
            </span>
            <span class="recording-file-timestamp ms-2" v-if="props.timestamp">
              <i class="bi bi-clock me-1"></i>{{ formatTimestamp(props.timestamp) }}
            </span>
            <span class="recording-file-description ms-2" v-if="props.description">{{ props.description }}</span>
            <slot name="extra-badges" />
          </div>
        </div>
      </div>
      <slot name="actions" />
    </div>
  </div>
</template>

<style>
/* Non-scoped styles so parent components can target child elements */

.recording-file-icon-medium {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: 5px;
  background-color: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
  font-size: 1rem;
}

.recording-file-name {
  font-size: 0.75rem;
  font-weight: 500;
  color: #212529;
}

.recording-file-row {
  background-color: rgba(255, 255, 255, 0.7);
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  transition: all 0.15s ease;
}

.recording-file-row:hover {
  background-color: white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transform: translateY(-1px);
}

/* JFR file styling - blue theme */
.recording-file-row.file-type-jfr {
  background-color: rgba(94, 100, 255, 0.08);
  border-left: 3px solid #5e64ff;
  border-top: 1px solid rgba(94, 100, 255, 0.2);
  border-right: 1px solid rgba(94, 100, 255, 0.2);
  border-bottom: 1px solid rgba(94, 100, 255, 0.2);
}

.recording-file-row.file-type-jfr:hover {
  background-color: rgba(94, 100, 255, 0.12);
  box-shadow: 0 2px 5px rgba(94, 100, 255, 0.15);
}

.recording-file-row.file-type-jfr .recording-file-icon-medium {
  background-color: rgba(94, 100, 255, 0.15);
  color: #5e64ff;
}

/* HEAP_DUMP file styling - purple theme */
.recording-file-row.file-type-heap-dump {
  background-color: rgba(111, 66, 193, 0.08);
  border-left: 3px solid #6f42c1;
  border-top: 1px solid rgba(111, 66, 193, 0.2);
  border-right: 1px solid rgba(111, 66, 193, 0.2);
  border-bottom: 1px solid rgba(111, 66, 193, 0.2);
}

.recording-file-row.file-type-heap-dump:hover {
  background-color: rgba(111, 66, 193, 0.12);
  box-shadow: 0 2px 5px rgba(111, 66, 193, 0.15);
}

.recording-file-row.file-type-heap-dump .recording-file-icon-medium {
  background-color: rgba(111, 66, 193, 0.15);
  color: #6f42c1;
}

/* HEAP_DUMP_GZ file styling - deeper purple/violet theme */
.recording-file-row.file-type-heap-dump-gz {
  background-color: rgba(81, 45, 168, 0.08);
  border-left: 3px solid #512da8;
  border-top: 1px solid rgba(81, 45, 168, 0.2);
  border-right: 1px solid rgba(81, 45, 168, 0.2);
  border-bottom: 1px solid rgba(81, 45, 168, 0.2);
}

.recording-file-row.file-type-heap-dump-gz:hover {
  background-color: rgba(81, 45, 168, 0.12);
  box-shadow: 0 2px 5px rgba(81, 45, 168, 0.15);
}

.recording-file-row.file-type-heap-dump-gz .recording-file-icon-medium {
  background-color: rgba(81, 45, 168, 0.15);
  color: #512da8;
}

/* PERF_COUNTERS file styling - sky blue theme */
.recording-file-row.file-type-perf-counters {
  background-color: rgba(14, 165, 233, 0.08);
  border-left: 3px solid #0ea5e9;
  border-top: 1px solid rgba(14, 165, 233, 0.2);
  border-right: 1px solid rgba(14, 165, 233, 0.2);
  border-bottom: 1px solid rgba(14, 165, 233, 0.2);
}

.recording-file-row.file-type-perf-counters:hover {
  background-color: rgba(14, 165, 233, 0.12);
  box-shadow: 0 2px 5px rgba(14, 165, 233, 0.15);
}

.recording-file-row.file-type-perf-counters .recording-file-icon-medium {
  background-color: rgba(14, 165, 233, 0.15);
  color: #0ea5e9;
}

/* JVM_LOG file styling - teal theme */
.recording-file-row.file-type-jvm-log {
  background-color: rgba(20, 184, 166, 0.08);
  border-left: 3px solid #14b8a6;
  border-top: 1px solid rgba(20, 184, 166, 0.2);
  border-right: 1px solid rgba(20, 184, 166, 0.2);
  border-bottom: 1px solid rgba(20, 184, 166, 0.2);
}

.recording-file-row.file-type-jvm-log:hover {
  background-color: rgba(20, 184, 166, 0.12);
  box-shadow: 0 2px 5px rgba(20, 184, 166, 0.15);
}

.recording-file-row.file-type-jvm-log .recording-file-icon-medium {
  background-color: rgba(20, 184, 166, 0.15);
  color: #14b8a6;
}

/* HS_JVM_ERROR_LOG file styling - red theme */
.recording-file-row.file-type-hs-jvm-error-log {
  background-color: rgba(198, 40, 40, 0.08);
  border-left: 3px solid #c62828;
  border-top: 1px solid rgba(198, 40, 40, 0.2);
  border-right: 1px solid rgba(198, 40, 40, 0.2);
  border-bottom: 1px solid rgba(198, 40, 40, 0.2);
}

.recording-file-row.file-type-hs-jvm-error-log:hover {
  background-color: rgba(198, 40, 40, 0.12);
  box-shadow: 0 2px 5px rgba(198, 40, 40, 0.15);
}

.recording-file-row.file-type-hs-jvm-error-log .recording-file-icon-medium {
  background-color: rgba(198, 40, 40, 0.15);
  color: #c62828;
}

/* ASPROF_TEMP file styling - orange theme with dashed border */
.recording-file-row.file-type-asprof-temp {
  background-color: rgba(255, 142, 51, 0.08);
  border-left: 3px solid #ff8e33;
  border-top: 1px dashed rgba(255, 142, 51, 0.4);
  border-right: 1px dashed rgba(255, 142, 51, 0.4);
  border-bottom: 1px dashed rgba(255, 142, 51, 0.4);
}

.recording-file-row.file-type-asprof-temp:hover {
  background-color: rgba(255, 142, 51, 0.12);
  box-shadow: 0 2px 5px rgba(255, 142, 51, 0.15);
}

.recording-file-row.file-type-asprof-temp .recording-file-icon-medium {
  background-color: rgba(255, 142, 51, 0.15);
  color: #ff8e33;
}

/* ACTIVE status styling - orange theme, overrides file-type */
.recording-file-row.status-active {
  background-color: rgba(255, 142, 51, 0.08);
  border-left: 3px solid #ff8e33;
  border-top: 1px solid rgba(255, 142, 51, 0.25);
  border-right: 1px solid rgba(255, 142, 51, 0.25);
  border-bottom: 1px solid rgba(255, 142, 51, 0.25);
  box-shadow: 0 1px 3px rgba(255, 142, 51, 0.15);
}

.recording-file-row.status-active:hover {
  background-color: rgba(255, 142, 51, 0.12);
  box-shadow: 0 2px 5px rgba(255, 142, 51, 0.2);
}

.recording-file-row.status-active .recording-file-icon-medium {
  background-color: rgba(255, 142, 51, 0.15);
  color: #ff8e33;
}

/* UNKNOWN file styling - gray theme */
.recording-file-row.file-type-unknown {
  background-color: rgba(108, 117, 125, 0.08);
  border-left: 3px solid #6c757d;
  border-top: 1px solid rgba(108, 117, 125, 0.2);
  border-right: 1px solid rgba(108, 117, 125, 0.2);
  border-bottom: 1px solid rgba(108, 117, 125, 0.2);
}

.recording-file-row.file-type-unknown:hover {
  background-color: rgba(108, 117, 125, 0.12);
  box-shadow: 0 2px 5px rgba(108, 117, 125, 0.15);
}

.recording-file-row.file-type-unknown .recording-file-icon-medium {
  background-color: rgba(108, 117, 125, 0.15);
  color: #6c757d;
}

/* Compact variant for smaller/denser display */
.recording-file-row-compact {
  font-size: 0.8rem;
}

.recording-file-icon-small {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  min-width: 22px;
  border-radius: 4px;
  background-color: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
  font-size: 0.75rem;
}

.recording-file-name-compact {
  font-size: 0.8rem;
  font-weight: 600;
  color: #212529;
  line-height: 1.2;
}

/* Compact file-type icon overrides */
.recording-file-row.file-type-jfr .recording-file-icon-small {
  background-color: rgba(94, 100, 255, 0.15);
  color: #5e64ff;
}

.recording-file-row.file-type-heap-dump .recording-file-icon-small {
  background-color: rgba(111, 66, 193, 0.15);
  color: #6f42c1;
}

.recording-file-row.file-type-heap-dump-gz .recording-file-icon-small {
  background-color: rgba(81, 45, 168, 0.15);
  color: #512da8;
}

.recording-file-row.file-type-perf-counters .recording-file-icon-small {
  background-color: rgba(14, 165, 233, 0.15);
  color: #0ea5e9;
}

.recording-file-row.file-type-jvm-log .recording-file-icon-small {
  background-color: rgba(20, 184, 166, 0.15);
  color: #14b8a6;
}

.recording-file-row.file-type-hs-jvm-error-log .recording-file-icon-small {
  background-color: rgba(198, 40, 40, 0.15);
  color: #c62828;
}

.recording-file-row.file-type-asprof-temp .recording-file-icon-small {
  background-color: rgba(255, 142, 51, 0.15);
  color: #ff8e33;
}

.recording-file-row.status-active .recording-file-icon-small {
  background-color: rgba(255, 142, 51, 0.15);
  color: #ff8e33;
}

.recording-file-row.file-type-unknown .recording-file-icon-small {
  background-color: rgba(108, 117, 125, 0.15);
  color: #6c757d;
}

.recording-file-row-compact .recording-file-size,
.recording-file-row-compact .recording-file-timestamp,
.recording-file-row-compact .recording-file-description {
  font-size: 0.65rem;
}

.recording-file-row-compact .d-flex.align-items-center.mt-0 {
  line-height: 1;
}

.recording-file-description {
  font-size: 0.75rem;
  color: #5e6e82;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}

.recording-file-size {
  font-size: 0.75rem;
  color: #5e6e82;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
}

.recording-file-timestamp {
  font-size: 0.75rem;
  color: #5e6e82;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
}
</style>
