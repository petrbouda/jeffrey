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
  { id: 'setup', text: 'Setup', level: 2 },
  { id: 'annotation', text: 'The @Traced Annotation', level: 2 },
  { id: 'arguments', text: 'Capturing Method Arguments', level: 2 },
  { id: 'weaving', text: 'How the Weaving Works', level: 2 },
  { id: 'requirements', text: 'Requirements and Fail-Open Behavior', level: 2 },
  { id: 'provisioner', text: 'Provisioner Integration', level: 2 },
  { id: 'pitfalls', text: 'Pitfalls', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const basicExample = `import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.Traced;

@Traced
Receipt checkout(String orderId, Card card) { ... }
// span name: "OrderService.checkout"  (derived), kind INTERNAL

@Traced(name = "order.checkout", kind = SpanKind.SERVER)
Receipt checkout(String orderId, Card card) { ... }
// span name: "order.checkout", kind SERVER`;

const agentCommand = `java -javaagent:/path/to/jeffrey-agent.jar=tracing.enabled=true -jar app.jar

# combined with the heartbeat feature (what the Provisioner generates):
java -javaagent:/path/to/jeffrey-agent.jar=heartbeat.dir=/sessions/s-123/.heartbeat,tracing.enabled=true -jar app.jar`;

const fullExample = `@Traced(name = "order.charge", kind = SpanKind.CLIENT,
        args = {"tier=gold"}, includeMethodArgs = {"orderId"})
void charge(String orderId, long amountInCents) {
    gateway.charge(orderId, amountInCents);
}`;

const fullExampleOutput = `jeffrey.TraceSpan {
  duration = 42.1 ms
  traceId = 6872570733206835563        // whatever span was in progress on the thread
  spanId = 2231855272711412124
  parentSpanId = 4444722480460712002
  name = "order.charge"
  kind = "CLIENT"
  status = "UNSET"
  attributes = "{\\"tier\\":\\"gold\\",\\"orderId\\":\\"a-1\\"}"
}`;

const refusalExample = `// Refused when the class is woven: nothing but a Card can arrive, so the
// warning names it once instead of every call.
//   WARNING  Not capturing a @Traced argument whose type cannot be recorded:
//            method=com.acme.OrderService parameter=card type=com.acme.Card
@Traced(name = "order.charge", includeMethodArgs = {"orderId", "card"})
void charge(String orderId, Card card) { ... }
// attributes = {"orderId":"a-1"}

// Undecidable at weave time: a String may well turn up under Object, so the
// parameter is kept and the value settles it at the call.
@Traced(name = "cache.put", includeMethodArgs = {"key"})
void put(Object key, Object value) { ... }
// put("user:42", ...)  ->  attributes = {"key":"user:42"}
// put(new Card(...), ...) ->  attributes = {"key":"<unsupported>"}

// A sweep: every capturable parameter, no warning for the rest.
@Traced(name = "order.charge", includeMethodArgs = {"*"})
void charge(String orderId, long amountInCents, Card card) { ... }
// attributes = {"orderId":"a-1","amountInCents":4200}`;

const errorExample = `@Traced(name = "order.charge")
void charge(String orderId) {
    throw new IllegalStateException("card declined");
}

// The woven span records:
//   status    = ERROR
//   errorType = "java.lang.IllegalStateException"
// and the caller receives the SAME exception instance — the agent
// invokes through a MethodHandle with invokeExact, so no wrapper
// (no InvocationTargetException) ever appears.`;

const equivalence = `// These two record byte-for-byte the same jeffrey.TraceSpan:

@Traced(name = "report.render", kind = SpanKind.INTERNAL)
Report render(Input input) { return doRender(input); }

Report render(Input input) {
    return Tracer.call("report.render", SpanKind.INTERNAL, () -> doRender(input));
}`;

const provisionerConfig = `# provisioner.conf (HOCON)
tracing { enabled = true }`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="@Traced &amp; the Jeffrey Agent"
      icon="bi bi-braces"
    />

    <div class="docs-content">
      <p>Every <router-link to="/docs/tracing/instrumentation">Tracer</router-link> method wraps work in a lambda — precise, and visible in the code. <code>@Traced</code> declares the same span on a method instead, and the <strong>Jeffrey Agent</strong> weaves it in at class load: the method is left exactly as it was written, and is not written around its own tracing.</p>

      <h2 id="overview">Overview</h2>

      <DocsCodeBlock :code="basicExample" language="java" />

      <p>A woven method emits the same <code>jeffrey.TraceSpan</code> event as <code>Tracer.call</code> — the two forms are indistinguishable in the recording:</p>

      <DocsCodeBlock :code="equivalence" language="java" />

      <p>The span nests under whatever span is in progress on the thread — an inbound HTTP request, a job, another traced method — and records as its own root when there is none. A method that throws marks its span failed with the exception's type, and the exception reaches the caller untouched.</p>

      <h2 id="setup">Setup</h2>

      <p>Two things are needed: the annotation comes from the <code>jeffrey-events</code> library (a compile dependency your code already has if it uses <code>Tracer</code>), and the weaving comes from the agent, attached at startup with tracing switched on:</p>

      <DocsCodeBlock :code="agentCommand" language="bash" />

      <p>Agent arguments are comma-separated <code>key=value</code> pairs:</p>

      <table>
        <thead>
          <tr>
            <th>Parameter</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>tracing.enabled</code></td>
            <td><code>false</code></td>
            <td>Set to <code>true</code> to weave <code>@Traced</code> methods into spans</td>
          </tr>
          <tr>
            <td><code>heartbeat.dir</code></td>
            <td>—</td>
            <td>Directory for the agent's liveness file (the agent's other, unrelated feature — see <router-link to="/docs/agent/overview">Jeffrey Agent</router-link>)</td>
          </tr>
          <tr>
            <td><code>heartbeat.interval</code></td>
            <td><code>5000</code></td>
            <td>Heartbeat interval in milliseconds</td>
          </tr>
          <tr>
            <td><code>heartbeat.enabled</code></td>
            <td><code>true</code></td>
            <td>Set to <code>false</code> to disable heartbeating</td>
          </tr>
        </tbody>
      </table>

      <p>There are no system properties and no config files — the agent argument string is the whole configuration.</p>

      <h2 id="annotation">The @Traced Annotation</h2>

      <p><code>cafe.jeffrey.jfr.events.trace.Traced</code> targets methods and carries five attributes:</p>

      <table>
        <thead>
          <tr>
            <th>Attribute</th>
            <th>Default</th>
            <th>Meaning</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>name</code></td>
            <td>derived</td>
            <td>The span name. Empty derives <code>SimpleClassName.methodName</code> (package and enclosing-class prefixes dropped), which is low-cardinality by construction</td>
          </tr>
          <tr>
            <td><code>kind</code></td>
            <td><code>INTERNAL</code></td>
            <td>What kind of work this is, exactly as the <code>SpanKind</code> passed to <code>Tracer.run</code></td>
          </tr>
          <tr>
            <td><code>args</code></td>
            <td>none</td>
            <td>Fixed <code>"key=value"</code> attributes recorded on every call</td>
          </tr>
          <tr>
            <td><code>includeMethodArgs</code></td>
            <td>none</td>
            <td>Which parameters to record, by name &mdash; a list of what may be recorded rather than of what may not. <code>{"*"}</code> records every parameter; it is not a legal Java identifier, so it can never collide with one</td>
          </tr>
        </tbody>
      </table>

      <p>A full example and the event it records:</p>

      <DocsCodeBlock :code="fullExample" language="java" />
      <DocsCodeBlock :code="fullExampleOutput" language="text" />

      <p>And the failure path:</p>

      <DocsCodeBlock :code="errorExample" language="java" />

      <h2 id="arguments">Capturing Method Arguments</h2>

      <h3 id="capturable">What May Be Captured</h3>

      <p>Naming a parameter is a request, not a guarantee. Only values whose textual form is part of what the type <em>is</em> are recorded, rather than something written for a debugger:</p>

      <ul>
        <li>primitives and their boxes, <code>char</code>/<code>Character</code> included</li>
        <li><code>CharSequence</code> &mdash; <code>String</code>, <code>StringBuilder</code> and anything else textual</li>
        <li>every <code>enum</code> constant, bodied constants included</li>
        <li><code>UUID</code></li>
        <li><code>BigDecimal</code> and <code>BigInteger</code></li>
        <li>the <code>java.time</code> value types &mdash; <code>Instant</code>, <code>LocalDate</code>, <code>LocalTime</code>, <code>LocalDateTime</code>, <code>OffsetTime</code>, <code>OffsetDateTime</code>, <code>ZonedDateTime</code>, <code>Duration</code>, <code>Period</code>, <code>Year</code>, <code>YearMonth</code>, <code>MonthDay</code>, <code>ZoneOffset</code></li>
      </ul>

      <p>This is deliberately not &ldquo;primitives and Strings&rdquo;. An amount, an id, a timestamp and an enum are exactly what belongs on a span, and each is an object whose textual form is stable, intentional and cheap. What is excluded is the arbitrary domain object &mdash; the <code>card</code> in the example above &mdash; not the non-primitive.</p>

      <p>The reason is that a captured argument goes through <code>String.valueOf</code> into a recording, and for a domain object that is the wrong thing to do three times over. It <strong>leaks</strong>: a record&rsquo;s generated <code>toString()</code>, or Lombok&rsquo;s <code>@ToString</code>, prints every component, so <code>Card[number=4111111111111111, cvv=123]</code> lands in a file that gets uploaded, shared and kept &mdash; and truncating does not help, since the first 256 characters of a card are still the card. It is <strong>unreadable</strong>: a type without <code>toString()</code> renders as <code>com.acme.Card@1b6d3586</code>, an identity hash unique per call, which is the worst possible key for a dashboard that groups spans by attribute value. And it <strong>can cost more than the method</strong>: <code>toString()</code> on a lazily loaded entity or a proxy can trigger a fetch or walk an unbounded object graph, on the hot path of the very call being measured.</p>

      <h3 id="refusals">How a Refusal Shows</h3>

      <p>It depends on when the refusal can be known &mdash; the declared type answers for some parameters and settles nothing for others:</p>

      <DocsCodeBlock :code="refusalExample" language="java" />

      <ul>
        <li>A parameter whose <strong>declared type could never be capturable</strong> &mdash; <code>charge(Card card)</code> &mdash; is dropped when the method&rsquo;s metadata is first read, with a WARNING naming the method, the parameter and its type. It costs nothing per call, and asking for something you will not get is said out loud once instead of being discovered in a recording later.</li>
        <li>A declaration that <strong>settles nothing</strong> &mdash; <code>Object</code>, <code>Number</code>, any interface, an erased type variable &mdash; is kept, and a value that turns out to be uncapturable is recorded as <code>&lt;unsupported&gt;</code>. A named parameter never simply goes missing.</li>
        <li>Under <code>{"*"}</code> an uncapturable parameter is <strong>passed over silently</strong>: a sweep is not a request for any particular one, so warning would fire on methods nobody asked anything specific of.</li>
      </ul>

      <h3 id="rendering">Rendering Rules</h3>

      <ul>
        <li>Captured text is rendered with <code>String.valueOf</code> and <strong>truncated at 256 characters</strong>, marked with an ellipsis &mdash; an argument is a fact about a call, not a payload. A value whose <code>toString()</code> throws is recorded as <code>&lt;unavailable&gt;</code>.</li>
        <li><code>Byte</code>/<code>Short</code>/<code>Integer</code>/<code>Long</code> and <code>Float</code>/<code>Double</code> are recorded as JSON numbers, booleans as JSON booleans &mdash; <code>{"amountInCents":4200}</code>, not <code>{"amountInCents":"4200"}</code>. <code>BigDecimal</code> and <code>BigInteger</code> are recorded as text rather than losing digits.</li>
        <li>Parameter names come from javac&rsquo;s <code>-parameters</code> flag. Without it they are <code>arg0</code>, <code>arg1</code>, &hellip; &mdash; and an <code>includeMethodArgs</code> entry that matches no parameter name is silently ignored, so compile with <code>-parameters</code> before relying on named capture.</li>
        <li>The fixed <code>args</code> attributes are pre-rendered once per method when no dynamic capture is configured, so the per-call cost is a lookup, not JSON building.</li>
        <li>Nothing is computed unless the event actually commits &mdash; a call under a recording threshold pays for none of this.</li>
      </ul>

      <DocsCallout type="warning">
        The allow-list keeps domain objects out of a recording; it cannot tell a safe <code>String</code> from an unsafe one. A token, a password or an email address is text, and text is capturable &mdash; capture arguments the way you would write them into a bug report, and let <code>includeMethodArgs</code> being an allow-list do the rest: name exactly what is safe.
      </DocsCallout>

      <h2 id="weaving">How the Weaving Works</h2>

      <p>Understanding the mechanism explains every requirement below:</p>

      <ol>
        <li><strong>Load-time weaving.</strong> The agent installs a ByteBuddy transformer from <code>premain</code>. Classes that declare at least one <code>@Traced</code> method are rewritten <em>as they load</em>: the original method body is rebased into a synthetic method and handed to the interceptor as a <code>Callable</code>. That shape is required — <code>ScopedValue</code> bindings are structured, so the body has to run <em>inside</em> the call that opens the span, not between enter/exit hooks.</li>
        <li><strong>Name-based matching.</strong> The agent matches the annotation by name (<code>cafe.jeffrey.jfr.events.trace.Traced</code>) and has zero compile-time knowledge of the library. ByteBuddy is shaded and relocated inside the agent jar, so it can never clash with the application's own ByteBuddy (Hibernate, Mockito, Spring).</li>
        <li><strong>Reflective runtime binding.</strong> At the first call of a woven method, the agent resolves the library's <code>TracedRuntime</code> through the <em>instrumented class's own class loader</em> — the only loader guaranteed to see the <code>jeffrey-events</code> the class was compiled against (Spring Boot fat jars, WARs, plugin containers). The lookup is cached per class; the invocation goes through a <code>MethodHandle</code> with <code>invokeExact</code>, so whatever the body throws propagates as itself.</li>
      </ol>

      <p>The runtime then does exactly what <code>Tracer.call</code> does: checks <code>isEnabled()</code> (nothing recording → the body just runs), opens the span with <code>Tracer.inSpanOf</code>, records the failure on a throw, and fills <code>name</code>/<code>kind</code>/<code>attributes</code> from the annotation metadata (cached per class) before committing.</p>

      <h2 id="requirements">Requirements and Fail-Open Behavior</h2>

      <p><code>@Traced</code> weaving needs three things <em>simultaneously</em>:</p>

      <ol>
        <li><code>tracing.enabled=true</code> in the agent arguments,</li>
        <li><strong>Java 25 or newer</strong> (the runtime is built on <code>ScopedValue</code>),</li>
        <li><code>jeffrey-events</code> visible from the annotated class's own class loader.</li>
      </ol>

      <p>Missing any one degrades <strong>silently to "the method just runs"</strong>, with a single WARNING log line. That is by design: an agent that broke an application because a dependency was missing would be a far worse bug than the missing spans. The same fail-open applies per class — a class that cannot be woven is logged and left unchanged, never left unloadable.</p>

      <DocsCallout type="warning">
        <strong>Classes are woven as they load</strong>, so the agent must be on the command line at startup (<code>premain</code> only — there is no runtime attach). A class already loaded when the agent attaches keeps its original methods. And without the agent entirely, <code>@Traced</code> is inert documentation — nothing fails, nothing is recorded.
      </DocsCallout>

      <h2 id="provisioner">Provisioner Integration</h2>

      <p>When applications are provisioned by the <router-link to="/docs/provisioner">Jeffrey Provisioner</router-link>, the agent flag is generated for you — enable method tracing in the provisioner config:</p>

      <DocsCodeBlock :code="provisionerConfig" language="text" filename="provisioner.conf" />

      <p>With tracing on, the Provisioner also starts a second JFR recording that lowers the thresholds of the JDK blocking events a trace is drawn from (socket/file I/O, monitor waits) — see <router-link to="/docs/tracing/configuration">Configuration</router-link> and the <router-link to="/docs/provisioner/configuration">Provisioner configuration reference</router-link>.</p>

      <h2 id="pitfalls">Pitfalls</h2>

      <table>
        <thead>
          <tr>
            <th>Symptom</th>
            <th>Cause</th>
            <th>Fix</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Annotated methods produce no spans, one WARNING in the log</td>
            <td><code>tracing.enabled</code> not set, Java &lt; 25, or <code>jeffrey-events</code> not on the class's loader</td>
            <td>Check all three requirements; the WARNING says which failed</td>
          </tr>
          <tr>
            <td>Spans appear for some classes but not others</td>
            <td>Classes loaded before the agent attached, or a container that refuses parent-first delegation to the system class path</td>
            <td>Attach at startup; the delegation limitation is documented, not worked around</td>
          </tr>
          <tr>
            <td><code>includeMethodArgs</code> records nothing</td>
            <td>Compiled without <code>-parameters</code> — the names are <code>arg0</code>, <code>arg1</code>, …</td>
            <td>Add <code>-parameters</code> to javac, or name <code>arg0</code>-style names explicitly</td>
          </tr>
          <tr>
            <td>A named argument is missing, one WARNING at class load</td>
            <td>Its declared type is not on the capturable allow-list &mdash; a domain object, a collection, an array</td>
            <td>Capture an id or an amount off the object instead; the WARNING names the parameter and its type</td>
          </tr>
          <tr>
            <td>An argument records as <code>&lt;unsupported&gt;</code></td>
            <td>Declared as <code>Object</code> or an interface, and the value that arrived is not capturable</td>
            <td>Expected &mdash; the refusal is recorded rather than dropped so the parameter does not silently vanish</td>
          </tr>
          <tr>
            <td>Interface <code>default</code> methods not woven</td>
            <td>The weaver targets non-interface classes</td>
            <td>Annotate the implementing class's method</td>
          </tr>
          <tr>
            <td>Woven spans are roots of their own traces</td>
            <td>No span in progress on the calling thread — no root filter, or an executor boundary</td>
            <td>Root the request (<router-link to="/docs/tracing/http-events">HTTP</router-link>/<router-link to="/docs/tracing/grpc-events">gRPC</router-link>), and cross executors with <code>Tracer.fork</code>/<code>propagating</code></td>
          </tr>
        </tbody>
      </table>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
