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
  <RemoteBrowser :initial-selection="initialSelection" @update:selection="syncQuery" />
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
import RemoteBrowser from '@remote/components/projects/RemoteBrowser.vue';
import {
  queryFromSelection,
  selectionFromQuery,
  type RemoteSelection
} from '@remote/services/remoteSelection';

const route = useRoute();
const router = useRouter();

// The browser opens where the link points (`/remote?workspaceId=…&projectId=…&instanceId=…&sessionId=…`),
// and every pick is written back so the address always names what is on screen.
const initialSelection = selectionFromQuery(route.query);

const REMOTE_PATH = '/remote';

const syncQuery = (selection: RemoteSelection) => {
  // A listing that lands after the reader has left the page must not rewrite the next page's address
  if (route.path !== REMOTE_PATH) {
    return;
  }
  router.replace({ path: REMOTE_PATH, query: queryFromSelection(selection) });
};
</script>
