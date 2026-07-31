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
  <div v-if="checks.length > 0" class="verify" :class="barTone">
    <div
      v-for="check in checks"
      :key="check.level"
      class="verify-item"
      :class="`is-${check.status.toLowerCase()}`"
      :title="check.detail ?? undefined"
    >
      <span class="dot"></span>
      <span class="verify-label">{{ levelLabel(check.level) }}</span>
      <span v-if="check.status !== 'PASSED'" class="verify-note">{{ statusNote(check) }}</span>
    </div>

    <div class="verify-ref">
      <i class="bi bi-git"></i>
      <span v-if="verifiedAgainstRef">verified against {{ shortRef }}</span>
      <span v-else>default branch — commit unknown</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type PatchVerification from '@/services/api/model/PatchVerification';
import type { PatchCheck, PatchCheckLevel } from '@/services/api/model/PatchVerification';

const props = defineProps<{ verification: PatchVerification | null }>();

const SHORT_REF_LENGTH = 8;

const LEVEL_LABELS: Record<PatchCheckLevel, string> = {
  APPLIES: 'Applies cleanly',
  COMPILES: 'Compiles',
  TESTS_PASS: 'Tests pass'
};

const checks = computed<PatchCheck[]>(() => props.verification?.checks ?? []);

const verifiedAgainstRef = computed(() => props.verification?.verifiedAgainstRef ?? null);

const shortRef = computed(() => verifiedAgainstRef.value?.slice(0, SHORT_REF_LENGTH) ?? '');

/**
 * The bar takes its tone from the worst outcome, so a failed apply reads as a warning at a glance
 * rather than needing the individual chips to be parsed.
 */
const barTone = computed(() => {
  if (checks.value.some(check => check.status === 'FAILED')) {
    return 'tone-failed';
  }
  if (checks.value.some(check => check.status === 'PASSED')) {
    return 'tone-passed';
  }
  return 'tone-skipped';
});

const levelLabel = (level: PatchCheckLevel): string => LEVEL_LABELS[level];

const statusNote = (check: PatchCheck): string => {
  if (check.status === 'FAILED') {
    return 'failed';
  }
  return check.detail ?? 'not checked';
};
</script>

<style scoped>
.verify {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0;
  border-bottom: 1px solid var(--color-border-light);
  background: var(--color-light);
}

.verify.tone-passed {
  background: color-mix(in srgb, var(--color-success) 8%, var(--color-white));
}

.verify.tone-failed {
  background: color-mix(in srgb, var(--color-danger) 8%, var(--color-white));
}

.verify-item {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 14px;
  font-size: 0.78rem;
  border-right: 1px solid var(--color-border-light);
  color: var(--color-text);
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-text-light);
}

.is-passed .dot {
  background: var(--color-success);
}

.is-failed .dot {
  background: var(--color-danger);
}

.is-skipped .dot {
  background: var(--color-text-light);
  opacity: 0.6;
}

.is-skipped .verify-label {
  color: var(--color-text-muted);
}

.verify-note {
  font-size: 0.72rem;
  color: var(--color-text-muted);
}

.verify-ref {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  padding: 9px 14px;
  font-size: 0.72rem;
  color: var(--color-text-muted);
}
</style>
