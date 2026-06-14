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

package cafe.jeffrey.profile.manager.model.security;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.security.SecurityData.NamedCount;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates {@code jdk.TLSHandshake} (an instant event) into a per-second handshake-count timeline
 * and counts by protocol version, cipher suite, and peer ({@code host:port}).
 */
public class TlsHandshakeBuilder implements RecordBuilder<GenericRecord, TlsHandshakeBuilder.Result> {

    public record Result(
            TimeseriesData timeline,
            List<NamedCount> protocols,
            List<NamedCount> ciphers,
            List<NamedCount> peers,
            long total,
            long distinctPeers) {
    }

    private static final String PEER_HOST_FIELD = "peerHost";
    private static final String PEER_PORT_FIELD = "peerPort";
    private static final String PROTOCOL_VERSION_FIELD = "protocolVersion";
    private static final String CIPHER_SUITE_FIELD = "cipherSuite";
    private static final String COUNT_SERIES = "TLS Handshakes";
    private static final String UNKNOWN = "unknown";
    private static final int MAX_ROWS = 50;

    private final LongLongHashMap countSeries;
    private final Map<String, Long> protocolCounts = new HashMap<>();
    private final Map<String, Long> cipherCounts = new HashMap<>();
    private final Map<String, Long> peerCounts = new HashMap<>();
    private long total;

    public TlsHandshakeBuilder(RelativeTimeRange timeRange) {
        this.countSeries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        countSeries.addToValue(record.timestampFromStart().toSeconds(), 1);
        total++;

        increment(protocolCounts, orUnknown(Json.readString(fields, PROTOCOL_VERSION_FIELD)));
        increment(cipherCounts, orUnknown(Json.readString(fields, CIPHER_SUITE_FIELD)));

        String host = orUnknown(Json.readString(fields, PEER_HOST_FIELD));
        long port = Json.readLong(fields, PEER_PORT_FIELD);
        increment(peerCounts, port >= 0 ? host + ":" + port : host);
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    private static void increment(Map<String, Long> counts, String key) {
        counts.merge(key, 1L, Long::sum);
    }

    @Override
    public Result build() {
        SingleSerie serie = TimeseriesUtils.buildSerie(COUNT_SERIES, countSeries);
        return new Result(
                new TimeseriesData(serie),
                topCounts(protocolCounts),
                topCounts(cipherCounts),
                topCounts(peerCounts),
                total,
                peerCounts.size());
    }

    private static List<NamedCount> topCounts(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> new NamedCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(NamedCount::count).reversed())
                .limit(MAX_ROWS)
                .toList();
    }
}
