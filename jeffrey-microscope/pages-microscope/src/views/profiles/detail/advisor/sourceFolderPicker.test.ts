/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import { describe, expect, it } from 'vitest';
import {
  filterFolders,
  findByPath,
  firstSelectable
} from '@/views/profiles/detail/advisor/sourceFolderPicker';
import type AdvisorProjectFolder from '@/services/api/model/AdvisorProjectFolder';

const folder = (name: string, path: string, present = true): AdvisorProjectFolder => ({
  folderId: name,
  name,
  path,
  present,
  createdAt: 0
});

const ORDER_SERVICE = folder('order-service', '/home/me/IdeaProjects/order-service');
const PAYMENTS = folder('payment-gateway', '/home/me/IdeaProjects/payment-gateway');
const INVENTORY = folder('Inventory API (legacy)', '/home/me/work/inventory-api', false);
const ALL = [ORDER_SERVICE, PAYMENTS, INVENTORY];

describe('filterFolders', () => {
  it('returns every folder when nothing was typed', () => {
    expect(filterFolders(ALL, '')).toEqual(ALL);
    expect(filterFolders(ALL, '   ')).toEqual(ALL);
  });

  it('matches on the name, ignoring case', () => {
    expect(filterFolders(ALL, 'INVENTORY')).toEqual([INVENTORY]);
  });

  // Two checkouts of the same project are told apart by their path, not their name.
  it('matches on the path too', () => {
    expect(filterFolders(ALL, '/work/')).toEqual([INVENTORY]);
  });

  it('matches a fragment anywhere in the name', () => {
    expect(filterFolders(ALL, 'gateway')).toEqual([PAYMENTS]);
  });

  it('returns nothing when the term matches neither name nor path', () => {
    expect(filterFolders(ALL, 'nope')).toEqual([]);
  });
});

describe('findByPath', () => {
  it('finds the folder a stored path belongs to', () => {
    expect(findByPath(ALL, '/home/me/IdeaProjects/order-service')).toBe(ORDER_SERVICE);
  });

  it('ignores surrounding whitespace and a trailing separator', () => {
    expect(findByPath(ALL, '  /home/me/IdeaProjects/order-service/  ')).toBe(ORDER_SERVICE);
  });

  // A hand-typed path is not a saved folder — the picker has to fall back to its custom mode.
  it('returns null for a path no saved folder holds', () => {
    expect(findByPath(ALL, '/tmp/somewhere-else')).toBeNull();
  });

  it('returns null for an empty or missing path', () => {
    expect(findByPath(ALL, '')).toBeNull();
    expect(findByPath(ALL, null)).toBeNull();
    expect(findByPath(ALL, undefined)).toBeNull();
  });

  it('keeps the root path intact rather than stripping it to nothing', () => {
    const root = folder('root', '/');
    expect(findByPath([root], '/')).toBe(root);
  });
});

describe('firstSelectable', () => {
  it('skips a folder that is missing on disk', () => {
    expect(firstSelectable([INVENTORY, PAYMENTS])).toBe(PAYMENTS);
  });

  it('returns null when every folder is missing', () => {
    expect(firstSelectable([INVENTORY])).toBeNull();
  });

  it('returns null for an empty list', () => {
    expect(firstSelectable([])).toBeNull();
  });
});
