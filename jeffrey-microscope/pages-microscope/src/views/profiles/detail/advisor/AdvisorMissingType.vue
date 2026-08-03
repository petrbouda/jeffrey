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

<!--
  What an Advisor page shows when you pick a type this recording carries no samples for. "No samples"
  on its own is a dead end, so the card answers the question it provokes: the profiler command that
  captures this type, with the missing flag as the only highlighted thing on it.
-->
<template>
  <MainCard>
    <template #header>
      <MainCardHeader :icon="eventTypeStyle(eventType).icon" :title="label">
        <template #actions>
          <Badge value="Not recorded" variant="grey" size="xs" />
        </template>
      </MainCardHeader>
    </template>

    <div class="missing">
      <p>
        This recording carries no <b>{{ label }}</b> samples, so there is no {{ missingArtifact }}.
        <template v-if="description">{{ label }} measures {{ description }}.</template>
      </p>

      <span class="missing-label">Add it to the profiler command</span>
      <code class="missing-command"
        ><span>{{ commandPrefix }}</span
        ><mark>{{ captureFlag }}</mark
        ><span>{{ COMMAND_SUFFIX }}</span></code
      >

      <button type="button" class="copy-btn" @click="copy">
        <i class="bi" :class="copied ? 'bi-check-lg' : 'bi-clipboard'"></i>
        {{ copied ? 'Copied' : 'Copy command' }}
      </button>
    </div>
  </MainCard>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import Badge from '@shared/components/Badge.vue';
import ToastService from '@shared/services/ToastService';
import {
  eventTypeCaptureFlag,
  eventTypeDescription,
  eventTypeStyle
} from '@/views/profiles/detail/advisor/eventTypeStyle';

const props = defineProps<{
  eventType: string;
  label: string;
  /** What this page would have held for the type — "the prompt", "a report", "a diff". */
  missingArtifact: string;
}>();

const COPIED_FEEDBACK_MS = 1500;

// The command the Profiler Settings page builds, with this type's flag spliced into it.
const COMMAND_PREFIX = '-agentpath:/path/to/libasyncProfiler.so=start,event=ctimer,';
const COMMAND_SUFFIX = ',loop=15m,file=profile.jfr';

const captureFlag = computed(() => eventTypeCaptureFlag(props.eventType));
const description = computed(() => eventTypeDescription(props.eventType));

/**
 * CPU is captured by the engine the command already names, so its flag is the prefix's own — showing
 * `event=ctimer` twice would read as a command that does not work.
 */
const commandPrefix = computed(() =>
  COMMAND_PREFIX.endsWith(captureFlag.value + ',')
    ? COMMAND_PREFIX.slice(0, -(captureFlag.value.length + 1))
    : COMMAND_PREFIX
);

const command = computed(() => commandPrefix.value + captureFlag.value + COMMAND_SUFFIX);

const copied = ref(false);

async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(command.value);
    copied.value = true;
    setTimeout(() => {
      copied.value = false;
    }, COPIED_FEEDBACK_MS);
  } catch {
    ToastService.error('Profiler command', 'The browser blocked access to the clipboard');
  }
}
</script>

<style scoped>
.missing {
  padding: 1rem;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.missing p {
  margin: 0 0 0.9rem;
  font-size: 0.85rem;
  line-height: 1.6;
  color: var(--color-text-muted);
}

.missing p b {
  color: var(--color-heading-dark);
  font-weight: 600;
}

.missing-label {
  display: block;
  font-size: 0.64rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  margin-bottom: 0.4rem;
}

.missing-command {
  display: block;
  align-self: stretch;
  font-family: var(--font-family-monospace);
  font-size: 0.72rem;
  line-height: 1.7;
  color: var(--color-heading-dark);
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 0.6rem 0.7rem;
  overflow-x: auto;
  white-space: pre;
}

.missing-command mark {
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-weight: 600;
  border-radius: var(--radius-sm);
  padding: 0.05rem 0.2rem;
}

.copy-btn {
  appearance: none;
  cursor: pointer;
  font-family: inherit;
  font-size: 0.74rem;
  font-weight: 600;
  border-radius: var(--radius-base);
  border: 1px solid var(--color-primary-border-light);
  background: transparent;
  color: var(--color-primary);
  padding: 0.32rem 0.65rem;
  margin-top: 0.9rem;
  display: inline-flex;
  align-items: center;
  gap: 0.32rem;
  transition: background 0.15s;
}

.copy-btn:hover {
  background: var(--color-primary-light);
}
</style>
