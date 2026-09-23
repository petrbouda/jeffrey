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

import SlugService from './SlugService';

describe('SlugService', () => {
  describe('generateSlug', () => {
    it('returns empty string for empty/falsy input', () => {
      expect(SlugService.generateSlug('')).toBe('');
      expect(SlugService.generateSlug(null as any)).toBe('');
      expect(SlugService.generateSlug(undefined as any)).toBe('');
    });

    it('converts spaces to dashes', () => {
      expect(SlugService.generateSlug('My New Project')).toBe('my-new-project');
    });

    it('converts uppercase to lowercase', () => {
      expect(SlugService.generateSlug('HELLO')).toBe('hello');
    });

    it('strips special characters', () => {
      expect(SlugService.generateSlug('Hello World! @#$%')).toBe('hello-world');
    });

    it('collapses consecutive dashes', () => {
      expect(SlugService.generateSlug('hello---world')).toBe('hello-world');
    });

    it('removes leading/trailing dashes', () => {
      expect(SlugService.generateSlug('-hello-world-')).toBe('hello-world');
    });

    it('handles multiple spaces', () => {
      expect(SlugService.generateSlug('hello   world')).toBe('hello-world');
    });

    it('preserves numbers', () => {
      expect(SlugService.generateSlug('Project 123')).toBe('project-123');
    });
  });

  describe('validateSlug', () => {
    it('returns valid slug unchanged', () => {
      expect(SlugService.validateSlug('my-project')).toBe('my-project');
    });

    it('lowercases uppercase input', () => {
      expect(SlugService.validateSlug('My-Project')).toBe('my-project');
    });

    it('strips special characters', () => {
      expect(SlugService.validateSlug('my_project!')).toBe('myproject');
    });

    it('collapses consecutive dashes', () => {
      expect(SlugService.validateSlug('my--project')).toBe('my-project');
    });

    it('removes leading/trailing dashes', () => {
      expect(SlugService.validateSlug('-my-project-')).toBe('my-project');
    });

    it('removes spaces', () => {
      expect(SlugService.validateSlug('my project')).toBe('myproject');
    });
  });
});
