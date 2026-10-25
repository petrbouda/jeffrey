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
import RemoteClient from "@remote/services/api/RemoteClient";

/**
 * Whether the configured remote refuses every change — an Azure file share declared read-only. A
 * page hides its delete and pin actions while this is true; the backend refuses them either way.
 *
 * Starts false and stays false when the remote cannot be read, or none is configured, so a page never
 * loses its actions to a failed lookup: the worst case is a button the backend then refuses with its
 * reason.
 */
export function useRemoteReadOnly(client: RemoteClient = new RemoteClient()): Ref<boolean> {
  const readOnly = ref(false);
  client
    .current({ suppressToast: true })
    .then((remote) => {
      readOnly.value = remote?.readOnly === true;
    })
    .catch(() => {
      readOnly.value = false;
    });
  return readOnly;
}
