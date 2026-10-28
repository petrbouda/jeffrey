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

import RecordingSession from '@remote/services/api/model/RecordingSession.ts';

/**
 * How long a session must have been going before all-zero sizes are worth remarking on.
 * Below this a session is simply new — its files exist and nothing has been flushed into
 * them yet, which is the normal first seconds of every recording.
 */
const UNREPORTED_SIZES_GRACE_MS = 60_000;

/**
 * A session read on its own whose files nonetheless all measure zero, after it has been going
 * long enough to have produced something. Jeffrey reads these sizes straight off the volume, so
 * this is what a filesystem that never refreshed them looks like: an object-storage FUSE mount, or
 * an NFS mount with attribute caching, serves each file's size as of when the file was created,
 * and an SMB share lists a file another client still holds open at the size of its last flush.
 * The files still hold their bytes and still download; only the figures are missing, and the UI
 * says so rather than printing a confident zero.
 *
 * <p>Deliberately narrower than "zero bytes". A failed session — one a newer session ended before
 * it wrote any data, as the backend judges it — has a crash for its explanation; what this catches
 * is the case a crash cannot explain, above all the instance's newest session. Undefined, a
 * session not read yet, has no sizes to judge.
 */
export function hasUnreportedSizes(session: RecordingSession | undefined): boolean {
  if (!session || session.failed) {
    return false;
  }
  return (
    session.fileCount > 0 &&
    session.sizeBytes === 0 &&
    (session.duration ?? 0) >= UNREPORTED_SIZES_GRACE_MS
  );
}
