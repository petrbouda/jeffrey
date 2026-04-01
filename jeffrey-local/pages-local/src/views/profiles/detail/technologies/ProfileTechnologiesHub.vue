<template>
  <div class="technologies-hub">
    <div class="hub-grid">
      <!-- Integration info strip -->
      <div class="info-strip">
        <div class="info-strip-accent"></div>
        <div class="info-strip-body">
          <div class="info-strip-icon">
            <i class="bi bi-puzzle"></i>
          </div>
          <div class="info-strip-text">
            <div class="info-strip-heading">How to enable these dashboards?</div>
            <div class="info-strip-sub">
              Add <code>cafe.jeffrey-analyst:jeffrey-events:0.10.0</code> to your app to emit proper events — dashboards activate when events are detected.
            </div>
          </div>
          <div class="info-strip-actions">
            <a class="info-strip-btn info-strip-btn-ghost" href="https://central.sonatype.com/artifact/cafe.jeffrey-analyst/jeffrey-events" target="_blank">
              <i class="bi bi-box-seam"></i> Maven Central
            </a>
            <a class="info-strip-btn info-strip-btn-ghost" href="/docs/jeffrey-jfr-events/overview" target="_blank">
              <i class="bi bi-book"></i> Docs
            </a>
            <a class="info-strip-btn info-strip-btn-ghost" href="https://github.com/petrbouda/jeffrey-events" target="_blank">
              <i class="bi bi-github"></i> Sources
            </a>
          </div>
        </div>
      </div>

      <div
          v-for="tech in sortedTechnologies"
          :key="tech.id"
          class="tech-card"
          :class="{ disabled: tech.disabled }"
          @click="navigateTo(tech)"
      >
        <div class="card-accent" :class="tech.colorClass"></div>
        <div class="card-body">
          <div class="card-row">
            <i class="bi card-icon" :class="[tech.icon, tech.colorClass]"></i>
            <div class="card-name">{{ tech.name }}</div>
          </div>
          <div class="card-desc">{{ tech.description }}</div>
          <div class="card-bottom">
            <div class="card-status" :class="tech.disabled ? 'inactive' : 'active'">
              <span class="card-status-dot"></span>
              {{ tech.disabled ? 'No data' : 'Available' }}
            </div>
            <span v-if="!tech.disabled" class="card-link">
              Explore <i class="bi bi-arrow-right"></i>
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import FeatureType from '@/services/api/model/FeatureType';

const props = defineProps<{
  disabledFeatures: FeatureType[];
}>();

const route = useRoute();
const router = useRouter();
const profileId = route.params.profileId as string;

interface TechnologyCard {
  id: string;
  name: string;
  description: string;
  icon: string;
  colorClass: string;
  route: string;
  featureType: FeatureType;
  disabled: boolean;
}

const technologies = [
  {
    id: 'http-server',
    name: 'HTTP Server',
    description: 'Inbound HTTP request analysis — latency, endpoints, status codes',
    icon: 'bi-globe2',
    colorClass: 'color-http',
    route: `/profiles/${profileId}/technologies/http/timeseries?mode=server`,
    featureType: FeatureType.HTTP_SERVER_DASHBOARD,
  },
  {
    id: 'http-client',
    name: 'HTTP Client',
    description: 'Outbound HTTP requests — response times, targets, errors',
    icon: 'bi-send',
    colorClass: 'color-http',
    route: `/profiles/${profileId}/technologies/http/timeseries?mode=client`,
    featureType: FeatureType.HTTP_CLIENT_DASHBOARD,
  },
  {
    id: 'grpc-server',
    name: 'gRPC Server',
    description: 'gRPC service calls — methods, latency, traffic patterns',
    icon: 'bi-diagram-3',
    colorClass: 'color-grpc',
    route: `/profiles/${profileId}/technologies/grpc/timeseries?mode=server`,
    featureType: FeatureType.GRPC_SERVER_DASHBOARD,
  },
  {
    id: 'grpc-client',
    name: 'gRPC Client',
    description: 'Outbound gRPC calls — client methods, latency, errors',
    icon: 'bi-arrow-left-right',
    colorClass: 'color-grpc',
    route: `/profiles/${profileId}/technologies/grpc/timeseries?mode=client`,
    featureType: FeatureType.GRPC_CLIENT_DASHBOARD,
  },
  {
    id: 'jdbc',
    name: 'Database (JDBC)',
    description: 'SQL statements, query groups, connection pool analysis',
    icon: 'bi-database',
    colorClass: 'color-db',
    route: `/profiles/${profileId}/technologies/jdbc/timeseries`,
    featureType: FeatureType.JDBC_STATEMENTS_DASHBOARD,
  },
  {
    id: 'method-tracing',
    name: 'Method Tracing',
    description: 'Custom method traces — execution times, call trees',
    icon: 'bi-speedometer2',
    colorClass: 'color-tracing',
    route: `/profiles/${profileId}/technologies/method-tracing/timeseries`,
    featureType: FeatureType.TRACING_DASHBOARD,
  },
];

const sortedTechnologies = computed<TechnologyCard[]>(() => {
  const cards = technologies.map(tech => ({
    ...tech,
    disabled: props.disabledFeatures.includes(tech.featureType),
  }));
  return cards.sort((a, b) => Number(a.disabled) - Number(b.disabled));
});

const navigateTo = (tech: TechnologyCard) => {
  if (!tech.disabled) {
    router.push(tech.route);
  }
};
</script>

<style scoped>
.technologies-hub {
  padding: var(--spacing-2) 0;
}

.hub-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-4);
}

/* Card structure */
.tech-card {
  border-radius: 14px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  overflow: hidden;
  cursor: pointer;
  transition: all var(--transition-base);
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.tech-card:hover {
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

/* Disabled — readable, dashed border, no hover */
.tech-card.disabled {
  cursor: default;
  border-style: dashed;
  border-color: var(--color-border-input);
  box-shadow: none;
  background: var(--color-bg-card);
}

.tech-card.disabled:hover {
  box-shadow: none;
  transform: none;
}

.tech-card.disabled .card-accent {
  background: #e5e7eb;
}

.tech-card.disabled .card-icon {
  color: var(--color-text-muted) !important;
}

.tech-card.disabled .card-name {
  color: var(--color-text);
}

.tech-card.disabled .card-desc {
  color: var(--color-text-muted);
}

/* Accent bar */
.card-accent {
  height: 5px;
}

.card-accent.color-http {
  background: linear-gradient(90deg, var(--color-primary), #818cf8);
}

.card-accent.color-grpc {
  background: linear-gradient(90deg, #00d27a, #34d399);
}

.card-accent.color-db {
  background: linear-gradient(90deg, #f5803e, #fb923c);
}

.card-accent.color-tracing {
  background: linear-gradient(90deg, #39afd1, #67e8f9);
}

/* Card body */
.card-body {
  padding: var(--spacing-4) var(--spacing-5);
  flex: 1;
  display: flex;
  flex-direction: column;
}

.card-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-2);
}

.card-icon {
  font-size: 1.1rem;
}

.card-icon.color-http {
  color: var(--color-primary);
}

.card-icon.color-grpc {
  color: var(--color-success-hover);
}

.card-icon.color-db {
  color: var(--color-warning-hover);
}

.card-icon.color-tracing {
  color: var(--color-info-hover);
}

.card-name {
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-base);
}

.card-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  line-height: var(--line-height-base);
  flex: 1;
}

/* Footer */
.card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--spacing-3);
}

.card-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  line-height: 1;
}

.card-status.active {
  color: var(--color-success-hover);
}

.card-status.inactive {
  color: var(--color-text-muted);
}

.card-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.card-status.active .card-status-dot {
  background: var(--color-success);
}

.card-status.inactive .card-status-dot {
  background: var(--color-border-input);
}

.card-link {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-primary);
  line-height: 1;
}

/* Info strip */
.info-strip {
  grid-column: 1 / -1;
  border-radius: 14px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.info-strip-accent {
  height: 5px;
  background: linear-gradient(90deg, #f5803e, #fb923c);
}

.info-strip-body {
  padding: var(--spacing-4) var(--spacing-6);
  display: flex;
  align-items: center;
  gap: var(--spacing-5);
}

.info-strip-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: rgba(245, 128, 62, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.info-strip-icon i {
  font-size: 1rem;
  color: var(--color-warning-hover);
}

.info-strip-text {
  flex: 1;
  min-width: 0;
}

.info-strip-heading {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: #0b1727;
  margin-bottom: 2px;
}

.info-strip-sub {
  font-size: var(--font-size-base);
  color: var(--color-text-muted);
}

.info-strip-sub code {
  font-size: var(--font-size-sm);
  background: rgba(0, 0, 0, 0.05);
  padding: 1px 5px;
  border-radius: 3px;
  color: var(--color-text);
}

.info-strip-actions {
  display: flex;
  gap: var(--spacing-3);
  flex-shrink: 0;
}

.info-strip-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  padding: 6px 14px;
  border-radius: 8px;
  text-decoration: none;
  transition: all var(--transition-base);
  cursor: pointer;
  border: none;
}

.info-strip-btn-primary {
  background: var(--color-primary);
  color: #fff;
}

.info-strip-btn-primary:hover {
  background: var(--color-primary-hover);
}

.info-strip-btn-ghost {
  background: transparent;
  color: var(--color-text-muted);
  border: 1px solid var(--color-border);
}

.info-strip-btn-ghost:hover {
  background: var(--color-primary-lighter);
  color: var(--color-primary);
  border-color: rgba(94, 100, 255, 0.2);
}
</style>
