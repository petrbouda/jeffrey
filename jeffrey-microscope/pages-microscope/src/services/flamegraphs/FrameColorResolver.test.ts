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
import FrameColorResolver from './FrameColorResolver';
import FrameType from './FrameType';

const ALL_TYPES = Object.values(FrameType).filter(value => typeof value === 'string') as string[];

describe('FrameColorResolver', () => {
  it('gives every frame type a fill its black label stays readable on', () => {
    for (const type of ALL_TYPES) {
      const color = FrameColorResolver.resolveByType(type);

      expect(color, type).not.toBe('#000000');
      expect(FrameColorResolver.isDarkFill(color), type).toBe(false);
    }
  });

  it('draws a type it does not know as UNKNOWN', () => {
    expect(FrameColorResolver.resolveByType('SOMETHING_NEW')).toBe(
      FrameColorResolver.resolveByType(FrameType.UNKNOWN)
    );
  });
});
