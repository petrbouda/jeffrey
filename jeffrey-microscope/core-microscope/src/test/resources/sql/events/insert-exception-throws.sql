-- Jeffrey
-- Copyright (C) 2026 Petr Bouda
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     https://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- Frames of the throw stacks. frame_hashes below are root-first, as the parser writes them.
INSERT INTO frames (frame_hash, class_name, method_name, frame_type, line_number, bytecode_index)
VALUES
    (301, 'java.lang.Throwable', '<init>', 'JIT', 266, 0),
    (302, 'java.lang.Exception', '<init>', 'JIT', 113, 0),
    (303, 'java.io.IOException', '<init>', 'JIT', 62, 0),
    (304, 'com.acme.orders.OrderService', 'place', 'JIT', 42, 0),
    (305, 'java.lang.Thread', 'run', 'JIT', 1583, 0),
    (306, 'java.lang.StackOverflowError', '<init>', 'JIT', 0, 0),
    (307, 'com.acme.orders.Recursive', 'descend', 'Interpreted', 0, 0);

-- 4001: IOException thrown from OrderService.place
-- 4002: StackOverflowError thrown from Recursive.descend
INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
VALUES
    (4001, 1, [305, 304, 303, 302, 301], []),
    (4002, 1, [305, 307, 306, 301], []);

-- The message of the second IOException is pooled, the way the parser lifts a large field.
INSERT INTO field_texts (text_hash, text)
VALUES (9001, 'connection reset');

INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples,
                        stacktrace_hash, thread_hash, fields, pooled_field, pooled_text_hash)
VALUES
    -- Three IOExceptions on two threads, one with a blank message.
    ('jdk.JavaExceptionThrow', '2025-01-15T10:00:00Z', 0, 0, 1, 4001, 1001,
     '{"thrownClass":"java.io.IOException","message":"connection reset"}', NULL, NULL),
    ('jdk.JavaExceptionThrow', '2025-01-15T10:00:01Z', 1000, 0, 1, 4001, 1002,
     '{"thrownClass":"java.io.IOException"}', 'message', 9001),
    ('jdk.JavaExceptionThrow', '2025-01-15T10:00:02Z', 2000, 0, 1, 4001, 1002,
     '{"thrownClass":"java.io.IOException","message":"   "}', NULL, NULL),
    -- One StackOverflowError, recorded the way JFR records an Error: two ExceptionThrow + one ErrorThrow.
    ('jdk.JavaExceptionThrow', '2025-01-15T10:00:03Z', 3000, 0, 1, 4002, 1001,
     '{"thrownClass":"java.lang.StackOverflowError"}', NULL, NULL),
    ('jdk.JavaExceptionThrow', '2025-01-15T10:00:03Z', 3000, 0, 1, 4002, 1001,
     '{"thrownClass":"java.lang.StackOverflowError"}', NULL, NULL),
    ('jdk.JavaErrorThrow', '2025-01-15T10:00:03Z', 3000, 0, 1, 4002, 1001,
     '{"thrownClass":"java.lang.StackOverflowError"}', NULL, NULL),
    -- A throw whose event named no class still counts.
    ('jdk.JavaExceptionThrow', '2025-01-15T10:00:04Z', 4000, 0, 1, NULL, NULL,
     '{}', NULL, NULL),
    -- Not a throw: never read.
    ('jdk.ExecutionSample', '2025-01-15T10:00:05Z', 5000, 0, 1, 4001, 1001, NULL, NULL, NULL);
