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
  Collapsible reference for the Guardian guard `matcherSpec` / `preconditions` JSON fields:
  node types, operators, traversal and the precondition keys, with real built-in examples.
  Emits `insert` so the host can drop an example into the matching textarea.
-->
<template>
  <div class="matcher-help" :class="{ collapsed: !expanded }">
    <button type="button" class="mh-head" @click="expanded = !expanded">
      <i class="bi bi-journal-code mh-icon"></i>
      <span class="mh-title">Matcher &amp; Preconditions guide</span>
      <span class="mh-sub">node types · operators · examples</span>
      <i class="bi bi-chevron-down mh-chev"></i>
    </button>

    <div v-if="expanded" class="mh-body">
      <p class="mh-h">Matcher spec shape</p>
      <JsonHighlight :value="SHAPE" />
      <p class="mh-note">
        <code>anchor</code> — the frame the matcher locks onto; its predicate tree finds it in each
        stack. <code>traversal</code> — how the measured frames are reached from the anchor
        (optional; defaults to <code>CurrentFrame</code>).
      </p>

      <p class="mh-h">Node types</p>
      <div class="mh-nodes">
        <div v-for="n in NODES" :key="n.name" class="mh-node">
          <span class="mh-nname">{{ n.name }}</span>
          <span class="mh-ndesc" v-html="n.desc"></span>
        </div>
      </div>

      <p class="mh-h">Operators (op)</p>
      <div class="mh-ops">
        <div v-for="o in OPS" :key="o.op" class="mh-op">
          <Badge :value="o.op" :variant="o.variant" size="s" :uppercase="false" />
          <span class="mh-opd">{{ o.desc }}</span>
        </div>
      </div>

      <p class="mh-h">Traversal <span class="mh-muted">(optional, defaults to CurrentFrame)</span></p>
      <div class="mh-nodes">
        <div v-for="t in TRAVERSAL" :key="t.name" class="mh-node">
          <span class="mh-nname">{{ t.name }}</span>
          <span class="mh-ndesc" v-html="t.desc"></span>
        </div>
      </div>

      <p class="mh-h">Matching mode <span class="mh-muted">— set via the Matching field</span></p>
      <div v-for="m in MATCHING" :key="m.mode" class="mh-ex">
        <div class="mh-ex-head">
          <Badge :value="m.mode" :variant="m.variant" size="s" :uppercase="false" />
          <span class="mh-ex-title">{{ m.example }}</span>
          <button type="button" class="mh-insert" @click="insertJson('matcherSpec', m.json)">
            <i class="bi bi-box-arrow-in-down"></i> Insert
          </button>
        </div>
        <p class="mh-opd mh-mdesc">{{ m.desc }}</p>
        <JsonHighlight :value="m.json" />
      </div>

      <p class="mh-h">Preconditions <span class="mh-muted">(optional gate)</span></p>
      <table class="mh-kv">
        <thead>
          <tr>
            <th>Key</th>
            <th>Meaning &amp; allowed values</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in PRECONDITIONS" :key="p.key">
            <td class="mh-k">{{ p.key }}</td>
            <td>
              {{ p.meaning }}
              <span class="mh-vals">
                <Badge v-for="v in p.values" :key="v" :value="v" variant="grey" size="s" :uppercase="false" />
              </span>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="mh-note">
        All keys are optional — omit a key (or use <code>{}</code>) to ignore it. The guard runs only
        when every present key matches the profile.
      </p>

      <p class="mh-h">Examples <span class="mh-muted">— click Insert to use one</span></p>
      <div v-for="ex in EXAMPLES" :key="ex.title" class="mh-ex">
        <div class="mh-ex-head">
          <span class="mh-ex-title">{{ ex.title }}</span>
          <Badge :value="ex.tag" :variant="ex.tagVariant" size="s" :uppercase="false" />
          <button type="button" class="mh-insert" @click="insert(ex)">
            <i class="bi bi-box-arrow-in-down"></i> Insert
          </button>
        </div>
        <JsonHighlight :value="ex.json" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import Badge from '@shared/components/Badge.vue';
import JsonHighlight from '@/components/common/JsonHighlight.vue';
import type { Variant } from '@shared/types/ui';

type GuardField = 'matcherSpec' | 'preconditions';

interface OpDoc {
  op: string;
  desc: string;
  variant: Variant;
}

interface GuardExample {
  title: string;
  tag: string;
  tagVariant: Variant;
  field: GuardField;
  json: string;
}

interface MatchingDoc {
  mode: string;
  variant: Variant;
  desc: string;
  example: string;
  json: string;
}

const emit = defineEmits<{
  insert: [payload: { field: GuardField; value: string }];
}>();

const expanded = ref(false);

const SHAPE = '{"anchor":"<MatchExpr>","traversal":"<optional>"}';

const NODES = [
  { name: 'Predicate', desc: 'Leaf test on a frame name. Fields: <code>op</code> + <code>value</code>.' },
  { name: 'AnyOf', desc: 'Logical OR — matches if any child in <code>of: [...]</code> matches.' },
  { name: 'AllOf', desc: 'Logical AND — matches only if every child in <code>of: [...]</code> matches.' },
  { name: 'Not', desc: 'Negation — matches when the single child in <code>expr</code> does not.' }
];

const OPS: OpDoc[] = [
  { op: 'PREFIX', desc: 'frame name starts with value', variant: 'info' },
  { op: 'SUFFIX', desc: 'frame name ends with value', variant: 'info' },
  { op: 'CONTAINS', desc: 'value appears anywhere in the frame name', variant: 'teal' },
  { op: 'EQUALS', desc: 'frame name is exactly value', variant: 'primary' },
  { op: 'REGEX', desc: 'frame name matches value as a Java regex', variant: 'danger' }
];

const TRAVERSAL = [
  { name: 'CurrentFrame', desc: 'Default. The matched anchor frame is the one measured. Omit <code>traversal</code> to use it.' },
  { name: 'Descend', desc: 'Walk <code>steps: [...]</code> from the anchor down to the measured frames (GC / threading guards).' },
  { name: 'ByName', desc: 'Step to a direct child by exact <code>frameName</code>.' },
  { name: 'ByMatcher', desc: 'Step via a <code>base</code> matcher, then pick the <code>target</code> frame in that subtree.' }
];

const MATCHING: MatchingDoc[] = [
  {
    mode: 'FULL_MATCH',
    variant: 'success',
    desc: 'Keep traversing and sum every matching frame in the tree. Use for application code that can appear in many different stacks.',
    example: 'Built-in: Logback CPU overhead',
    json: '{"anchor":{"type":"Predicate","op":"PREFIX","value":"ch.qos.logback"}}'
  },
  {
    mode: 'SINGLE_MATCH',
    variant: 'warning',
    desc: 'Stop at the first match and count a single occurrence. Use for JVM-internal work that runs in one dedicated thread / call site (JIT, GC, safepoints).',
    example: 'Built-in: VM Operation overhead',
    json: '{"anchor":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"}}'
  }
];

const PRECONDITIONS = [
  { key: 'eventSource', meaning: 'Recording source.', values: ['ASYNC_PROFILER', 'JDK', 'UNKNOWN', 'HEAP_DUMP'] },
  { key: 'garbageCollectorType', meaning: 'GC the profile must use.', values: ['SERIAL', 'PARALLEL', 'G1', 'Z', 'ZGENERATIONAL', 'SHENANDOAH'] },
  { key: 'debugSymbolsAvailable', meaning: 'Java debug symbols present.', values: ['true', 'false'] },
  { key: 'kernelSymbolsAvailable', meaning: 'Kernel symbols present.', values: ['true', 'false'] }
];

const EXAMPLES: GuardExample[] = [
  {
    title: 'Simple Predicate — Logback CPU',
    tag: 'PREFIX',
    tagVariant: 'info',
    field: 'matcherSpec',
    json: '{"anchor":{"type":"Predicate","op":"PREFIX","value":"ch.qos.logback"}}'
  },
  {
    title: 'AnyOf — exception overhead',
    tag: 'AnyOf',
    tagVariant: 'primary',
    field: 'matcherSpec',
    json: '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"SUFFIX","value":"Throwable#<init>"},{"type":"Predicate","op":"SUFFIX","value":"Throwable#fillInStackTrace"}]}}'
  },
  {
    title: 'AnyOf + EQUALS — JIT threads',
    tag: 'EQUALS',
    tagVariant: 'primary',
    field: 'matcherSpec',
    json: '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"EQUALS","value":"JavaThread::thread_main_inner"},{"type":"Predicate","op":"EQUALS","value":"CompileBroker::compiler_thread_loop"}]}}'
  },
  {
    title: 'Not — exclude a namespace',
    tag: 'Not',
    tagVariant: 'violet',
    field: 'matcherSpec',
    json: '{"anchor":{"type":"Not","expr":{"type":"Predicate","op":"PREFIX","value":"java.lang."}}}'
  },
  {
    title: 'Descend — Parallel GC',
    tag: 'Descend',
    tagVariant: 'teal',
    field: 'matcherSpec',
    json: '{"anchor":{"type":"Predicate","op":"EQUALS","value":"Thread::call_run"},"traversal":{"type":"Descend","steps":[{"type":"ByName","frameName":"WorkerThread::run"},{"type":"ByMatcher","base":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"},"target":{"type":"Predicate","op":"PREFIX","value":"VM_ParallelGC"}}]}}'
  },
  {
    title: 'Preconditions — async-profiler + G1',
    tag: 'gate',
    tagVariant: 'grey',
    field: 'preconditions',
    json: '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"G1"}'
  }
];

// Pretty-print so the inserted value matches how the textarea renders edited JSON.
function insertJson(field: GuardField, json: string): void {
  emit('insert', { field, value: JSON.stringify(JSON.parse(json), null, 2) });
}

function insert(ex: GuardExample): void {
  insertJson(ex.field, ex.json);
}
</script>

<style scoped>
.matcher-help {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.mh-head {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 10px 14px;
  border: none;
  background: var(--color-light);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: background var(--transition-base);
}

.mh-head:hover {
  background: var(--color-lighter);
}

.mh-icon {
  color: var(--color-primary);
}

.mh-title {
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
  font-size: 0.82rem;
}

.mh-sub {
  font-size: 0.72rem;
  color: var(--color-text-muted);
}

.mh-chev {
  margin-left: auto;
  color: var(--color-text-muted);
  transition: transform var(--transition-base);
}

.matcher-help.collapsed .mh-chev {
  transform: rotate(-90deg);
}

.mh-body {
  padding: 14px;
}

.mh-h {
  font-size: 0.7rem;
  font-weight: var(--font-weight-semibold);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-muted);
  margin: 0 0 8px;
}

.mh-h:not(:first-child) {
  margin-top: 18px;
}

.mh-muted {
  font-weight: var(--font-weight-normal);
  text-transform: none;
  letter-spacing: 0;
  color: var(--color-text-light);
}

.mh-nodes {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mh-node {
  display: flex;
  gap: 10px;
  align-items: baseline;
  font-size: 0.78rem;
}

.mh-nname {
  flex: 0 0 92px;
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
}

.mh-ndesc {
  color: var(--color-text);
  line-height: 1.5;
}

.mh-ndesc :deep(code) {
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  font-size: 0.72rem;
  background: var(--color-light);
  padding: 0 4px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  color: var(--color-primary-hover);
}

.mh-op {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 0.76rem;
  margin-bottom: 6px;
}

.mh-opd {
  color: var(--color-text-muted);
}

.mh-mdesc {
  margin: 0 0 5px;
  line-height: 1.5;
}

.mh-kv {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.76rem;
}

.mh-kv th,
.mh-kv td {
  text-align: left;
  padding: 6px 8px;
  border-bottom: 1px solid var(--color-border);
  vertical-align: top;
}

.mh-kv th {
  font-size: 0.66rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
  font-weight: var(--font-weight-semibold);
}

.mh-k {
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  color: var(--color-dark);
  white-space: nowrap;
}

.mh-vals {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.mh-note {
  font-size: 0.72rem;
  color: var(--color-text-muted);
  line-height: 1.5;
  margin: 8px 0 0;
}

.mh-note code {
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  font-size: 0.72rem;
  background: var(--color-light);
  padding: 0 4px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
}

.mh-ex {
  margin-bottom: 12px;
}

.mh-ex-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
}

.mh-ex-title {
  font-size: 0.76rem;
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
}

.mh-insert {
  margin-left: auto;
  border: 1px solid var(--color-border-input);
  background: var(--color-white);
  color: var(--color-primary);
  border-radius: var(--radius-sm);
  font-family: inherit;
  font-size: 0.68rem;
  font-weight: var(--font-weight-semibold);
  padding: 3px 9px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: all var(--transition-base);
}

.mh-insert:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-lighter);
}
</style>
