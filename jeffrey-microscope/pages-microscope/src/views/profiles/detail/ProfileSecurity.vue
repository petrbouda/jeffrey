<template>
  <LoadingState v-if="loading" message="Loading security analysis..." />

  <ErrorState v-else-if="error" message="Failed to load security analysis" />

  <div v-else>
    <PageHeader
      title="Security & TLS"
      description="TLS handshakes, X.509 certificates, deserialization and crypto-provider usage"
      icon="bi-shield-lock"
    />

    <EmptyState
      v-if="!hasData"
      icon="bi-shield-lock"
      title="No security events in this recording"
      description="This profile contains no TLS, certificate, deserialization, or crypto-provider events."
    />

    <div v-else>
      <div class="mb-4">
        <StatsTable :metrics="metrics" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- TLS Handshakes -->
      <div v-show="activeTab === 'tls'">
        <ChartDescription
          shows="TLS handshakes per second over the recording"
          use-case="Sustained high handshake rates indicate connection churn / missing session resumption — a latency and CPU cost"
        />
        <TimeSeriesChart
          :primary-data="tlsTimeline"
          primary-title="TLS Handshakes"
          :primary-axis-type="AxisFormatType.NUMBER"
          :visible-minutes="60"
          primary-color="#4285F4"
        />
        <div class="row mt-4">
          <div class="col-lg-4">
            <h6 class="section-title">Protocol Versions</h6>
            <div class="table-responsive">
              <table class="table table-sm table-hover mb-0">
                <thead>
                  <tr>
                    <th>Protocol</th>
                    <th class="text-end">Handshakes</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in data!.protocols" :key="p.name">
                    <td>
                      {{ p.name }}
                      <Badge
                        v-if="isLegacyProtocol(p.name)"
                        value="legacy"
                        variant="danger"
                        size="xs"
                      />
                    </td>
                    <td class="text-end">{{ FormattingService.formatNumber(p.count) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="col-lg-4">
            <h6 class="section-title">Cipher Suites</h6>
            <div class="table-responsive">
              <table class="table table-sm table-hover mb-0">
                <thead>
                  <tr>
                    <th>Cipher</th>
                    <th class="text-end">Handshakes</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="c in data!.ciphers" :key="c.name">
                    <td>{{ c.name }}</td>
                    <td class="text-end">{{ FormattingService.formatNumber(c.count) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="col-lg-4">
            <h6 class="section-title">Top Peers</h6>
            <div class="table-responsive">
              <table class="table table-sm table-hover mb-0">
                <thead>
                  <tr>
                    <th>Peer</th>
                    <th class="text-end">Handshakes</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="peer in data!.peers" :key="peer.name">
                    <td>{{ peer.name }}</td>
                    <td class="text-end">{{ FormattingService.formatNumber(peer.count) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <EmptyState
          v-if="data!.protocols.length === 0"
          icon="bi-shield"
          title="No TLS handshakes recorded"
        />
      </div>

      <!-- Certificates -->
      <div v-show="activeTab === 'certificates'">
        <ChartDescription
          shows="X.509 certificates observed, with key/signature strength and expiry flags"
          use-case="Spot weak keys, deprecated signature algorithms, and certificates that are expired or expiring soon"
        />
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Subject</th>
                <th>Issuer</th>
                <th>Key</th>
                <th>Signature</th>
                <th>Valid Until</th>
                <th class="text-end">Validations</th>
                <th>Flags</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(c, i) in data!.certificates" :key="i">
                <td>{{ c.subject }}</td>
                <td>{{ c.issuer }}</td>
                <td>{{ c.keyType }} {{ c.keyLength }}</td>
                <td>{{ c.signatureAlgorithm }}</td>
                <td>{{ FormattingService.formatTimestamp(c.validUntil) }}</td>
                <td class="text-end">{{ FormattingService.formatNumber(c.validationCount) }}</td>
                <td>
                  <Badge
                    v-if="c.weakKey"
                    value="weak key"
                    variant="danger"
                    size="xs"
                    class="me-1"
                  />
                  <Badge
                    v-if="c.weakSignature"
                    value="weak sig"
                    variant="danger"
                    size="xs"
                    class="me-1"
                  />
                  <Badge v-if="c.expired" value="expired" variant="danger" size="xs" class="me-1" />
                  <Badge
                    v-if="c.expiringSoon"
                    value="expiring"
                    variant="warning"
                    size="xs"
                    class="me-1"
                  />
                  <span v-if="!c.weakKey && !c.weakSignature && !c.expired && !c.expiringSoon"
                    >—</span
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState
          v-if="data!.certificates.length === 0"
          icon="bi-patch-check"
          title="No certificates recorded"
          description="No jdk.X509Certificate events were present."
        />
      </div>

      <!-- Deserialization -->
      <div v-show="activeTab === 'deserialization'">
        <ChartDescription
          shows="Object deserialization by type, with filter status and payload size"
          use-case="Unfiltered or oversized deserialization is a security and performance risk — review rejected events and the largest graphs"
        />
        <div class="mb-3">
          <StatsTable :metrics="deserMetrics" />
        </div>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Type</th>
                <th class="text-end">Count</th>
                <th class="text-end">Total Bytes</th>
                <th class="text-end">Max Bytes</th>
                <th class="text-end">Max Depth</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(t, i) in data!.deserializationTypes" :key="i">
                <td>{{ t.type }}</td>
                <td class="text-end">{{ FormattingService.formatNumber(t.count) }}</td>
                <td class="text-end">{{ FormattingService.formatBytes(t.totalBytes) }}</td>
                <td class="text-end">{{ FormattingService.formatBytes(t.maxBytes) }}</td>
                <td class="text-end">{{ FormattingService.formatNumber(t.maxDepth) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState
          v-if="data!.deserializationTypes.length === 0"
          icon="bi-box-arrow-in-down"
          title="No deserialization recorded"
          description="No jdk.Deserialization events were present."
        />

        <h6 class="section-title mt-4">Serialization Misdeclarations</h6>
        <ChartDescription
          shows="Serializable classes the JVM flagged for misdeclared serialization members"
          use-case="Misdeclared serialVersionUID, writeObject, or serialPersistentFields silently break serialization contracts — fix or document each"
        />
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Class</th>
                <th>Message</th>
                <th class="text-end">Count</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(m, i) in data!.serializationMisdeclarations" :key="i">
                <td>{{ m.misdeclaredClass }}</td>
                <td>{{ m.message }}</td>
                <td class="text-end">{{ FormattingService.formatNumber(m.count) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState
          v-if="data!.serializationMisdeclarations.length === 0"
          icon="bi-check-circle"
          title="No serialization misdeclarations"
          description="No jdk.SerializationMisdeclaration events were recorded (JDK 26+)."
        />
      </div>

      <!-- Crypto Providers -->
      <div v-show="activeTab === 'providers'">
        <ChartDescription
          shows="JCA crypto providers, service types, and algorithms actually exercised"
          use-case="Confirm which providers/algorithms are in use and detect unexpected or weak algorithm usage"
        />
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Provider</th>
                <th>Type</th>
                <th>Algorithm</th>
                <th class="text-end">Uses</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(p, i) in data!.cryptoProviders" :key="i">
                <td>{{ p.provider }}</td>
                <td>{{ p.type }}</td>
                <td>{{ p.algorithm }}</td>
                <td class="text-end">{{ FormattingService.formatNumber(p.count) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState
          v-if="data!.cryptoProviders.length === 0"
          icon="bi-key"
          title="No crypto-provider events"
          description="No jdk.SecurityProviderService events were present."
        />
      </div>

      <!-- About -->
      <div v-show="activeTab === 'about'">
        <ConfigurationSection title="How Security & TLS Analysis Works" icon="bi-info-circle">
          <p class="about-text">
            This page aggregates the JDK security JFR events. <strong>TLS handshakes</strong>
            (<code>jdk.TLSHandshake</code>) reveal negotiated protocols/ciphers and connection
            churn;
            <strong>certificates</strong> (<code>jdk.X509Certificate</code> /
            <code>jdk.X509Validation</code>) are flagged for weak keys, deprecated signature
            algorithms, and expiry;
            <strong>deserialization</strong> (<code>jdk.Deserialization</code>) surfaces unfiltered
            or oversized object graphs; and <strong>crypto providers</strong>
            (<code>jdk.SecurityProviderService</code>) show which JCA algorithms were exercised.
            These are instant events, so no per-operation latency is available.
          </p>
        </ConfigurationSection>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import TabBar from '@/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import ConfigurationSection from '@/components/ConfigurationSection.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import Badge from '@/components/Badge.vue';
import ProfileSecurityClient from '@/services/api/ProfileSecurityClient';
import FormattingService from '@/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import type SecurityData from '@/services/api/model/SecurityModels';

const route = useRoute();

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<SecurityData>();

const tabs = [
  { id: 'tls', label: 'TLS Handshakes', icon: 'shield' },
  { id: 'certificates', label: 'Certificates', icon: 'patch-check' },
  { id: 'deserialization', label: 'Deserialization', icon: 'box-arrow-in-down' },
  { id: 'providers', label: 'Crypto Providers', icon: 'key' },
  { id: 'about', label: 'About', icon: 'info-circle' }
];
const activeTab = ref(tabs[0].id);

const hasData = computed(() => {
  const d = data.value;
  if (!d) {
    return false;
  }
  return (
    d.header.tlsHandshakes > 0 ||
    d.header.certificates > 0 ||
    d.header.deserializationEvents > 0 ||
    d.cryptoProviders.length > 0
  );
});

const tlsTimeline = computed(() => data.value?.tlsTimeline.series?.[0]?.data ?? []);

const isLegacyProtocol = (name: string): boolean => name.includes('1.0') || name.includes('1.1');

const metrics = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return [];
  }
  return [
    {
      icon: 'shield',
      title: 'TLS Handshakes',
      value: FormattingService.formatNumber(h.tlsHandshakes),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'Distinct Peers', value: FormattingService.formatNumber(h.distinctPeers) }
      ]
    },
    {
      icon: 'patch-check',
      title: 'Certificates',
      value: FormattingService.formatNumber(h.certificates),
      variant: h.flaggedCertificates > 0 ? ('danger' as const) : ('success' as const),
      breakdown: [
        { label: 'Flagged', value: FormattingService.formatNumber(h.flaggedCertificates) }
      ]
    },
    {
      icon: 'box-arrow-in-down',
      title: 'Deserialization',
      value: FormattingService.formatNumber(h.deserializationEvents),
      variant: h.deserializationRejected > 0 ? ('warning' as const) : ('info' as const),
      breakdown: [
        { label: 'Rejected', value: FormattingService.formatNumber(h.deserializationRejected) }
      ]
    }
  ];
});

const deserMetrics = computed(() => {
  const s = data.value?.deserialization;
  if (!s) {
    return [];
  }
  return [
    {
      icon: 'box-arrow-in-down',
      title: 'Deserialization Events',
      value: FormattingService.formatNumber(s.totalEvents),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Filter Configured',
          value: FormattingService.formatNumber(s.filterConfiguredEvents)
        }
      ]
    },
    {
      icon: 'shield-exclamation',
      title: 'Rejected',
      value: FormattingService.formatNumber(s.rejectedEvents),
      variant: s.rejectedEvents > 0 ? ('warning' as const) : ('success' as const),
      breakdown: [{ label: 'Exceptions', value: FormattingService.formatNumber(s.exceptionEvents) }]
    }
  ];
});

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;
    const client = new ProfileSecurityClient(route.params.profileId as string);
    data.value = await client.getData();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading security analysis:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(loadData);
</script>

<style scoped>
.section-title {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--color-text-muted);
  margin-bottom: 0.5rem;
}

.about-text {
  font-size: 0.9rem;
  line-height: 1.6;
  color: var(--color-dark);
  margin: 0;
}
</style>
