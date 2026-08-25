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
      <div class="st-head">
        <span class="st-t">Stack</span>
        <span class="st-meta">
          <template v-if="shown < frames.length">
            <b>{{ shown }}</b> of {{ frames.length }} frames
          </template>
          <template v-else>{{ frames.length }} frames</template>
        </span>
        <span class="st-acts">
          <!--
            Whatever the host wants to put before this panel's own two controls. The stack knows how
            to fold and copy itself and nothing else — a button that leaves for somewhere else, like
            the dock's "select the span this threw in", belongs to whoever knows where it goes. The
            span detail simply passes nothing, and the slot draws nothing.
          -->
          <slot name="lead"></slot>
          <!--
            The application's own button vocabulary rather than a local chip: btn-sm, and a toggle
            expressed as a variant swap between btn-primary and btn-outline-secondary, which is how
            every other profile view renders one.
          -->
          <button
            type="button"
            class="btn btn-sm"
            :class="folding ? 'btn-primary' : 'btn-outline-secondary'"
            :aria-pressed="folding"
            :title="folding ? 'Show every frame' : 'Fold library frames away'"
            @click.stop="folding = !folding"
          >
            <i class="bi" :class="folding ? 'bi-funnel-fill' : 'bi-funnel'"></i>
            Fold libraries
          </button>
          <button
            type="button"
            class="btn btn-sm btn-outline-secondary"
            title="Copy the whole stack"
            @click.stop="copy"
          >
            <i class="bi" :class="copied ? 'bi-check-lg' : 'bi-clipboard'"></i>
            {{ copied ? 'Copied' : 'Copy' }}
          </button>
        </span>
      </div>

      <!--
        The line a JVM prints above the frames. Not chrome repeated inside the panel: `class: message`
        followed by indented frames is the shape every stack-trace reader knows, and without it what
        is below is a list of frames rather than a stack trace. It is also the one row here that is
        not a frame, so it is not indented under `at` and does not carry a source column.
      -->
      <div v-if="thrownClass" class="st-thrown">
        <span class="st-thrown-cls">{{ thrownClass }}</span
        ><template v-if="message"
          >: <span class="st-thrown-msg">{{ message }}</span></template
        >
      </div>

      <div class="st-rows">
        <template v-for="entry in entries" :key="entry.kind + '-' + entry.depth">
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
            <span class="st-src">{{ frameLocation(entry.frame) }}</span>
          </div>
        </template>
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
  frameLocation,
  stackTraceText,
  shownFrameCount,
  unfoldedStack,
  type StackEntry
} from '@/services/trace/traceStackFolding';

const props = defineProps<{
  profileId: string;
  /** Null when the throw carried no stack, which renders as a sentence rather than as nothing. */
  stacktraceId: string | null;
  /**
   * The class that was thrown. Not decoration: it is what locates the end of the constructor chain
   * JFR puts on top of every throw, and so which frame wears the throwing mark. Without it the mark
   * falls back to the top frame, which is `Throwable.<init>`.
   */
  thrownClass?: string | null;
  /** The throw's message, which the JVM line carries after the class. Null is common and fine. */
  message?: string | null;
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
    return unfoldedStack(frames.value, props.thrownClass);
  }
  return foldedStack(frames.value, props.thrownClass).flatMap(entry => {
    if (entry.kind === 'fold' && opened.value.has(entry.depth)) {
      // The bar stays above what it opened, so the reader can see what they opened and close it.
      return [entry, ...expandFold(entry)];
    }
    return [entry];
  });
});

const shown = computed(() => shownFrameCount(entries.value));

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

const copied = ref(false);

/** Hands the whole stack to the clipboard in the shape a JVM prints it. */
async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(
      stackTraceText(frames.value, props.thrownClass, props.message)
    );
    copied.value = true;
    window.setTimeout(() => (copied.value = false), 1500);
  } catch {
    // A denied clipboard is the browser's decision, not a fault to report — the reader can still
    // select the rows. Silently leaving the button unchanged says "that did not happen".
  }
}
</script>

<style scoped>
/*
 * 0.78rem over 0.68rem is not an arbitrary pair and not a missing token: it is what
 * ProfileHeapDumpThreads draws its thread-dump frame list at, for this same content. Two stack views
 * in one app that disagree about how big a frame is would be the actual defect, so these follow it
 * rather than the nearest token. Do not "fix" them back to --font-size-sm / --font-size-xs.
 */
.st {
  font-family: var(--font-family-monospace);
  font-size: 0.78rem;
  line-height: 1.5;
}

.st-note {
  padding: 0.3rem 0.4rem;
  color: var(--color-text-muted);
  font-family: inherit;
  font-size: var(--font-size-sm);
}

.st-note.is-error {
  color: var(--color-danger);
}

/* The throw itself, above the frames. Red because it is the throw and not a frame, and set flush
   left because the frames beneath it are the ones standing in for `at` lines. */
.st-thrown {
  padding: 0.18rem 0.4rem 0.22rem;
  margin: 0 0 0.15rem;
  border-left: 2px solid var(--color-danger);
  background: var(--color-danger-bg-lighter);
  line-height: 1.5;
}

.st-thrown-cls {
  font-weight: 700;
  color: var(--color-danger);
}

.st-thrown-msg {
  color: #475569;
}

.st-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.3rem 0.4rem 0.35rem;
  margin-bottom: 0.2rem;
  border-bottom: 1px solid var(--color-border-light);
  font-family: inherit;
  font-size: var(--font-size-sm);
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
  gap: 0.4rem;
  align-items: center;
}

/*
 * The buttons are the app's global .btn classes, so nothing is styled here beyond the icon gap
 * Bootstrap does not give them.
 */
.st-acts .btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
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
  color: var(--color-text-muted);
  font-size: 0.68rem;
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
  font-size: var(--font-size-sm);
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
