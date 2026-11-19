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

import mitt from 'mitt';

export default class MessageBus {
  static INSTANCE = mitt();

  static SUBSECOND_SELECTION_CLEAR = 'subsecond-selection-clear';
  static SIDEBAR_CHANGED = 'sidebar-changed';
  static HEAP_DUMP_STATUS_CHANGED = 'heap-dump-status-changed';
  static IDE_VIEW_SOURCE = 'ide-view-source';
  static IDE_TARGET_CHANGED = 'ide-target-changed';

  static emit(type: string, content: any) {
    this.INSTANCE.emit(type, content);
  }

  static on(type: string, handler: any) {
    this.INSTANCE.on(type, handler);
  }

  static off(type: string, handler?: any) {
    this.INSTANCE.off(type, handler);
  }
}
