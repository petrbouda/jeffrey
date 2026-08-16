<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<!--
  The "see it in its surroundings" edge, worn as a detail chip: any row that knows when its subject
  ran and for how long can offer the unified timeline zoomed to that padded moment. Clicks stop
  here — several host rows are themselves clickable, and opening a modal was never what this meant.
-->
<template>
  <router-link
    class="show-on-timeline"
    :to="link"
    title="Open the unified timeline zoomed to this moment"
    @click.stop
  >
    <i class="bi bi-distribute-horizontal"></i> Timeline
  </router-link>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { timelineWindowLink } from '@/services/timeline/timelineLink';

const props = defineProps<{
  /** When the subject started, absolute epoch millis — the unit every slow-row DTO carries. */
  startEpochMillis: number;
  durationNanos: number;
}>();

// From the route rather than a prop: every host view already lives under /profiles/:profileId,
// and threading the id through six otherwise-uninterested components taught none of them anything.
const route = useRoute();

const link = computed(() =>
  timelineWindowLink(route.params.profileId as string, props.startEpochMillis, props.durationNanos)
);
</script>

<style scoped>
.show-on-timeline {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--color-primary);
  text-decoration: none;
  white-space: nowrap;
}

.show-on-timeline:hover {
  text-decoration: underline;
}

.show-on-timeline:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
  border-radius: var(--radius-xs);
}

.show-on-timeline i {
  font-size: 0.6rem;
}
</style>
