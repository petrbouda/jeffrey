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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import DocsSpanTree from '@/components/docs/DocsSpanTree.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'events', text: 'The Two Events', level: 2 },
  { id: 'setup', text: 'Add the Module, Register the Interceptors', level: 2 },
  { id: 'callbacks', text: 'Why gRPC Is Not a Servlet Filter', level: 2 },
  { id: 'fields', text: 'What It Records', level: 2 },
  { id: 'manual', text: 'The Pattern by Hand', level: 2 },
  { id: 'spring-support', text: 'Using Spring Boot?', level: 2 },
  { id: 'pitfalls', text: 'Pitfalls', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const dependency = `<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-tracing-grpc</artifactId>
    <version><!-- latest release --></version>
</dependency>`;

const registration = `Server server = ServerBuilder.forPort(port)
        .intercept(new JfrGrpcServerInterceptor())    // every service on this server
        .build();

ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .intercept(new JfrGrpcClientInterceptor())    // every stub on this channel
        .build();`;

const manualPattern = `// The shape the server interceptor implements — openSpanOf + reenter,
// because a gRPC call cannot be wrapped in one try/finally on one thread.
GrpcServerExchangeEvent event = new GrpcServerExchangeEvent();
event.begin();
SpanContext span = Tracer.openSpanOf(event);   // stamps the event, binds NOTHING

return new SimpleForwardingServerCallListener<>(listener) {
    @Override
    public void onMessage(ReqT message) {
        Tracer.reenter(span, () -> super.onMessage(message));
    }

    @Override
    public void onHalfClose() {                          // where a unary handler actually runs
        Tracer.reenter(span, () -> super.onHalfClose()); // resumes the SAME span, not a child
    }

    // onCancel / onComplete / onReady wrapped the same way; the event is
    // committed (with status, sizes, failed(cause) on a failure) from
    // onClose — the one callback that always arrives, success and failure alike.
};`;

const outputExample = `jeffrey.GrpcServerExchange {
  duration = 18.4 ms
  traceId = 4919739129371883702
  spanId = 6533423119469147918
  parentSpanId = 0
  name = "jeffrey.api.v1.WorkspaceService/List"   // "{service}/{method}", from @Span
  kind = "SERVER"
  status = "UNSET"
  service = "jeffrey.api.v1.WorkspaceService"
  method = "List"
  statusCode = "OK"
  authority = "hub.internal:9090"
  requestSize = 42
  responseSize = 1187
}

plus one jeffrey.TraceScope per listener callback, naming the executor
thread the span actually ran on:
  jeffrey.TraceScope  scopedSpanId=6533423119469147918  thread=grpc-default-executor-0
  jeffrey.TraceScope  scopedSpanId=6533423119469147918  thread=grpc-default-executor-2`;

const clientSpans = [
  { depth: 0, name: 'GET /api/internal/workspaces', kind: 'SERVER' as const, start: 0, duration: 34,
    event: 'HttpServerExchangeEvent', note: 'microscope' },
  { depth: 1, name: 'jeffrey.api.v1.WorkspaceService/List', kind: 'CLIENT' as const,
    start: 5, duration: 21.4, event: 'GrpcClientExchangeEvent', note: 'leaf' }
];
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="gRPC Events"
      icon="bi bi-arrow-left-right"
    />

    <div class="docs-content">
      <p>Two event types cover gRPC: <code>jeffrey.GrpcServerExchange</code> — every inbound call becomes the <strong>root span</strong> of a trace — and <code>jeffrey.GrpcClientExchange</code> — every outbound call becomes a <strong>leaf</strong> under whatever the caller was doing. Your services and stubs need zero changes.</p>

      <h2 id="events">The Two Events</h2>

      <table>
        <thead>
          <tr>
            <th>Event</th>
            <th>Kind</th>
            <th>Role</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.GrpcServerExchange</code></td>
            <td><code>SERVER</code></td>
            <td>Root span of the inbound call; work your handler does — in any listener callback — nests underneath</td>
          </tr>
          <tr>
            <td><code>jeffrey.GrpcClientExchange</code></td>
            <td><code>CLIENT</code></td>
            <td>Leaf: the work it triggers happens in another process this recording cannot see</td>
          </tr>
        </tbody>
      </table>

      <p>Names are low-cardinality by construction: <code>package.Service/Method</code>, straight off the <code>MethodDescriptor</code> (the class declares the <code>@Span("{service}/{method}")</code> template). Nothing derived from a message ever reaches the name.</p>

      <h2 id="setup">Add the Module, Register the Interceptors</h2>

      <DocsCodeBlock :code="dependency" language="xml" />
      <DocsCodeBlock :code="registration" language="java" />

      <p>Both interceptors are stateless and thread-safe: one instance per server or channel is enough, and registering the same instance on several is fine. That is the whole integration on every stack — a server is assembled from interceptors the application chooses and a channel is built by application code, so there is nothing to auto-configure and no filter chain to hook into.</p>

      <h2 id="callbacks">Why gRPC Is Not a Servlet Filter</h2>

      <p>A servlet filter can wrap the whole request in one <code>try</code>/<code>finally</code> on one thread. A gRPC call cannot: <code>interceptCall</code> returns immediately, and the call then proceeds through callbacks (<code>onMessage</code>, <code>onHalfClose</code>, <code>onClose</code>) that arrive on transport threads the application does not control, possibly long afterwards.</p>

      <p>Both interceptors therefore split the span from the thread that holds it, using the <router-link to="/docs/tracing/tracer-api/open-span-of">openSpanOf</router-link> / <router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link> pair:</p>

      <ul>
        <li>The span is <strong>opened without binding</strong> — <code>Tracer.openSpanOf(event)</code> — at the point where the trace identity is known. On the client that is the calling thread, so the outbound call becomes a child of the request being served; on the server there is nothing above it, so it is a root.</li>
        <li>Every callback runs inside <code>Tracer.reenter(span, …)</code>, which re-establishes the span for whatever the callback does and records a <code>jeffrey.TraceScope</code> event saying which thread it ran on. Work your handler does in a callback still nests under the call.</li>
        <li>The exchange is committed from <code>onClose</code>/<code>close</code>, the one callback that always arrives — for a success and for a failure alike.</li>
      </ul>

      <p>You get all of this by using the module; it is the reason to use it rather than write your own.</p>

      <h2 id="fields">What It Records</h2>

      <table>
        <thead>
          <tr>
            <th>Field</th>
            <th>Server</th>
            <th>Client</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>span name</td>
            <td><code>package.Service/Method</code></td>
            <td><code>package.Service/Method</code></td>
          </tr>
          <tr>
            <td><code>kind</code></td>
            <td><code>SERVER</code> (trace root)</td>
            <td><code>CLIENT</code> (leaf)</td>
          </tr>
          <tr>
            <td><code>service</code> / <code>method</code></td>
            <td>from the <code>MethodDescriptor</code></td>
            <td>from the <code>MethodDescriptor</code></td>
          </tr>
          <tr>
            <td><code>authority</code></td>
            <td>—</td>
            <td>the channel's target authority</td>
          </tr>
          <tr>
            <td><code>statusCode</code></td>
            <td>the gRPC status name (<code>OK</code>, <code>INTERNAL</code>, …)</td>
            <td>the same</td>
          </tr>
          <tr>
            <td><code>requestSize</code> / <code>responseSize</code></td>
            <td>serialized protobuf sizes</td>
            <td>serialized protobuf sizes</td>
          </tr>
        </tbody>
      </table>

      <p><strong>Status handling escalates only</strong>: the derived verdict in <code>describeSpan()</code> marks anything but <code>OK</code> as <code>ERROR</code>, but a call that already recorded a failure (a <code>Status</code> carrying a cause is recorded with <code>failed(cause)</code>, so the span shows red with the exception type) is never talked back down. Message sizes are read from <code>MessageLite#getSerializedSize</code> when the message is a protobuf — every generated stub; a non-protobuf marshaller records size 0 rather than guessing.</p>

      <DocsCodeBlock :code="outputExample" language="text" />

      <p>On the client side, the outbound call nests under whatever the caller was doing:</p>

      <DocsSpanTree
        trace="4e11d5b8…"
        :spans="clientSpans"
        caption="The server side of the same call is a separate trace, in the hub's own recording."
      />

      <h2 id="manual">The Pattern by Hand</h2>

      <p>For a custom transport, or to understand precisely what the module does:</p>

      <DocsCodeBlock :code="manualPattern" language="java" />

      <h2 id="spring-support">Using Spring Boot?</h2>

      <p>There is no starter and no auto-configuration for gRPC — there is nothing for one to hook into. On Spring gRPC the server interceptor is declared as a global bean, which is the only Spring-specific line in the whole integration.</p>

      <DocsLinkCard
        to="/docs/tracing/spring-support"
        icon="bi bi-flower1"
        title="Spring Support"
        description="The @GlobalServerInterceptor bean, and why gRPC gets no starter."
      />

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
            <td>Server spans present, client spans missing</td>
            <td>The client interceptor is not registered — they are two separate registrations</td>
            <td><code>.intercept(new JfrGrpcClientInterceptor())</code> on the channel builder</td>
          </tr>
          <tr>
            <td>Outbound calls appear as roots of their own traces</td>
            <td>The call was made outside any bound span (a scheduler thread, an <code>@Async</code> method)</td>
            <td>Wrap the caller with <code>Tracer.run</code>, or hand the context over with <code>Tracer.fork</code>/<code>continueIn</code></td>
          </tr>
          <tr>
            <td>Streaming calls measured as ~0 ms</td>
            <td>A hand-written interceptor that committed from <code>interceptCall</code> instead of <code>onClose</code></td>
            <td>Use the module; it commits from the closing callback</td>
          </tr>
          <tr>
            <td>Two events per call</td>
            <td>The interceptor registered both globally and per-service</td>
            <td>Register once — globally on the server, once per channel on the client</td>
          </tr>
          <tr>
            <td>Handler work not nested under the call</td>
            <td>The handler dispatched to an executor without carrying the context</td>
            <td><code>Tracer.propagating(executor)</code>, or <code>Tracer.fork</code> at the hand-off</td>
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
