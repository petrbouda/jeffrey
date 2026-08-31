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
  { id: 'instants', text: 'Instants: the Other Half of a Trace', level: 2 },
  { id: 'notification-event', text: 'jeffrey.Notification', level: 2 },
  { id: 'emitting', text: 'Emitting a Notification', level: 2 },
  { id: 'cardinality', text: 'Keep the Constant Fields Constant', level: 2 },
  { id: 'pinning', text: 'Pinning to a Span', level: 2 },
  { id: 'exceptions-recording', text: 'Exceptions: What Is Recorded on a Span', level: 2 },
  { id: 'exceptions-analysis', text: 'Exceptions: What the Analysis Adds', level: 2 },
  { id: 'visualization', text: 'How Both Are Visualized', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const notificationExample = `NotificationEvent notification = new NotificationEvent();
notification.type = "CONNECTION_POOL_EXHAUSTED";     // one per KIND, screaming snake case
notification.message = "Connection pool has no idle connections";
notification.severity = Severity.HIGH.name();        // CRITICAL | HIGH | MEDIUM | LOW
notification.category = "RESOURCE";                  // e.g. PERFORMANCE, SECURITY, RESOURCE, AVAILABILITY
notification.source = "hikari";                      // the component that raised it
notification.attributes = EventAttributes.create()
        .put("pool", "orders")
        .put("inUse", 46)
        .json();
notification.emit();                                 // emit(), NOT commit()`;

const cardinalityExample = `// Wrong: a new constant-pool entry per occurrence, and nothing can search
// for "profile 4f2a" — an id inside a message is not indexed
notification.message = "Could not build profile " + profileId + " after " + ms + "ms";

// Right: one entry however often it is raised, and both values are searchable
notification.message = "Building a profile threw on a background thread";
notification.attributes = EventAttributes.create()
        .put("profileId", profileId)
        .put("durationMs", ms)
        .json();`;

const spanErrorExample = `// 1. Hand-written spans: an exception escaping the body does it all —
//    status=ERROR, errorType=the class name, rethrown unchanged
Tracer.run("payment.charge", SpanKind.CLIENT, () -> {
    throw new IllegalStateException("card declined");
});

// 2. Instrumented events: state the failure with failed(), then rethrow.
//    Never assign status directly.
try {
    result = doWork();
} catch (Exception e) {
    event.failed(e);          // status=ERROR + errorType="…IllegalStateException"
    throw e;
}

// 3. TracedEvents.emit does #2 for you — and the filler still runs on the
//    failure path, with a null result
TracedEvents.emit(event, () -> doWork(), (e, result) -> { … });

// 4. Derived verdicts (HTTP >= 400, gRPC != OK) only ever ESCALATE:
//    a transport failure recorded by failed() is never painted over
//    by a status code that never arrived.`;

const escapedExample = `// The span records only the CLASS NAME of the failure:
jeffrey.TraceSpan {
  name = "payment.charge"
  status = "ERROR"
  errorType = "com.acme.CardDeclinedException"
}

// The message, the instant, and the stack come from the JDK's own
// jdk.JavaExceptionThrow event, correlated back into the trace at
// analysis time — the throw whose class matches the span's errorType
// is marked "escaped": the one that failed the span.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Notifications &amp; Exceptions"
      icon="bi bi-exclamation-diamond"
    />

    <div class="docs-content">
      <p>Two things happen inside a trace that are not spans and never will be: what the application <em>said</em> while it ran, and what was <em>thrown</em> at it. Notifications are the first — a note your code writes into its own recording. Exceptions are the second — captured with no instrumentation at all.</p>

      <h2 id="instants">Instants: the Other Half of a Trace</h2>

      <p>A trace is made of two things. A <strong>span</strong> is an interval — a name, a kind, an outcome, a duration and a place in the tree. An <strong>instant</strong> (<code>AbstractTracedInstant</code>) is a moment: it has none of those, and the only structural thing it carries is <em>which span was open when it fired</em> (<code>traceId</code> + <code>enclosingSpanId</code>). An instant commits through <code>emit()</code>, not <code>commitSpan()</code>; a plain <code>commit()</code> still records it — simply with no trace context.</p>

      <h2 id="notification-event">jeffrey.Notification</h2>

      <p>A <code>jeffrey.Notification</code> is a note the application writes into its own recording — a threshold crossed, a cache warmed, a feature flag flipped, a circuit breaker opened — so that "what the process thought was happening" sits next to the samples showing what actually did. Its fields:</p>

      <table>
        <thead>
          <tr>
            <th>Field</th>
            <th>Meaning</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>type</code></td>
            <td>Identifier for this <em>kind</em> of notification (<code>HIGH_CPU_USAGE</code>, <code>CONNECTION_POOL_EXHAUSTED</code>) — the notification's name as well as its identity. There is deliberately no separate <code>title</code>: a short label for a kind is a function of its type and nothing else.</td>
          </tr>
          <tr>
            <td><code>message</code></td>
            <td>Detailed description — constant per kind, never per occurrence (see below)</td>
          </tr>
          <tr>
            <td><code>severity</code></td>
            <td><code>CRITICAL</code> | <code>HIGH</code> | <code>MEDIUM</code> | <code>LOW</code>. The whole of "how serious is this" — there is deliberately no second event type for the serious ones</td>
          </tr>
          <tr>
            <td><code>category</code></td>
            <td>e.g. <code>PERFORMANCE</code>, <code>SECURITY</code>, <code>RESOURCE</code>, <code>AVAILABILITY</code></td>
          </tr>
          <tr>
            <td><code>source</code></td>
            <td>The component or service that raised it</td>
          </tr>
          <tr>
            <td><code>attributes</code></td>
            <td>What varies per occurrence, as the same JSON map spans carry</td>
          </tr>
        </tbody>
      </table>

      <h2 id="emitting">Emitting a Notification</h2>

      <DocsCodeBlock :code="notificationExample" language="java" />

      <p><code>emit()</code> stamps the enclosing span's ids onto the event and commits. Ids the caller already set are left alone — so a notification raised on a pool thread for work belonging elsewhere carries the context it was <em>handed</em> rather than the one it happens to be running in. A notification committed with no span open simply carries zeroes and belongs to no trace (still recorded, still listed).</p>

      <h2 id="cardinality">Keep the Constant Fields Constant</h2>

      <p><code>type</code>, <code>severity</code>, <code>category</code> and <code>message</code> are the low-cardinality half of a notification, and they have to stay that way. <strong>JFR interns every distinct string in the per-chunk constant pool</strong> and stores each event's field as an index into it, so ten thousand notifications of one kind cost <em>one</em> pool entry per field. Splice an id into the message and it is a new entry every time — the fastest way there is to make a recording enormous.</p>

      <DocsCodeBlock :code="cardinalityExample" language="java" />

      <p>The same split decides what is <em>queryable</em>: attributes are flattened into the profile's searchable index — one row per distinct value — so an id in an attribute can be searched, and the same id inside a message cannot. It also lets Jeffrey store the text once: each distinct message goes into a hash-keyed dictionary and every notification keeps a reference, so a recording that raises one kind ten thousand times holds that sentence a single time.</p>

      <DocsCallout type="warning">
        <strong>The rule in one line:</strong> <em>the message says what kind of thing happened; the attributes say which one.</em> If two occurrences genuinely need to say different things, they are two <code>type</code>s — not one type with two messages. (Jeffrey's own emitter enforces this by construction: the message lives on the <code>NotificationType</code> constant and there is no way to set it per call.)
      </DocsCallout>

      <h2 id="pinning">Pinning to a Span</h2>

      <p>Sometimes a notification belongs to a span whose <code>ScopedValue</code> binding is already gone — a failure reported from a callback after the work completed. Since a committed span event still carries its ids, set them on the notification explicitly (from the span event or a kept <code>SpanContext</code>) before <code>emit()</code> — ids already present are never overwritten. Without that, the notification would land in whatever trace happened to be running, or in none.</p>

      <h2 id="exceptions-recording">Exceptions: What Is Recorded on a Span</h2>

      <p>Four mechanisms, all converging on the same two fields — <code>status = ERROR</code> and <code>errorType = &lt;the exception's class name&gt;</code>:</p>

      <DocsCodeBlock :code="spanErrorExample" language="java" />

      <p>Deliberately, <strong>no message and no stack trace is ever recorded on the span</strong> — the class name is the one part of an exception worth indexing. The rest is recovered at analysis time:</p>

      <DocsCodeBlock :code="escapedExample" language="text" />

      <h2 id="exceptions-analysis">Exceptions: What the Analysis Adds</h2>

      <p>Throws need no instrumentation and no ids at all. <code>jdk.JavaExceptionThrow</code> and <code>jdk.JavaErrorThrow</code> are already recorded by the JVM; a throw is always recorded on the thread that threw it, at the instant it threw, so the derivation attributes it to the <strong>innermost span whose window contains that instant on that thread</strong> — the same rule <router-link to="/docs/tracing/jdk-events">promoted blocking spans</router-link> follow. Nothing new is recorded, so this applies retroactively to existing profiles.</p>

      <ul>
        <li><strong>One throw per failed span is marked <em>escaped</em></strong>: the one whose class matches the span's own <code>errorType</code>. That is the throw that failed the span — a bare class name becomes a class name with a message, an instant and a stack behind it. Everything else was caught and stays drawn quietly.</li>
        <li><strong>Every throw carries its stack</strong>, folded for reading: runs of library frames (the runtime, Spring, Tomcat, reflection) collapse into one bar each, the frame that actually threw is marked, and <em>Copy</em> hands you a real <code>class: message</code> stack trace.</li>
        <li><strong>An <code>Error</code> is counted once, not three times.</strong> JFR emits these events from constructors rather than at the throw, and it instruments both <code>Throwable</code> and <code>Error</code> — so constructing an <code>Error</code> records three events: two <code>jdk.JavaExceptionThrow</code> and one <code>jdk.JavaErrorThrow</code>. Counted raw, every <code>Error</code> reads as three throws split across two findings. The derivation collapses them back to the one throwable and keeps the <code>jdk.JavaErrorThrow</code> row, so the event type still says <code>Error</code> rather than <code>Exception</code>.</li>
        <li><strong>Caught resolution failures are filtered out.</strong> The JVM throws at itself constantly while it works — the MethodHandle layer probing invoker species, libraries probing optional dependencies with <code>Class.forName</code>. Every <em>caught</em> throw in the <code>LinkageError</code>, <code>ReflectiveOperationException</code> and <code>java.lang.invoke</code> hierarchies is dropped from traces as noise; one that <em>escaped</em> and failed its span is kept whatever its class. The <router-link to="/docs/microscope/profiles/exceptions">Exceptions view</router-link> still counts every throw — its job is the whole picture, not one request's.</li>
      </ul>

      <h2 id="visualization">How Both Are Visualized</h2>

      <p>In the <router-link to="/docs/tracing/analysis">Trace Detail view</router-link>, notifications and exceptions are drawn twice on purpose:</p>

      <ul>
        <li><strong>On rails above the span rows</strong> — a notification is a <strong>diamond</strong> (coloured by severity), a throw is a <strong>cross</strong> (red when escaped). The rails sit outside the rows, so folding a subtree or filtering to the critical path can never take a mark away. Clicking a mark opens the entry — a notification with its attribute table, a throw with its folded stack docked across the full width.</li>
        <li><strong>As pins on the span they belong to</strong> — the pin says <em>which</em> span, which the rail cannot; the same entries are listed in the span's inline detail panel.</li>
      </ul>

      <p>Shape carries the family before colour does — a CRITICAL notification and an escaped throw are both red and mean entirely different things. Each family has its own toolbar toggle, so silencing the chatter never silences the failure. A folded row reports what it swallowed with hollow counts and a hidden-error dot.</p>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/notification-popover.webp" alt="The waterfall with notification and exception rails, a notification opened" />
        <figcaption>The rails above the rows, and a <code>PIPELINE_COMPLETED</code> notification opened &mdash; identity and severity on the left, its four attributes on the right, one click from the span that emitted it.</figcaption>
      </figure>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/exception-stack.webp" alt="A caught exception's stack trace docked across the waterfall with library frames folded" />
        <figcaption>A caught <code>SQLFeatureNotSupportedException</code> attributed to its span &mdash; the stack docks across the full width with library frames folded, so your own code stands out.</figcaption>
      </figure>

      <p>Notifications are searchable from <strong>Traces by Attributes</strong>: pick <code>jeffrey.Notification</code> as the event type and its keys appear beside the ones spans carry. A notification condition narrows the <em>trace</em> result set exactly as a span condition does — but the two are indexed apart on purpose: a notification's <code>severity</code> says something went wrong somewhere; a span's <code>status</code> says <em>that span</em> failed. Searching for <code>status = ERROR</code> never matches a notification that merely said so.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
