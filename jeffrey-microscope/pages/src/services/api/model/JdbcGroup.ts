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

import JdbcStatementNameStats from '@/services/api/model/JdbcStatementNameStats.ts';

export default class JdbcGroup {
  constructor(
    public group: string,
    public count: number,
    public totalExecutionTime: number,
    public totalRowsProcessed: number,
    public maxExecutionTime: number,
    public p99ExecutionTime: number,
    public p95ExecutionTime: number,
    public errorCount: number,
    public statementNames: JdbcStatementNameStats[]
  ) {}
}
