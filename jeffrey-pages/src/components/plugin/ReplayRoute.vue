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
import PluginNodeChip from '@/components/plugin/PluginNodeChip.vue';
import { nodeId, ownerNodeId, usePluginUsage } from '@/composables/usePluginUsage';
import type { ReplayRun, ReplayStep } from '@/types/pluginUsage';

// Where the run has gone up to the playback position: each skill or agent that took over, in order,
// who handed it the work, and the tool families its calls reached.

const props = defineProps<{ run: ReplayRun; position: number }>();
const emit = defineEmits<{ (e: 'pick', id: string): void }>();

type OwnerKind = 'skill' | 'agent' | 'main';

interface RouteStop {
  key: string;
  kind: OwnerKind;
  name: string;
  from: string | null;
  /** How many times the run handed work to it. */
  calls: number;
  families: Map<string, number>;
}

const MAIN = 'main';
const HAND_OVER: Record<'skill' | 'agent', string> = { skill: 'invokes', agent: 'hands over to' };

const model = usePluginUsage();

function ownerKey(step: ReplayStep): string {
  return step.by ? ownerNodeId(step.by) : MAIN;
}

const stops = computed<RouteStop[]>(() => {
  const byKey = new Map<string, RouteStop>();
  const order: RouteStop[] = [];
  const stopFor = (key: string, kind: OwnerKind, name: string, from: string | null): RouteStop => {
    let stop = byKey.get(key);
    if (!stop) {
      stop = { key, kind, name, from, calls: 0, families: new Map() };
      byKey.set(key, stop);
      order.push(stop);
    }
    return stop;
  };
  for (const step of props.run.steps.slice(0, props.position + 1)) {
    if (step.kind === 'skill' || step.kind === 'agent') {
      const from = step.by ? step.by[1] : null;
      stopFor(model.currentNodeId(nodeId(step.kind, step.name)), step.kind, step.name, from).calls += 1;
    } else if (step.kind === 'jeffrey') {
      const family = model.familyOf(step.name);
      const owner = step.by ? stopFor(ownerKey(step), step.by[0], step.by[1], null) : stopFor(MAIN, 'main', 'Main conversation', null);
      if (family) {
        owner.families.set(family, (owner.families.get(family) ?? 0) + 1);
      }
    }
  }
  return order;
});

const familiesOf = (stop: RouteStop): [string, number][] => [...stop.families].sort((a, b) => b[1] - a[1]);
</script>

<template>
  <div class="route">
    <p v-if="!stops.length" class="route-empty">No skill, agent or Jeffrey call yet.</p>
    <template v-for="stop in stops" :key="stop.key">
      <div v-if="stop.from && stop.kind !== 'main'" class="route-arrow">
        <i class="bi bi-arrow-down" aria-hidden="true"></i> {{ stop.from }} {{ HAND_OVER[stop.kind] }}
      </div>
      <div class="route-stop" :class="`route-stop--${stop.kind}`">
        <button
          v-if="stop.kind !== 'main'"
          type="button"
          class="route-stop-name"
          :title="`Show ${stop.name} on the map`"
          @click="emit('pick', stop.key)"
        >
          <span class="route-stop-kind">{{ stop.kind }}</span>
          {{ stop.name }}<span v-if="stop.calls > 1" class="route-stop-times"> ×{{ stop.calls }}</span>
        </button>
        <span v-else class="route-stop-name"><span class="route-stop-kind">no skill</span>{{ stop.name }}</span>
        <div v-if="stop.families.size" class="route-families">
          <PluginNodeChip
            v-for="[family, count] in familiesOf(stop)"
            :key="family"
            kind="family"
            :label="`${family}_* ×${count}`"
            @pick="emit('pick', nodeId('family', family))"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.route {
  display: flex;
  flex-direction: column;
}

.route-empty {
  margin: 0;
  font-size: 13px;
  color: var(--site-faint);
}

.route-arrow {
  margin: 6px 0;
  font-size: 12px;
  color: var(--site-faint);
  text-align: center;
}

.route-stop {
  padding: 9px 11px;
  border: 1.5px solid var(--site-line-strong);
  border-radius: var(--site-radius-sm);
  background: var(--site-surface);
}

.route-stop--skill {
  border-color: var(--ap-skill);
  background: var(--ap-skill-bg);
}

.route-stop--agent {
  border-color: var(--ap-agent);
  background: var(--ap-agent-bg);
}

.route-stop-name {
  display: block;
  padding: 0;
  border: 0;
  background: transparent;
  font-family: var(--site-font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--site-ink);
  text-align: left;
}

button.route-stop-name {
  cursor: pointer;
}

button.route-stop-name:hover {
  text-decoration: underline;
}

.route-stop-times {
  font-weight: 400;
  color: var(--site-faint);
}

.route-stop-kind {
  display: block;
  font-size: 10.5px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--site-faint);
}

.route-stop--skill .route-stop-kind {
  color: var(--ap-skill);
}

.route-stop--agent .route-stop-kind {
  color: var(--ap-agent);
}

.route-families {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 7px;
}
</style>
