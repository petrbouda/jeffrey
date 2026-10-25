/*
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
 */
import { ref, type Ref } from "vue";
import HubClient from "@hubs/services/api/HubClient";

/**
 * Whether the hub a page belongs to refuses every change — a source that reads the recording
 * volume straight from storage (an Azure file share) rather than through a Jeffrey Hub. A page
 * hides its delete and pin actions while this is true; the backend refuses them either way.
 *
 * Starts false and stays false when the hub cannot be read, so a page never loses its actions
 * to a failed lookup: the worst case is a button the backend then refuses with its reason.
 */
export function useHubReadOnly(hubId: string, client: HubClient = new HubClient()): Ref<boolean> {
  const readOnly = ref(false);
  client
    .getById(hubId)
    .then((hub) => {
      readOnly.value = hub.readOnly === true;
    })
    .catch(() => {
      readOnly.value = false;
    });
  return readOnly;
}
