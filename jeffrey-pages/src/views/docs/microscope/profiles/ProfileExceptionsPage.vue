<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
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
  { id: 'rate-timeline', text: 'Rate Timeline', level: 2 },
  { id: 'application', text: 'Application', level: 2 },
  { id: 'origin', text: 'Application vs JVM Internal', level: 2 },
  { id: 'top-types', text: 'All Exceptions', level: 2 },
  { id: 'errors', text: 'Errors', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Exceptions"
      icon="bi bi-exclamation-octagon"
    />

    <div class="docs-content">
      <p>The Exceptions page surfaces how many throwables the application created during the recording, when it created them, and — when per-throw events are enabled — which exception classes dominate, where in the code they were thrown, and which of them are the JVM's own noise rather than the application's. It is the place to investigate exception storms, bursts of timeouts and the hidden cost of exceptions used as control flow.</p>

      <h2 id="overview">Overview</h2>
      <p>The header strip shows <strong>Total Throwables</strong> (every throwable constructed since JVM start, from the periodic statistics gauge), <strong>Sampled Throws</strong> and <strong>Errors</strong> (individual throw events JFR recorded, an <code>Error</code> counted once although JFR emits it as several events), <strong>Application Throws</strong> (the sampled throws that are not the JVM&rsquo;s own class-loading and linking noise), and the number of distinct thrown classes. An info icon next to the title opens a <em>Total Throwables vs Sampled Throws</em> explainer modal describing why the two counts differ.</p>

      <DocsCallout type="tip">
        <strong>Reading the numbers:</strong> Total Throwables is typically far larger than the sampled counts — it counts every constructed exception, including ones that are caught immediately. A high construction rate with few visible throws still means real allocation and stack-walk cost.
      </DocsCallout>

      <h2 id="rate-timeline">Rate Timeline</h2>
      <p>Exceptions created per second, computed as the per-sample delta of the cumulative <code>jdk.ExceptionStatistics</code> gauge. Sustained plateaus reveal exception-driven control flow; sharp spikes usually correlate with failures, retries, or restarts of downstream dependencies. This tab always has data — the statistics event is part of every default JFR configuration.</p>

      <h2 id="application">Application</h2>
      <p>The landing tab whenever the recording carries per-throw events. It lists <strong>throw sites</strong> — an exception class together with the frame that contained the <code>throw</code>, found below the exception&rsquo;s own constructor chain — with the JVM-internal throws left out, ranked by count with the number of distinct throwing threads. Different call paths that reach the same <code>throw</code> statement are one site, because that line is what a reader changes. Expand a row for the site&rsquo;s messages and its commonest stack, drawn by the same stack view as the trace drill-down: the throwing frame is marked, library frames fold away, and the stack can be copied.</p>
      <p>Library throws stay on this tab on purpose: a <code>SocketTimeoutException</code> raised inside an HTTP client or a failure inside a logging library is the application&rsquo;s problem too, even when no frame of its own threw it.</p>

      <h2 id="origin">Application vs JVM Internal</h2>
      <p>Every sampled throw is classified by who threw it. A throw is <strong>JVM internal</strong> when either rule matches, and <strong>Application</strong> otherwise:</p>
      <ul>
        <li><strong>The class</strong> is one of the JVM&rsquo;s resolution failures: the <code>LinkageError</code> family (<code>NoClassDefFoundError</code>, <code>NoSuchMethodError</code>, <code>ExceptionInInitializerError</code>, …), the <code>java.lang.invoke</code> throwables, or the <code>ReflectiveOperationException</code> family (<code>ClassNotFoundException</code>, <code>NoSuchMethodException</code>, <code>InvocationTargetException</code>, …). The method-handle layer raises these by the handful while linking an <code>invokedynamic</code> call site, and libraries do the same through <code>Class.forName</code> when they probe for an optional dependency.</li>
        <li><strong>The thrower</strong> — the frame that contained the <code>throw</code> — sits in a class loader (<code>jdk.internal.loader</code>, <code>java.lang.ClassLoader</code>, <code>URLClassLoader</code>, <code>SecureClassLoader</code>, Spring Boot&rsquo;s loader) or in the <code>java.lang.invoke</code> linking layer, whatever the class: an <code>IOException</code> raised while a loader probes a jar is the loader&rsquo;s business.</li>
      </ul>
      <p>The trace drill-down uses the same class list to leave caught JVM-internal throws out of a trace, so the two views agree on what counts as noise.</p>

      <h2 id="top-types">All Exceptions</h2>
      <p>Sampled throws grouped by exception class, with throw counts and the number of distinct throwing threads. A switch in the toolbar narrows the table to the application&rsquo;s classes or to the JVM-internal ones, and a class every throw of which was JVM-internal carries a <em>JVM internal</em> badge. Click any row to expand a searchable list of the messages captured for that class. The classic pattern to look for: one exception class with a huge count and a small set of messages — that is an exception being used as a branch.</p>

      <h2 id="errors">Errors</h2>
      <p>The same breakdown filtered to <code>java.lang.Error</code> subclasses (<code>NoSuchMethodError</code>, <code>OutOfMemoryError</code>, <code>NoClassDefFoundError</code>, …), with the same expandable per-class message list. Errors are rarely benign — even ones the framework swallows during startup are worth understanding.</p>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.ExceptionStatistics</code> — periodic cumulative throwable count (Rate Timeline, Overview; always enabled).</li>
        <li><code>jdk.JavaExceptionThrow</code> — one event per throwable constructed, with its stack (Application and All Exceptions tabs; often disabled in the JFR configuration, in which case the tabs show a notice). An <code>Error</code> emits it too, beside its own event; the page collapses those back to one throw.</li>
        <li><code>jdk.JavaErrorThrow</code> — individual Error throws (Errors tab; like the exception-throw event, may be disabled in the JFR configuration).</li>
      </ul>

      <DocsCallout type="info">
        <strong>Read the JFR canonical event list:</strong> the
        <a href="https://sap.github.io/jfrevents/" target="_blank" rel="noopener">SAP JFR Events catalog</a>
        documents the exception events and the exact fields each one carries.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
