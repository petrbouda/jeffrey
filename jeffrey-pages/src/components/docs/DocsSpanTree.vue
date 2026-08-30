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

<script setup lang="ts">
import { computed } from 'vue';

/**
 * One row of a documented trace. `waterfall` reads depth/start/duration; `cards`
 * reads the three ids. Both read name and kind, so a tree can be re-rendered in
 * either variant without restating it.
 */
interface SpanRow {
  name: string;
  kind?: 'SERVER' | 'CLIENT' | 'INTERNAL';
  /** Nesting level, 0 = trace root. Waterfall only. */
  depth?: number;
  /** Offset from the start of the trace, in milliseconds. Waterfall only. */
  start?: number;
  /** Duration in milliseconds. Waterfall only. */
  duration?: number;
  /** Source event type — `jeffrey.TraceSpan`, `JdbcQueryEvent`, … */
  event?: string;
  /** Free note rendered beside the name: `root`, `leaf`, `pool thread A`. */
  note?: string;
  /** Overrides the kind colour — for spans the analysis synthesized from a JDK event. */
  color?: string;
  traceId?: string;
  spanId?: string;
  parentSpanId?: string;
}

const props = withDefaults(defineProps<{
  spans: SpanRow[];
  variant?: 'waterfall' | 'cards';
  trace?: string;
  caption?: string;
}>(), {
  variant: 'waterfall',
  trace: '',
  caption: ''
});

// Advances for the site's monospace stack, used to size the label column so
// names never collide with the track.
const NAME_CHAR = 7.5;
const META_CHAR = 6.4;
const CANVAS_WIDTH = 920;
const INDENT = 18;

const kindColor = (kind?: string): string => {
  if (kind === 'SERVER') {
    return 'var(--span-server)';
  }
  if (kind === 'CLIENT') {
    return 'var(--span-client)';
  }
  return 'var(--span-internal)';
};

/* ────────────────────────────  waterfall  ──────────────────────────── */

const ROW_HEIGHT = 30;
const BAR_HEIGHT = 14;
const HEADER_HEIGHT = 30;

const totalMs = computed(() => {
  const end = props.spans.map(s => (s.start ?? 0) + (s.duration ?? 0));
  return Math.max(1, ...end);
});

const labelWidth = computed(() => {
  const nameW = Math.max(
    ...props.spans.map(s => (s.depth ?? 0) * INDENT + s.name.length * NAME_CHAR + (s.note ? s.note.length * META_CHAR + 28 : 0))
  );
  const eventW = Math.max(0, ...props.spans.map(s => (s.event ? s.event.length * META_CHAR + 18 : 0)));
  return Math.min(560, nameW + eventW + 34);
});

const durationWidth = computed(() => {
  return Math.max(...props.spans.map(s => formatDuration(s.duration).length * META_CHAR)) + 14;
});

const trackX = computed(() => labelWidth.value + durationWidth.value + 14);
const trackWidth = computed(() => Math.max(180, CANVAS_WIDTH - trackX.value - 8));

const waterfallHeight = computed(() => {
  return HEADER_HEIGHT + props.spans.length * ROW_HEIGHT + (props.caption ? 30 : 10);
});

// A row is a container when the row below it sits deeper: containers are drawn
// hollow, because their bar is mostly the children's time rather than their own.
const hasChildren = (index: number): boolean => {
  const next = props.spans[index + 1];
  return !!next && (next.depth ?? 0) > (props.spans[index].depth ?? 0);
};

const rows = computed(() => {
  return props.spans.map((span, index) => {
    const start = span.start ?? 0;
    const duration = span.duration ?? 0;
    const x = trackX.value + (start / totalMs.value) * trackWidth.value;
    const width = Math.max(3, (duration / totalMs.value) * trackWidth.value);
    return {
      span,
      y: HEADER_HEIGHT + index * ROW_HEIGHT,
      indent: (span.depth ?? 0) * INDENT,
      color: span.color ?? kindColor(span.kind),
      container: hasChildren(index),
      barX: x,
      barWidth: width,
      // The duration is legible inside the bar only once the bar outgrows it.
      inBar: width > formatDuration(duration).length * META_CHAR + 20
    };
  });
});

const ticks = computed(() => {
  const total = totalMs.value;
  const rough = total / 4;
  const magnitude = Math.pow(10, Math.floor(Math.log10(rough)));
  const step = [1, 2, 2.5, 5, 10].map(m => m * magnitude).find(s => s >= rough) ?? magnitude * 10;
  const out: { x: number; label: string }[] = [];
  for (let value = 0; value <= total; value += step) {
    out.push({ x: trackX.value + (value / total) * trackWidth.value, label: formatTick(value, total) });
  }
  return out;
});

function formatDuration(ms?: number): string {
  if (ms === undefined) {
    return '';
  }
  if (ms >= 10000) {
    return (ms / 1000).toFixed(1) + ' s';
  }
  return ms + ' ms';
}

function formatTick(value: number, total: number): string {
  if (value === 0) {
    return '0';
  }
  if (total >= 2000) {
    const seconds = value / 1000;
    return (Number.isInteger(seconds) ? seconds : seconds.toFixed(1)) + 's';
  }
  return value + 'ms';
}

/* ──────────────────────────────  cards  ────────────────────────────── */

const CARD_HEIGHT = 74;
const CARD_GAP = 38;
const CARD_TOP = 34;

const cards = computed(() => {
  return props.spans.map((span, index) => ({
    span,
    y: CARD_TOP + index * (CARD_HEIGHT + CARD_GAP),
    color: kindColor(span.kind),
    isRoot: span.parentSpanId === '0' || !span.parentSpanId
  }));
});

// Each card is linked to the earlier card whose spanId it names as its parent —
// which is the whole claim the diagram makes: the tree is those ids and nothing else.
const links = computed(() => {
  const out: { from: number; to: number }[] = [];
  props.spans.forEach((span, index) => {
    if (!span.parentSpanId || span.parentSpanId === '0') {
      return;
    }
    const parent = props.spans.findIndex(candidate => candidate.spanId === span.parentSpanId);
    if (parent >= 0 && parent < index) {
      out.push({ from: parent, to: index });
    }
  });
  return out;
});

const cardsHeight = computed(() => {
  return CARD_TOP + props.spans.length * (CARD_HEIGHT + CARD_GAP) - CARD_GAP + (props.caption ? 34 : 8);
});

const cardY = (index: number): number => CARD_TOP + index * (CARD_HEIGHT + CARD_GAP);

const LINK_FROM_X = 404;
const LINK_TO_X = 630;

const linkPath = (link: { from: number; to: number }): string => {
  const y1 = cardY(link.from) + CARD_HEIGHT;
  const y2 = cardY(link.to);
  return 'M ' + LINK_FROM_X + ' ' + y1
    + ' C ' + LINK_FROM_X + ' ' + (y1 + 16)
    + ', ' + LINK_TO_X + ' ' + (y2 - 16)
    + ', ' + LINK_TO_X + ' ' + y2;
};
</script>

<template>
  <figure class="span-tree" :class="'variant-' + variant">
    <!-- ───────────────── waterfall ───────────────── -->
    <svg
      v-if="variant === 'waterfall'"
      :viewBox="'0 0 ' + CANVAS_WIDTH + ' ' + waterfallHeight"
      role="img"
      :aria-label="'Trace waterfall: ' + spans.map(s => s.name).join(', ')"
    >
      <!-- column headers and time axis -->
      <text class="st-head" x="0" y="12">span</text>
      <text v-if="trace" class="st-trace" :x="labelWidth + durationWidth" y="12" text-anchor="end">
        trace {{ trace }}
      </text>
      <text
        v-for="tick in ticks"
        :key="'t' + tick.label"
        class="st-tick"
        :x="tick.x"
        y="12"
      >{{ tick.label }}</text>
      <line class="st-axis" :x1="trackX" y1="19" :x2="CANVAS_WIDTH - 8" y2="19" />
      <line
        v-for="tick in ticks"
        :key="'g' + tick.label"
        class="st-grid"
        :x1="tick.x"
        y1="19"
        :x2="tick.x"
        :y2="HEADER_HEIGHT + spans.length * ROW_HEIGHT - 6"
      />

      <g v-for="(row, index) in rows" :key="index">
        <!-- kind dot, name, optional note and source event type -->
        <circle :cx="row.indent + 5" :cy="row.y + 11" r="4" :fill="row.color" />
        <text class="st-name" :x="row.indent + 16" :y="row.y + 15">{{ row.span.name }}</text>
        <text
          v-if="row.span.note"
          class="st-note"
          :x="row.indent + 24 + row.span.name.length * NAME_CHAR"
          :y="row.y + 15"
        >{{ row.span.note }}</text>
        <text
          v-if="row.span.event"
          class="st-meta"
          :x="labelWidth - 10"
          :y="row.y + 15"
          text-anchor="end"
        >{{ row.span.event }}</text>

        <!-- duration, then the bar itself -->
        <text
          v-if="!row.inBar"
          class="st-num"
          :x="labelWidth + durationWidth"
          :y="row.y + 15"
          text-anchor="end"
        >{{ formatDuration(row.span.duration) }}</text>

        <rect
          :x="row.barX"
          :y="row.y + 4"
          :width="row.barWidth"
          :height="BAR_HEIGHT"
          rx="3"
          :fill="row.color"
          :fill-opacity="row.container ? 0.24 : 1"
          :stroke="row.container ? row.color : 'none'"
          :stroke-opacity="row.container ? 0.55 : 0"
        />
        <text
          v-if="row.inBar"
          class="st-inbar"
          :x="row.barX + 8"
          :y="row.y + 15"
          :fill="row.container ? row.color : '#ffffff'"
        >{{ formatDuration(row.span.duration) }}</text>
      </g>

      <text
        v-if="caption"
        class="st-caption"
        x="0"
        :y="waterfallHeight - 8"
      >{{ caption }}</text>
    </svg>

    <!-- ───────────────── identity cards ───────────────── -->
    <svg
      v-else
      :viewBox="'0 0 ' + CANVAS_WIDTH + ' ' + cardsHeight"
      role="img"
      :aria-label="'Span identity: ' + spans.map(s => s.name).join(', ')"
    >
      <defs>
        <marker id="span-tree-arrow" viewBox="0 0 10 10" refX="8" refY="5"
                markerWidth="6" markerHeight="6" orient="auto-start-reverse">
          <path d="M 0 1 L 9 5 L 0 9 z" fill="var(--span-link)" />
        </marker>
      </defs>

      <text class="st-head" x="0" y="14">
        the tree is nothing but these three numbers
      </text>

      <g v-for="(card, index) in cards" :key="'c' + index">
        <rect class="st-card" x="0" :y="card.y" :width="CANVAS_WIDTH" :height="CARD_HEIGHT" rx="8" />
        <rect x="0" :y="card.y" width="4" :height="CARD_HEIGHT" rx="2" :fill="card.color" />

        <text class="st-name" x="20" :y="card.y + 26">{{ card.span.name }}</text>
        <g v-if="card.span.kind" :transform="'translate(' + (32 + card.span.name.length * NAME_CHAR) + ',' + (card.y + 13) + ')'">
          <rect :width="card.span.kind.length * 6.6 + 16" height="19" rx="9.5" :fill="card.color" fill-opacity="0.15" />
          <rect
            :width="card.span.kind.length * 6.6 + 16"
            height="19"
            rx="9.5"
            fill="none"
            :stroke="card.color"
            stroke-opacity="0.35"
          />
          <text
            class="st-chip"
            :x="(card.span.kind.length * 6.6 + 16) / 2"
            y="13"
            text-anchor="middle"
            :fill="card.color"
          >{{ card.span.kind }}</text>
        </g>
        <text v-if="card.span.event" class="st-meta" :x="CANVAS_WIDTH - 20" :y="card.y + 26" text-anchor="end">
          {{ card.span.event }}
        </text>

        <text class="st-field" x="20" :y="card.y + 48">traceId</text>
        <text class="st-id" x="20" :y="card.y + 64">{{ card.span.traceId }}</text>

        <text class="st-field" x="330" :y="card.y + 48">spanId</text>
        <text class="st-id" x="330" :y="card.y + 64">{{ card.span.spanId }}</text>

        <text class="st-field" x="620" :y="card.y + 48">parentSpanId</text>
        <text
          class="st-id"
          :class="{ 'st-id-absent': card.isRoot }"
          x="620"
          :y="card.y + 64"
        >{{ card.isRoot ? '0' : card.span.parentSpanId }}</text>
        <g v-if="card.isRoot" :transform="'translate(646,' + (card.y + 51) + ')'">
          <rect width="38" height="17" rx="8.5" fill="none" stroke="var(--span-link)" />
          <text class="st-chip" x="19" y="12" text-anchor="middle" fill="var(--span-link)">root</text>
        </g>
      </g>

      <!-- parent spanId → child parentSpanId -->
      <path
        v-for="(link, index) in links"
        :key="'l' + index"
        class="st-link"
        :d="linkPath(link)"
        marker-end="url(#span-tree-arrow)"
      />

      <text
        v-if="caption"
        class="st-caption"
        x="0"
        :y="cardsHeight - 10"
      >{{ caption }}</text>
    </svg>
  </figure>
</template>

<style scoped>
.span-tree {
  /* Span-kind hues are Jeffrey's own UI tokens, so a CLIENT span reads the same
     colour in the docs as it does in the product it documents. */
  --span-server: #5e64ff;
  --span-client: #2f96b4;
  --span-internal: #7780bf;
  --span-link: #0d9488;
  /* Wait categories, for spans promoted out of a JDK event. */
  --span-socket-io: #f86624;
  --span-file-io: #9c27b0;
  --span-lock: #a855f7;
  /* Traced methods (jdk.MethodTrace). The product draws them in its own-work
     green; that token is a pale fill meant for a bar with dark text, so the
     diagram uses a legible tone of the same hue instead. */
  --span-method: #6ea44c;

  margin: 1.5rem 0;
  padding: 1.125rem 1.25rem;
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow-x: auto;
}

.span-tree svg {
  display: block;
  width: 100%;
  min-width: 660px;
  height: auto;
}

.span-tree text {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
}

.st-name {
  font-size: 12.5px;
  fill: #334155;
}

.st-meta,
.st-note {
  font-size: 11px;
  fill: #94a3b8;
}

.st-num,
.st-id {
  font-size: 11.5px;
  fill: #475569;
  font-variant-numeric: tabular-nums;
}

.st-id {
  fill: #334155;
}

.st-id-absent {
  fill: #94a3b8;
}

.st-inbar {
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.st-trace,
.st-field {
  font-size: 10px;
  fill: #94a3b8;
}

.st-head {
  font-size: 9.5px;
  fill: #94a3b8;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.st-tick {
  font-size: 10px;
  fill: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.st-chip {
  font-size: 10px;
}

.st-caption {
  font-size: 11px;
  fill: #64748b;
}

.st-axis {
  stroke: #e2e8f0;
  stroke-width: 1;
}

.st-grid {
  stroke: #eef2f6;
  stroke-width: 1;
}

.st-card {
  fill: #ffffff;
  stroke: #e2e8f0;
}

.st-link {
  fill: none;
  stroke: var(--span-link);
  stroke-width: 1.5;
  stroke-dasharray: 4 3;
}
</style>
