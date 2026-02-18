<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'input-configuration', text: 'Input Configuration', level: 2 },
  { id: 'generated-environment', text: 'Generated Environment Variables', level: 2 },
  { id: 'variable-reference', text: 'Variable Reference', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const inputConfig = `# Jeffrey CLI Init Command Configuration
# Usage: java -jar target/jeffrey-cli.jar init jeffrey-init.conf

# Jeffrey home directory path
jeffrey-home = "/tmp/jeffrey"

# Profiler configuration
profiler-path = "/tmp/asprof/libasyncProfiler.so"

# Project identification
project {
    workspace-id = "uat"
    name = "jeffrey"
    label = "Jeffrey"
}

# Features

# Debug Non-Safepoints - more precise profiling (enabled by default)
debug-non-safepoints {
    enabled = true
}

# Performance counters - can be used to detect finished session
perf-counters {
    enabled = true
}

# Heap dump on OutOfMemoryError
heap-dump {
    enabled = true
    type = "crash"  # "exit" or "crash"
}

# JVM logging with <<JEFFREY_CURRENT_SESSION>> placeholder for file path
# Use '-jvm.log' suffix to automatically recognize the file as a JVM log file by Jeffrey
jvm-logging {
    enabled = true
    command = "jfr*=trace:file=<<JEFFREY_CURRENT_SESSION>>/jfr-jvm.log::filecount=3,filesize=5m"
}

# Messaging - enables jeffrey.ImportantMessage JFR events
# with a dedicated repository for real-time message consumption
messaging {
    enabled = true
    max-age = "24h"  # How long to keep messages (e.g., 12h, 1d, 30m)
}

# JDK Java Options - exports JDK_JAVA_OPTIONS environment variable
jdk-java-options {
    enabled = true
    additional-options = "-Xmx1200m -Xms1200m -XX:+UseG1GC -XX:+AlwaysPreTouch"
}

# Attributes (key-value map)
# Supports HOCON substitution: \${VAR} (required) or \${?VAR} (optional)
attributes {
    cluster = "blue"
    namespace = "klingon"
}`;

const generatedEnvOutput = `# ENV file with variables to source:
# /tmp/jeffrey/workspaces/uat/jeffrey/.env
export JEFFREY_HOME=/tmp/jeffrey
export JEFFREY_WORKSPACES=/tmp/jeffrey/workspaces
export JEFFREY_CURRENT_WORKSPACE=/tmp/jeffrey/workspaces/uat
export JEFFREY_CURRENT_PROJECT=/tmp/jeffrey/workspaces/uat/jeffrey
export JEFFREY_CURRENT_SESSION=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560
export JEFFREY_FILE_PATTERN=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/profile-%t.jfr
export JEFFREY_PROFILER_CONFIG='-agentpath:/tmp/asprof/libasyncProfiler.so=start,alloc,lock,event=ctimer,jfrsync=default,loop=15m,chunksize=5m,file=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/profile-%t.jfr -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:+UsePerfData -XX:PerfDataSaveFile=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/perf-counters.hsperfdata -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpGzipLevel=1 -XX:HeapDumpPath=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/heap-dump.hprof.gz -XX:+CrashOnOutOfMemoryError -XX:ErrorFile=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/hs-jvm-err.log -Xlog:jfr*=trace:file=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/jfr-jvm.log::filecount=3,filesize=5m -XX:FlightRecorderOptions:repository=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/streaming-repo,preserve-repository=true -XX:StartFlightRecording=name=jeffrey-streaming,maxage=24h,jeffrey.ImportantMessage#enabled=true -Xmx1200m -Xms1200m -XX:+UseG1GC -XX:+AlwaysPreTouch'
export JDK_JAVA_OPTIONS='-agentpath:/tmp/asprof/libasyncProfiler.so=start,alloc,lock,event=ctimer,jfrsync=default,loop=15m,chunksize=5m,file=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/profile-%t.jfr -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:+UsePerfData -XX:PerfDataSaveFile=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/perf-counters.hsperfdata -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpGzipLevel=1 -XX:HeapDumpPath=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/heap-dump.hprof.gz -XX:+CrashOnOutOfMemoryError -XX:ErrorFile=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/hs-jvm-err.log -Xlog:jfr*=trace:file=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/jfr-jvm.log::filecount=3,filesize=5m -XX:FlightRecorderOptions:repository=/tmp/jeffrey/workspaces/uat/jeffrey/019bea41-630d-7764-9ca5-7bc6099bd560/streaming-repo,preserve-repository=true -XX:StartFlightRecording=name=jeffrey-streaming,maxage=24h,jeffrey.ImportantMessage#enabled=true -Xmx1200m -Xms1200m -XX:+UseG1GC -XX:+AlwaysPreTouch'`;
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Generated Output"
        icon="bi bi-file-earmark-code"
      />

      <div class="docs-content">
        <p>This page shows a complete example of the Jeffrey CLI <code>init</code> command: the input configuration file and the generated environment variables.</p>

        <h2 id="input-configuration">Input Configuration</h2>
        <p>The following HOCON configuration file (<code>jeffrey-init.conf</code>) enables all features and demonstrates typical settings:</p>

        <DocsCodeBlock
          language="hocon"
          :code="inputConfig"
        />

        <DocsCallout type="tip">
          <strong>Run the command:</strong> Execute <code>java -jar jeffrey-cli.jar init jeffrey-init.conf</code> to generate the environment variables shown below.
        </DocsCallout>

        <h2 id="generated-environment">Generated Environment Variables</h2>
        <p>When you run <code>jeffrey-cli init</code> with the above configuration, it outputs the following environment variables:</p>

        <DocsCodeBlock
          language="bash"
          :code="generatedEnvOutput"
        />

        <DocsCallout type="info">
          <strong>Source the output:</strong> Redirect the output to a file and source it: <code>java -jar jeffrey-cli.jar init config.conf > /tmp/jeffrey.env && source /tmp/jeffrey.env</code>
        </DocsCallout>

        <h2 id="variable-reference">Variable Reference</h2>
        <p>Each generated environment variable serves a specific purpose:</p>

        <table>
          <thead>
            <tr>
              <th>Variable</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>JEFFREY_HOME</code></td>
              <td>Base directory for all Jeffrey data (from <code>jeffrey-home</code> config)</td>
            </tr>
            <tr>
              <td><code>JEFFREY_WORKSPACES</code></td>
              <td>Directory containing all workspaces (<code>$JEFFREY_HOME/workspaces</code>)</td>
            </tr>
            <tr>
              <td><code>JEFFREY_CURRENT_WORKSPACE</code></td>
              <td>Path to the current workspace directory (from <code>project.workspace-id</code> config)</td>
            </tr>
            <tr>
              <td><code>JEFFREY_CURRENT_PROJECT</code></td>
              <td>Path to the current project directory (from <code>project.name</code> config)</td>
            </tr>
            <tr>
              <td><code>JEFFREY_CURRENT_SESSION</code></td>
              <td>Path to the current session directory (unique UUID per CLI invocation)</td>
            </tr>
            <tr>
              <td><code>JEFFREY_FILE_PATTERN</code></td>
              <td>JFR output file pattern with <code>%t</code> timestamp placeholder</td>
            </tr>
            <tr>
              <td><code>JEFFREY_PROFILER_CONFIG</code></td>
              <td>Complete JVM flags for profiling - use with <code>java $JEFFREY_PROFILER_CONFIG -jar app.jar</code></td>
            </tr>
            <tr>
              <td><code>JDK_JAVA_OPTIONS</code></td>
              <td>Same flags as <code>JEFFREY_PROFILER_CONFIG</code> but auto-picked by JVM (when <code>jdk-java-options</code> enabled)</td>
            </tr>
          </tbody>
        </table>

        <h3>JVM Flags in JEFFREY_PROFILER_CONFIG</h3>
        <p>The <code>JEFFREY_PROFILER_CONFIG</code> variable contains all JVM flags based on enabled features:</p>

        <table>
          <thead>
            <tr>
              <th>Flag</th>
              <th>Source Feature</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>-agentpath:...libasyncProfiler.so=...</code></td>
              <td>Core profiler (always included)</td>
            </tr>
            <tr>
              <td><code>-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints</code></td>
              <td><code>debug-non-safepoints { enabled = true }</code> (enabled by default)</td>
            </tr>
            <tr>
              <td><code>-XX:+UsePerfData -XX:PerfDataSaveFile=...</code></td>
              <td><code>perf-counters { enabled = true }</code></td>
            </tr>
            <tr>
              <td><code>-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=...</code></td>
              <td><code>heap-dump { enabled = true }</code></td>
            </tr>
            <tr>
              <td><code>-XX:+CrashOnOutOfMemoryError -XX:ErrorFile=...</code></td>
              <td><code>heap-dump { type = "crash" }</code></td>
            </tr>
            <tr>
              <td><code>-Xlog:jfr*=trace:file=...</code></td>
              <td><code>jvm-logging { enabled = true }</code></td>
            </tr>
            <tr>
              <td><code>-XX:FlightRecorderOptions:repository=...</code></td>
              <td><code>messaging { enabled = true }</code></td>
            </tr>
            <tr>
              <td><code>-XX:StartFlightRecording=...</code></td>
              <td><code>messaging { enabled = true }</code></td>
            </tr>
            <tr>
              <td><code>-Xmx1200m -Xms1200m -XX:+UseG1GC -XX:+AlwaysPreTouch</code></td>
              <td><code>jdk-java-options { additional-options = "..." }</code></td>
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
