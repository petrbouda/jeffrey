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
  <div v-if="claims.length > 0" class="claims">
    <div class="claims-head">
      <span class="claims-title">Findings checked against the profile</span>
      <Badge
        :value="`${groundedCount} of ${claims.length} grounded`"
        :variant="groundedCount === claims.length ? 'success' : 'warning'"
        size="xs"
        :uppercase="false"
      />
    </div>

    <div
      v-for="(claim, index) in claims"
      :key="`${claim.citedFrame}-${index}`"
      class="claim"
      :class="{ ungrounded: !claim.grounded }"
    >
      <div class="claim-top">
        <span class="claim-title">{{ claim.title }}</span>
        <Badge
          v-if="claim.grounded"
          :value="`${claim.selfPct.toFixed(1)}% self`"
          variant="primary"
          size="xs"
          :uppercase="false"
          borderless
        />
        <Badge
          :value="claim.grounded ? 'Grounded' : 'Unverified'"
          :variant="claim.grounded ? 'success' : 'warning'"
          size="xs"
          :uppercase="false"
        />
      </div>

      <div class="claim-links">
        <span class="claim-link">
          <i class="bi bi-fire"></i>
          <code v-if="claim.grounded">{{ claim.citedFrame }}</code>
          <span v-else class="missing">{{ claim.citedFrame }} — not present in this profile</span>
        </span>
        <span v-if="claim.sourcePath" class="claim-link">
          <i class="bi bi-file-earmark-code"></i>
          <code :class="{ missing: !claim.sourceFound }">{{ claim.sourcePath }}</code>
          <span v-if="!claim.sourceFound" class="missing">— not found in the checkout</span>
        </span>
      </div>
    </div>

    <p v-if="groundedCount < claims.length" class="claims-note">
      Unverified findings are excluded from the severity grade, so a hotspot the model could not
      substantiate cannot raise this recording's priority.
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Badge from '@shared/components/Badge.vue';
import type { AdvisorClaim } from '@/services/api/model/Advisor';

const props = defineProps<{ claims: AdvisorClaim[] }>();

const groundedCount = computed(() => props.claims.filter(claim => claim.grounded).length);
</script>

<style scoped>
.claims {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border-bottom: 1px solid var(--color-border-light);
}

.claims-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 2px;
}

.claims-title {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-dark);
}

.claim {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  background: var(--color-white);
}

.claim.ungrounded {
  opacity: 0.75;
  background: var(--color-light);
}

.claim-top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.claim-title {
  flex: 1 1 200px;
  font-size: 0.83rem;
  font-weight: 600;
  color: var(--color-dark);
}

.claim-links {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  font-size: 0.73rem;
  color: var(--color-text-muted);
}

.claim-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
}

.claim-link code {
  font-size: 0.95em;
  color: var(--color-primary);
  word-break: break-all;
}

.missing {
  color: var(--color-warning);
}

.claims-note {
  margin: 2px 0 0;
  font-size: 0.72rem;
  line-height: 1.45;
  color: var(--color-text-muted);
}
</style>
