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
  { id: 'configmap', text: 'ConfigMap', level: 2 },
  { id: 'service-ingress', text: 'Service & Ingress', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const deploymentYaml = `apiVersion: apps/v1
kind: Deployment
metadata:
  name: jeffrey
  namespace: profiling
spec:
  replicas: 1
  selector:
    matchLabels:
      app: jeffrey
  template:
    metadata:
      labels:
        app: jeffrey
    spec:
      volumes:
        - name: config-volume
          configMap:
            name: jeffrey-config
        - name: jeffrey-data
          persistentVolumeClaim:
            claimName: jeffrey-pvc
      containers:
        - name: jeffrey
          image: petrbouda/jeffrey:latest
          command:
            - /bin/bash
            - '-c'
            - >-
              eval "$(java -jar /jeffrey-libs/jeffrey-cli.jar
              init /mnt/config/jeffrey-init.conf)" &&
              exec java -jar /app/jeffrey.jar
              --spring.config.location=file:/mnt/config/application.properties
          ports:
            - name: http
              containerPort: 8585
              protocol: TCP
          env:
            - name: ENV_NAME
              value: "UAT"
          resources:
            limits:
              memory: 2Gi
              cpu: '1'
            requests:
              memory: 2Gi
              cpu: '1'
          volumeMounts:
            - name: config-volume
              mountPath: /mnt/config
            - name: jeffrey-data
              mountPath: /data/jeffrey
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8585
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8585
            initialDelaySeconds: 30
            periodSeconds: 10
      restartPolicy: Always
      terminationGracePeriodSeconds: 30
  strategy:
    type: Recreate`;

const configMapYaml = `apiVersion: v1
kind: ConfigMap
metadata:
  name: jeffrey-config
  namespace: profiling
data:
  application.properties: |
    server.port=8585
    spring.main.banner-mode=off

    logging.level.pbouda.jeffrey=INFO

    # CORS mode: DEV (all origins) or PROD (restricted)
    jeffrey.cors.mode=DEV

    # Jeffrey home directory
    jeffrey.home.dir=/data/jeffrey
    jeffrey.temp.dir=\${jeffrey.home.dir}/temp

    # Job scheduler
    jeffrey.job.scheduler.enabled=true
    jeffrey.job.default.period=1m

    # Profile initialization
    jeffrey.profile.data-initializer.enabled=true
    jeffrey.profile.data-initializer.blocking=true

    # Recording storage
    jeffrey.project.recording-storage.path=\${jeffrey.home.dir}/recordings

    # Global profiler settings
    jeffrey.profiler.global-settings.create-if-not-exists=true
    jeffrey.profiler.global-settings.command=-agentpath:<<JEFFREY_PROFILER_PATH>>=start,alloc,lock,event=ctimer,loop=15m,chunksize=5m,file=<<JEFFREY_CURRENT_SESSION>>/profile-%t.jfr

    # Database
    jeffrey.persistence.database.url=jdbc:duckdb:\${jeffrey.home.dir}/jeffrey-data.db
    jeffrey.persistence.database.pool-size=25

  jeffrey-init.conf: |
    # Jeffrey CLI configuration for self-monitoring

    jeffrey-home = "/data/jeffrey"
    profiler-path = "/jeffrey-libs/libasyncProfiler.so"

    workspace-id = "uat"
    project-name = "jeffrey-"\${ENV_NAME}
    project-label = "Jeffrey "\${ENV_NAME}

    perf-counters { enabled = true }
    heap-dump { enabled = true, type = "crash" }
    jvm-logging {
      enabled = true
      command = "jfr*=trace:file=<<JEFFREY_CURRENT_SESSION>>/jfr-jvm.log::filecount=3,filesize=5m"
    }

    jdk-java-options {
      enabled = true
      additional-options = "-Xmx1200m -Xms1200m -XX:+UseG1GC -XX:+AlwaysPreTouch"
    }

    attributes {
      env = \${?ENV_NAME}
      namespace = "profiling"
    }`;

const serviceIngressYaml = `apiVersion: v1
kind: Service
metadata:
  name: jeffrey-service
  namespace: profiling
spec:
  selector:
    app: jeffrey
  ports:
    - name: http
      port: 8585
      targetPort: 8585
  type: ClusterIP
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: jeffrey-ingress
  namespace: profiling
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "200m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "300"
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - jeffrey.example.com
      secretName: jeffrey-tls
  rules:
    - host: jeffrey.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: jeffrey-service
                port:
                  number: 8585`;
</script>

<template>
  <article class="docs-article">
      <nav class="docs-breadcrumb">
        <router-link to="/docs" class="breadcrumb-item">
          <i class="bi bi-book me-1"></i>Docs
        </router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">Deployments</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Kubernetes</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-diagram-3"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Kubernetes Deployment</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>This guide demonstrates deploying Jeffrey on Kubernetes with <strong>self-monitoring</strong> - Jeffrey profiles itself using Jeffrey CLI.</p>

        <h2 id="overview">Overview</h2>
        <p>The deployment consists of:</p>
        <ul>
          <li><strong>Deployment</strong> - Jeffrey container with CLI initialization</li>
          <li><strong>ConfigMap</strong> - Application properties and CLI configuration</li>
          <li><strong>Service & Ingress</strong> - Network exposure</li>
          <li><strong>PersistentVolumeClaim</strong> - Storage for recordings and database</li>
        </ul>

        <DocsCallout type="info">
          <strong>Self-monitoring:</strong> Jeffrey uses its own CLI to configure profiling of itself. This is useful for monitoring Jeffrey's performance in production and dogfooding the profiling setup.
        </DocsCallout>

        <h3>How It Works</h3>
        <p>The container startup command:</p>
        <ol>
          <li>Runs <code>jeffrey-cli.jar init</code> to generate JVM flags</li>
          <li>Uses <code>eval</code> to set environment variables (including <code>JDK_JAVA_OPTIONS</code>)</li>
          <li>Starts Jeffrey with the profiling configuration active</li>
        </ol>

        <h2 id="deployment">Deployment</h2>
        <DocsCodeBlock
          language="yaml"
          :code="deploymentYaml"
        />

        <h3>Key Points</h3>
        <ul>
          <li><strong>Command</strong> - Uses <code>eval "$(java -jar ... init ...)"</code> to set environment variables before starting the app</li>
          <li><strong>Volume mounts</strong> - ConfigMap for configuration, PVC for persistent data</li>
          <li><strong>Environment variables</strong> - <code>ENV_NAME</code> is used in CLI config via HOCON substitution</li>
          <li><strong>Resources</strong> - 2GB memory recommended for Jeffrey with profiling enabled</li>
        </ul>

        <h2 id="configmap">ConfigMap</h2>
        <p>Contains both <code>application.properties</code> for Jeffrey and <code>jeffrey-init.conf</code> for the CLI:</p>
        <DocsCodeBlock
          language="yaml"
          :code="configMapYaml"
        />

        <h3>CLI Configuration Highlights</h3>
        <ul>
          <li><strong>jdk-java-options</strong> - Enabled so JVM flags are set via <code>JDK_JAVA_OPTIONS</code></li>
          <li><strong>attributes</strong> - Uses <code>${?VAR}</code> syntax for optional environment variable substitution</li>
          <li><strong>heap-dump</strong> - Configured to dump on crash for post-mortem analysis</li>
          <li><strong>jvm-logging</strong> - JFR trace logging for debugging profiler issues</li>
        </ul>

        <h2 id="service-ingress">Service & Ingress</h2>
        <DocsCodeBlock
          language="yaml"
          :code="serviceIngressYaml"
        />

        <DocsCallout type="tip">
          <strong>Large uploads:</strong> The ingress annotations increase proxy timeouts and body size limits. This is important for uploading large JFR files to Jeffrey.
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
