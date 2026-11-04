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
import {
  MENU_GAP_PX,
  VIEWPORT_MARGIN_PX,
  floatingMenuPlacement
} from '@shared/services/floatingMenuPlacement';

const VIEWPORT = { width: 1000, height: 800 };
const MENU_HEIGHT = 200;

describe('floatingMenuPlacement', () => {
  it('opens below the button when the menu fits there', () => {
    const placement = floatingMenuPlacement(
      { top: 100, bottom: 130, right: 900 },
      MENU_HEIGHT,
      VIEWPORT
    );

    expect(placement).toEqual({
      position: 'fixed',
      top: `${130 + MENU_GAP_PX}px`,
      right: '100px',
      maxHeight: `${800 - 130 - MENU_GAP_PX - VIEWPORT_MARGIN_PX}px`
    });
  });

  it('opens above the button near the bottom of the viewport', () => {
    const placement = floatingMenuPlacement(
      { top: 700, bottom: 730, right: 900 },
      MENU_HEIGHT,
      VIEWPORT
    );

    expect(placement).toEqual({
      position: 'fixed',
      bottom: `${800 - 700 + MENU_GAP_PX}px`,
      right: '100px',
      maxHeight: `${700 - MENU_GAP_PX - VIEWPORT_MARGIN_PX}px`
    });
  });

  it('stays below when the menu fits neither side but below has more room', () => {
    const placement = floatingMenuPlacement({ top: 300, bottom: 330, right: 900 }, 1000, VIEWPORT);

    expect(placement).toHaveProperty('top');
    expect(placement.maxHeight).toBe(`${800 - 330 - MENU_GAP_PX - VIEWPORT_MARGIN_PX}px`);
  });

  it('never yields a negative size when the button is off screen', () => {
    const placement = floatingMenuPlacement(
      { top: 900, bottom: 930, right: 1200 },
      MENU_HEIGHT,
      VIEWPORT
    );

    expect(placement.right).toBe('0px');
    expect(placement).toHaveProperty('bottom', '0px');
  });
});
