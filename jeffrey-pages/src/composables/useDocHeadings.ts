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
