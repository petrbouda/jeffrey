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
  { id: 'why', text: 'Why a Custom Event', level: 2 },
  { id: 'recipe', text: 'The Recipe', level: 2 },
  { id: 'example', text: 'A Complete Example', level: 2 },
  { id: 'emitting', text: 'Emitting: Interior Span or Leaf', level: 2 },
  { id: 'describe-span', text: 'Deriving Name and Status: describeSpan()', level: 2 },
  { id: 'span-annotation', text: 'The @Span Naming Template', level: 2 },
  { id: 'commit-vs-commitspan', text: 'commitSpan() vs commit()', level: 2 },
  { id: 'instants', text: 'Custom Instants: AbstractTracedInstant', level: 2 },
  { id: 'attribute-utils', text: 'EventAttributes and AttributeValues', level: 2 },
  { id: 'discovery', text: 'How Jeffrey Discovers It', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const customEvent = `import cafe.jeffrey.jfr.events.trace.AbstractTracedEvent;
import cafe.jeffrey.jfr.events.trace.Span;
import jdk.jfr.Category;
import jdk.jfr.DataAmount;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@Name("com.acme.KafkaPublish")
@Label("Kafka Publish")
@Category({"Application", "Messaging"})
@StackTrace(false)                             // spans rarely need a stack; keep them cheap
@Span("PUBLISH {topic}")                       // the operation-name template (see below)
public class KafkaPublishEvent extends AbstractTracedEvent {   // what makes it a span

    @Label("Topic")
    public String topic;

    @Label("Partition")
    public int partition;

    @Label("Payload Size")
    @DataAmount
    public long payloadSize;
}`;

const interiorUsage = `// Work NESTS INSIDE the publish (serialization, the broker ack):
// the event IS the span — inSpanOf stamps the ids, marks ERROR on a
// throw, and commits through commitSpan()
KafkaPublishEvent event = new KafkaPublishEvent();
event.topic = "orders";
Tracer.inSpanOf(event, () -> {
    byte[] payload = Tracer.call("order.serialize", () -> serialize(order));
    event.payloadSize = payload.length;
    send(payload);
});`;

const leafUsage = `// Nothing nests inside — the publish is a self-contained action.
// TracedEvents.emit writes the whole leaf lifecycle in one call:
// guard, begin, end on success, failed(e) on a throw, commitSpan().
KafkaPublishEvent event = new KafkaPublishEvent();
RecordMetadata metadata = TracedEvents.emit(event,
        () -> producer.send(record).get(),
        (e, result) -> {
            e.topic = record.topic();
            e.partition = result != null ? result.partition() : -1;
            e.payloadSize = record.value().length;
        });`;

const expandedLifecycle = `// What TracedEvents.emit expands to, where the helper does not fit:
KafkaPublishEvent event = new KafkaPublishEvent();
if (!event.isEnabled()) {              // no recording -> zero-cost passthrough
    return doPublish();
}
event.begin();
try {
    RecordMetadata result = doPublish();
    event.end();                       // interval ends when the work ends
    return result;
} catch (Exception e) {
    event.failed(e);                   // status=ERROR + errorType; rethrow after
    throw e;
} finally {
    if (event.shouldCommit()) {        // respects per-recording thresholds
        event.topic = topic;           // fill fields only when committing
        event.commitSpan();            // stamps under the span in progress, then commits
    }
}`;

const outputExample = `jfr print --events com.acme.KafkaPublish app.jfr

com.acme.KafkaPublish {
  duration = 12.7 ms
  traceId = 6872570733206835563        // nested under the request that published
  spanId = 5561200973317418716
  parentSpanId = 4444722480460712002
  name = ""                            // empty — the @Span template names it in Jeffrey
  kind = "INTERNAL"
  status = "UNSET"
  topic = "orders"
  partition = 3
  payloadSize = 2.4 kB
}

// In Jeffrey's waterfall and Traces by Operation the span is named
// "PUBLISH orders" — the @Span template applied to the event's own fields.`;

const describeSpanExample = `@Name("com.acme.BatchJobStep")
@Label("Batch Job Step")
@Category({"Application", "Batch"})
@StackTrace(false)
@Span("{jobName} step {stepName}")
public class BatchJobStepEvent extends AbstractTracedEvent {

    @Label("Job Name")
    public String jobName;

    @Label("Step Name")
    public String stepName;

    @Label("Items Failed")
    public long itemsFailed;

    @Override
    protected void describeSpan() {
        name = jobName + " step " + stepName;
        // Escalate-only: never overwrite an ERROR recorded by failed()
        if (!SpanStatus.ERROR.name().equals(status) && itemsFailed > 0) {
            status = SpanStatus.ERROR.name();
        }
    }
}`;

const customInstant = `import cafe.jeffrey.jfr.events.trace.AbstractTracedInstant;

@Name("com.acme.CircuitBreakerOpened")
@Label("Circuit Breaker Opened")
@Category({"Application", "Resilience"})
@StackTrace(false)
public class CircuitBreakerOpenedEvent extends AbstractTracedInstant {

    @Label("Breaker")
    public String breaker;
}

// Emitting: emit() stamps the enclosing span's ids (traceId +
// enclosingSpanId) and commits. Ids already set by the caller are left alone.
CircuitBreakerOpenedEvent event = new CircuitBreakerOpenedEvent();
event.breaker = "payments";
event.attributes = EventAttributes.create().put("failureRate", 0.42).json();
event.emit();`;

const attributesExample = `// EventAttributes: a zero-dependency JSON-object builder with full escaping.
// Build it only inside the shouldCommit() block — an event under threshold
// pays nothing.
event.attributes = EventAttributes.create()
        .put("cache", "miss")            // strings are escaped (quotes, control chars)
        .put("retries", 2)               // integrals -> JSON numbers
        .put("hitRatio", 0.87)           // non-finite doubles -> null
        .put("fallback", true)
        .json();

// AttributeValues: shared value-rendering rules (used by @Traced arg capture
// and MyBatis parameter capture) — numbers stay numbers, everything else is
// text truncated at the given limit, toString() failures record "<unavailable>".
AttributeValues.put(builder, "orderId", orderId, 256);`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Custom Traced Events"
      icon="bi bi-puzzle"
    />

    <div class="docs-content">
      <p>Any domain event can be a full span in Jeffrey's traces — a Kafka publish, a batch-job step, a cache rebuild, a rules-engine evaluation. Extend <code>AbstractTracedEvent</code>, commit through <code>commitSpan()</code>, and Jeffrey discovers, nests and names it with <strong>zero configuration on its side</strong>.</p>

      <h2 id="why">Why a Custom Event</h2>

      <p>A <code>Tracer.run("kafka.publish", …)</code> span answers <em>how long</em>. A custom event answers <em>how long, on which topic, which partition, how many bytes</em> — its own typed fields ride on the span, show up in the span detail, and become searchable in <router-link to="/docs/tracing/analysis">Traces by Attributes</router-link> as declared event fields. Use a custom type when the operation has structure worth recording; stay with <code>Tracer.run</code> when a name is enough.</p>

      <h2 id="recipe">The Recipe</h2>

      <ol>
        <li><strong>Extend <code>AbstractTracedEvent</code></strong> — that is what makes it a span: the <code>spanId</code> field Jeffrey discovers structurally.</li>
        <li>Add <code>@Name("com.acme.…")</code>, <code>@Label</code>, <code>@Category({"Application", "…"})</code>, and usually <code>@StackTrace(false)</code>.</li>
        <li>Declare your own fields, with <code>@Label</code> (and units like <code>@DataAmount</code>/<code>@Timespan</code> where they apply).</li>
        <li>Optionally annotate the class with <code>@Span("{template}")</code> so every reader derives the span name from metadata.</li>
        <li>Optionally override <code>describeSpan()</code> to derive <code>name</code>/<code>status</code> from your fields.</li>
        <li>Commit through <code>commitSpan()</code> — or wrap the whole lifecycle in <code>TracedEvents.emit</code>.</li>
      </ol>

      <h2 id="example">A Complete Example</h2>

      <DocsCodeBlock :code="customEvent" language="java" />

      <h2 id="emitting">Emitting: Interior Span or Leaf</h2>

      <p>Pick by whether traced work nests <em>inside</em> the event's interval:</p>

      <DocsCodeBlock :code="interiorUsage" language="java" />
      <DocsCodeBlock :code="leafUsage" language="java" />

      <p>Where the helper does not fit, write what it expands to — this is the canonical leaf lifecycle:</p>

      <DocsCodeBlock :code="expandedLifecycle" language="java" />

      <p>And this is what lands in the recording, and how Jeffrey names it:</p>

      <DocsCodeBlock :code="outputExample" language="text" />

      <h2 id="describe-span">Deriving Name and Status: <code>describeSpan()</code></h2>

      <p><code>commitSpan()</code> calls <code>describeSpan()</code> just before committing — the hook where an event derives its span shape from its own fields, exactly as the HTTP event names itself <code>GET /api/users/{id}</code> and fails from status 400 upwards:</p>

      <DocsCodeBlock :code="describeSpanExample" language="java" />

      <DocsCallout type="warning">
        <strong>Derivation only ever escalates.</strong> A <code>describeSpan()</code> override may promote <code>UNSET</code> to <code>ERROR</code>, never the reverse — a failure recorded with <code>failed(throwable)</code> knows something the derived verdict does not (the gRPC event's guard shows the pattern: check <code>status</code> is not already <code>ERROR</code> before deriving).
      </DocsCallout>

      <h2 id="span-annotation">The @Span Naming Template</h2>

      <p><code>@Span("PUBLISH {topic}")</code> is a metadata annotation (<code>@MetadataDefinition</code>): the template is persisted <em>into every recording</em> that contains the event type, so any reader — Jeffrey included — derives the span name from the event's own fields without knowing the type. Rules:</p>

      <ul>
        <li>Tokens are <code>{fieldName}</code> over literal text; field names match <code>[A-Za-z0-9_]+</code> and name the event's own fields.</li>
        <li>The annotation is <code>@Inherited</code> — declare it on an abstract base and every subtype carries it, the way <code>@Category</code> already behaves.</li>
        <li>Template naming keeps working even for events committed with plain <code>commit()</code> — unlike the status verdict, which must be recorded.</li>
        <li>Template fields must themselves be low-cardinality (<code>{topic}</code>, not <code>{messageKey}</code>) — the rendered name is the operation's identity in <router-link to="/docs/tracing/analysis">Traces by Operation</router-link>.</li>
      </ul>

      <p>Where the naming comes from is layered, in this order: the <code>@Span</code> template carried in the recording's metadata; then Jeffrey's built-in conventions for its own event types (on recordings that predate the annotation); then the <code>name</code> the event recorded for itself; and only then the event type as a last resort.</p>

      <h2 id="commit-vs-commitspan">commitSpan() vs commit()</h2>

      <table>
        <thead>
          <tr>
            <th></th>
            <th><code>commitSpan()</code></th>
            <th><code>commit()</code></th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Stamps trace identity under the span in progress</td>
            <td>Yes (when not already stamped)</td>
            <td>No — ids stay at 0</td>
          </tr>
          <tr>
            <td>Runs <code>describeSpan()</code></td>
            <td>Yes</td>
            <td>No</td>
          </tr>
          <tr>
            <td>Appears in dashboards / event views</td>
            <td>Yes</td>
            <td>Yes</td>
          </tr>
          <tr>
            <td>Appears in traces</td>
            <td>Yes</td>
            <td><strong>No — silently untraced</strong></td>
          </tr>
        </tbody>
      </table>

      <p>A bare <code>commit()</code> is the deliberate opt-out, not the default — committing with <code>commit()</code> where <code>commitSpan()</code> belonged is the single most common instrumentation mistake. (<code>stampAndCommit()</code> is a deprecated alias of <code>commitSpan()</code> kept for older call sites.)</p>

      <h2 id="instants">Custom Instants: <code>AbstractTracedInstant</code></h2>

      <p>Not everything is an interval. For a <em>moment</em> that should land in the right trace — a circuit breaker opening, a threshold crossing — extend <code>AbstractTracedInstant</code> instead: it carries <code>traceId</code>, <code>enclosingSpanId</code> and <code>attributes</code>, and commits through <code>emit()</code>:</p>

      <DocsCodeBlock :code="customInstant" language="java" />

      <p>The identity field is deliberately called <code>enclosingSpanId</code>, never <code>spanId</code> — span discovery is structural, and an instant naming its field <code>spanId</code> would be built into a nameless, durationless span. <code>jeffrey.Notification</code> is the shipped instant; see <router-link to="/docs/tracing/notifications-exceptions">Notifications &amp; Exceptions</router-link> for the cardinality contract that applies to instants of your own too.</p>

      <h2 id="attribute-utils">EventAttributes and AttributeValues</h2>

      <DocsCodeBlock :code="attributesExample" language="java" />

      <p><code>EventAttributes</code> is single-use and not thread-safe — create, fill, <code>json()</code>, discard. Spans and instants use the same field name and encoding, so one reader renders both and one index searches both.</p>

      <h2 id="discovery">How Jeffrey Discovers It</h2>

      <ul>
        <li><strong>Span participation</strong>: detected from the declared <code>spanId</code> field in the recording's metadata. Any event extending <code>AbstractTracedEvent</code> takes part — no registration, no Jeffrey release needed.</li>
        <li><strong>Naming</strong>: the <code>@Span</code> template travels in the recording; Jeffrey applies it when deriving traces.</li>
        <li><strong>Nesting</strong>: from <code>(traceId, spanId, parentSpanId)</code> alone.</li>
        <li><strong>Fields</strong>: every declared field lands in the profile database, is shown in the span's inline detail, and appears in <router-link to="/docs/tracing/analysis">Traces by Attributes</router-link> under your event type.</li>
        <li><strong>Failure</strong>: the one thing that must be <em>recorded</em> — commit through <code>commitSpan()</code> (or call <code>failed()</code>) if failures should count.</li>
      </ul>

      <DocsCallout type="tip">
        On the module path, <code>opens</code> your event packages to <code>jdk.jfr</code> — JFR rewrites event-class bytecode via <code>MethodHandles.privateLookupIn</code>, and without the <code>opens</code>, <code>commit()</code> throws <code>IllegalAccessException</code>. (<code>jeffrey-events</code> already does this for its own packages; on the classpath there is nothing to configure.)
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
