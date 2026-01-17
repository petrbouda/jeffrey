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
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { adjacentPages } = useDocsNavigation();
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'deployment', text: 'Deployment', level: 2 },
  { id: 'configmap', text: 'ConfigMap', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const deploymentYaml = `apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-service
  namespace: profiling
spec:
  replicas: 1
  selector:
    matchLabels:
      app: my-service
  template:
    metadata:
      labels:
        app: my-service
    spec:
      volumes:
        - name: config-volume
          configMap:
            name: my-service-config
        - name: jeffrey-data
          persistentVolumeClaim:
            claimName: jeffrey-pvc
      containers:
        - name: my-service
          image: my-registry/my-service:latest
          command:
            - /bin/bash
            - '-c'
            - >-
              eval "$(java -jar /data/jeffrey/libs/jeffrey-cli.jar
              init /mnt/config/jeffrey-init.conf)" &&
              exec java -jar /app/my-service.jar
          ports:
            - name: http
              containerPort: 8080
              protocol: TCP
          env:
            - name: ENV_NAME
              value: "UAT"
            - name: SERVICE_NAME
              value: "my-service"
          resources:
            limits:
              memory: 1Gi
              cpu: '1'
            requests:
              memory: 1Gi
              cpu: '1'
          volumeMounts:
            - name: config-volume
              mountPath: /mnt/config
            - name: jeffrey-data
              mountPath: /data/jeffrey
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
      restartPolicy: Always
      terminationGracePeriodSeconds: 30`;

const configMapYaml = `apiVersion: v1
kind: ConfigMap
metadata:
  name: my-service-config
  namespace: profiling
data:
  jeffrey-init.conf: |
    # Jeffrey CLI configuration for service profiling

    jeffrey-home = "/data/jeffrey"
    profiler-path = "/data/jeffrey/libs/libasyncProfiler.so"

    workspace-id = "uat"
    project-name = \${SERVICE_NAME}"-"\${ENV_NAME}
    project-label = \${SERVICE_NAME}" "\${ENV_NAME}

    perf-counters { enabled = true }
    heap-dump { enabled = true, type = "crash" }

    messaging {
      enabled = true
      max-age = "24h"
    }

    jvm-logging {
      enabled = true
      command = "jfr*=trace:file=<<JEFFREY_CURRENT_SESSION>>/jfr-jvm.log::filecount=3,filesize=5m"
    }

    jdk-java-options {
      enabled = true
      additional-options = "-Xmx512m -Xms512m -XX:+UseG1GC"
    }

    attributes {
      env = \${?ENV_NAME}
      service = \${?SERVICE_NAME}
      namespace = "profiling"
    }`;
</script>

<template>
  <article class="docs-article">
      <nav class="docs-breadcrumb">
        <router-link to="/docs" class="breadcrumb-item">
          <i class="bi bi-book me-1"></i>Docs
        </router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">Live Recording</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Service Deployment</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-gear"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Service Deployment</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>This guide demonstrates deploying a Java service on Kubernetes with <strong>continuous profiling</strong> using Jeffrey CLI and shared storage.</p>

        <DocsCallout type="danger">
          <strong>Single pod limitation:</strong> Currently, only single-replica deployments are supported. Multi-pod scaling support is coming soon.
        </DocsCallout>

        <h2 id="overview">Overview</h2>
        <p>The deployment consists of:</p>
        <ul>
          <li><strong>Deployment</strong> - Your service container with CLI initialization</li>
          <li><strong>ConfigMap</strong> - Jeffrey CLI configuration</li>
          <li><strong>Shared PVC</strong> - Same storage volume that Jeffrey monitors</li>
        </ul>

        <DocsCallout type="info">
          <strong>Shared storage:</strong> The service mounts the same PersistentVolumeClaim as Jeffrey. Recording sessions are written to this shared storage, and Jeffrey automatically discovers and displays them in the Repository.
        </DocsCallout>

        <h3>How It Works</h3>
        <p>The container startup command:</p>
        <ol>
          <li>Runs <code>jeffrey-cli.jar</code> from the shared storage to generate JVM flags</li>
          <li>Uses <code>eval</code> to set environment variables (including <code>JDK_JAVA_OPTIONS</code>)</li>
          <li>Starts your service with profiling automatically enabled</li>
        </ol>

        <DocsCallout type="tip">
          <strong>No image modification required:</strong> Jeffrey CLI and Async-Profiler are loaded from the shared storage. Your service image doesn't need to include any profiling tools.
        </DocsCallout>

        <h2 id="deployment">Deployment</h2>
        <DocsCodeBlock
          language="yaml"
          :code="deploymentYaml"
        />

        <h3>Key Points</h3>
        <ul>
          <li><strong>Shared PVC</strong> - Mounts <code>jeffrey-pvc</code> at <code>/data/jeffrey</code> (same as Jeffrey)</li>
          <li><strong>CLI from shared storage</strong> - Uses <code>/data/jeffrey/libs/jeffrey-cli.jar</code></li>
          <li><strong>Environment variables</strong> - <code>SERVICE_NAME</code> and <code>ENV_NAME</code> are used in the CLI config</li>
          <li><strong>No special image</strong> - Works with any Java application image</li>
        </ul>

        <h2 id="configmap">ConfigMap</h2>
        <p>Contains the <code>jeffrey-init.conf</code> for Jeffrey CLI:</p>
        <DocsCodeBlock
          language="yaml"
          :code="configMapYaml"
        />

        <h3>CLI Configuration Highlights</h3>
        <ul>
          <li><strong>profiler-path</strong> - Points to Async-Profiler on shared storage</li>
          <li><strong>project-name</strong> - Uses <code>SERVICE_NAME</code> and <code>ENV_NAME</code> for unique project identification</li>
          <li><strong>jdk-java-options</strong> - Enabled so profiling flags are automatically picked up by the JVM</li>
          <li><strong>attributes</strong> - Custom metadata that appears in Jeffrey UI</li>
        </ul>

        <DocsCallout type="info">
          <strong>Multiple services:</strong> Each service creates its own project in Jeffrey based on <code>project-name</code>. Use environment variables to ensure unique project names across your services.
        </DocsCallout>
      </div>

      <nav class="docs-nav-footer">
        <router-link
          v-if="adjacentPages.prev"
          :to="`/docs/${adjacentPages.prev.category}/${adjacentPages.prev.path}`"
          class="nav-link prev"
        >
          <i class="bi bi-arrow-left"></i>
          <div class="nav-text">
            <span class="nav-label">Previous</span>
            <span class="nav-title">{{ adjacentPages.prev.title }}</span>
          </div>
        </router-link>
        <div v-else class="nav-spacer"></div>
        <router-link
          v-if="adjacentPages.next"
          :to="`/docs/${adjacentPages.next.category}/${adjacentPages.next.path}`"
          class="nav-link next"
        >
          <div class="nav-text">
            <span class="nav-label">Next</span>
            <span class="nav-title">{{ adjacentPages.next.title }}</span>
          </div>
          <i class="bi bi-arrow-right"></i>
        </router-link>
      </nav>
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
