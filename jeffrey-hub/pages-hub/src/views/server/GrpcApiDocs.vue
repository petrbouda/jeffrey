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

<template>
  <div class="docs-page">
    <!-- Shared header with nav -->
    <div class="page-header">
      <div class="header-left">
        <img src="/jeffrey-icon.svg" alt="Jeffrey" class="header-logo">
        <h4>Jeffrey Hub</h4>
        <span v-if="version" class="version-badge">{{ version }}</span>
      </div>
      <nav class="header-nav">
        <router-link to="/" class="nav-tab">Workspaces</router-link>
        <router-link to="/scheduler" class="nav-tab">Scheduler</router-link>
        <router-link to="/api-docs" class="nav-tab">API Documentation</router-link>
      </nav>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="spinner-border spinner-border-sm text-secondary" role="status"></div>
      <span>Loading API documentation...</span>
    </div>

    <div v-else-if="error" class="error-state">
      <i class="bi bi-exclamation-triangle"></i>
      <span>{{ error }}</span>
    </div>

    <div v-else class="docs-content">
      <!-- Service nav -->
      <div class="service-nav">
        <button
            v-for="service in allServices"
            :key="service.fullName"
            class="service-nav-item"
            :class="{ active: activeService === service.fullName }"
            @click="scrollToService(service.fullName)"
        >
          {{ shortName(service.name) }}
          <span class="method-count">{{ service.methods.length }}</span>
        </button>
      </div>

      <!-- Services -->
      <div class="services-list">
        <div
            v-for="service in allServices"
            :key="service.fullName"
            :id="'svc-' + service.fullName"
            class="service-block"
        >
          <div class="service-header">
            <div class="service-title">
              <span class="service-name">{{ service.name }}</span>
              <span class="service-pkg">{{ service.fullName }}</span>
            </div>
            <p v-if="service.description" class="service-desc">{{ service.description }}</p>
          </div>

          <div class="methods-list">
            <div
                v-for="method in service.methods"
                :key="method.name"
                class="method-card"
            >
              <div class="method-header" @click="toggleMethod(service.fullName + '.' + method.name)">
                <div class="method-info">
                  <span class="method-name">{{ method.name }}</span>
                  <div class="method-badges">
                    <Badge
                      v-if="method.responseStreaming"
                      value="stream"
                      variant="orange"
                      size="xxs"
                    />
                    <Badge v-else value="unary" variant="green" size="xxs" />
                  </div>
                </div>
                <p v-if="method.description" class="method-desc">{{ method.description }}</p>
                <div class="method-signature">
                  <span class="sig-type sig-req">{{ method.requestType }}</span>
                  <i class="bi bi-arrow-right sig-arrow"></i>
                  <span class="sig-type sig-res" :class="{ 'sig-stream': method.responseStreaming }">
                    {{ method.responseStreaming ? 'stream ' : '' }}{{ method.responseType }}
                  </span>
                </div>
              </div>

              <div v-if="expandedMethods.has(service.fullName + '.' + method.name)" class="method-detail">
                <div class="schema-pair">
                  <div class="schema-block">
                    <div class="schema-label">Request</div>
                    <div class="schema-name">{{ method.requestType }}</div>
                    <table v-if="getMessage(method.requestType)" class="fields-table">
                      <thead>
                        <tr>
                          <th>Field</th>
                          <th>Type</th>
                          <th>Label</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="field in getMessage(method.requestType)!.fields" :key="field.name">
                          <td class="field-name">{{ field.name }}</td>
                          <td class="field-type">{{ field.type }}</td>
                          <td class="field-label">{{ field.label || '\u2014' }}</td>
                        </tr>
                      </tbody>
                    </table>
                    <div v-else class="empty-msg">Empty message</div>
                  </div>

                  <div class="schema-block">
                    <div class="schema-label">Response</div>
                    <div class="schema-name">{{ method.responseType }}</div>
                    <table v-if="getMessage(method.responseType)" class="fields-table">
                      <thead>
                        <tr>
                          <th>Field</th>
                          <th>Type</th>
                          <th>Label</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="field in getMessage(method.responseType)!.fields" :key="field.name">
                          <td class="field-name">{{ field.name }}</td>
                          <td class="field-type" :class="{ 'type-link': isMessageType(field) }" @click="isMessageType(field) && expandNestedMessage(field.type)">
                            {{ field.label === 'repeated' ? '[]' : '' }}{{ field.type }}
                          </td>
                          <td class="field-label">{{ field.label || '\u2014' }}</td>
                        </tr>
                      </tbody>
                    </table>
                    <div v-else class="empty-msg">Empty message</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Messages reference -->
        <div v-if="allMessages.length > 0" id="messages-ref" class="service-block messages-ref">
          <div class="service-header">
            <div class="service-title">
              <span class="service-name">Message Types</span>
            </div>
          </div>

          <div class="messages-grid">
            <div
                v-for="msg in allMessages"
                :key="msg.fullName"
                :id="'msg-' + msg.name"
                class="message-card"
                :class="{ 'highlighted': highlightedMessage === msg.name }"
            >
              <div class="msg-header">
                <span class="msg-name">{{ msg.name }}</span>
                <span v-if="msg.description" class="msg-desc">{{ msg.description }}</span>
              </div>
              <table v-if="msg.fields.length > 0" class="fields-table compact">
                <thead>
                  <tr>
                    <th>Field</th>
                    <th>Type</th>
                    <th>Label</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="field in msg.fields" :key="field.name">
                    <td class="field-name">{{ field.name }}</td>
                    <td class="field-type" :class="{ 'type-link': isMessageType(field) }" @click="isMessageType(field) && expandNestedMessage(field.type)">
                      {{ field.label === 'repeated' ? '[]' : '' }}{{ field.type }}
                    </td>
                    <td class="field-label">{{ field.label || '\u2014' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- Enums reference -->
        <div v-if="allEnums.length > 0" class="service-block">
          <div class="service-header">
            <div class="service-title">
              <span class="service-name">Enum Types</span>
            </div>
          </div>

          <div class="messages-grid">
            <div v-for="enumType in allEnums" :key="enumType.longName" class="message-card">
              <div class="msg-header">
                <span class="msg-name">{{ enumType.name }}</span>
              </div>
              <table class="fields-table compact">
                <thead>
                  <tr>
                    <th>Value</th>
                    <th>Number</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="val in enumType.values" :key="val.name">
                    <td class="field-name">{{ val.name }}</td>
                    <td class="field-type">{{ val.number }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import Badge from '@shared/components/Badge.vue';
import GrpcDocsClient from '@/services/api/GrpcDocsClient';
import VersionClient from '@/services/api/VersionClient';
import type { GrpcDocs, GrpcField, GrpcMessage, GrpcService, GrpcEnum } from '@/services/api/GrpcDocsClient';

const client = new GrpcDocsClient();
const versionClient = new VersionClient();
const loading = ref(true);
const error = ref<string | null>(null);
const docs = ref<GrpcDocs | null>(null);
const expandedMethods = ref<Set<string>>(new Set());
const activeService = ref<string>('');
const highlightedMessage = ref<string | null>(null);
const version = ref<string>('');

const SCALAR_TYPES = new Set([
  'double', 'float', 'int32', 'int64', 'uint32', 'uint64',
  'sint32', 'sint64', 'fixed32', 'fixed64', 'sfixed32', 'sfixed64',
  'bool', 'string', 'bytes'
]);

const allServices = computed<GrpcService[]>(() => {
  if (!docs.value) return [];
  return docs.value.files.flatMap(f => f.services);
});

const allMessages = computed<GrpcMessage[]>(() => {
  if (!docs.value) return [];
  const msgs = docs.value.files.flatMap(f => f.messages);
  // Filter out request/response wrappers that have no interesting fields
  return msgs.filter(m => !m.name.endsWith('Request') && !m.name.endsWith('Response'));
});

const allEnums = computed<GrpcEnum[]>(() => {
  if (!docs.value) return [];
  return docs.value.files.flatMap(f => f.enums);
});

const messageMap = computed<Map<string, GrpcMessage>>(() => {
  if (!docs.value) return new Map();
  const map = new Map<string, GrpcMessage>();
  for (const file of docs.value.files) {
    for (const msg of file.messages) {
      map.set(msg.name, msg);
    }
  }
  return map;
});

const getMessage = (name: string): GrpcMessage | undefined => {
  const msg = messageMap.value.get(name);
  if (msg && msg.fields.length > 0) return msg;
  return undefined;
};

const isMessageType = (field: GrpcField): boolean => {
  return !SCALAR_TYPES.has(field.type);
};

const shortName = (name: string): string => {
  return name.replace(/Service$/, '');
};

const toggleMethod = (key: string) => {
  if (expandedMethods.value.has(key)) {
    expandedMethods.value.delete(key);
  } else {
    expandedMethods.value.add(key);
  }
};

const scrollToService = (fullName: string) => {
  activeService.value = fullName;
  const el = document.getElementById('svc-' + fullName);
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
};

const expandNestedMessage = (typeName: string) => {
  highlightedMessage.value = typeName;
  const el = document.getElementById('msg-' + typeName);
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    setTimeout(() => { highlightedMessage.value = null; }, 2000);
  }
};

onMounted(async () => {
  versionClient.getVersion()
      .then(v => { version.value = v; })
      .catch(err => console.error('Failed to load version:', err));
  try {
    docs.value = await client.getDocs();
    if (allServices.value.length > 0) {
      activeService.value = allServices.value[0].fullName;
    }
  } catch (e) {
    error.value = 'Failed to load API documentation';
    console.error(e);
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.docs-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 32px 24px;
}

/* Header (shared with dashboard) */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-logo {
  width: 32px;
  height: 32px;
}

.header-left h4 {
  margin: 0;
  font-weight: 600;
  color: var(--color-heading-dark);
}

.version-badge {
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--color-slate-muted);
  background: var(--color-grey-bg);
  padding: 2px 8px;
  border-radius: 10px;
  font-variant-numeric: tabular-nums;
}

.header-nav {
  display: flex;
  gap: 2px;
  background: var(--color-grey-bg);
  border-radius: 8px;
  padding: 3px;
}

.nav-tab {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--color-slate-muted);
  text-decoration: none;
  transition: all 0.15s ease;
}

.nav-tab:hover {
  color: var(--color-slate-text);
}

.nav-tab.router-link-active {
  background: white;
  color: var(--color-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

/* Loading / Error */
.loading-state,
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 60px 20px;
  color: var(--color-text-light);
}

.error-state i {
  font-size: 2rem;
  color: var(--color-red-border);
}

/* Service nav pills */
.service-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 20px;
}

.service-nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: white;
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--color-slate-text);
  cursor: pointer;
  transition: all 0.15s ease;
}

.service-nav-item:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.service-nav-item.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: white;
}

.service-nav-item.active .method-count {
  background: rgba(255, 255, 255, 0.25);
  color: white;
}

.method-count {
  font-size: 0.65rem;
  background: var(--color-grey-bg);
  color: var(--color-text-light);
  padding: 1px 6px;
  border-radius: 8px;
}

/* Services list */
.services-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.service-block {
  scroll-margin-top: 20px;
}

.service-header {
  margin-bottom: 10px;
}

.service-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.service-name {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-heading-dark);
}

.service-pkg {
  font-size: 0.7rem;
  color: var(--color-text-light);
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.service-desc {
  margin: 4px 0 0;
  font-size: 0.8rem;
  color: var(--color-slate-muted);
}

/* Methods */
.methods-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.method-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: white;
  overflow: hidden;
  transition: border-color 0.15s ease;
}

.method-card:hover {
  border-color: var(--color-muted-separator);
}

.method-header {
  padding: 10px 14px;
  cursor: pointer;
}

.method-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}

.method-name {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-heading-dark);
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.method-desc {
  margin: 2px 0 6px;
  font-size: 0.75rem;
  color: var(--color-slate-muted);
}

.method-signature {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sig-type {
  font-size: 0.72rem;
  font-family: 'SF Mono', 'Fira Code', monospace;
  padding: 2px 8px;
  border-radius: 4px;
}

.sig-req {
  background: var(--color-indigo-bg-light);
  color: var(--color-primary);
}

.sig-res {
  background: var(--color-success-bg);
  color: var(--color-emerald);
}

.sig-stream {
  background: var(--color-amber-bg);
  color: var(--color-amber-highlight);
}

.sig-arrow {
  font-size: 0.65rem;
  color: var(--color-muted-separator);
}

/* Method detail (expanded) */
.method-detail {
  border-top: 1px solid var(--color-grey-bg);
  padding: 12px 14px;
  background: var(--color-light);
}

.schema-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.schema-block {
  min-width: 0;
}

.schema-label {
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-light);
  margin-bottom: 2px;
}

.schema-name {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-slate-text);
  font-family: 'SF Mono', 'Fira Code', monospace;
  margin-bottom: 6px;
}

.empty-msg {
  font-size: 0.72rem;
  color: var(--color-muted-separator);
  font-style: italic;
}

/* Fields table */
.fields-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.72rem;
}

.fields-table th {
  text-align: left;
  font-weight: 600;
  color: var(--color-text-light);
  font-size: 0.65rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 4px 8px;
  border-bottom: 1px solid var(--color-border);
}

.fields-table td {
  padding: 4px 8px;
  border-bottom: 1px solid var(--color-grey-bg);
  color: var(--color-slate-text);
}

.fields-table tr:last-child td {
  border-bottom: none;
}

.field-name {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-weight: 500;
  color: var(--color-heading-dark);
}

.field-type {
  font-family: 'SF Mono', 'Fira Code', monospace;
  color: var(--color-primary);
}

.field-label {
  color: var(--color-text-light);
}

.type-link {
  cursor: pointer;
  text-decoration: underline;
  text-decoration-style: dotted;
  text-underline-offset: 2px;
}

.type-link:hover {
  color: var(--color-primary-hover);
}

.fields-table.compact {
  font-size: 0.7rem;
}

/* Messages reference section */
.messages-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: white;
  padding: 10px 14px;
  transition: all 0.3s ease;
}

.message-card.highlighted {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.1);
}

.msg-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 6px;
}

.msg-name {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-heading-dark);
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.msg-desc {
  font-size: 0.72rem;
  color: var(--color-text-light);
}
</style>
