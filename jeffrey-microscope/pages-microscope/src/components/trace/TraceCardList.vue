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
  One trace per line: how long it took, what it was, and how much of it there is — the single way a
  list of traces is drawn, wherever the list came from.

  Every page that lists traces used to dress them itself, and they drifted: Search Traces and the
  operation summary grew the same card and the same hundred lines of CSS twice over, while the
  Slowest Traces tab drew something else entirely and a trace looked like a different kind of thing
  depending on which page had found it. This owns that row now; a caller supplies traces and, where
  it has something extra to say about one, fills a slot.

  One line, not two. The row carries five facts and a name, and stacking them made a 98px row that
  fits six traces on a screen — for a list whose whole job is to be scanned for the one row worth
  opening. Flat, it is a little over 40px and fits thirteen. The meta is plain text rather than chips
  for the same reason: three pills under a name cost a second line's worth of padding to carry three
  short numbers.

  Generic over the item rather than taking bare traces, because a search result is a trace plus what
  matched, and the extra travels with it into the #footer slot.
-->
<template>
  <div class="trace-cards">
    <!-- Only where the list is a slice of something larger; silence would read as "this is all". -->
    <SlowestCountHeader
      v-if="total !== undefined || note !== undefined"
      :shown="shownItems.length"
      :total="total ?? items.length"
      :note="note"
    />

    <EmptyState
      v-if="items.length === 0"
      icon="bi-diagram-3"
      title="No traces"
      :description="emptyDescription"
    />

    <div v-for="item in shownItems" :key="trace(item).traceId" class="trace-card">
      <!--
        Keyboard-operable: opening the waterfall is this list's whole purpose, and as a plain div the
        row was unreachable without a mouse. The click stands down mid-selection — the id on the row
        exists to be copied, and finishing a drag-select used to open the trace instead.
      -->
      <div
        class="trace-row"
        role="button"
        tabindex="0"
        @click="onRowClick(item)"
        @keydown.enter.prevent="emit('open', trace(item))"
        @keydown.space.prevent="emit('open', trace(item))"
      >
        <!--
          The duration, tinted by where it sits in the population the caller named — green up to its
          median, red past its p95 — so the slow ones are visible before a number is read. Without
          thresholds the zone stays neutral rather than guessing.
        -->
        <span class="trace-zone" :class="severityClass(item)">
          {{ duration(trace(item).durationNanos) }}
        </span>

        <span class="trace-main">
          <MetricName
            class="trace-op"
            :segments="parseOperationName(trace(item).rootName, trace(item).rootEventType)"
            :title="trace(item).rootName"
          />

          <!-- Beside the name rather than out in the meta: a failure belongs to what failed. -->
          <Badge
            v-if="trace(item).errorCount > 0"
            :value="errorLabel(trace(item).errorCount)"
            variant="danger"
            size="s"
            icon="bi bi-exclamation-triangle"
            :uppercase="false"
          />

          <!--
            The tail is a grid, not a run: the event type is the rightmost thing on every row, and a
            fixed column starts it at the same offset each time instead of letting the length of an
            id push it around. That column is what makes a list of mixed types scannable.
          -->
          <span class="trace-tail">
            <span class="trace-meta">
              <b>{{ FormattingService.formatNumber(trace(item).spanCount) }}</b> spans
              <span class="dot">·</span>
              +<b>{{ startedAt(trace(item).startMillisFromBeginning) }}</b>
              <span class="dot">·</span>
              <!-- The one thing on the row that names this run rather than its type. -->
              <b class="trace-id">{{ trace(item).traceId }}</b>
            </span>
            <span class="trace-type">
              <!--
                Not uppercased: jeffrey.HttpServerExchange is how the recording, jfr print and JMC
                all spell it, and shouting it makes it harder to recognise. A size down from the
                other badges too — it is the same words on nearly every row, so it should read as
                the label on a column rather than compete with the name for attention.
              -->
              <Badge
                :value="trace(item).rootEventType"
                variant="secondary"
                size="xs"
                borderless
                :uppercase="false"
              />
            </span>
            <slot name="right" :item="item" />
            <i class="bi bi-chevron-right trace-chevron"></i>
          </span>
        </span>
      </div>

      <!--
        Rendered bare, so a caller whose slot draws nothing for this trace costs nothing: there is no
        second line to hide it on now, and an always-present band would put an empty strip under
        every row.
      -->
      <slot name="footer" :item="item" />
    </div>
  </div>
</template>

<script setup lang="ts" generic="T">
import { computed } from 'vue';

import Badge from '@shared/components/Badge.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import SlowestCountHeader from '@shared/components/SlowestCountHeader.vue';
import FormattingService from '@shared/services/FormattingService';
import MetricName from '@/components/common/MetricName.vue';
import { errorLabel, parseOperationName } from '@/services/trace/traceLabels';
import type { TraceRow } from '@/services/api/model/trace/TraceModels';

const props = withDefaults(
  defineProps<{
    items: T[];
    /** How to reach the trace inside an item — identity for a plain list of traces. */
    trace: (item: T) => TraceRow;
    /**
     * The population the tint reads against, which is never this list: a capped or filtered slice
     * has its own percentiles, and colouring a page by its own median calls its fastest half fast
     * however slow they are. Omit both to leave every zone neutral.
     */
    p50Nanos?: number;
    p95Nanos?: number;
    /** How many traces there are in total, when this list is a slice of them. */
    total?: number;
    note?: string;
    maxDisplayed?: number;
    emptyDescription?: string;
  }>(),
  {
    p50Nanos: undefined,
    p95Nanos: undefined,
    total: undefined,
    note: undefined,
    maxDisplayed: 50,
    emptyDescription: 'No traces to show here.'
  }
);

const emit = defineEmits<{ open: [trace: TraceRow] }>();

/* Drawn exactly as given: every caller has already ordered and cut its own page. */
const shownItems = computed(() => props.items.slice(0, props.maxDisplayed));

function duration(nanos: number): string {
  return FormattingService.formatDuration2Units(nanos);
}

/**
 * Where the trace sits in the recording, which is the only reading of "when" that lines up with the
 * waterfall, the timeline and the flamegraphs. An absolute instant lines up with nothing.
 */
function startedAt(millisFromBeginning: number): string {
  return FormattingService.formatDurationInMillis2Units(millisFromBeginning);
}

function severityClass(item: T): string | undefined {
  if (props.p50Nanos === undefined || props.p95Nanos === undefined) {
    return undefined;
  }
  const nanos = props.trace(item).durationNanos;
  if (nanos > props.p95Nanos) {
    return 'zone-slow';
  }
  if (nanos <= props.p50Nanos) {
    return 'zone-fast';
  }
  return undefined;
}

function onRowClick(item: T): void {
  const selection = window.getSelection();
  if (selection && !selection.isCollapsed) {
    return;
  }
  emit('open', props.trace(item));
}
</script>

<style scoped>
.trace-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  background: var(--color-bg-card);
  overflow: hidden;
}

.trace-card + .trace-card {
  margin-top: 4px;
}

.trace-row {
  display: flex;
  align-items: stretch;
  cursor: pointer;
}

.trace-row:hover {
  background: var(--color-bg-hover);
}

.trace-row:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

/* Right-aligned so the digits line up down the list, which is how the column is read. */
.trace-zone {
  width: 104px;
  min-width: 104px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0.5rem var(--spacing-2) 0.5rem var(--spacing-4);
  font-family: var(--font-family-monospace);
  font-variant-numeric: tabular-nums;
  font-size: 0.76rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  color: var(--color-dark);
}

.trace-zone.zone-fast {
  background: var(--color-success-light);
  color: var(--color-success-dark);
}

.trace-zone.zone-slow {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.trace-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.5rem 0.7rem;
}

/* Takes the slack and truncates; everything to its right is fixed. */
.trace-op {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  /*
   * The name's own line metrics, not the page's 1.5. Flexbox centres this box either way, but with a
   * numeric line-height the spare leading is split evenly around the font's content area, and the
   * monospace face MetricName uses has an asymmetric ascent/descent — so the glyphs came to rest
   * eight tenths of a pixel above the middle of the row while the meta text beside them sat dead
   * centre. `normal` lets the font distribute its own leading, which puts the ink where the eye
   * expects it. Measured, not guessed: -0.82px before, -0.01px after.
   */
  line-height: normal;
}

.trace-tail {
  flex: none;
  display: grid;
  grid-template-columns: auto 11.5rem auto;
  align-items: center;
  gap: 0.7rem;
}

/*
 * A flex box, not the bare inline span it looks like: an inline-flex badge inside an inline box sits
 * on the text baseline, which leaves the descender space under it and lands the badge a couple of
 * pixels below the middle of the row. Flex centres the box itself and the baseline stops mattering.
 */
.trace-type {
  display: flex;
  align-items: center;
}

.trace-meta {
  text-align: right;
  font-size: 0.68rem;
  color: var(--color-text);
  white-space: nowrap;
}

.trace-meta b {
  font-family: var(--font-family-monospace);
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
}

.trace-meta .dot {
  color: var(--color-text-muted);
  padding: 0 0.3rem;
}

.trace-chevron {
  font-size: 0.75rem;
  color: var(--color-text-light);
}

.trace-main :deep(.badge) {
  flex-shrink: 0;
}
</style>
