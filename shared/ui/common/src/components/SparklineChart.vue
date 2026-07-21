<template>
  <svg
    class="sparkline-chart"
    :viewBox="`0 0 ${VIEWBOX_WIDTH} ${VIEWBOX_HEIGHT}`"
    preserveAspectRatio="none"
    :style="{ height: `${height}px` }"
    role="img"
    :aria-label="ariaLabel"
  >
    <template v-if="variant === 'bars'">
      <rect
        v-for="(bar, index) in barRects"
        :key="index"
        :x="bar.x"
        :y="bar.y"
        :width="bar.width"
        :height="bar.height"
        :rx="BAR_CORNER_RADIUS"
        :fill="bar.color"
      />
    </template>
    <template v-else>
      <path v-if="variant === 'area' && areaPath" :d="areaPath" :fill="color" :opacity="AREA_OPACITY" />
      <path
        v-if="linePath"
        :d="linePath"
        fill="none"
        :stroke="color"
        :stroke-width="LINE_STROKE_WIDTH"
        stroke-linejoin="round"
        vector-effect="non-scaling-stroke"
      />
      <circle
        v-if="endpointDot"
        :cx="endpointDot.x"
        :cy="endpointDot.y"
        :r="ENDPOINT_RADIUS"
        :fill="color"
      />
    </template>
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue';

/**
 * Lightweight SVG sparkline for dashboard tiles: a compact area/line/bars trend
 * without axes or interactivity. Points are `[timestampMillis, value]` pairs
 * (the shape used by timeseries Serie data); values are downsampled to a fixed
 * number of buckets so long per-second series stay smooth and cheap to render.
 */
interface Props {
  points: number[][];
  color: string;
  variant?: 'area' | 'line' | 'bars';
  height?: number;
  /** Anchor the value axis at zero (quantities); disable for narrow-range gauges. */
  zeroBaseline?: boolean;
  /** Bars only: bars with a value above this threshold are drawn in highlightColor. */
  highlightAbove?: number;
  highlightColor?: string;
  ariaLabel?: string;
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'area',
  height: 44,
  zeroBaseline: true,
  highlightAbove: undefined,
  highlightColor: undefined,
  ariaLabel: 'trend chart'
});

const VIEWBOX_WIDTH = 200;
const VIEWBOX_HEIGHT = 44;
const PADDING = 3;
const DOWNSAMPLE_BUCKETS = 100;
const LINE_STROKE_WIDTH = 1.8;
const ENDPOINT_RADIUS = 2.6;
const BAR_CORNER_RADIUS = 1;
const BAR_GAP = 1.2;
const AREA_OPACITY = 0.12;
const RANGE_HEADROOM = 1.05;

const values = computed<number[]>(() => {
  const raw = props.points
    .map((point) => point[1])
    .filter((value) => Number.isFinite(value));
  if (raw.length <= DOWNSAMPLE_BUCKETS) {
    return raw;
  }
  const buckets: number[] = new Array(DOWNSAMPLE_BUCKETS).fill(0);
  const counts: number[] = new Array(DOWNSAMPLE_BUCKETS).fill(0);
  raw.forEach((value, index) => {
    const bucket = Math.min(
      DOWNSAMPLE_BUCKETS - 1,
      Math.floor((index / raw.length) * DOWNSAMPLE_BUCKETS)
    );
    if (props.variant === 'bars') {
      buckets[bucket] = Math.max(buckets[bucket], value);
      counts[bucket] = 1;
    } else {
      buckets[bucket] += value;
      counts[bucket]++;
    }
  });
  return buckets.map((sum, index) => (counts[index] > 0 ? sum / counts[index] : 0));
});

const valueRange = computed<{ min: number; max: number }>(() => {
  if (values.value.length === 0) {
    return { min: 0, max: 1 };
  }
  const dataMax = Math.max(...values.value);
  const dataMin = Math.min(...values.value);
  const min = props.zeroBaseline ? 0 : dataMin / RANGE_HEADROOM;
  const max = Math.max(dataMax * RANGE_HEADROOM, min + 1e-9);
  return { min, max };
});

function toCoordinates(): Array<{ x: number; y: number }> {
  const data = values.value;
  if (data.length < 2) {
    return [];
  }
  const { min, max } = valueRange.value;
  const innerWidth = VIEWBOX_WIDTH - 2 * PADDING;
  const innerHeight = VIEWBOX_HEIGHT - 2 * PADDING;
  return data.map((value, index) => ({
    x: PADDING + (index * innerWidth) / (data.length - 1),
    y: VIEWBOX_HEIGHT - PADDING - ((value - min) / (max - min)) * innerHeight
  }));
}

const linePath = computed<string | null>(() => {
  const coordinates = toCoordinates();
  if (coordinates.length === 0) {
    return null;
  }
  return coordinates
    .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)} ${point.y.toFixed(1)}`)
    .join(' ');
});

const areaPath = computed<string | null>(() => {
  const coordinates = toCoordinates();
  if (coordinates.length === 0 || !linePath.value) {
    return null;
  }
  const first = coordinates[0];
  const last = coordinates[coordinates.length - 1];
  const baseline = VIEWBOX_HEIGHT - PADDING;
  return `${linePath.value} L ${last.x.toFixed(1)} ${baseline} L ${first.x.toFixed(1)} ${baseline} Z`;
});

const endpointDot = computed<{ x: number; y: number } | null>(() => {
  const coordinates = toCoordinates();
  if (coordinates.length === 0) {
    return null;
  }
  return coordinates[coordinates.length - 1];
});

const barRects = computed(() => {
  const data = values.value;
  if (data.length === 0) {
    return [];
  }
  const { min, max } = valueRange.value;
  const innerWidth = VIEWBOX_WIDTH - 2 * PADDING;
  const innerHeight = VIEWBOX_HEIGHT - 2 * PADDING;
  const barWidth = innerWidth / data.length;
  return data.map((value, index) => {
    const barHeight = Math.max(1, ((value - min) / (max - min)) * innerHeight);
    const highlighted =
      props.highlightAbove !== undefined &&
      props.highlightColor !== undefined &&
      value > props.highlightAbove;
    return {
      x: (PADDING + index * barWidth + BAR_GAP / 2).toFixed(1),
      y: (VIEWBOX_HEIGHT - PADDING - barHeight).toFixed(1),
      width: Math.max(0.5, barWidth - BAR_GAP).toFixed(1),
      height: barHeight.toFixed(1),
      color: highlighted ? props.highlightColor : props.color
    };
  });
});
</script>

<style scoped>
.sparkline-chart {
  display: block;
  width: 100%;
}
</style>
