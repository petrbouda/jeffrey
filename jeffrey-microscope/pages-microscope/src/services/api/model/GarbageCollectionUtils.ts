/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import GCGenerationType from './GCGenerationType';

export const getGenerationTypeBadgeVariant = (generationType: GCGenerationType) => {
  switch (generationType) {
    case GCGenerationType.YOUNG:
      return 'blue' as const;
    case GCGenerationType.OLD:
      return 'orange' as const;
    default:
      return 'grey' as const;
  }
};

export const getConcurrentBadgeVariant = (isConcurrent: boolean) => {
  return isConcurrent ? ('success' as const) : ('warning' as const);
};

export const getConcurrentBadgeValue = (isConcurrent: boolean) => {
  return isConcurrent ? 'Concurrent' : 'Stop-the-World';
};
