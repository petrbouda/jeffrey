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

/**
 * Where a menu floating in <body> goes relative to the button that opened it: right-aligned with the
 * button, below it when the menu fits there (or when below has at least as much room as above),
 * otherwise above it. The menu's height is capped by the room on the chosen side, so a taller menu
 * scrolls instead of leaving the viewport.
 */

/** Gap between the button and the menu. */
export const MENU_GAP_PX = 4;
/** Room kept free between the menu and the viewport's edge. */
export const VIEWPORT_MARGIN_PX = 8;

/** The button's box in viewport coordinates, as `getBoundingClientRect` gives it. */
export interface AnchorRect {
  top: number;
  bottom: number;
  right: number;
}

export interface Viewport {
  width: number;
  height: number;
}

/** An inline style for the menu: fixed, right-aligned, pinned by its top (below) or bottom (above). */
export type MenuPlacement = { position: 'fixed'; right: string; maxHeight: string } & (
  { top: string } | { bottom: string }
);

const px = (value: number) => `${Math.max(value, 0)}px`;

export function floatingMenuPlacement(
  anchor: AnchorRect,
  menuHeight: number,
  viewport: Viewport
): MenuPlacement {
  const spaceBelow = viewport.height - anchor.bottom - MENU_GAP_PX - VIEWPORT_MARGIN_PX;
  const spaceAbove = anchor.top - MENU_GAP_PX - VIEWPORT_MARGIN_PX;
  const right = px(viewport.width - anchor.right);

  if (menuHeight <= spaceBelow || spaceBelow >= spaceAbove) {
    return {
      position: 'fixed',
      top: px(anchor.bottom + MENU_GAP_PX),
      right,
      maxHeight: px(spaceBelow)
    };
  }
  return {
    position: 'fixed',
    bottom: px(viewport.height - anchor.top + MENU_GAP_PX),
    right,
    maxHeight: px(spaceAbove)
  };
}
