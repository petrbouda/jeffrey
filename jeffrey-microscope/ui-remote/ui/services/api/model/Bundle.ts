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

import type RecordingFile from '@remote/services/api/model/RecordingFile';
import type { RecordingTag } from '@remote/services/api/model/Recording';

/**
 * Several files that arrived together — a hub session download — shown as one row. Each item that can
 * be initialized is a recording of its own carrying this id as its bundleId; `files` are the rest
 * (logs, crash files), download-only.
 */
export default interface Bundle {
  id: string;
  name: string;
  groupId: string | null;
  createdAt: number;
  files: RecordingFile[];
  tags: RecordingTag[];
}
