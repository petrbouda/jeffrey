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
  <div class="st">
    <div v-if="loading" class="st-note">Loading the stack…</div>
    <div v-else-if="error" class="st-note is-error">{{ error }}</div>
    <div v-else-if="frames.length === 0" class="st-note">
      The recording captured no stack for this throw.
    </div>

    <template v-else>
      <!--
        The header carries the count of what is actually on screen, not the resting count: opening
        a bar changes it, and a number that disagreed with the rows under it would be worse than
        no number at all.
      -->
      <div v-if="!preview" class="st-head">
        <span class="st-t">Stack</span>
        <span class="st-meta">
          <template v-if="shown < frames.length">
            <b>{{ shown }}</b> of {{ frames.length }} frames
          </template>
          <template v-else>{{ frames.length }} frames</template>
        </span>
        <span class="st-acts">
          <button
            type="button"
            class="st-chip"
            :class="{ on: folding }"
            :title="folding ? 'Show every frame' : 'Fold library frames away'"
            @click.stop="folding = !folding"
          >
            <i class="bi" :class="folding ? 'bi-funnel-fill' : 'bi-funnel'"></i>
            Fold libraries
          </button>
          <button type="button" class="st-chip" title="Copy the whole stack" @click.stop="copy">
            <i class="bi" :class="copied ? 'bi-check-lg' : 'bi-clipboard'"></i>
            {{ copied ? 'Copied' : 'Copy' }}
          </button>
        </span>
      </div>

      <div class="st-rows" :class="{ 'is-preview': preview }">
        <template v-for="entry in visible" :key="entry.kind + '-' + entry.depth">
          <!-- A bar stands in for the frames it hid; clicking it puts them back in place. -->
          <button
            v-if="entry.kind === 'fold'"
            type="button"
            class="st-fold"
            @click.stop="toggleFold(entry.depth)"
          >
            <i
              class="bi"
              :class="opened.has(entry.depth) ? 'bi-chevron-down' : 'bi-chevron-right'"
            ></i>
            <b>{{ entry.frames.length }}</b> frames in
            <span class="st-pkgs" :title="entry.packages.join(' · ')">
              {{ barPackages(entry.packages) }}
            </span>
          </button>

          <div v-else class="st-fr" :class="{ app: entry.application, throwing: entry.throwing }">
            <span class="st-sig">
              <span v-if="framePkg(entry.frame)" class="st-pkg">{{ framePkg(entry.frame) }}.</span
              ><span class="st-cls">{{ frameSimpleName(entry.frame) }}</span
              >.<b>{{ entry.frame.methodName }}</b>
            </span>
            <span class="st-src">{{ frameSource(entry.frame) }}</span>
          </div>
        </template>
      </div>

      <!--
        The preview shows the top of the stack only. Saying how much is not shown keeps it honest:
        a four-line preview that looked like the whole stack would be worse than no preview.
      -->
      <div v-if="preview && frames.length > shown" class="st-note is-more">
        Open the span for the full stack — {{ frames.length }} frames
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { TraceStackFrameRow } from '@/services/api/model/trace/TraceModels';
import { useTraceStacktrace } from '@/composables/useTraceStacktrace';
import {
  expandFold,
  foldedStack,
  shownFrameCount,
  unfoldedStack,
  type StackEntry
} from '@/services/trace/traceStackFolding';

const props = defineProps<{
  profileId: string;
  /** Null when the throw carried no stack, which renders as a sentence rather than as nothing. */
  stacktraceId: string | null;
  /**
   * Preview mode: the top few frames only, no header, no controls. What the rail's popover shows,
   * where there is room for about six rows before it starts scrolling.
   */
  preview?: boolean;
  /** How many entries a preview draws. Ignored outside preview mode. */
  previewRows?: number;
}>();

const stacktraceId = computed(() => props.stacktraceId);
const { frames, loading, error } = useTraceStacktrace(props.profileId, stacktraceId);

const folding = ref(true);
/** Depths of the bars the reader has opened, so opening one does not close another. */
const opened = ref(new Set<number>());

// A different throw is a different stack, so carrying the opened bars across would restore them at
// depths that mean nothing in the new one.
watch(stacktraceId, () => {
  opened.value = new Set<number>();
  folding.value = true;
});

/** The stack as the fold rule leaves it, with the reader's opened bars put back. */
const entries = computed<StackEntry[]>(() => {
  if (!folding.value) {
    return unfoldedStack(frames.value);
  }
  return foldedStack(frames.value).flatMap(entry => {
    if (entry.kind === 'fold' && opened.value.has(entry.depth)) {
      // The bar stays above what it opened, so the reader can see what they opened and close it.
      return [entry, ...expandFold(entry)];
    }
    return [entry];
  });
});

const visible = computed(() =>
  props.preview ? entries.value.slice(0, props.previewRows ?? 6) : entries.value
);

const shown = computed(() => shownFrameCount(visible.value));

/** How many packages a bar names before the rest become a count. Two fit; six is a wall of text. */
const BAR_PACKAGE_LIMIT = 2;

/**
 * What a fold bar says it holds.
 *
 * A long run crosses several packages — Spring into reflection into Tomcat — and naming all of them
 * turns the bar into the noise it was drawn to remove. The leaders are the ones that own the frames,
 * the rest become a number, and the full list stays on the title attribute for anyone who wants it.
 */
function barPackages(packages: string[]): string {
  if (packages.length <= BAR_PACKAGE_LIMIT) {
    return packages.join(' · ');
  }
  const rest = packages.length - BAR_PACKAGE_LIMIT;
  return `${packages.slice(0, BAR_PACKAGE_LIMIT).join(' · ')} +${rest} more`;
}

function toggleFold(depth: number): void {
  const next = new Set(opened.value);
  if (next.has(depth)) {
    next.delete(depth);
  } else {
    next.add(depth);
  }
  opened.value = next;
}

function frameSimpleName(frame: TraceStackFrameRow): string {
  const className = frame.className;
  if (!className) {
    return '(native)';
  }
  const lastDot = className.lastIndexOf('.');
  return lastDot < 0 ? className : className.slice(lastDot + 1);
}

/** The package as a prefix, full rather than trimmed: a frame row has room the fold bar does not. */
function framePkg(frame: TraceStackFrameRow): string {
  const className = frame.className;
  if (!className) {
    return '';
  }
  const lastDot = className.lastIndexOf('.');
  return lastDot < 0 ? '' : className.slice(0, lastDot);
}

/**
 * The source location, or the frame type when there is none — `Native` says why a frame has no
 * line, where a blank column would look like missing data.
 */
function frameSource(frame: TraceStackFrameRow): string {
  const simple = frameSimpleName(frame).split('$')[0];
  if (frame.lineNumber === null) {
    return frame.frameType;
  }
  return `${simple}.java:${frame.lineNumber}`;
}

const copied = ref(false);

/** The whole stack as text, in the shape a JVM prints it, for pasting into an issue. */
async function copy(): Promise<void> {
  const text = frames.value
    .map(frame => {
      const where =
        frame.lineNumber === null
          ? frame.frameType
          : `${frameSimpleName(frame).split('$')[0]}.java:${frame.lineNumber}`;
      return `\tat ${frame.className ?? '(native)'}.${frame.methodName}(${where})`;
    })
    .join('\n');

  try {
    await navigator.clipboard.writeText(text);
    copied.value = true;
    window.setTimeout(() => (copied.value = false), 1500);
  } catch {
    // A denied clipboard is the browser's decision, not a fault to report — the reader can still
    // select the rows. Silently leaving the button unchanged says "that did not happen".
  }
}
</script>

<style scoped>
.st {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-sm);
  line-height: 1.5;
}

.st-note {
  padding: 0.3rem 0.4rem;
  color: var(--color-text-muted);
  font-family: inherit;
  font-size: var(--font-size-xs);
}

.st-note.is-error {
  color: var(--color-danger);
}

.st-note.is-more {
  margin-top: 0.25rem;
  padding-top: 0.3rem;
  border-top: 1px solid var(--color-border-light);
  color: var(--color-primary);
  font-weight: 600;
}

.st-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.3rem 0.4rem 0.35rem;
  margin-bottom: 0.2rem;
  border-bottom: 1px solid var(--color-border-light);
  font-family: inherit;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.st-t {
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.st-meta b {
  color: var(--color-dark);
}

.st-acts {
  margin-left: auto;
  display: flex;
  gap: 0.35rem;
}

.st-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0 0.35rem;
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-pill);
  background: var(--color-white);
  color: var(--color-primary);
  font-family: inherit;
  font-size: var(--font-size-xs);
  font-weight: 600;
  cursor: pointer;
}

.st-chip.on {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}

.st-rows.is-preview {
  max-height: 7rem;
  overflow-y: auto;
}

.st-fr {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.8rem;
  align-items: baseline;
  padding: 0.09rem 0.4rem;
  border-radius: var(--radius-xs);
  color: var(--color-text-light);
}

.st-fr:hover {
  background: var(--color-primary-lighter);
}

.st-sig {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.st-pkg {
  color: var(--color-text-light);
}

.st-src {
  color: var(--color-text-light);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

/* Application frames carry the ink; everything else recedes. The reader only ever needs
   "mine" against "not mine", so this is two weights and not a palette. */
.st-fr.app {
  color: var(--color-dark);
}

.st-fr.app .st-sig {
  font-weight: 600;
}

.st-fr.app .st-pkg {
  color: var(--color-text-muted);
}

.st-fr.app .st-src {
  color: var(--color-primary);
}

/* The throwing frame. A stack has exactly one, and it is the line the reader opened it for. */
.st-fr.throwing {
  background: var(--color-danger-bg-lighter);
  color: var(--color-danger);
  font-weight: 700;
}

.st-fr.throwing .st-pkg,
.st-fr.throwing .st-src {
  color: var(--color-danger);
}

.st-fold {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  width: 100%;
  padding: 0.09rem 0.4rem;
  margin: 0.1rem 0;
  border: 0;
  border-radius: var(--radius-xs);
  background: var(--color-lighter);
  color: var(--color-text-muted);
  font-family: inherit;
  font-size: var(--font-size-xs);
  text-align: left;
  cursor: pointer;
}

.st-fold:hover {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.st-fold b {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.st-pkgs {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
