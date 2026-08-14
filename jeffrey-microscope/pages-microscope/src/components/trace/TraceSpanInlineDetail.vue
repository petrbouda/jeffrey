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

<template>
  <!--
    Opens inside the waterfall, directly under the row it belongs to and indented to the same depth,
    so the bar that was clicked stays in view. A panel below the bars would push it off a tall trace.

    Two views, kept visibly apart. The span's own facts come first; what the event underneath it
    recorded sits in a region of its own, because a statement's `sql` is schema the event declares,
    not something a developer attached by hand.
  -->
  <div class="span-detail" :style="{ paddingLeft: indentRem(span.depth) + 2.4 + 'rem' }">
    <div class="sd-head">
      <Badge :variant="kindVariant" size="xs" :value="span.kind" />
      <Badge
        v-if="span.status === 'ERROR'"
        variant="danger"
        size="xs"
        :value="span.errorType ?? 'error'"
      />
      <p class="sd-vitals">
        <strong>{{ FormattingService.formatDuration2Units(span.durationNanos) }}</strong>
      </p>
      <div class="sd-actions">
        <button type="button" class="sd-btn" @click="$emit('viewEvents')">
          <i class="bi bi-list-ul"></i> Events in span
        </button>
        <button type="button" class="sd-btn" @click="$emit('viewFlamegraph')">
          <i class="bi bi-fire"></i> Flamegraph
        </button>
      </div>
    </div>

    <div class="sd-body">
      <div class="sd-cols">
        <section class="sd-block">
          <h4 class="sd-label"><i class="sd-dot"></i> Span · timing</h4>
          <!--
            Where the span's time went, drawn in the waterfall's own two greens so the bar means the
            same thing here as it does in the row above. A table of durations could not show that a
            two-minute span spent 0.2% of it doing its own work; a sliver can.
          -->
          <div class="sd-meter">
            <div class="sd-bar" :title="meterTitle">
              <i :style="{ width: selfPercentOfSpan + '%' }"></i>
            </div>

            <div class="sd-legend">
              <span class="sd-leg">
                <i class="sd-sw self"></i>
                <b>{{ FormattingService.formatDuration2Units(span.selfDurationNanos) }}</b>
                <span>{{ selfLegend }}</span>
              </span>
              <span v-if="childrenNanos > 0" class="sd-leg">
                <i class="sd-sw children"></i>
                <b>{{ FormattingService.formatDuration2Units(childrenNanos) }}</b>
                <span>in children · {{ percent(childrenNanos, span.durationNanos) }}</span>
              </span>
            </div>

            <p class="sd-foot">
              <span>{{ shape }}</span>
              <span>started <b>{{ startedAt }}</b> into the recording</span>
            </p>
          </div>
        </section>

        <section class="sd-block">
          <h4 class="sd-label"><i class="sd-dot"></i> Span · identity</h4>
          <table class="sd-table">
            <tbody>
              <tr>
                <td class="sd-k">span id</td>
                <td class="sd-v">{{ span.spanId }}</td>
              </tr>
              <tr>
                <td class="sd-k">parent</td>
                <td class="sd-v" :class="{ absent: span.parentSpanId === null }">
                  {{ span.parentSpanId ?? 'none — trace root' }}
                </td>
              </tr>
              <tr>
                <td class="sd-k">thread</td>
                <td class="sd-v">{{ span.threadName ?? span.threadHash }}</td>
              </tr>
              <tr>
                <td class="sd-k">source event</td>
                <td class="sd-v">{{ span.eventType }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>

      <!--
        The attribute map gets the same region treatment the event fields get, in the span's own
        colour rather than the event's: the panel keeps one shape for every span while still saying
        which of the two things is on screen.
      -->
      <section v-if="detail.attributes.length > 0" class="sd-region">
        <header>
          Attributes
          <span class="sd-src">TraceSpanEvent.attributes · {{ keyCount(detail.attributes) }}</span>
        </header>
        <div class="sd-region-body">
          <table class="sd-table is-attr">
            <tbody>
              <tr v-for="row in detail.attributes" :key="row.key">
                <td class="sd-k">{{ row.label }}</td>
                <td class="sd-v" :class="{ absent: row.absent }">{{ row.value }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="hasEventView" class="sd-region is-evt">
        <header>
          Event fields
          <span class="sd-src">{{ span.eventType }}</span>
        </header>
        <div class="sd-region-body">
          <pre v-if="detail.sql" class="sd-sql">{{ detail.sql }}</pre>
          <table v-if="detail.eventFields.length > 0" class="sd-table">
            <tbody>
              <tr v-for="row in detail.eventFields" :key="row.key">
                <td class="sd-k" :title="row.description ?? undefined">
                  {{ row.label }}
                  <small v-if="row.label !== row.key">{{ row.key }}</small>
                </td>
                <td class="sd-v" :class="{ absent: row.absent }">{{ row.value }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!--
        Said plainly rather than left blank: a hand-written span that passed no attributes is the
        ordinary case, not a sign that the panel failed to load.
      -->
      <p v-if="!hasEventView && detail.attributes.length === 0" class="sd-none">
        No attributes recorded. Pass a map to <code>Tracer</code> at the call site to see it here.
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { NANOS_PER_MILLI } from '@/services/trace/timeUnits';
import { computed } from 'vue';
import Badge from '@shared/components/Badge.vue';
import FormattingService from '@shared/services/FormattingService';
import type { EventFieldRow, TraceSpanRow } from '@/services/api/model/trace/TraceModels';
import { spanKindVariant } from '@/services/trace/traceLabels';
import type { SpanDetailRow } from '@/services/trace/spanAttributes';
import { spanDetail } from '@/services/trace/spanAttributes';
import { indentRem } from '@/services/trace/TraceWaterfallLayout';


/** Below this, a share rounds to 0% and the number itself says more than the percentage. */
const MIN_REPORTED_PERCENT = 0.1;

const props = defineProps<{
  span: TraceSpanRow;
  /** How the recording described this event type's fields; empty when it described none. */
  fields: EventFieldRow[];
  /**
   * How many spans hang off this one. Needed to word the meter honestly: self time only subtracts
   * children that ran on this span's own thread, so a span that handed all its work to another
   * thread has no child time to show and is still not a leaf.
   */
  childCount: number;
}>();

defineEmits<{
  (event: 'viewEvents'): void;
  (event: 'viewFlamegraph'): void;
}>();

const detail = computed(() => spanDetail(props.span.attributes, props.span.eventFields, props.fields));

/** A hand-written span has no event view: its own fields are already the span's. */
const hasEventView = computed(
  () => detail.value.sql !== null || detail.value.eventFields.length > 0
);

const kindVariant = computed(() => spanKindVariant(props.span.kind));

/** What its children covered — exact, since it is the duration the span did not account for. */
const childrenNanos = computed(() =>
  Math.max(0, props.span.durationNanos - props.span.selfDurationNanos)
);

const selfPercentOfSpan = computed(() => {
  const { selfDurationNanos, durationNanos } = props.span;
  if (durationNanos <= 0) {
    return 100;
  }
  return Math.min(100, (selfDurationNanos / durationNanos) * 100);
});

const selfLegend = computed(() => {
  if (childrenNanos.value === 0) {
    return 'all of it its own';
  }
  return `its own · ${percent(props.span.selfDurationNanos, props.span.durationNanos)}`;
});

/**
 * What kind of span this is, said in words the bar cannot. The forked case is called out because a
 * full green bar would otherwise read as "a leaf" for a span that has children — they simply ran
 * somewhere else, and none of this span's time went into them.
 */
const shape = computed(() => {
  if (props.childCount === 0) {
    return 'a leaf — nothing ran inside it';
  }
  const children = props.childCount === 1 ? '1 child' : `${props.childCount} children`;
  if (childrenNanos.value === 0) {
    return `${children}, all on other threads`;
  }
  return children;
});

const meterTitle = computed(
  () =>
    `${FormattingService.formatDuration2Units(props.span.selfDurationNanos)} its own of ` +
    `${FormattingService.formatDuration2Units(props.span.durationNanos)} total`
);

const startedAt = computed(() =>
  FormattingService.formatDuration2Units(props.span.startMillisFromBeginning * NANOS_PER_MILLI)
);

function keyCount(rows: SpanDetailRow[]): string {
  return rows.length === 1 ? '1 key' : `${rows.length} keys`;
}

function percent(part: number, whole: number): string {
  const share = (part / whole) * 100;
  return share < MIN_REPORTED_PERCENT ? '<0.1%' : share.toFixed(share < 10 ? 1 : 0) + '%';
}
</script>

<style scoped>
/*
 * Reads as a branch of the row above rather than as a panel of its own: the accent rail on the left
 * lines up with the indent, and the sunken ground separates it from the bars without a hard border.
 */
.span-detail {
  background: var(--color-bg-hover);
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  border-left: 3px solid var(--color-primary);
  padding-top: 0.7rem;
  padding-right: 1rem;
  padding-bottom: 0.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.sd-head {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex-wrap: wrap;
}

/*
 * Sized to `.table td` in assets/styles.scss -- 0.8rem is what every data table in Jeffrey sets, so
 * the panel reads as part of the app rather than as a zoomed-in inset over the 0.7rem bars.
 */
.sd-vitals {
  margin: 0;
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
  flex-wrap: wrap;
  font-variant-numeric: tabular-nums;
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.sd-vitals strong {
  color: var(--color-dark);
  font-weight: 600;
}

.sd-actions {
  margin-left: auto;
  display: flex;
  gap: 0.35rem;
}

.sd-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font: inherit;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.25rem 0.55rem;
  cursor: pointer;
}

.sd-btn:hover {
  background: var(--color-primary-light);
}

.sd-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.sd-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.sd-cols {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(17rem, 1fr));
  gap: 0.75rem 1.4rem;
  align-items: start;
}

.sd-block {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-width: 0;
}

.sd-label {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin: 0;
  font-size: var(--font-size-sm);
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.sd-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: var(--radius-circle);
  background: var(--color-primary);
  flex: none;
}

/* ------------------------------------------------------------------ meter */

.sd-meter {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

/*
 * The same two greens the waterfall bars use -- solid for the span's own work, pale for what its
 * children covered -- so the split reads as a magnified version of the bar in the row above rather
 * than as a second, unrelated chart.
 */
.sd-bar {
  height: 0.75rem;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--flamegraph-color-green) 40%, transparent);
  overflow: hidden;
}

.sd-bar i {
  display: block;
  height: 100%;
  background: var(--flamegraph-color-green);
}

.sd-legend {
  display: flex;
  gap: 1.1rem;
  flex-wrap: wrap;
  font-size: 0.8rem;
}

.sd-leg {
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
}

.sd-sw {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: var(--radius-sm);
  flex: none;
  align-self: center;
}

.sd-sw.self {
  background: var(--flamegraph-color-green);
}

.sd-sw.children {
  background: color-mix(in srgb, var(--flamegraph-color-green) 40%, transparent);
  border: 1px solid var(--color-border);
}

.sd-leg b {
  font-family: var(--font-family-monospace);
  font-variant-numeric: tabular-nums;
  color: var(--color-dark);
  font-weight: 600;
}

.sd-leg > span {
  color: var(--color-text-muted);
}

.sd-foot {
  margin: 0;
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.sd-foot b {
  font-family: var(--font-family-monospace);
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
  font-weight: 500;
}

/* ------------------------------------------------------------------ table */

.sd-table {
  border-collapse: collapse;
  width: 100%;
  table-layout: fixed;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.sd-table td {
  padding: 0.4rem 0.7rem;
  border-bottom: 1px solid var(--color-border-light);
  vertical-align: top;
  line-height: 1.5;
}

.sd-table tr:last-child td {
  border-bottom: 0;
}

.sd-table tr:nth-child(even) td {
  background: var(--color-bg-hover);
}

.sd-k {
  width: 10rem;
  font-size: 0.8rem;
  color: var(--color-text);
  font-weight: 500;
  overflow-wrap: anywhere;
}

.sd-k small {
  display: block;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
  font-weight: 400;
}

/* An attribute key is the developer's own string, so it is set in mono and never relabelled. */
.sd-table.is-attr .sd-k {
  font-family: var(--font-family-monospace);
  color: var(--color-dark);
  font-weight: 400;
}

.sd-v {
  font-family: var(--font-family-monospace);
  font-size: 0.8rem;
  color: var(--color-dark);
  /* Long values wrap rather than truncate: the panel can grow, and a hidden value is worse. */
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.sd-v.num {
  font-variant-numeric: tabular-nums;
}

/* The words that qualify a measurement -- "all of it", "into the recording". Prose, not data. */
.sd-note {
  font-family: var(--font-family-base);
  color: var(--color-text-muted);
}

.sd-v.absent {
  color: var(--color-text-light);
  font-style: italic;
  font-family: inherit;
}

/* ----------------------------------------------------------------- region */

.sd-region {
  border: 1px solid var(--color-primary-light);
  border-left: 3px solid var(--color-primary);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
  overflow: hidden;
}

.sd-region > header {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.75rem;
  background: var(--color-primary-lighter);
  border-bottom: 1px solid var(--color-primary-light);
  font-size: var(--font-size-sm);
  font-weight: 600;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--color-primary);
}

.sd-src {
  font-family: var(--font-family-monospace);
  letter-spacing: 0;
  text-transform: none;
  font-weight: 400;
  color: var(--color-text-muted);
  margin-left: auto;
}

.sd-region-body {
  padding: 0.6rem 0.75rem 0.7rem;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.sd-region .sd-table {
  border: 0;
  border-radius: 0;
}

/*
 * The event's own fields get a second hue, so the two views never read as one continuous list.
 * Teal rather than another blue: the waterfall already spends blue and cyan on span kinds.
 */
.sd-region.is-evt {
  border-color: var(--color-teal-lighter);
  border-left-color: var(--color-teal);
}

.sd-region.is-evt > header {
  background: var(--color-teal-light);
  border-bottom-color: var(--color-teal-lighter);
  color: var(--color-teal);
}

.sd-region.is-evt .sd-table tr:nth-child(even) td {
  background: var(--color-teal-light);
}

.sd-sql {
  margin: 0;
  font-family: var(--font-family-monospace);
  font-size: 0.8rem;
  line-height: 1.55;
  color: var(--color-dark);
  background: var(--color-bg-hover);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.55rem 0.7rem;
  /* Statements run long and wrap badly; scrolling this block keeps the row heights predictable. */
  overflow-x: auto;
  white-space: pre;
  max-height: 14rem;
  overflow-y: auto;
}

.sd-none {
  margin: 0;
  font-size: 0.8rem;
  color: var(--color-text-muted);
  background: var(--color-bg-card);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.65rem 0.8rem;
}

.sd-none code {
  font-family: var(--font-family-monospace);
  color: var(--color-dark);
}
</style>
