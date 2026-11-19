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
-- Fixture for JdbcTraceRepositoryTest.Derivation, layered on top of insert-trace-spans.sql.
--
-- Two exchanges of a real recording (jeffrey-testapp-server), each the root of its own trace, whose
-- payloads -- their fields without the span plumbing -- differ only in the remote port and the path id.
-- DuckDB's hash() gives the two texts the same 64 bits; keyed by it, trace_span_payloads refused the
-- second as a duplicate and the whole derivation failed.
INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:05.000Z', 5000, 3000000, 1, NULL, NULL, NULL, 3001,
     '{"remoteHost":"192.168.194.81","remotePort":50218,"uri":"/persons/{id}","method":"DELETE","mediaType":null,"statusCode":200,"queryParams":null,"pathParams":"{\"id\":\"696\"}","requestLength":-1,"responseLength":-1,"traceId":501,"spanId":5011,"parentSpanId":0,"name":"DELETE /persons/{id}","kind":"SERVER","status":"UNSET","errorType":null,"attributes":null}'),
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:06.000Z', 6000, 3000000, 1, NULL, NULL, NULL, 3001,
     '{"remoteHost":"192.168.194.81","remotePort":50238,"uri":"/persons/{id}","method":"DELETE","mediaType":null,"statusCode":200,"queryParams":null,"pathParams":"{\"id\":\"896\"}","requestLength":-1,"responseLength":-1,"traceId":502,"spanId":5021,"parentSpanId":0,"name":"DELETE /persons/{id}","kind":"SERVER","status":"UNSET","errorType":null,"attributes":null}');
