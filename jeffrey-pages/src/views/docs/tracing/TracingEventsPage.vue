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
  { id: 'event-types', text: 'Event Types', level: 2 },
  { id: 'integration', text: 'Add It', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

type RecordedAs = 'SERVER' | 'CLIENT' | 'INTERNAL' | 'INSTANT' | 'PLAIN';

interface EventType {
  name: string;
  recordedAs: RecordedAs;
  description: string;
}

interface EventFamily {
  key: string;
  icon: string;
  title: string;
  category: string;
  note: string;
  events: EventType[];
}

const RECORDED_AS_LABELS: Record<RecordedAs, string> = {
  SERVER: 'span · SERVER',
  CLIENT: 'span · CLIENT',
  INTERNAL: 'span · INTERNAL',
  INSTANT: 'instant',
  PLAIN: 'event'
};

const families: EventFamily[] = [
  {
    key: 'http',
    icon: 'bi bi-globe',
    title: 'HTTP',
    category: 'Application / HTTP',
    note: 'One event per exchange, on both sides of the wire.',
    events: [
      {
        name: 'jeffrey.HttpServerExchange',
        recordedAs: 'SERVER',
        description: 'An inbound request: method, matched URI template, response status, remote host and port, media type, query and path parameters, request and response body lengths.'
      },
      {
        name: 'jeffrey.HttpClientExchange',
        recordedAs: 'CLIENT',
        description: 'An outbound request the application made, with the same fields.'
      }
    ]
  },
  {
    key: 'grpc',
    icon: 'bi bi-hdd-network',
    title: 'gRPC',
    category: 'Application / gRPC',
    note: 'One event per call, recorded from the interceptor that closes it.',
    events: [
      {
        name: 'jeffrey.GrpcServerExchange',
        recordedAs: 'SERVER',
        description: 'An inbound call: service and method, gRPC status code, authority, remote host and port, serialized request and response sizes.'
      },
      {
        name: 'jeffrey.GrpcClientExchange',
        recordedAs: 'CLIENT',
        description: 'An outbound call on a channel, with the same fields.'
      }
    ]
  },
  {
    key: 'jdbc',
    icon: 'bi bi-database',
    title: 'JDBC Statements',
    category: 'Application / JDBC',
    note: 'One event per statement, dispatched by verb. All of them carry the SQL, its parameters, a grouping label and the affected or returned row count.',
    events: [
      {
        name: 'jeffrey.JdbcQuery',
        recordedAs: 'CLIENT',
        description: 'A SELECT, plus the number of samples behind the row count.'
      },
      {
        name: 'jeffrey.JdbcStream',
        recordedAs: 'CLIENT',
        description: 'A query whose result set is streamed rather than materialised.'
      },
      {
        name: 'jeffrey.JdbcInsert',
        recordedAs: 'CLIENT',
        description: 'An INSERT, flagged when it carries a LOB parameter or runs as a batch.'
      },
      {
        name: 'jeffrey.JdbcUpdate',
        recordedAs: 'CLIENT',
        description: 'An UPDATE.'
      },
      {
        name: 'jeffrey.JdbcDelete',
        recordedAs: 'CLIENT',
        description: 'A DELETE.'
      },
      {
        name: 'jeffrey.JdbcExecute',
        recordedAs: 'CLIENT',
        description: 'Anything else executed against the connection — DDL, a call, a plain execute.'
      }
    ]
  },
  {
    key: 'pool',
    icon: 'bi bi-layers',
    title: 'Connection Pool',
    category: 'Application / JDBC Pool',
    note: 'Pool behaviour, keyed by pool name. These are plain JFR events rather than spans — a pool has no operation to nest under.',
    events: [
      {
        name: 'jeffrey.JdbcPoolStatistics',
        recordedAs: 'PLAIN',
        description: 'A periodic gauge: total, idle, active, minimum and maximum connections, and the number of threads waiting for one.'
      },
      {
        name: 'jeffrey.PooledJdbcConnectionCreated',
        recordedAs: 'PLAIN',
        description: 'How long it took the pool to open a new physical connection.'
      },
      {
        name: 'jeffrey.PooledJdbcConnectionAcquired',
        recordedAs: 'PLAIN',
        description: 'How long a caller waited to get a connection out of the pool.'
      },
      {
        name: 'jeffrey.PooledJdbcConnectionBorrowed',
        recordedAs: 'PLAIN',
        description: 'How long a connection stayed checked out before it was returned.'
      },
      {
        name: 'jeffrey.AcquiringPooledJdbcConnectionTimeout',
        recordedAs: 'PLAIN',
        description: 'A caller gave up waiting — the pool was exhausted for the whole timeout.'
      }
    ]
  },
  {
    key: 'trace',
    icon: 'bi bi-bezier2',
    title: 'Tracing',
    category: 'Application / Tracing',
    note: 'The events the Tracer API writes directly.',
    events: [
      {
        name: 'jeffrey.TraceSpan',
        recordedAs: 'INTERNAL',
        description: 'A span opened by hand through Tracer.run / Tracer.call, or woven onto a @Traced method: a name, a kind, a status and optional JSON attributes.'
      },
      {
        name: 'jeffrey.TraceScope',
        recordedAs: 'PLAIN',
        description: 'Records that a span was entered on a given thread — how work handed to an executor or a callback is tied back to the span it belongs to.'
      }
    ]
  },
  {
    key: 'notification',
    icon: 'bi bi-bell',
    title: 'Notifications',
    category: 'Application / Notification',
    note: 'An instant rather than a duration — a moment worth marking, not a stretch of time.',
    events: [
      {
        name: 'jeffrey.Notification',
        recordedAs: 'INSTANT',
        description: 'Something the application wants to say: a type, a constant message, a severity, a category and a source.'
      }
    ]
  }
];

const totalEventTypes = families.reduce((count, family) => count + family.events.length, 0);

const dependency = `<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-events</artifactId>
    <version><!-- latest release --></version>
</dependency>`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Jeffrey JFR Events"
      icon="bi bi-broadcast"
    />

    <div class="docs-content">
      <p><strong>Jeffrey Events</strong> is a small, zero-dependency library of JFR event definitions for application-level facts — requests, calls, statements, pool activity, spans and notifications. They are written into the same recording as the JVM's own events, so one file carries both what the application did and what the JVM did underneath it.</p>

      <DocsCallout type="info">
        <strong>Open source:</strong> the library lives at <a href="https://github.com/petrbouda/jeffrey/tree/master/utilities/jeffrey-events" target="_blank" rel="noopener">github.com/petrbouda/jeffrey</a> under <code>utilities/jeffrey-events</code>. How these events become traces — nesting, naming, failure — is the <router-link to="/docs/tracing">Jeffrey Tracing guide</router-link>.
      </DocsCallout>

      <h2 id="event-types">Event Types</h2>

      <p>{{ totalEventTypes }} event types in six families. <strong>Records as</strong> says whether the event takes part in a trace as a span, marks an instant, or is a plain measurement.</p>

      <section
        v-for="family in families"
        :key="family.key"
        class="event-family"
      >
        <div class="family-head">
          <i :class="family.icon"></i>
          <h3>{{ family.title }}</h3>
          <span class="family-category">{{ family.category }}</span>
        </div>

        <p class="family-note">{{ family.note }}</p>

        <table>
          <thead>
            <tr>
              <th class="col-event">Event</th>
              <th class="col-recorded-as">Records as</th>
              <th>What it records</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="event in family.events"
              :key="event.name"
            >
              <td><code class="event-name">{{ event.name }}</code></td>
              <td>
                <span :class="['records-as', 'records-as-' + event.recordedAs.toLowerCase()]">
                  {{ RECORDED_AS_LABELS[event.recordedAs] }}
                </span>
              </td>
              <td>{{ event.description }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <h2 id="integration">Add It</h2>

      <DocsCodeBlock :code="dependency" language="xml" filename="pom.xml" />

      <p class="docs-read-more">
        <router-link to="/docs/tracing">Read the Jeffrey Tracing guide &rarr;</router-link><br>
        <router-link to="/docs/tracing/instrumentation">Read the Tracer API reference &rarr;</router-link>
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.event-family {
  margin: 2rem 0;
}

.family-head {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.family-head i {
  color: #5e64ff;
  font-size: 1.05rem;
}

.family-head h3 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: #343a40;
}

.family-category {
  margin-left: auto;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.75rem;
  color: #94a3b8;
}

.family-note {
  margin: 0.35rem 0 0;
  font-size: 0.9rem;
  color: #5e6e82;
}

.event-family table {
  margin-top: 0.85rem;
  table-layout: fixed;
}

.event-family th,
.event-family td {
  padding: 0.75rem 0.85rem;
}

.event-family td {
  vertical-align: top;
}

.col-event {
  width: 300px;
}

.col-recorded-as {
  width: 140px;
}

/* The longest name is wider than its column; break it rather than let it
   squeeze the description out of the pool table. */
.event-name {
  color: #4349d8;
  font-weight: 600;
  overflow-wrap: break-word;
}

.records-as {
  display: inline-block;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  padding: 0.1rem 0.4rem;
  border-radius: 5px;
  white-space: nowrap;
}

.records-as-server {
  background: rgba(94, 100, 255, 0.12);
  color: #4349d8;
}

.records-as-client {
  background: rgba(57, 175, 209, 0.16);
  color: #23788f;
}

.records-as-internal {
  background: rgba(119, 128, 191, 0.16);
  color: #5b649b;
}

.records-as-instant {
  background: rgba(13, 148, 136, 0.14);
  color: #0b7c6d;
}

.records-as-plain {
  background: #eef1f5;
  color: #6b7280;
}
</style>
