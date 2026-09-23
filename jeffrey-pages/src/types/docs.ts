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

export interface DocPage {
  title: string;
  path?: string;  // Relative path under the parent section. URL = /docs/{section.path}/{page.path}.
  to?: string;    // Absolute URL override; takes precedence over path-based URL building.
  anchor?: string;
  children?: DocPage[];
}

export interface DocSection {
  title: string;
  path: string;
  icon: string;
  children: DocPage[];
  // Marks a single-page section that links into ANOTHER product's docs.
  // The sidebar renders a "Docs ↗" badge on these so the cross-product jump is visible.
  crossLink?: boolean;
}

export interface DocHeading {
  id: string;
  text: string;
  level: number;
}

export interface AdjacentPages {
  prev: DocPageWithCategory | null;
  next: DocPageWithCategory | null;
}

export interface DocPageWithCategory extends DocPage {
  category: string;
  section: string;
}

export interface CurrentPageInfo extends DocPage {
  section: string;
  sectionPath: string;
}

export interface SearchableDoc {
  title: string;
  section: string;
  path: string;
}
