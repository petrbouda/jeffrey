-- Fixture for JdbcMethodTraceWeightRepositoryTest.
--
-- Row order matters: the derivation breaks a tie between two identical intervals with the row id,
-- so the pair at 20ms is written parent-first on purpose.
--
-- Thread 100 holds a nest three deep plus a second child, so the pass has to sum two siblings at one
-- level and recurse at another:
--
--   A  outer      [ 0ms .. 10ms ]   10ms   self 5ms   (minus B 3ms and C 2ms)
--   B  inner      [ 1ms ..  4ms ]    3ms   self 2ms   (minus D 1ms)
--   D  innermost  [1.5ms.. 2.5ms]    1ms   self 1ms
--   C  sibling    [ 5ms ..  7ms ]    2ms   self 2ms
--   -> the four self times add up to A's 10ms, which is the point of the whole pass
--
-- H is a park inside A. It is not a method trace, so it must neither lose its own weight nor be
-- subtracted from A's -- a traced method that waits still spent that time.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.MethodTrace', 'Method Trace', 1, 'Traced method invocation', '["Java Application"]', '1', NULL, true, NULL, NULL, NULL),
    ('jdk.ThreadPark', 'Thread Park', 2, 'Thread park', '["Java Application"]', '1', NULL, true, NULL, NULL, NULL);

INSERT INTO events_raw (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- A: the outer call
    ('jdk.MethodTrace', '2025-01-15T10:00:00.000000Z', 10000000, 1, 10000000, 'Probe#outer',     NULL, 100, '{}'),
    -- B: nested inside A
    ('jdk.MethodTrace', '2025-01-15T10:00:00.001000Z',  3000000, 1,  3000000, 'Probe#inner',     NULL, 100, '{}'),
    -- D: nested inside B, so A must not be charged for it twice
    ('jdk.MethodTrace', '2025-01-15T10:00:00.001500Z',  1000000, 1,  1000000, 'Probe#innermost', NULL, 100, '{}'),
    -- C: a second child of A, disjoint from B
    ('jdk.MethodTrace', '2025-01-15T10:00:00.005000Z',  2000000, 1,  2000000, 'Probe#sibling',   NULL, 100, '{}'),
    -- E: overlaps A in wall-clock time but runs on another thread, so it nests with nothing
    ('jdk.MethodTrace', '2025-01-15T10:00:00.002000Z',  4000000, 1,  4000000, 'Probe#other',     NULL, 200, '{}'),
    -- F and G: the identical interval a recursive call or a same-microsecond return produces.
    -- Written parent-first: the lower row id is the one allowed to be the parent.
    ('jdk.MethodTrace', '2025-01-15T10:00:00.020000Z',  1000000, 1,  1000000, 'Probe#twin',      NULL, 100, '{}'),
    ('jdk.MethodTrace', '2025-01-15T10:00:00.020000Z',  1000000, 1,  1000000, 'Probe#twin',      NULL, 100, '{}'),
    -- I: a traced call with nothing inside it, on a thread that has nesting elsewhere
    ('jdk.MethodTrace', '2025-01-15T10:00:00.030000Z',  2000000, 1,  2000000, 'Probe#alone',     NULL, 100, '{}'),
    -- H: a park inside A, on A's thread. Not a method trace.
    ('jdk.ThreadPark',  '2025-01-15T10:00:00.003000Z',   500000, 1,   500000, 'Probe#park',      NULL, 100, '{}');
