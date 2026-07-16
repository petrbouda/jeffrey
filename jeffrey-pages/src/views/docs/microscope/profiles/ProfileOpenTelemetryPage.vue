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
  { id: 'producers', text: 'Supported Producers', level: 2 },
  { id: 'file-format', text: 'The .otlp File', level: 2 },
  { id: 'mapping', text: 'How Profiles Are Mapped', level: 2 },
  { id: 'trace-correlation', text: 'Trace Correlation', level: 2 },
  { id: 'limitations', text: 'Limitations', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="OpenTelemetry Profiles" icon="bi bi-broadcast" />

    <div class="docs-content">
      <p>
        Jeffrey ingests recordings in the OpenTelemetry profiling signal format (OTLP profiles) as a
        third recording type next to JFR recordings and heap dumps. Upload an <code>.otlp</code> file
        to a project or the Recordings section and analyze it with the same flamegraphs, timeseries
        and sub-second views used for JFR — including mixed-language stacks captured by whole-system
        profilers.
      </p>

      <h2 id="overview">Overview</h2>
      <p>
        The OpenTelemetry profiling signal is the vendor-neutral wire format for continuous
        profiling. It is a generalized descendant of pprof: a batch carries a shared dictionary
        (strings, functions, locations, stacks, links, attributes) and profiles reference it by
        index. Each profile has one sample type (<code>cpu</code>, <code>alloc</code>, ...) and its
        samples reference a stack plus optional per-sample attributes, timestamps and a
        trace/span link.
      </p>
      <DocsCallout type="warning">
        <strong>Alpha signal.</strong> OTLP profiles are in public Alpha
        (<code>opentelemetry.proto.profiles.v1development</code>). Jeffrey vendors a pinned proto
        revision; the format may still change before it stabilizes.
      </DocsCallout>

      <h2 id="producers">Supported Producers</h2>
      <p>Any producer emitting the OTLP profiles data model works. The most common ones:</p>
      <ul>
        <li>
          <strong>async-profiler 4.1+</strong> — exports OTLP directly
          (<code>-o otlp</code>); cpu, wall-clock, allocation and lock profiles.
        </li>
        <li>
          <strong>OpenTelemetry eBPF profiler</strong> — whole-system, on-CPU sampling across
          kernel, native and language runtimes (JVM, Python, V8, Go, .NET, ...).
        </li>
        <li>
          <strong>OpenTelemetry Collector</strong> — profiles pipelines, including the pprof
          receiver that converts pprof files to OTLP profiles.
        </li>
      </ul>

      <h2 id="file-format">The .otlp File</h2>
      <p>
        The OTLP profiles signal is network-first — there is no standardized file format — so
        Jeffrey defines a simple convention for files with the <code>.otlp</code> extension: an
        optional header (<code>OTLP</code> magic + format version) followed by a sequence of
        length-delimited <code>ProfilesData</code> messages, each being one self-contained export
        batch. Because every batch carries its own dictionary, merging rotated files is a plain
        byte concatenation.
      </p>
      <p>
        A raw serialized <code>ProfilesData</code>/<code>ExportProfilesServiceRequest</code> message
        (e.g. a Collector debug-exporter dump or async-profiler's OTLP output) is accepted as-is
        without the header.
      </p>

      <h2 id="mapping">How Profiles Are Mapped</h2>
      <p>
        Every OTLP sample type becomes an <code>otel.*</code> event type:
        <code>otel.cpu</code>, <code>otel.samples</code>, <code>otel.wall</code>,
        <code>otel.alloc</code> and <code>otel.lock</code> light up the matching flamegraph cards;
        unrecognized sample types become custom <code>otel.*</code> event types browsable in the
        Event Viewer. Duration units map to the event weight in nanoseconds, byte units to the
        weight in bytes, counts to samples.
      </p>
      <p>
        Stacks are stored root-first with the frame type derived from the
        <code>profile.frame.type</code> semantic convention — JVM frames render as Java frames with
        class/method splitting, kernel and native frames keep their own colors, and other language
        runtimes (Python, JavaScript, Go, ...) get language-specific frame types. Thread identity
        comes from the <code>thread.name</code>/<code>thread.id</code> sample attributes when the
        producer emits them; resource attributes such as <code>service.name</code> appear as event
        type settings.
      </p>

      <h2 id="trace-correlation">Trace Correlation</h2>
      <p>
        Samples linked to a trace carry their <code>trace_id</code>/<code>span_id</code> in the
        per-event fields (visible in the Event Viewer). Flamegraphs and timeseries can be scoped to
        a single trace or span via the <code>traceId</code>/<code>spanId</code> fields of the
        generate requests, showing exactly where a specific request spent its CPU or allocated its
        memory.
      </p>

      <h2 id="limitations">Limitations</h2>
      <ul>
        <li>
          Samples exported in aggregated form (a total value without per-sample timestamps) appear
          as one event per export batch — totals stay exact, timeseries resolution degrades to the
          batch interval.
        </li>
        <li>
          Unsymbolized native frames (common with eBPF producers without symbol upload) render as
          hexadecimal addresses.
        </li>
        <li>
          All resources of a file fold into one profile; multi-service batches are best split per
          service before uploading.
        </li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>
