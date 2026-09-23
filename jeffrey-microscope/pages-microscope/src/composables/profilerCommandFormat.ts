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
 * How the Live Command panel hands the command over. The command itself is the same in every
 * format; only its wrapping differs.
 *
 * - `env`: the `JEFFREY_PROFILER_COMMAND` variable Jeffrey Provisioner reads, set on a pod.
 * - `hocon`: the `profiler-command` key of the provisioner's configuration file.
 * - `raw`: the bare command — options for Jeffrey JIB, a JVM argument for a custom profiler.
 */
export type CommandFormat = 'env' | 'hocon' | 'raw';

export const PROFILER_COMMAND_ENV = 'JEFFREY_PROFILER_COMMAND';
export const PROFILER_COMMAND_KEY = 'profiler-command';

const SINGLE_QUOTE = "'";
const SHELL_ESCAPED_SINGLE_QUOTE = "'\\''";

/**
 * Quoted for a shell: the command carries `<<JEFFREY:...>>` and `%t`, which an unquoted
 * assignment would read as a heredoc and a job reference.
 */
function envAssignment(command: string): string {
  const quoted = command.split(SINGLE_QUOTE).join(SHELL_ESCAPED_SINGLE_QUOTE);
  return `${PROFILER_COMMAND_ENV}=${SINGLE_QUOTE}${quoted}${SINGLE_QUOTE}`;
}

/** A HOCON quoted string, so a JFC path with a backslash or quote survives the parse. */
function hoconEntry(command: string): string {
  const escaped = command.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  return `${PROFILER_COMMAND_KEY} = "${escaped}"`;
}

const FORMATTERS: Record<CommandFormat, (command: string) => string> = {
  env: envAssignment,
  hocon: hoconEntry,
  raw: command => command
};

/** Wraps a generated command for the chosen format; an empty command stays empty. */
export function formatCommand(command: string, format: CommandFormat): string {
  if (!command) {
    return '';
  }
  return FORMATTERS[format](command);
}
