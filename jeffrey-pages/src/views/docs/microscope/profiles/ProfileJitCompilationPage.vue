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
import { onMounted } from 'vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'tiers', text: 'Compilation Tiers', level: 2 },
  { id: 'activity', text: 'Activity', level: 2 },
  { id: 'long-compilations', text: 'Long Compilations', level: 2 },
  { id: 'code-cache', text: 'Code Cache', level: 2 },
  { id: 'deoptimizations', text: 'Deoptimizations', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="JIT Compilation"
      icon="bi bi-lightning"
    />

    <div class="docs-content">
      <p>The JIT Compilation pages show how HotSpot turned your hot bytecode into native code during the recording — how much compilation work happened and when, which methods took the longest to compile, how full the code cache became, and how often the JIT had to throw compiled code away and fall back to the interpreter. The feature is split into two views: <strong>JIT Compilation</strong> (compilation activity, long compilations, code cache) and <strong>JIT Deoptimizations</strong> (when and why optimized code was discarded). Together they are the place to understand warmup behaviour, compilation churn, code-cache exhaustion, and speculation that didn't pay off.</p>

      <h2 id="overview">Overview</h2>
      <p>The JIT Compilation header strip surfaces the headline numbers — total compilations (split into <em>Standard</em> vs <em>OSR</em>), failed compilations (split into <em>Bailouts</em> vs <em>Invalidations</em>), nMethods memory usage (<em>Code</em> vs <em>Metadata</em>), and peak compilation time. Each metric carries an info icon that opens a modal explaining the distinction it draws. The Deoptimizations view has its own strip — total deopts with rate, the top deopt reason, the hottest deopted method, and the C1/C2 compiler mix.</p>

      <DocsCallout type="tip">
        <strong>Warmup is real:</strong> an early burst of compilation activity that tapers off is normal JVM warmup. Compilation that stays busy long after startup — or a code cache that keeps filling — is the signal worth chasing.
      </DocsCallout>

      <h2 id="tiers">Compilation Tiers</h2>
      <p>HotSpot uses tiered compilation. Understanding the tiers makes every chart on these pages readable:</p>
      <ul>
        <li><strong>Tier 0 — Interpreter:</strong> bytecode runs interpreted while the JVM gathers profiling data.</li>
        <li><strong>Tiers 1–3 — C1 (client):</strong> fast, lightly optimized native code produced quickly to get methods off the interpreter, with profiling counters at tier 3.</li>
        <li><strong>Tier 4 — C2 (server):</strong> heavily optimized native code for the hottest methods, compiled from the profile collected at lower tiers. Slower to produce, fastest to run.</li>
        <li><strong>On-Stack Replacement (OSR):</strong> compilation of a long-running loop <em>while it is still executing</em>, swapping the running frame from interpreted to compiled mid-flight. OSR compilations are flagged separately because they have different triggers than standard method-entry compilation.</li>
      </ul>

      <h2 id="activity">Activity</h2>
      <p>A time-series of compilation work sampled by the CPU profiler across the recording, plus — when <code>jdk.CompilerQueueUtilization</code> data is present — C1 and C2 compiler queue depth over time. An early activity burst is normal warmup; sustained activity afterward can signal compilation churn (methods repeatedly compiled and discarded). A persistent C2 queue backlog during warmup means hot methods are waiting in line to be optimized — they keep running in slower C1 code until the queue drains.</p>

      <h2 id="long-compilations">Long Compilations</h2>
      <p>A sortable table of individual compilations that exceeded the long-compilation threshold (shown as a badge), built from <code>jdk.Compilation</code> events. Each row shows the compile ID, method, tier level, compile time, generated code size, compiler (C1/C2), an OSR marker, and a success/failed status — failed compilations are highlighted. Use it to find methods so large or complex that compiling them is itself expensive, and to spot compilations that failed (which leave the method running in a lower tier or interpreted).</p>

      <h2 id="code-cache">Code Cache</h2>
      <p>A per-heap breakdown of the code cache from <code>jdk.CodeCacheStatistics</code> — used vs reserved bytes, method and adaptor counts, and a per-heap full count. If <code>jdk.CodeCacheFull</code> fired during the recording, a red <em>Code Cache Full</em> badge appears with the count.</p>

      <DocsCallout type="warning">
        <strong>Code cache full is serious:</strong> when the code cache fills, the JIT shuts off and every not-yet-compiled method runs interpreted for the rest of the JVM's life — a large, permanent throughput loss. The usual fix is raising <code>-XX:ReservedCodeCacheSize</code>.
      </DocsCallout>

      <h2 id="deoptimizations">Deoptimizations</h2>
      <p>The JIT Deoptimizations view tracks every <code>jdk.Deoptimization</code> event — moments when the JVM discarded optimized native code and fell back to the interpreter because a speculative assumption it had compiled in turned out to be wrong (an unexpected class appeared at a call site, a never-taken branch fired, a class was loaded that invalidated an optimization). A handful during warmup is healthy speculation settling down; sustained deoptimization of the same method is wasted work. The view has four tabs:</p>
      <ul>
        <li><strong>Activity</strong> — deoptimizations over time. A burst during warmup that fades is normal; a steady stream in steady state is a problem.</li>
        <li><strong>Events</strong> — the most recent per-event detail (time, method, line, bytecode index, reason, compiler). Click a row for the full payload.</li>
        <li><strong>Top Methods</strong> — the methods deopted most often, the place to find a hot method stuck in a compile-deopt-recompile cycle.</li>
        <li><strong>Reason Distribution</strong> — deopts grouped by reason (e.g. <code>class_check</code>, <code>null_check</code>, <code>bimorphic_or_optimized</code>), so you can tell <em>why</em> the JIT keeps guessing wrong.</li>
      </ul>

      <DocsCallout type="tip">
        <strong>What to worry about:</strong> the same method deopting hundreds of times, or repeated <code>class_check</code> / <code>bimorphic_or_optimized</code> reasons, point at a megamorphic call site — too many concrete types flowing through one virtual call for the JIT to optimize. The <em>How It Works</em> tab on the page carries a full reasons-and-actions reference.
      </DocsCallout>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.Compilation</code> — individual method compilations: tier, duration, code size, OSR flag, success (Long Compilations, Activity).</li>
        <li><code>jdk.CompilerQueueUtilization</code> — C1/C2 compiler queue depth over time (Activity queue chart).</li>
        <li><code>jdk.CodeCacheStatistics</code> — per-heap code-cache occupancy (Code Cache, Overview).</li>
        <li><code>jdk.CodeCacheFull</code> — emitted when a code-cache heap fills (Code Cache full badge).</li>
        <li><code>jdk.CompilerConfiguration</code> — compiler thread counts and tiered-compilation settings.</li>
        <li><code>jdk.Deoptimization</code> — individual deoptimizations: method, bytecode index, reason, action, compiler (all Deoptimizations tabs).</li>
      </ul>

      <DocsCallout type="info">
        <strong>Read the JFR canonical event list:</strong> the
        <a href="https://sap.github.io/jfrevents/" target="_blank" rel="noopener">SAP JFR Events catalog</a>
        documents every compiler and deoptimization event the JDK emits and the exact fields each one carries.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
