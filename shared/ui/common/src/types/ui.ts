/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

// UI component types

/** A single tab in {@link MainNavigation}: a router target with an icon and label. */
export interface NavItem {
  to: string;
  icon: string;
  label: string;
  /** When true, the link is only active on an exact route match (e.g. the root "/" tab). */
  exact?: boolean;
}

export type Size = 'xxs' | 'xs' | 's' | 'm' | 'l' | 'xl';
export type Variant =
  | 'primary'
  | 'info'
  | 'secondary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'light'
  | 'dark'
  | 'blue'
  | 'green'
  | 'orange'
  | 'red'
  | 'purple'
  | 'violet'
  | 'grey'
  | 'pink'
  | 'yellow'
  | 'cyan'
  | 'indigo'
  | 'teal'
  | 'lime'
  | 'brown'
  | 'status-active'
  | 'status-finished'
  | 'status-blocked'
  | 'status-deleted'
  | 'status-unknown';

/**
 * Left-gutter colour of a {@link SlowestRowList} row. A subset of {@link Variant} plus the
 * unmarked default, so callers translate their own domain (span kind, outcome, ...) into a
 * neutral vocabulary the shared list understands.
 */
export type SlowestRowAccent =
  | Extract<Variant, 'primary' | 'info' | 'secondary' | 'success' | 'warning' | 'danger'>
  | 'neutral';

/** Time-bar treatment of a {@link SlowestRowList} row: the brand ramp, or the failure ramp. */
export type SlowestRowTone = 'default' | 'danger';
