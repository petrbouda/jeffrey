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

import BasePlatformClient from '@shared/services/api/BasePlatformClient';
import type { RequestOptions } from '@shared/services/api/BasePlatformClient';
import Remote, { CurrentRemote } from '@remote/services/api/model/Remote';

/** The one remote this Microscope reaches, as configuration declares it; read-only. */
export default class RemoteClient extends BasePlatformClient {
  constructor() {
    super('/remote');
  }

  async current(options?: RequestOptions): Promise<Remote | null> {
    const answer = await super.get<CurrentRemote>('', undefined, options);
    return answer.remote;
  }
}
