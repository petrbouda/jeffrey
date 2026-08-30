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
import DocsSpanTree from '@/components/docs/DocsSpanTree.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'enabling', text: 'Turning It On', level: 2 },
  { id: 'filters', text: 'Writing a Filter', level: 2 },
  { id: 'span', text: 'What the Span Looks Like', level: 2 },
  { id: 'membership', text: 'A Traced Method Needs a Trace Around It', level: 2 },
  { id: 'nesting', text: 'Nesting, Adoption and Self Time', level: 2 },
  { id: 'reading', text: 'Reading Traced Methods in the Waterfall', level: 2 },
  { id: 'choosing', text: 'Method Tracing, @Traced or the Tracer API', level: 2 },
  { id: 'pitfalls', text: 'Pitfalls', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const enableCommand = `# JDK 25+. A second, settings-only recording (no filename=, so it writes
# nothing of its own) whose settings are unioned into whatever is already
# recording — JFR applies the union of every active filter. Line breaks are
# for reading; it is one unbroken option.
java -XX:StartFlightRecording:name=method-trace,maxage=30m,\\
jdk.MethodTrace#enabled=true,\\
jdk.MethodTrace#threshold=0ms,\\
jdk.MethodTrace#stackTrace=true,\\
jdk.MethodTrace#filter=com.acme.order.OrderService \\
     -jar app.jar`;

const filterForms = `com.acme.order.OrderService::charge      # one named method
com.acme.order.OrderService              # every method of the class
@org.springframework.web.bind.annotation.RestController
                                         # every method of every annotated class
::<clinit>                               # every static initializer
com.acme.order.OrderService;com.acme.billing.Ledger::post
                                         # several entries, semicolon-separated`;

const recordedEvent = `jfr print --events jdk.MethodTrace app.jfr

jdk.MethodTrace {
  startTime   = 10:10:00.020
  duration    = 214 ms
  method      = com.acme.order.OrderService.charge()
  eventThread = "http-nio-8080-exec-3"
  stackTrace  = [ com.acme.order.OrderController.submit() line: 64 ]  // the CALLER
}`;

const derivedSpan = `name         OrderService#charge          // shortened for the row
kind         INTERNAL                     // the application's own work
status       UNSET                        // the JVM records no outcome
synthesized  true                         // "Promoted from: jdk.MethodTrace"
attributes   —                            // nothing to attach; there is no API here
payload      { "method": "com.acme.order.OrderService#charge" }`;

const promotedSpans = [
  { depth: 0, name: 'POST /api/orders', kind: 'SERVER' as const, start: 0, duration: 300,
    event: 'jeffrey.HttpServerExchange', note: 'recorded' },
  { depth: 1, name: 'OrderController#submit', kind: 'INTERNAL' as const, start: 8, duration: 284,
    event: 'jdk.MethodTrace', note: 'promoted', color: 'var(--span-method)' },
  { depth: 2, name: 'OrderService#charge', kind: 'INTERNAL' as const, start: 20, duration: 214,
    event: 'jdk.MethodTrace', note: 'promoted', color: 'var(--span-method)' },
  { depth: 3, name: 'insert_payment', kind: 'CLIENT' as const, start: 40, duration: 174,
    event: 'jeffrey.JdbcInsert', note: 'adopted' },
  { depth: 4, name: 'Socket read', kind: 'CLIENT' as const, start: 60, duration: 121,
    event: 'jdk.SocketRead', color: 'var(--span-socket-io)' }
];
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="JFR Method Tracing"
      icon="bi bi-layers"
    />

    <div class="docs-content">
      <p>Every other route in this section puts something in your code — a <code>Tracer</code> call, an annotation, a filter you register. JFR method tracing (<strong>JEP&nbsp;520</strong>, JDK&nbsp;25) puts nothing anywhere: you name methods in the <em>recording configuration</em> and the JVM instruments them itself, writing a <code>jdk.MethodTrace</code> event per invocation. Jeffrey promotes each of those events into a span, so a method you named this morning is a bar inside its request this afternoon — with the application unchanged and un-redeployed.</p>

      <h2 id="overview">Overview</h2>

      <p>A <code>jdk.MethodTrace</code> event carries the method it traced and how long the call took, callees included. Jeffrey's trace derivation turns each one into a span that hangs under whatever was already open on that thread:</p>

      <DocsSpanTree
        trace="9c31af07…"
        :spans="promotedSpans"
        caption="Two traced methods promoted inside a recorded HTTP request — and the JDBC span re-hung underneath the method that actually issued it."
      />

      <p>Three things are worth reading off that picture before the details:</p>

      <ul>
        <li>the trace root and the JDBC leaf are <strong>recorded</strong> — they came from instrumentation, and method tracing does not replace it;</li>
        <li>the two traced methods are <strong>promoted</strong> — nothing in the application knows they exist;</li>
        <li><code>insert_payment</code> was recorded as a child of the HTTP span, and is drawn under <code>OrderService#charge</code> — the method that ran it. That re-hanging is <a href="#nesting">adoption</a>, and it is what keeps a wrapping method from claiming the whole request as its own time.</li>
      </ul>

      <DocsCallout type="warning">
        <strong>JDK 25 or newer.</strong> JEP&nbsp;520 landed in JDK&nbsp;25. An older JVM does not refuse to start — it prints <code>The .jfc option/setting 'jdk.MethodTrace#filter' doesn't exist</code> once per setting and runs on — but it records no method traces at all, so check the version before hunting for missing bars.
      </DocsCallout>

      <h2 id="enabling">Turning It On</h2>

      <p>Method tracing is four JFR settings on one event type. The least invasive way to deliver them is the same trick the <router-link to="/docs/provisioner">Provisioner</router-link> uses for <router-link to="/docs/tracing/jdk-events">trace-context thresholds</router-link> — a second recording that writes nothing itself and exists only so its settings join the ones already active:</p>

      <DocsCodeBlock :code="enableCommand" language="bash" />

      <table>
        <thead>
          <tr>
            <th>Setting</th>
            <th>What it does</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>filter</code></td>
            <td>Which methods the JVM instruments. No filter, no events — this is the whole feature.</td>
          </tr>
          <tr>
            <td><code>threshold</code></td>
            <td>The latency floor. <code>0ms</code> records every invocation; raise it for a method called many times per request.</td>
          </tr>
          <tr>
            <td><code>stackTrace</code></td>
            <td>Needed by the <router-link to="/docs/microscope/profiles">Method Traces flamegraph</router-link>, which stacks the calls. Span promotion reads the event's own <code>method</code> field and does not need it.</td>
          </tr>
          <tr>
            <td><code>enabled</code></td>
            <td>Named alongside the rest, since a threshold alone is ignored for an event type a custom profiler configuration switched off.</td>
          </tr>
        </tbody>
      </table>

      <p>The same four settings work in a <code>.jfc</code> configuration, through <code>jcmd JFR.start</code> on a running JVM, or through <code>jdk.jfr.Recording</code> in code. Jeffrey does not care which produced the events — it reads the recording.</p>

      <DocsCallout type="tip">
        Jeffrey's own <code>run-microscope.sh</code> runs exactly this, filtering on <code>@RestController</code> — one line that covers every controller in the codebase and needs no maintenance when a controller is added. Because each controller runs inside the HTTP span the tracing starter already opens, every one shows up as a bar inside its own request.
      </DocsCallout>

      <h2 id="filters">Writing a Filter</h2>

      <p>A filter is a list of entries. An entry names a method, a class, or an annotation — and the annotation form is the one that changes how the feature is used, because it selects by <em>role</em> rather than by name:</p>

      <DocsCodeBlock :code="filterForms" language="text" />

      <ul>
        <li><strong>Semicolons separate entries, never commas.</strong> JFR splits its own command-line options on commas, so a comma inside a filter ends the filter.</li>
        <li><strong>There are no wildcards.</strong> JEP&nbsp;520 rejected them deliberately — a class-wide or annotation-wide entry is the intended way to cover more than one method.</li>
        <li><strong>Breadth costs bars, not just bytes.</strong> A filter matching a method called thousands of times per request produces thousands of spans in one trace. Prefer entries at the granularity of a unit of work — a controller, a service entry point, a pipeline stage — and raise <code>threshold</code> for the rest.</li>
      </ul>

      <h2 id="span">What the Span Looks Like</h2>

      <p>This is what the JVM recorded:</p>

      <DocsCodeBlock :code="recordedEvent" language="text" />

      <p>and this is the span Jeffrey derives from it:</p>

      <DocsCodeBlock :code="derivedSpan" language="text" />

      <p>Two of those lines are less obvious than they look.</p>

      <p><strong>The name comes from the event's <code>method</code> field</strong>, not from its stack trace. JEP&nbsp;520 roots a <code>jdk.MethodTrace</code> stack at the <em>caller</em>, so the leaf frame names the method that made the call rather than the one being traced — a span named from the stack would call <code>OrderService#charge</code> "OrderController#submit". The package is dropped for the row, because a waterfall row is a few centimetres wide and a qualified name pushes the timing off the end of it; the full name stays in the payload the detail panel shows. The <code>#</code> is JFR's own separator, kept because it says "a method" in a span name the way <code>{service}/{method}</code> says "an RPC".</p>

      <p><strong>The kind is <code>INTERNAL</code> and there is no status</strong>, because the JVM records neither an outcome nor a peer: a traced method is the application's own code running, and it is not a call out to anything. An exception escaping it does not turn the bar red — only instrumentation records an outcome, so use <router-link to="/docs/tracing/traced-annotation">@Traced</router-link> or the <router-link to="/docs/tracing/tracer-api/run">Tracer API</router-link> where the failure matters.</p>

      <h2 id="membership">A Traced Method Needs a Trace Around It</h2>

      <p>Method tracing produces spans, not traces. Which trace a <code>jdk.MethodTrace</code> event belongs to is decided by the <strong>innermost recorded span open on the same thread</strong> when the method started — a method traced on a thread with no span open is not promoted at all, because there is no trace to hang it on. Trace identity comes from instrumentation and only from instrumentation: a method span is inside a trace because a recorded span contains it, never because another method span does.</p>

      <DocsCallout type="info">
        <strong>Pair it with something that opens spans.</strong> On a Spring application the <router-link to="/docs/tracing/spring-support">tracing starter</router-link> alone is enough — its HTTP filter opens a span per request, and every traced method inside one lands in that request's waterfall. Without a root, method tracing still fills the <router-link to="/docs/microscope/profiles">Method Tracing</router-link> dashboard and flamegraph; it just contributes nothing to Traces.
      </DocsCallout>

      <h2 id="nesting">Nesting, Adoption and Self Time</h2>

      <p>A traced method's duration <em>includes the methods it calls</em>. That single fact makes this the one promotion that is not a leaf, and it has two consequences the derivation handles for you.</p>

      <p><strong>Method spans nest into each other.</strong> Trace two methods where one calls the other and the inner one is drawn under the outer one — with any promoted wait that happened inside it under that. A method span is therefore the only promoted span that can be a parent. Drawn as siblings instead, the outer bar would hold the callee's time as if it were its own.</p>

      <p><strong>Recorded spans are adopted into them.</strong> A recorded span names the span the Tracer's context held when it was created, and <code>jdk.MethodTrace</code> is invisible to that context — so a traced controller method wrapping an entire request would otherwise draw <em>beside</em> the spans it contains. Jeffrey re-hangs such a recorded span under the innermost method span standing between it and its own recorded parent. The rule is deliberately narrow: the method span must itself hang under exactly the span the child names as its parent, and both must be on the same thread. A method span only ever slots in between a recorded parent and its children — it never moves a span across recorded ancestry, and never onto another thread.</p>

      <p><strong>Self time follows the tree that results.</strong> Each span's own time is its duration minus the stretches its same-thread children covered, so the wrapping method in the example above is charged for the milliseconds it spent outside the JDBC call, not for the whole request. Nothing is lost by the subtraction: the inner call keeps its time one level down, and the totals reconstitute.</p>

      <DocsCallout type="info">
        <strong>A traced method is work, not waiting.</strong> It maps to no context category and never appears in the why-slow panel. That panel accounts for the time a trace spent <em>not</em> doing its own work; a traced method's time is precisely the trace's own work, and giving it a category would move that time out of "own work" and into a wait total that never happened.
      </DocsCallout>

      <h2 id="reading">Reading Traced Methods in the Waterfall</h2>

      <ul>
        <li><strong>Their own toolbar switch.</strong> <em>Methods</em> sits beside <em>I/O ops</em> and <em>Blocking ops</em>, on by default — a filter set to trace a whole class can put far more rows on screen than either wait family, and switching those off must not take the socket read that explains the trace with them. Switching methods off takes their promoted subtrees too, while the recorded spans a method adopted resurface where instrumentation put them.</li>
        <li><strong>Green, with a dashed outline.</strong> The green is the own-work green: the same quantity the trace-level "own work" total counts, seen per method. The dashed outline says the span was derived rather than recorded. The name renders <code>Class</code> in plain text, a grey <code>#</code>, and the method in bold.</li>
        <li><strong>Ordinary spans everywhere it matters.</strong> They count in the trace's span total, they rank in the operation's span breakdown as "Traced method", and they subtract from their parent's self time.</li>
        <li><strong>Never repeated in "Events in span".</strong> The drill-down that lists JVM events inside a span's window leaves out the promoted event types — a traced method is already a child bar of the span being opened.</li>
      </ul>

      <h2 id="choosing">Method Tracing, @Traced or the Tracer API</h2>

      <table>
        <thead>
          <tr>
            <th></th>
            <th>JFR method tracing</th>
            <th><router-link to="/docs/tracing/traced-annotation">@Traced</router-link></th>
            <th><router-link to="/docs/tracing/tracer-api/run">Tracer API</router-link></th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Touches the application</td>
            <td>Nothing — a recording setting</td>
            <td>An annotation plus the agent</td>
            <td>Code around the work</td>
          </tr>
          <tr>
            <td>Granularity</td>
            <td>Whole methods, named or annotated</td>
            <td>Whole methods, one at a time</td>
            <td>Any block, however small</td>
          </tr>
          <tr>
            <td>Name and kind</td>
            <td>The method; always <code>INTERNAL</code></td>
            <td>Yours, or derived</td>
            <td>Yours</td>
          </tr>
          <tr>
            <td>Attributes, errors, forking</td>
            <td>None</td>
            <td>Attributes and <code>ERROR</code> status</td>
            <td>Everything</td>
          </tr>
          <tr>
            <td>Changed without a redeploy</td>
            <td>Yes</td>
            <td>No</td>
            <td>No</td>
          </tr>
          <tr>
            <td>Requires</td>
            <td>JDK 25+</td>
            <td>The Jeffrey Agent</td>
            <td>Java 25+</td>
          </tr>
        </tbody>
      </table>

      <p>They compose: the usual shape is instrumentation for the operations that define a trace, and method tracing switched on temporarily to see inside one that is slow for a reason nobody instrumented for.</p>

      <h2 id="pitfalls">Pitfalls</h2>

      <ul>
        <li><strong>Two engines writing the same event.</strong> async-profiler's <code>trace=pkg.Class.method</code> also emits <code>jdk.MethodTrace</code>. A method named by both is recorded twice — two identical bars in one waterfall, and every count for it doubled in the dashboard. Name a method in one engine only.</li>
        <li><strong>A method that runs outside every span produces no bar.</strong> That is not a bug in the filter; see <a href="#membership">above</a>.</li>
        <li><strong>An overlapping filter double-counts in aggregates, not in the waterfall.</strong> When a filter matches two methods on one stack, each invocation of the inner one is measured again inside the outer one's duration. The aggregate views handle it by summing <em>self</em> time; a waterfall bar is one invocation and keeps its whole call's duration, with the wash showing how much of it was its own.</li>
        <li><strong>The commonest filter mistake is a comma.</strong> <code>jdk.MethodTrace#filter=A,B</code> silently traces only <code>A</code> — everything after the comma is parsed as further JFR options.</li>
        <li><strong>Retrofitting an old recording does not work.</strong> Unlike the <router-link to="/docs/tracing/jdk-events">promotion of JDK wait events</router-link>, which is pure analysis over events every recording already holds, method tracing has to have been switched on when the recording was taken.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
