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
import FrameSearchPattern from './FrameSearchPattern';

describe('FrameSearchPattern', () => {
  describe('literal reading', () => {
    it('matches a plain substring', () => {
      expect(new FrameSearchPattern('OrderService').matches('com.example.OrderService#place')).toBe(
        true
      );
    });

    it('matches an inner class by its exact title despite the dollar', () => {
      const pattern = new FrameSearchPattern('com.example.Outer$Inner#run');

      expect(pattern.matches('com.example.Outer$Inner#run')).toBe(true);
    });

    it('matches an array type by its exact title', () => {
      expect(new FrameSearchPattern('byte[]').matches('byte[]')).toBe(true);
    });

    it('searches a pattern that does not compile literally instead of throwing', () => {
      const pattern = new FrameSearchPattern('parse(');

      expect(pattern.matches('Parser#parse(String)')).toBe(true);
      expect(pattern.matches('Parser#parse')).toBe(false);
    });
  });

  describe('regular-expression reading', () => {
    it('finds an alternation anywhere in the title', () => {
      const pattern = new FrameSearchPattern('Order|Payment');

      expect(pattern.matches('com.example.PaymentGateway#charge')).toBe(true);
      expect(pattern.matches('com.example.Shipping#ship')).toBe(false);
    });

    it('lets a dot span the class-method boundary', () => {
      expect(new FrameSearchPattern('JsonReader.<init>').matches('JsonReader#<init>')).toBe(true);
    });
  });
});
