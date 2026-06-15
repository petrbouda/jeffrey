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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'severity-model', text: 'Severity Model', level: 2 },
  { id: 'prerequisites-panel', text: 'Prerequisites Panel', level: 2 },
  { id: 'self-time-vs-subtree', text: 'Self-Time vs. Subtree Attribution', level: 2 },
  { id: 'guard-catalog', text: 'Guard Catalog', level: 2 },
  { id: 'configuration', text: 'Configuration', level: 2 },
  { id: 'cache-invalidation', text: 'Cache Invalidation', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const matcherSpecExample = `{
  "anchor": {
    "type": "AllOf",
    "of": [
      { "type": "Predicate", "op": "PREFIX",   "value": "com.acme." },
      { "type": "Not", "expr": { "type": "Predicate", "op": "CONTAINS", "value": "$Proxy" } }
    ]
  }
}`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Guardian"
      icon="bi bi-shield-check"
    />

    <div class="docs-content">
      <p>Guardian is Jeffrey's automated analysis layer — a curated, fully configurable set of rules that walk the profile's stacktraces, allocation bytes, and blocking durations and surface the ones that warrant human attention. It's the <strong>first page to open</strong> after processing a recording: it tells you whether the JVM looks healthy, where the hot spots are, and which deeper analysis pages to visit next.</p>

      <div class="docs-images-grid">
        <img src="/images/feature-screenshots/profile_guardian.png" alt="Guardian overview" class="docs-feature-screenshot" />
        <img src="/images/feature-screenshots/profile_guardian_2.png" alt="Guardian rule detail" class="docs-feature-screenshot" />
      </div>

      <h2 id="overview">Overview</h2>

      <p>Each guard targets a <strong>free-form JFR event type</strong> whose stacktraces it analyses — any
      stacktrace-carrying event works, including custom ones — and is grouped in the UI by its
      <strong>category</strong>. Guardian builds one (weighted) frame tree per event type present in the recording
      and runs every guard for that type over it; a guard reads samples or weight depending on its result type.
      The built-in guards cover the common dimensions:</p>

      <ul>
        <li><code>jdk.ExecutionSample</code> — CPU-time overheads (Logback CPU, Regex, Reflection, JIT, Safepoint, VM Operation, per-GC, …) and, by weight, the allocation overheads (Logback Alloc, Boxing, Collection, …).</li>
        <li><code>jdk.CPUTimeSample</code> — the same CPU-overhead checks for the JDK 25 CPU-time profiler.</li>
        <li><code>profiler.WallClockSample</code> — wall-time overheads (Reflection, Class Loading, Thread Sync, …).</li>
        <li><code>jdk.JavaMonitorEnter</code> — blocking/contention by duration weight (Lock Contention, I/O, DB Pool, HTTP Client, …).</li>
        <li><strong>Prerequisites</strong> — data-quality checks that help interpret everything else.</li>
      </ul>

      <p>Because the event type is just a string on each guard, you can point a custom guard at any other
      event type (e.g. <code>jdk.ObjectAllocationSample</code> or your own application event) from the UI.</p>

      <h2 id="severity-model">Severity Model</h2>

      <p>Every rule returns one of four severities:</p>

      <div class="severity-grid">
        <div class="severity-card severity-ok">
          <div class="severity-header"><i class="bi bi-check-circle-fill"></i> OK</div>
          <p>Ratio is at or below the info threshold. Nothing to worry about.</p>
        </div>
        <div class="severity-card severity-info">
          <div class="severity-header"><i class="bi bi-info-circle-fill"></i> INFO</div>
          <p>Above the info threshold but below the warning threshold. Worth a glance — often the canary before a real regression.</p>
        </div>
        <div class="severity-card severity-warning">
          <div class="severity-header"><i class="bi bi-exclamation-triangle-fill"></i> WARNING</div>
          <p>Above the warning threshold. Action recommended — the rule's explanation and solution fields tell you what to look at.</p>
        </div>
        <div class="severity-card severity-na">
          <div class="severity-header"><i class="bi bi-slash-circle-fill"></i> N/A</div>
          <p>Rule did not run. Missing event type, wrong GC, too few samples, or the recording source doesn't support the signal (e.g. async-profiler-only rules on a JDK-JFR recording).</p>
        </div>
      </div>

      <DocsCallout type="tip">
        The <strong>INFO band</strong> was added in Jeffrey 0.7 to soften the old OK-or-WARNING cliff. A rule whose ratio sits at 4.9 % no longer flips from silent to urgent at 5.0 % — it crosses INFO first, giving you advance warning. See <a href="#configuration">Configuration</a> for the property pair per rule.
      </DocsCallout>

      <h2 id="prerequisites-panel">Prerequisites Panel</h2>

      <p>Before any rule fires, Guardian produces three data-quality checks that live in a dedicated "Prerequisites" card. These aren't performance findings — they explain what Guardian can and can't tell you about this particular recording:</p>

      <table class="guardian-table">
        <thead>
          <tr><th>Check</th><th>Reads</th><th>What it tells you</th></tr>
        </thead>
        <tbody>
          <tr>
            <td><strong>Recording Duration</strong></td>
            <td><code>profilingStartedAt</code>/<code>profilingFinishedAt</code></td>
            <td>WARNING if shorter than 60 seconds. Sample-ratio heuristics noise out on very short recordings — warmup JIT activity dominates, so most ratios are wrong in both directions.</td>
          </tr>
          <tr>
            <td><strong>Event Coverage</strong></td>
            <td>Event type summaries</td>
            <td>How many of the Guardian event groups (CPU / CPU-Time / Allocation / Wall-Clock / Blocking) are present. Fewer groups → narrower coverage, more N/A rules.</td>
          </tr>
          <tr>
            <td><strong>Debug Symbols</strong></td>
            <td>Async-profiler metadata</td>
            <td>Whether the recording had debug symbols (JDK) and kernel symbols (OS) available. Missing symbols produce <code>[unknown_Java]</code>-style frames in some JVM-internal stacks and degrade some flame graphs. Only applies to async-profiler recordings.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="self-time-vs-subtree">Self-Time vs. Subtree Attribution</h2>

      <p>Guardian measures "how much of the profile was spent in X?" by walking the frame tree that matches X's frame matcher. There are two ways to count, and the right choice depends on whether X does its own work or merely dispatches:</p>

      <ul>
        <li><strong>Subtree (<code>SAMPLES</code> / <code>WEIGHT</code>)</strong> — attribute every sample under the matched frame's subtree to X. Correct when the matched frame itself performs the measured work: <code>Pattern.compile</code> actually compiles the regex, <code>Logback</code> actually formats and writes the log line, <code>StringBuilder</code> actually builds the string. The subtree IS the work.</li>
        <li><strong>Self (<code>SELF_SAMPLES</code> / <code>SELF_WEIGHT</code>)</strong> — walk the matched subtree but only count self-time of frames still in the matcher's namespace; stop descending as soon as the stack leaves it. Correct when the matched frame is a <strong>pass-through wrapper</strong> that immediately dispatches to user code. <code>Method.invoke</code> → <code>DirectMethodHandleAccessor</code> → your reflected target: the target's CPU is not reflection overhead, it's your own code.</li>
      </ul>

      <DocsCallout type="info">
        <strong>Why this matters.</strong> Before Jeffrey 0.7, the Reflection rule ran on <code>SAMPLES</code>. A reflective call into a slow target reported as 80 % reflection overhead. Since 0.7 the Reflection, Serialization, and Class Loading rules default to <code>SELF_SAMPLES</code> — they attribute only the wrapper's own dispatch glue, not the reflected method's body. If you see a sudden drop in those rules on the same recording after upgrading, that's why — the old number was misleading.
      </DocsCallout>

      <h2 id="guard-catalog">Guard Catalog</h2>

      <p>Full list of shipped rules by group. Metric column: <code>S</code> = CPU-sample fraction, <code>SS</code> = self-sample fraction, <code>W</code> = allocation-byte fraction, <code>D</code> = blocking-duration fraction, <code>ms</code> = per-event millisecond threshold.</p>

      <h3>Execution Sample group</h3>
      <table class="guardian-table">
        <thead><tr><th>Rule</th><th>Metric</th><th>Default warn</th><th>Notes</th></tr></thead>
        <tbody>
          <tr><td>Logback CPU Overhead</td><td>S</td><td>0.03</td><td></td></tr>
          <tr><td>Log4j CPU Overhead</td><td>S</td><td>0.03</td><td></td></tr>
          <tr><td>HashMap Collisions</td><td>S</td><td>0.04</td><td>Tree-node traversal in <code>HashMap.get/put</code>.</td></tr>
          <tr><td>Regex Overhead</td><td>S</td><td>0.04</td><td></td></tr>
          <tr><td>Class Loading Overhead</td><td>SS</td><td>0.05</td><td>Self-time only — user classloader bodies excluded.</td></tr>
          <tr><td>Reflection Overhead</td><td>SS</td><td>0.05</td><td>Self-time only — reflected target bodies excluded.</td></tr>
          <tr><td>Java Serialization Overhead</td><td>SS</td><td>0.05</td><td>Self-time only — user <code>readObject</code>/<code>writeObject</code> excluded.</td></tr>
          <tr><td>XML Parsing Overhead</td><td>S</td><td>0.05</td><td></td></tr>
          <tr><td>JSON Processing Overhead</td><td>S</td><td>0.05</td><td></td></tr>
          <tr><td>Exception Overhead</td><td>S</td><td>0.05</td><td>Counts <code>fillInStackTrace</code> — genuine exception-creation cost.</td></tr>
          <tr><td>String Concat Overhead</td><td>S</td><td>0.05</td><td></td></tr>
          <tr><td>Thread Synchronization Overhead</td><td>S</td><td>0.05</td><td></td></tr>
          <tr><td>Crypto / TLS Overhead</td><td>S</td><td>0.05</td><td></td></tr>
          <tr><td>Compress Overhead</td><td>S</td><td>0.05</td><td></td></tr>
          <tr><td>Finalizer / Cleaner Overhead</td><td>S</td><td>0.03</td><td><strong>New in 0.7.</strong> Material CPU in <code>java.lang.ref.Finalizer</code> / <code>jdk.internal.ref.Cleaner</code>.</td></tr>
          <tr><td>JIT Compilation</td><td>S</td><td>0.2</td><td>Async-profiler only.</td></tr>
          <tr><td>Deoptimization</td><td>S</td><td>0.05</td><td>Async-profiler only.</td></tr>
          <tr><td>Safepoint Overhead</td><td>S</td><td>0.05</td><td>Async-profiler only. Aggregate CPU — see also Safepoint Outliers for tail latency.</td></tr>
          <tr><td>VM Operation Overhead</td><td>S</td><td>0.05</td><td>Async-profiler only.</td></tr>
          <tr><td>Serial / Parallel / G1 / Shenandoah / Z / ZGenerational GC</td><td>S</td><td>0.1</td><td>Async-profiler only. Only the rule matching the active GC fires.</td></tr>
        </tbody>
      </table>

      <h3>Allocation group</h3>
      <table class="guardian-table">
        <thead><tr><th>Rule</th><th>Metric</th><th>Default warn</th><th>Notes</th></tr></thead>
        <tbody>
          <tr><td>Logback Allocation Overhead</td><td>W</td><td>0.10</td><td></td></tr>
          <tr><td>Log4j Allocation Overhead</td><td>W</td><td>0.05</td><td></td></tr>
          <tr><td>HashMap Collision Allocations</td><td>W</td><td>0.05</td><td></td></tr>
          <tr><td>Regex Allocations</td><td>W</td><td>0.05</td><td></td></tr>
          <tr><td>String Concat Allocations</td><td>W</td><td>0.05</td><td></td></tr>
          <tr><td>Exception Allocations</td><td>W</td><td>0.05</td><td></td></tr>
          <tr><td>Boxing Allocations</td><td>W</td><td>0.05</td><td></td></tr>
          <tr><td>Collection Allocations</td><td>W</td><td>0.05</td><td></td></tr>
          <tr><td>TLAB Waste</td><td>W</td><td>0.15</td><td><strong>New in 0.7.</strong> Fraction of allocation bytes routed outside TLAB — signals large-object churn or TLAB sizing pressure.</td></tr>
        </tbody>
      </table>

      <h3>Wall-Clock group</h3>
      <table class="guardian-table">
        <thead><tr><th>Rule</th><th>Metric</th><th>Default warn</th><th>Notes</th></tr></thead>
        <tbody>
          <tr><td>Logback Wall-Clock Overhead</td><td>S</td><td>0.03</td><td></td></tr>
          <tr><td>Log4j Wall-Clock Overhead</td><td>S</td><td>0.03</td><td></td></tr>
          <tr><td>HashMap Collisions</td><td>S</td><td>0.04</td><td></td></tr>
          <tr><td>Regex Overhead</td><td>S</td><td>0.04</td><td></td></tr>
          <tr><td>Reflection Wall-Clock Overhead</td><td>SS</td><td>0.05</td><td></td></tr>
          <tr><td>Exception Wall-Clock Overhead</td><td>S</td><td>0.05</td><td></td></tr>
          <tr><td>Crypto/TLS Wall-Clock Overhead</td><td>S</td><td>0.05</td><td></td></tr>
          <tr><td>Class Loading Wall-Clock Overhead</td><td>SS</td><td>0.05</td><td></td></tr>
          <tr><td>Thread Synchronization Wall-Clock Overhead</td><td>S</td><td>0.05</td><td></td></tr>
        </tbody>
      </table>

      <h3>Blocking group</h3>
      <table class="guardian-table">
        <thead><tr><th>Rule</th><th>Metric</th><th>Default warn</th><th>Notes</th></tr></thead>
        <tbody>
          <tr><td>Database Connection Pool Blocking</td><td>D</td><td>0.05</td><td>HikariCP, Tomcat, c3p0, etc.</td></tr>
          <tr><td>Lock Contention Blocking</td><td>D</td><td>0.05</td><td><code>ReentrantLock</code> / AQS.</td></tr>
          <tr><td>I/O Blocking</td><td>D</td><td>0.05</td><td>Socket and file I/O waits.</td></tr>
          <tr><td>HTTP Client Blocking</td><td>D</td><td>0.05</td><td>OkHttp, Apache HttpClient, JDK <code>HttpClient</code>, etc.</td></tr>
          <tr><td>Logback Blocking</td><td>D</td><td>0.05</td><td>Synchronous appender waits.</td></tr>
          <tr><td>Log4j Blocking</td><td>D</td><td>0.05</td><td>Synchronous appender waits.</td></tr>
        </tbody>
      </table>

      <h3>Metadata / latency-tail guards</h3>
      <table class="guardian-table">
        <thead><tr><th>Rule</th><th>Metric</th><th>Default warn</th><th>Notes</th></tr></thead>
        <tbody>
          <tr><td>Safepoint Outliers (p99)</td><td>ms</td><td>100</td><td><strong>New in 0.7.</strong> p99 of <code>jdk.SafepointBegin</code> durations — catches tail-latency pauses that aggregate-CPU rules miss.</td></tr>
          <tr><td>Virtual Thread Pinning</td><td>ms</td><td>20</td><td><strong>New in 0.7.</strong> Worst <code>jdk.VirtualThreadPinned</code> duration — a pinned vthread defeats the scheduler and consumes a carrier thread.</td></tr>
        </tbody>
      </table>

      <h2 id="configuration">Configuration</h2>

      <p>Guards are no longer hard-coded. Every guard is a row in a central <code>guardian_guards</code> table in the Microscope core database, seeded with all the built-in guards on first start (via the Flyway migration). Manage them from the <strong>Guardians</strong> page in the Microscope — edit any built-in guard, toggle it on/off, or add your own. Configuration is shared across all profiles, not per-recording.</p>

      <p>Each guard row carries its event type, category, result type, target-frame type, matching type, the INFO/WARNING thresholds, a per-guard minimum-sample gate, optional preconditions (event source, GC type), the summary/explanation/solution text, and a <strong>matcher spec</strong>. The matcher spec is a small JSON predicate tree — a leaf <code>Predicate</code> tests a frame name with one of <code>PREFIX</code>, <code>SUFFIX</code>, <code>CONTAINS</code>, <code>EQUALS</code>, or <code>REGEX</code>, and <code>AnyOf</code> / <code>AllOf</code> / <code>Not</code> compose leaves into arbitrary boolean expressions. This means a custom matcher can be expressed entirely from the UI without any code change:</p>

      <DocsCodeBlock :code="matcherSpecExample" language="json" />

      <p>For guards that must descend through intermediate frames (the GC guards), the spec adds a <code>traversal</code> of type <code>Descend</code> with <code>ByName</code> / <code>ByMatcher</code> steps; omitting it defaults to matching the anchor frame itself.</p>

      <p>The INFO and WARNING thresholds work as before — if the two are set equal, the INFO band collapses and the guard behaves as a binary OK/WARNING flip. Each guard also carries a <code>min_samples</code> gate (default <code>1000</code>) that prevents noisy results: its event type must have at least that many samples in the recording before the guard runs.</p>

      <DocsCallout type="tip">
        The built-in guard definitions and the table schema are seeded in <code>V001__init.sql</code> in <code>jeffrey-microscope/microscope-core-sql-persistence</code>; the matcher/traversal model is the <code>MatchExpr</code> / <code>TraversalStrategy</code> sealed types in <code>jeffrey-microscope/profiles/profile-guardian</code>.
      </DocsCallout>

      <h2 id="cache-invalidation">Cache Invalidation</h2>

      <p>Guardian results are cached per profile — the analysis only runs once per recording, subsequent views serve from DuckDB. The cache key is <strong>versioned</strong>: it contains an 8-hex fingerprint of the effective guard definitions. Editing, adding, disabling, or removing a guard automatically invalidates stale results on the next request — no manual cache flush, no recording re-import.</p>

      <DocsCallout type="info">
        Cache lifetime is per-profile, not per-instance. After changing a guard, just re-open the Guardian page on an already-imported profile — the cache hash will differ and Guardian will re-run.
      </DocsCallout>

      <DocsNavFooter
        :prev="{ title: 'Profiles', path: '/docs/microscope/profiles' }"
      />
    </div>
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.docs-images-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 1rem;
  margin: 1.5rem 0;
}

.docs-feature-screenshot {
  width: 100%;
  border-radius: 0.5rem;
  border: 1px solid var(--color-border, #e5e7eb);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.severity-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
  margin: 1rem 0 1.5rem;
}
.severity-card {
  padding: 1rem 1.25rem;
  border-radius: 0.5rem;
  border: 1px solid var(--color-border, #e5e7eb);
  background: var(--color-bg-card, #fff);
}
.severity-card p {
  margin: 0.5rem 0 0;
  font-size: 0.9rem;
  line-height: 1.5;
}
.severity-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 700;
  font-size: 0.95rem;
}
.severity-ok    { border-left: 3px solid #10b981; }
.severity-ok    .severity-header { color: #10b981; }
.severity-info  { border-left: 3px solid #3b82f6; }
.severity-info  .severity-header { color: #3b82f6; }
.severity-warning { border-left: 3px solid #f59e0b; }
.severity-warning .severity-header { color: #f59e0b; }
.severity-na    { border-left: 3px solid #9ca3af; }
.severity-na    .severity-header { color: #6b7280; }

.guardian-table {
  width: 100%;
  border-collapse: collapse;
  margin: 0.75rem 0 1.5rem;
  font-size: 0.88rem;
}
.guardian-table th,
.guardian-table td {
  padding: 0.6rem 0.75rem;
  text-align: left;
  border-bottom: 1px solid var(--color-border, #e5e7eb);
  vertical-align: top;
}
.guardian-table thead th {
  background: var(--color-bg-subtle, #f9fafb);
  font-weight: 600;
  font-size: 0.82rem;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--color-text-muted, #6b7280);
}
.guardian-table tbody tr:hover {
  background: var(--color-bg-hover, #f9fafb);
}
</style>
