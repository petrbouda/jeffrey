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
  Why the Flamegraph tab has nothing to show, explained by example rather than by rule: one real
  trace, with the samples appearing the moment the work leaves the request's virtual thread. The
  contrast does the teaching, so the prose underneath only has to name it.
-->
<template>
  <div class="vt-notice">
    <div class="vt-head">
      <span class="vt-head-icon"><i class="bi bi-diagram-2"></i></span>
      <div>
        <h4>{{ heading }}</h4>
        <p class="vt-head-reason">{{ reason }}</p>
      </div>
    </div>

    <div class="vt-exhibit">
      <div class="vt-exhibit-head">
        <span class="vt-eyebrow">Example — where samples land in one trace</span>
        <span class="vt-exhibit-source">{{ EXAMPLE_TRACE }}</span>
      </div>

      <div class="vt-exhibit-body">
        <table class="vt-table">
          <thead>
            <tr>
              <th scope="col">Span</th>
              <th scope="col">Thread</th>
              <th scope="col"><span class="visually-hidden">Thread kind</span></th>
              <th scope="col" class="num">Duration</th>
              <th scope="col" class="num">Samples</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, index) in EXAMPLE_ROWS"
              :key="index"
              :class="{ platform: !row.virtual }"
            >
              <td :class="`indent-${row.depth}`">{{ row.span }}</td>
              <td>{{ row.thread }}</td>
              <td>
                <span class="vt-kind" :class="row.virtual ? 'virtual' : 'platform'">
                  {{ row.virtual ? 'virtual' : 'platform' }}
                </span>
              </td>
              <td class="num">{{ row.duration }}</td>
              <td class="num" :class="row.samples === 0 ? 'zero' : 'hit'">{{ row.samples }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <p class="vt-verdict">
      <strong>Samples appear the moment the work leaves the virtual thread.</strong>
      Same trace, same recording — only the thread differs.
    </p>

    <ul class="vt-fixes">
      <li>
        Fork the part you care about with <code>Tracer.continueIn(…)</code>, the way
        <code>chunk.parse</code> does above. That span then carries the samples.
      </li>
      <li>
        Or drop <code>event=ctimer</code> from the
        <router-link to="/docs/microscope/profiler-settings">profiler settings</router-link> and
        record <code>jdk.CPUTimeSample</code> instead (JDK 25+, Linux). The JDK's own CPU-time
        sampler names the virtual thread and walks its continuation stack: in a JDK 26 recording of
        this app it put 5,235 samples on <code>tomcat-handler-*</code> virtual threads, where
        async-profiler marked none of its 154,481 samples virtual and abandoned 4,882 stacks at the
        continuation return barrier. The cost is async-profiler's better CPU engine. Allocation and
        wall-clock samples stay on the carrier either way.
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

/** One row of the worked example: a span, the thread it ran on, and what matched it. */
interface ExampleRow {
  span: string;
  thread: string;
  virtual: boolean;
  duration: string;
  samples: number;
  depth: number;
}

const EXAMPLE_TRACE = 'POST /api/internal/recordings/…/analyze';

/*
 * Measured, not invented: a recording upload and the parse it triggers, as Jeffrey recorded itself.
 * The parse forks onto pool threads, which is exactly where the samples start matching.
 */
const EXAMPLE_ROWS: ExampleRow[] = [
  {
    span: 'POST /api/…/analyze',
    thread: 'tomcat-handler-53',
    virtual: true,
    duration: '4173ms',
    samples: 0,
    depth: 0
  },
  {
    span: 'profile.initialize',
    thread: 'tomcat-handler-53',
    virtual: true,
    duration: '4102ms',
    samples: 0,
    depth: 1
  },
  {
    span: 'recording.parse',
    thread: 'tomcat-handler-53',
    virtual: true,
    duration: '2797ms',
    samples: 0,
    depth: 2
  },
  {
    span: 'chunk.parse',
    thread: 'bulk-parallel',
    virtual: false,
    duration: '2730ms',
    samples: 153,
    depth: 3
  },
  {
    span: 'chunk.parse',
    thread: 'bulk-parallel',
    virtual: false,
    duration: '2571ms',
    samples: 140,
    depth: 3
  },
  {
    span: 'profile.data-init',
    thread: 'tomcat-handler-53',
    virtual: true,
    duration: '933ms',
    samples: 0,
    depth: 2
  },
  {
    span: 'guardian.results',
    thread: 'parallel',
    virtual: false,
    duration: '932ms',
    samples: 63,
    depth: 3
  }
];

const props = defineProps<{
  /** What the reader opened: a single span, or every trace of an operation. */
  scope: 'span' | 'operation';
}>();

// The header above already names the span or operation, so the note does not repeat it.
const heading = computed(() =>
  props.scope === 'span'
    ? 'No flamegraph can be drawn for this span'
    : 'No flamegraph can be drawn for this operation'
);

const reason = computed(() =>
  props.scope === 'span'
    ? 'It ran on a virtual thread, and the profiler attributes every sample to the carrier thread underneath. Samples are matched by thread, so none of them belong to this span.'
    : 'Its spans ran on a virtual thread, and the profiler attributes every sample to the carrier thread underneath. Samples are matched by thread, so none of them belong to these spans.'
);
</script>

<style scoped>
.vt-notice {
  padding: 0.25rem 0 0;
}

.vt-head {
  display: flex;
  gap: 0.75rem;
  align-items: flex-start;
  margin-bottom: 1.1rem;
}

.vt-head-icon {
  flex: none;
  width: 2.1rem;
  height: 2.1rem;
  display: grid;
  place-items: center;
  border-radius: var(--radius-circle);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: 1rem;
}

.vt-head h4 {
  margin: 0 0 0.2rem;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
}

.vt-head-reason {
  margin: 0;
  max-width: 74ch;
  font-size: var(--font-size-base);
  line-height: 1.6;
  color: var(--color-text);
}

.vt-exhibit {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  overflow: hidden;
}

.vt-exhibit-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 1rem;
  padding: 0.6rem 0.85rem;
  background: var(--color-bg-hover);
  border-bottom: 1px solid var(--color-border);
}

.vt-eyebrow {
  font-size: 0.65rem;
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.vt-exhibit-source {
  font-family: var(--font-family-monospace);
  font-size: 0.7rem;
  color: var(--color-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* The table keeps its columns; the panel scrolls rather than the page. */
.vt-exhibit-body {
  overflow-x: auto;
  padding: 0.7rem 0.25rem;
}

.vt-table {
  width: 100%;
  border-collapse: collapse;
  font-family: var(--font-family-monospace);
  font-size: 0.72rem;
}

.vt-table th {
  padding: 0 0.6rem 0.4rem;
  text-align: left;
  font-family: var(--font-family-base);
  font-size: 0.6rem;
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  border-bottom: 1px solid var(--color-border);
}

.vt-table td {
  padding: 0.3rem 0.6rem;
  white-space: nowrap;
  color: var(--color-text);
}

.vt-table .num {
  text-align: right;
}

/*
 * The banded rows are the point of the example. The kind column says "platform" in words too, so
 * the reading does not depend on the colour.
 */
.vt-table tr.platform td {
  background: var(--color-success-light);
}

.vt-table tr.platform td:first-child {
  /* A left rule marking the platform row, drawn as an inset shadow so it costs no layout. */
  border-left: 3px solid var(--color-success);
}

.vt-table .hit {
  color: var(--color-success-dark);
  font-weight: var(--font-weight-semibold);
}

.vt-table .zero {
  color: var(--color-text-muted);
}

/* Qualified by the table, or `.vt-table td`'s own padding wins and the tree renders flat. */
.vt-table td.indent-1 {
  padding-left: 1.6rem;
}
.vt-table td.indent-2 {
  padding-left: 2.6rem;
}
.vt-table td.indent-3 {
  padding-left: 3.6rem;
}

.vt-kind {
  font-size: 0.62rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.vt-kind.virtual {
  color: var(--color-secondary);
}

.vt-kind.platform {
  color: var(--color-success-dark);
}

.vt-verdict {
  margin: 1rem 0 0;
  max-width: 72ch;
  font-size: var(--font-size-base);
  line-height: 1.6;
  color: var(--color-text);
}

.vt-verdict strong {
  color: var(--color-dark);
  font-weight: var(--font-weight-semibold);
}

.vt-fixes {
  margin: 0.9rem 0 0;
  padding-left: 1.1rem;
  max-width: 72ch;
  font-size: var(--font-size-base);
  line-height: 1.6;
}

.vt-fixes li + li {
  margin-top: 0.35rem;
}

.vt-notice code {
  font-family: var(--font-family-monospace);
  font-size: 0.78rem;
  color: var(--color-primary);
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}
</style>
