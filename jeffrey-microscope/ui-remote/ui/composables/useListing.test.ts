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

import { describe, expect, it } from 'vitest';
import { useListing } from '@remote/composables/useListing.ts';

const FAILURE = 'Could not load projects';

/** A read the test settles by hand, to land answers in any order. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const identity = (items: string[]) => items;

describe('useListing', () => {
  it('keeps the answer and stops loading', async () => {
    const listing = useListing<string>(FAILURE);
    const answer = await listing.load(() => Promise.resolve(['a', 'b']), identity);
    expect(answer).toEqual(['a', 'b']);
    expect(listing.items.value).toEqual(['a', 'b']);
    expect(listing.loading.value).toBe(false);
    expect(listing.error.value).toBeNull();
  });

  it('reports a failure with the server message, or the fallback', async () => {
    const listing = useListing<string>(FAILURE);
    const answer = await listing.load(
      () => Promise.reject({ response: { data: { message: 'share unreachable' } } }),
      identity
    );
    expect(answer).toBeUndefined();
    expect(listing.error.value).toBe('share unreachable');
    expect(listing.items.value).toEqual([]);

    await listing.load(() => Promise.reject({}), identity);
    expect(listing.error.value).toBe(FAILURE);
  });

  it('ignores an older answer that lands after a newer read', async () => {
    const listing = useListing<string>(FAILURE);
    const older = deferred<string[]>();
    const newer = deferred<string[]>();

    const olderLoad = listing.load(() => older.promise, identity);
    const newerLoad = listing.load(() => newer.promise, identity);

    newer.resolve(['new']);
    expect(await newerLoad).toEqual(['new']);
    older.resolve(['old']);
    expect(await olderLoad).toBeUndefined();

    expect(listing.items.value).toEqual(['new']);
    expect(listing.loading.value).toBe(false);
  });

  it('forgets a read in flight when cleared', async () => {
    const listing = useListing<string>(FAILURE);
    const pending = deferred<string[]>();
    const load = listing.load(() => pending.promise, identity);

    listing.clear();
    pending.resolve(['late']);

    expect(await load).toBeUndefined();
    expect(listing.items.value).toEqual([]);
    expect(listing.loading.value).toBe(false);
  });
});
