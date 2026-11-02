<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 -->
<template>
  <ConnectorBrowser :initial-selection="initialSelection" @update:selection="syncQuery" />
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
import ConnectorBrowser from '@/components/connector/ConnectorBrowser.vue';
import {
  queryFromSelection,
  selectionFromQuery,
  type ConnectorSelection
} from '@/services/connector/connectorSelection';

const route = useRoute();
const router = useRouter();

// The browser opens where the link points (`/connector?workspaceId=…&projectId=…&instanceId=…&sessionId=…`),
// and every pick is written back so the address always names what is on screen.
const initialSelection = selectionFromQuery(route.query);

const CONNECTOR_PATH = '/connector';

const syncQuery = (selection: ConnectorSelection) => {
  // A listing that lands after the reader has left the page must not rewrite the next page's address
  if (route.path !== CONNECTOR_PATH) {
    return;
  }
  router.replace({ path: CONNECTOR_PATH, query: queryFromSelection(selection) });
};
</script>
