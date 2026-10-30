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

import { useRouter } from 'vue-router';
import { profileStore } from '@/stores/profileStore';

const RECORDINGS_PATH = '/recordings';

/**
 * Navigation from a profile's pages: the workspace and project the open profile came from, and the
 * way back to the Recordings page, where every profile is listed.
 */
export function useNavigation() {
  const router = useRouter();

  const navigateToRecordings = () => {
    router.push(RECORDINGS_PATH);
  };

  return {
    workspaceId: profileStore.workspaceId,
    projectId: profileStore.projectId,
    navigateToRecordings
  };
}
