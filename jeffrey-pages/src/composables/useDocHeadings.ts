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

import { ref, provide, inject, type InjectionKey, type Ref } from 'vue';
import type { DocHeading } from '@/types/docs';

interface DocHeadingsContext {
  headings: Ref<DocHeading[]>;
  setHeadings: (h: DocHeading[]) => void;
}

const DOC_HEADINGS_KEY: InjectionKey<DocHeadingsContext> = Symbol('doc-headings');

/**
 * Provider function - call this in the layout component to set up the context
 */
export function provideDocHeadings(): { headings: Ref<DocHeading[]> } {
  const headings = ref<DocHeading[]>([]);

  const setHeadings = (h: DocHeading[]): void => {
    headings.value = h;
  };

  provide(DOC_HEADINGS_KEY, { headings, setHeadings });

  return { headings };
}

/**
 * Consumer function - call this in doc pages to register their headings
 */
export function useDocHeadings(): DocHeadingsContext {
  const context = inject(DOC_HEADINGS_KEY);
  if (!context) {
    throw new Error('useDocHeadings must be used within a component that has provideDocHeadings');
  }
  return context;
}
