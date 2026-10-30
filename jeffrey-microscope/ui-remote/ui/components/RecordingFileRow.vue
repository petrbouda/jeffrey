<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<!--
  One row of a file listing, shared by a remote session's detail and a bundle on the Recordings page:
  the file type's colour on the row, its icon and badge, then the name, size and time. A row stands
  for one file, or for a group of files of one type when the caller names it after the group. A
  download-only row (a log, a crash file: nothing Jeffrey initializes) keeps its solid left edge and
  draws the rest of its border dashed.
-->
<script setup lang="ts">
import { computed } from 'vue';
import Badge from '@shared/components/Badge.vue';
import FormattingService from '@shared/services/FormattingService';
import type { Variant } from '@shared/types/ui';
import Utils from '@/services/Utils';
import RecordingFileType from '@remote/services/api/model/RecordingFileType';

interface Props {
  filename: string;
  fileType: string;
  sizeInBytes?: number;
  description?: string;
  timestamp?: number;
  compact?: boolean;
  /** A file Jeffrey never initializes: it is only downloaded or handed to an agent as a path. */
  downloadOnly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  sizeInBytes: undefined,
  description: undefined,
  timestamp: undefined,
  compact: false,
  downloadOnly: false
});

/** How a file type looks: the class that sets the row's accent, its badge colour and its icon. */
interface FileTypeStyle {
  cssClass: string;
  variant: Variant;
  icon: string;
}

const FILE_TYPE_STYLES: Record<string, FileTypeStyle> = {
  [RecordingFileType.JFR]: {
    cssClass: 'file-type-jfr',
    variant: 'primary',
    icon: 'bi-file-earmark-code'
  },
  [RecordingFileType.JFR_LZ4]: {
    cssClass: 'file-type-jfr',
    variant: 'indigo',
    icon: 'bi-file-earmark-zip'
  },
  [RecordingFileType.HEAP_DUMP_GZ]: {
    cssClass: 'file-type-heap-dump-gz',
    variant: 'violet',
    icon: 'bi-file-earmark-zip'
  },
  [RecordingFileType.HEAP_DUMP]: {
    cssClass: 'file-type-heap-dump',
    variant: 'purple',
    icon: 'bi-file-earmark-binary'
  },
  [RecordingFileType.PERF_COUNTERS]: {
    cssClass: 'file-type-perf-counters',
    variant: 'blue',
    icon: 'bi-file-earmark-bar-graph'
  },
  [RecordingFileType.PPROF]: {
    cssClass: 'file-type-pprof',
    variant: 'teal',
    icon: 'bi-bar-chart'
  },
  [RecordingFileType.OTLP_PROFILE]: {
    cssClass: 'file-type-otlp',
    variant: 'orange',
    icon: 'bi-broadcast'
  },
  [RecordingFileType.JVM_LOG]: {
    cssClass: 'file-type-jvm-log',
    variant: 'green',
    icon: 'bi-file-earmark-text'
  },
  [RecordingFileType.APP_LOG]: {
    cssClass: 'file-type-app-log',
    variant: 'brown',
    icon: 'bi-file-earmark-text'
  },
  [RecordingFileType.HS_JVM_ERROR_LOG]: {
    cssClass: 'file-type-hs-jvm-error-log',
    variant: 'red',
    icon: 'bi-file-earmark-x'
  },
  [RecordingFileType.ASPROF]: {
    cssClass: 'file-type-asprof-temp',
    variant: 'orange',
    icon: 'bi-hourglass-split'
  }
};

const UNKNOWN_STYLE: FileTypeStyle = {
  cssClass: 'file-type-unknown',
  variant: 'grey',
  icon: 'bi-file-earmark'
};

const DOWNLOAD_ONLY_TITLE =
  'Download only: Jeffrey does not initialize this file. Download it, or hand it to an agent as a path.';

const style = computed(() => FILE_TYPE_STYLES[props.fileType] ?? UNKNOWN_STYLE);
</script>

<template>
  <div
    class="recording-file-row"
    :class="[
      style.cssClass,
      props.compact ? 'recording-file-row-compact p-1' : 'p-2',
      { 'recording-file-row--download-only': props.downloadOnly }
    ]"
    :title="props.downloadOnly ? DOWNLOAD_ONLY_TITLE : undefined"
  >
    <div class="d-flex align-items-center justify-content-between">
      <div class="d-flex align-items-center recording-file-main">
        <slot name="before" />
        <div
          :class="
            props.compact ? 'recording-file-icon-small me-2' : 'recording-file-icon-medium me-2'
          "
        >
          <i class="bi" :class="style.icon"></i>
        </div>
        <div class="recording-file-text">
          <div :class="props.compact ? 'recording-file-name-compact' : 'recording-file-name'">
            {{ props.filename }}
          </div>
          <div :class="['d-flex', 'align-items-center', props.compact ? 'mt-0' : 'mt-1']">
            <Badge
              :value="Utils.formatFileType(props.fileType)"
              :variant="style.variant"
              size="xxs"
            />
            <span class="recording-file-size ms-2" v-if="props.sizeInBytes !== undefined">
              <i class="bi bi-hdd me-1"></i>{{ FormattingService.formatBytes(props.sizeInBytes) }}
            </span>
            <span class="recording-file-timestamp ms-2" v-if="props.timestamp">
              <i class="bi bi-clock me-1"></i
              >{{ FormattingService.formatTimestampUTC(props.timestamp) }}
            </span>
            <span class="recording-file-description ms-2" v-if="props.description">{{
              props.description
            }}</span>
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

/*
 * Each file type sets one accent; the row's tint, border and icon tile are all mixed from it.
 * A download-only row keeps its solid left edge and dashes the rest of its border, the way
 * async-profiler's unfinished scratch file always has.
 */
.recording-file-row {
  --file-accent: var(--color-grey-muted);
  background-color: color-mix(in srgb, var(--file-accent) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--file-accent) 20%, transparent);
  border-left: 3px solid var(--file-accent);
  border-radius: var(--radius-sm);
  transition: background-color 0.15s ease;
}

.recording-file-row:hover {
  background-color: color-mix(in srgb, var(--file-accent) 12%, transparent);
}

.recording-file-row.recording-file-row--download-only,
.recording-file-row.file-type-asprof-temp {
  border-top-style: dashed;
  border-right-style: dashed;
  border-bottom-style: dashed;
}

.recording-file-row.file-type-asprof-temp {
  border-top-color: color-mix(in srgb, var(--file-accent) 40%, transparent);
  border-right-color: color-mix(in srgb, var(--file-accent) 40%, transparent);
  border-bottom-color: color-mix(in srgb, var(--file-accent) 40%, transparent);
}

.recording-file-row.file-type-jfr {
  --file-accent: var(--color-primary);
}

.recording-file-row.file-type-heap-dump {
  --file-accent: var(--color-purple);
}

.recording-file-row.file-type-heap-dump-gz {
  --file-accent: var(--color-violet-text);
}

.recording-file-row.file-type-perf-counters {
  --file-accent: var(--color-sky);
}

.recording-file-row.file-type-pprof {
  --file-accent: var(--color-teal);
}

.recording-file-row.file-type-otlp {
  --file-accent: var(--color-orange);
}

.recording-file-row.file-type-jvm-log {
  --file-accent: var(--color-green-accent);
}

.recording-file-row.file-type-app-log {
  --file-accent: var(--color-amber-dark);
}

.recording-file-row.file-type-hs-jvm-error-log {
  --file-accent: var(--color-red-text);
}

.recording-file-row.file-type-asprof-temp {
  --file-accent: var(--color-orange-accent);
}

.recording-file-main {
  min-width: 0;
}

.recording-file-text {
  min-width: 0;
}

.recording-file-icon-medium,
.recording-file-icon-small {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: color-mix(in srgb, var(--file-accent, var(--color-primary)) 15%, transparent);
  color: var(--file-accent, var(--color-primary));
}

.recording-file-icon-medium {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: var(--radius-base);
  font-size: 1rem;
}

.recording-file-icon-small {
  width: 22px;
  height: 22px;
  min-width: 22px;
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
}

/* The expand chevron in front of a group row, or the same space in front of a single file. */
.recording-file-chevron {
  width: 16px;
  min-width: 16px;
  margin-right: var(--spacing-1);
  padding: 0;
  border: 0;
  background: transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.65rem;
  color: var(--color-text-muted);
  cursor: pointer;
}

.recording-file-name {
  font-size: 0.75rem;
  font-weight: var(--font-weight-medium);
  color: var(--color-dark);
  overflow-wrap: anywhere;
}

/* The files of an opened group, indented under its row to start below its icon. */
.recording-file-children {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
  margin-left: var(--spacing-8);
  padding-left: var(--spacing-5);
  border-left: 1px solid var(--color-border);
}

/* Compact variant for smaller/denser display */
.recording-file-row-compact {
  font-size: 0.8rem;
}

.recording-file-name-compact {
  font-size: 0.8rem;
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
  line-height: 1.2;
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
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}

.recording-file-size {
  font-size: 0.75rem;
  color: var(--color-text);
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
}

.recording-file-timestamp {
  font-size: 0.75rem;
  color: var(--color-text);
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
}
</style>
