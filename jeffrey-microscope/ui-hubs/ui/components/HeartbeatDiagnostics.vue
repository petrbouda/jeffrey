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

<script setup lang="ts">
import { computed } from 'vue';
import RecordingSession from '@hubs/services/api/model/RecordingSession.ts';
import FormattingService from '@shared/services/FormattingService.ts';
import {
  AGENT_JAR_FILE,
  diagnoseMissingHeartbeat,
  HEARTBEAT_FILE,
  HEARTBEAT_GRACE_LABEL,
  VERDICT_COPY
} from '@hubs/services/heartbeatDiagnosis.ts';

/**
 * Explains a session that never reported a heartbeat: what the hub found in its directory on the
 * left, and the most likely cause with what to change on the right.
 */
const props = defineProps<{
  session: RecordingSession;
}>();

interface FindingRow {
  key: string;
  ok: boolean;
  text: string;
  code?: string;
  detail?: string;
}

const diagnosis = computed(() => diagnoseMissingHeartbeat(props.session));

const verdictCopy = computed(() => VERDICT_COPY[diagnosis.value.verdict]);

const findings = computed<FindingRow[]>(() => {
  const d = diagnosis.value;
  const rows: FindingRow[] = [
    {
      key: 'registered',
      ok: true,
      text: 'Session registered by the Provisioner',
      detail: FormattingService.formatTimestampUTC(d.registeredAt)
    },
    d.agentPresent
      ? { key: 'agent', ok: true, text: 'Jeffrey Agent written to the session', code: AGENT_JAR_FILE }
      : { key: 'agent', ok: false, text: 'No Jeffrey Agent in the session', code: AGENT_JAR_FILE },
    {
      key: 'heartbeat',
      ok: false,
      text: 'No heartbeat file',
      code: HEARTBEAT_FILE,
      detail: `${HEARTBEAT_GRACE_LABEL} grace passed`
    }
  ];

  if (d.recordingFileCount > 0) {
    const noun = d.recordingFileCount === 1 ? 'recording file' : 'recording files';
    rows.push({
      key: 'recordings',
      ok: true,
      text: `${d.recordingFileCount} ${noun}, last at ${FormattingService.formatTimestampUTC(d.lastRecordingFileAt)}`
    });
  } else {
    rows.push({ key: 'recordings', ok: false, text: 'No recording files from the profiler' });
  }
  return rows;
});
</script>

<template>
  <div class="hb-diag" @click.stop>
    <div class="hb-diag-findings">
      <div class="hb-diag-title">
        <i class="bi bi-heartbreak"></i>
        What Jeffrey found in the session directory
      </div>
      <ul class="hb-checklist">
        <li
          v-for="row in findings"
          :key="row.key"
          class="hb-check"
          :class="row.ok ? 'hb-check--ok' : 'hb-check--fail'"
        >
          <span class="hb-check-mark" :aria-label="row.ok ? 'found' : 'missing'">
            <i class="bi" :class="row.ok ? 'bi-check-lg' : 'bi-x-lg'"></i>
          </span>
          <span class="hb-check-text">
            {{ row.text }}
            <code v-if="row.code" class="hb-code">{{ row.code }}</code>
            <span v-if="row.detail" class="hb-check-detail">· {{ row.detail }}</span>
          </span>
        </li>
      </ul>
    </div>

    <div class="hb-verdict">
      <div class="hb-verdict-headline">
        <i class="bi bi-lightbulb"></i>
        <span>{{ verdictCopy.headline }}</span>
      </div>
      <p class="hb-verdict-advice">
        <template v-for="(segment, idx) in verdictCopy.advice" :key="idx">
          <code v-if="segment.code" class="hb-code">{{ segment.text }}</code>
          <template v-else>{{ segment.text }}</template>
        </template>
      </p>
    </div>
  </div>
</template>

<style scoped>
.hb-diag {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: var(--spacing-3);
  margin: 0 var(--spacing-3) var(--spacing-3);
  padding: var(--spacing-3);
  background: var(--color-warning-bg);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-md);
  color: var(--color-warning-text);
  font-size: 0.78rem;
  line-height: 1.45;
  cursor: default;
}

@media (max-width: 992px) {
  .hb-diag {
    grid-template-columns: minmax(0, 1fr);
  }
}

.hb-diag-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  margin-bottom: var(--spacing-2);
  font-weight: 700;
  color: var(--color-amber-text);
}

.hb-diag-title .bi {
  color: var(--color-purple-text);
}

.hb-checklist {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-1);
}

.hb-check {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-2);
}

.hb-check-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border-radius: var(--radius-circle);
  font-size: 0.7rem;
  margin-top: 1px;
}

.hb-check--ok .hb-check-mark {
  background: var(--color-success-100);
  color: var(--color-success-dark);
}

.hb-check--fail .hb-check-mark {
  background: var(--color-danger-100);
  color: var(--color-danger-dark);
}

.hb-check-text {
  color: var(--color-dark);
  min-width: 0;
  word-break: break-word;
}

.hb-check--fail .hb-check-text {
  color: var(--color-danger-dark);
}

.hb-check-detail {
  color: var(--color-text-muted);
}

.hb-code {
  font-family: var(--font-family-monospace);
  font-size: 0.72rem;
  padding: 0 var(--spacing-1);
  border-radius: var(--radius-sm);
  background: var(--color-bg-card);
  border: 1px solid var(--color-warning-border);
  color: var(--color-amber-dark);
  word-break: break-all;
}

.hb-verdict {
  padding: var(--spacing-3);
  background: var(--color-bg-card);
  border: 1px solid var(--color-amber-border);
  border-radius: var(--radius-base);
}

.hb-verdict-headline {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-2);
  font-weight: 700;
  color: var(--color-amber-dark);
  margin-bottom: var(--spacing-1);
}

.hb-verdict-headline .bi {
  color: var(--color-amber-highlight);
  margin-top: 1px;
}

.hb-verdict-advice {
  margin: 0;
  color: var(--color-text);
}
</style>
