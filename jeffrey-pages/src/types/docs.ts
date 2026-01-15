/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

export interface DocPage {
  title: string;
  path: string;
  anchor?: string;  // Optional anchor for linking to sections within a page
  children?: DocPage[];  // Optional nested children for collapsible sub-items
}

export interface DocSection {
  title: string;
  path: string;
  icon: string;
  children: DocPage[];
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
