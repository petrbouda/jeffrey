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
            <DataTable v-if="data!.protocols.length > 0">
              <template #toolbar>
                <TableToolbar v-model="protocolsView.query" search-placeholder="Filter protocols...">
                  <span class="toolbar-info">Protocol Versions</span>
                  <template #filters>
                    <Badge
                      key-label="Total"
                      :value="protocolsView.matchCount"
                      variant="secondary"
                      size="s"
                      borderless
                    />
                  </template>
                </TableToolbar>
              </template>
              <thead>
                <tr>
                  <th>Protocol</th>
                  <th class="text-end">Handshakes</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in protocolsView.visible" :key="p.name">
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
              <template #footer>
                <TableShowMore
                  :shown="protocolsView.visible.length"
                  :match-count="protocolsView.matchCount"
                  :total="protocolsView.total"
                  :expanded="protocolsView.expanded"
                  :page-size="protocolsView.pageSize"
                  @toggle="protocolsView.toggle"
                />
              </template>
            </DataTable>
          </div>
          <div class="col-lg-4">
            <DataTable v-if="data!.ciphers.length > 0">
              <template #toolbar>
                <TableToolbar v-model="ciphersView.query" search-placeholder="Filter ciphers...">
                  <span class="toolbar-info">Cipher Suites</span>
                  <template #filters>
                    <Badge
                      key-label="Total"
                      :value="ciphersView.matchCount"
                      variant="secondary"
                      size="s"
                      borderless
                    />
                  </template>
                </TableToolbar>
              </template>
              <thead>
                <tr>
                  <th>Cipher</th>
                  <th class="text-end">Handshakes</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="c in ciphersView.visible" :key="c.name">
                  <td>{{ c.name }}</td>
                  <td class="text-end">{{ FormattingService.formatNumber(c.count) }}</td>
                </tr>
              </tbody>
              <template #footer>
                <TableShowMore
                  :shown="ciphersView.visible.length"
                  :match-count="ciphersView.matchCount"
                  :total="ciphersView.total"
                  :expanded="ciphersView.expanded"
                  :page-size="ciphersView.pageSize"
                  @toggle="ciphersView.toggle"
                />
              </template>
            </DataTable>
          </div>
          <div class="col-lg-4">
            <DataTable v-if="data!.peers.length > 0">
              <template #toolbar>
                <TableToolbar v-model="peersView.query" search-placeholder="Filter peers...">
                  <span class="toolbar-info">Top Peers</span>
                  <template #filters>
                    <Badge
                      key-label="Total"
                      :value="peersView.matchCount"
                      variant="secondary"
                      size="s"
                      borderless
                    />
                  </template>
                </TableToolbar>
              </template>
              <thead>
                <tr>
                  <th>Peer</th>
                  <th class="text-end">Handshakes</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="peer in peersView.visible" :key="peer.name">
                  <td>{{ peer.name }}</td>
                  <td class="text-end">{{ FormattingService.formatNumber(peer.count) }}</td>
                </tr>
              </tbody>
              <template #footer>
                <TableShowMore
                  :shown="peersView.visible.length"
                  :match-count="peersView.matchCount"
                  :total="peersView.total"
                  :expanded="peersView.expanded"
                  :page-size="peersView.pageSize"
                  @toggle="peersView.toggle"
                />
              </template>
            </DataTable>
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
        <DataTable v-if="data!.certificates.length > 0">
          <template #toolbar>
            <TableToolbar
              v-model="certificatesView.query"
              search-placeholder="Filter certificates..."
            >
              <span class="toolbar-info">Certificates</span>
              <template #filters>
                <Badge
                  key-label="Total"
                  :value="certificatesView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
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
            <tr v-for="(c, i) in certificatesView.visible" :key="i">
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
          <template #footer>
            <TableShowMore
              :shown="certificatesView.visible.length"
              :match-count="certificatesView.matchCount"
              :total="certificatesView.total"
              :expanded="certificatesView.expanded"
              :page-size="certificatesView.pageSize"
              @toggle="certificatesView.toggle"
            />
          </template>
        </DataTable>
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
        <DataTable v-if="data!.deserializationTypes.length > 0">
          <template #toolbar>
            <TableToolbar
              v-model="deserializationTypesView.query"
              search-placeholder="Filter types..."
            >
              <span class="toolbar-info">Deserialization Types</span>
              <template #filters>
                <Badge
                  key-label="Total"
                  :value="deserializationTypesView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
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
            <tr v-for="(t, i) in deserializationTypesView.visible" :key="i">
              <td>{{ t.type }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(t.count) }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(t.totalBytes) }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(t.maxBytes) }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(t.maxDepth) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="deserializationTypesView.visible.length"
              :match-count="deserializationTypesView.matchCount"
              :total="deserializationTypesView.total"
              :expanded="deserializationTypesView.expanded"
              :page-size="deserializationTypesView.pageSize"
              @toggle="deserializationTypesView.toggle"
            />
          </template>
        </DataTable>
        <EmptyState
          v-if="data!.deserializationTypes.length === 0"
          icon="bi-box-arrow-in-down"
          title="No deserialization recorded"
          description="No jdk.Deserialization events were present."
        />

        <ChartDescription
          class="mt-4"
          shows="Serializable classes the JVM flagged for misdeclared serialization members"
          use-case="Misdeclared serialVersionUID, writeObject, or serialPersistentFields silently break serialization contracts — fix or document each"
        />
        <DataTable v-if="data!.serializationMisdeclarations.length > 0">
          <template #toolbar>
            <TableToolbar
              v-model="serializationMisdeclarationsView.query"
              search-placeholder="Filter misdeclarations..."
            >
              <span class="toolbar-info">Serialization Misdeclarations</span>
              <template #filters>
                <Badge
                  key-label="Total"
                  :value="serializationMisdeclarationsView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Class</th>
              <th>Message</th>
              <th class="text-end">Count</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(m, i) in serializationMisdeclarationsView.visible" :key="i">
              <td>{{ m.misdeclaredClass }}</td>
              <td>{{ m.message }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(m.count) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="serializationMisdeclarationsView.visible.length"
              :match-count="serializationMisdeclarationsView.matchCount"
              :total="serializationMisdeclarationsView.total"
              :expanded="serializationMisdeclarationsView.expanded"
              :page-size="serializationMisdeclarationsView.pageSize"
              @toggle="serializationMisdeclarationsView.toggle"
            />
          </template>
        </DataTable>
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
        <DataTable v-if="data!.cryptoProviders.length > 0">
          <template #toolbar>
            <TableToolbar
              v-model="cryptoProvidersView.query"
              search-placeholder="Filter providers..."
            >
              <span class="toolbar-info">Crypto Providers</span>
              <template #filters>
                <Badge
                  key-label="Total"
                  :value="cryptoProvidersView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Provider</th>
              <th>Type</th>
              <th>Algorithm</th>
              <th class="text-end">Uses</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(p, i) in cryptoProvidersView.visible" :key="i">
              <td>{{ p.provider }}</td>
              <td>{{ p.type }}</td>
              <td>{{ p.algorithm }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(p.count) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="cryptoProvidersView.visible.length"
              :match-count="cryptoProvidersView.matchCount"
              :total="cryptoProvidersView.total"
              :expanded="cryptoProvidersView.expanded"
              :page-size="cryptoProvidersView.pageSize"
              @toggle="cryptoProvidersView.toggle"
            />
          </template>
        </DataTable>
        <EmptyState
          v-if="data!.cryptoProviders.length === 0"
          icon="bi-key"
          title="No crypto-provider events"
          description="No jdk.SecurityProviderService events were present."
        />
      </div>

      <!-- How It Works -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Security & TLS"
          subtitle="What your TLS, certificate, deserialization and crypto usage looks like"
        >
          <AboutCallout variant="intro">
            <p>
              This page aggregates the JDK's security JFR events into one view: negotiated
              <strong>TLS handshakes</strong>, the <strong>X.509 certificates</strong> seen and how
              strong they are, Java <strong>deserialization</strong> activity and its filter status,
              and which JCA <strong>crypto providers</strong> and algorithms your code actually
              exercised. These are instant events, so there is no per-operation latency — they tell
              you <em>what</em> happened, not how long it took.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-shield-lock" title="What the Views Show">
            <FeatureGrid>
              <FeatureCard icon="bi-shield" variant="primary" title="TLS Handshakes">
                Handshakes over time plus the negotiated protocols, cipher suites and top peers.
                Sustained high rates and legacy protocols signal connection churn or missing session
                resumption.
              </FeatureCard>
              <FeatureCard icon="bi-patch-check" variant="info" title="Certificates">
                X.509 certificates with key/signature strength and expiry, flagged for weak keys,
                deprecated signature algorithms, and certificates that are expired or expiring soon.
              </FeatureCard>
              <FeatureCard icon="bi-box-arrow-in-down" variant="warning" title="Deserialization">
                Object deserialization by type, with filter status, payload sizes and graph depth —
                unfiltered or oversized graphs are a security and performance risk.
              </FeatureCard>
              <FeatureCard icon="bi-key" variant="success" title="Crypto Providers">
                The JCA providers, service types and algorithms actually used, so you can confirm
                expected providers and spot weak or unexpected algorithm usage.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutCallout variant="tip" title="Treat flagged items as a checklist" icon="bi-lightbulb-fill">
            Legacy TLS protocols, weak keys/signatures, expiring certificates, and rejected
            deserialization are the rows worth acting on first — they are concrete, fixable security
            findings, not just informational telemetry.
          </AboutCallout>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.TLSHandshake</code> — negotiated protocol/cipher per handshake, with the
                peer; powers handshake-rate and connection-churn views.
              </li>
              <li>
                <code>jdk.X509Certificate</code> — certificates seen (subject, issuer, key
                algorithm/size, validity window).
              </li>
              <li>
                <code>jdk.X509Validation</code> — certificate-chain validation events, counted per
                certificate.
              </li>
              <li>
                <code>jdk.Deserialization</code> — Java deserialization attempts (filter status,
                sizes, depth).
              </li>
              <li>
                <code>jdk.SecurityProviderService</code> — JCA provider/algorithm lookups.
              </li>
            </ul>
            <p>
              These security events are <strong>generally disabled by default</strong> in the
              bundled <code>default</code> / <code>profile</code> configs and must be enabled in the
              JFR configuration before they appear here. They are <strong>instant events</strong>,
              so they carry no per-operation latency — only what was negotiated, seen, or attempted.
            </p>
          </AboutSection>
        </AboutPanel>
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
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import Badge from '@/components/Badge.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import { useTableView } from '@/composables/useTableView';
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
  { id: 'about', label: 'How It Works', icon: 'book' }
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

const protocolsView = useTableView(() => data.value?.protocols ?? [], {
  searchableText: p => p.name
});
const ciphersView = useTableView(() => data.value?.ciphers ?? [], {
  searchableText: c => c.name
});
const peersView = useTableView(() => data.value?.peers ?? [], {
  searchableText: peer => peer.name
});
const certificatesView = useTableView(() => data.value?.certificates ?? [], {
  searchableText: c => `${c.subject} ${c.issuer}`
});
const deserializationTypesView = useTableView(() => data.value?.deserializationTypes ?? [], {
  searchableText: t => t.type
});
const serializationMisdeclarationsView = useTableView(
  () => data.value?.serializationMisdeclarations ?? [],
  {
    searchableText: m => `${m.misdeclaredClass} ${m.message}`
  }
);
const cryptoProvidersView = useTableView(() => data.value?.cryptoProviders ?? [], {
  searchableText: p => `${p.provider} ${p.type} ${p.algorithm}`
});

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

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}
</style>
