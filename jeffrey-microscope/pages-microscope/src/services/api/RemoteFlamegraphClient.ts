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

import axios from 'axios';
import HttpUtils from '@/services/HttpUtils';
import FlamegraphClient from '@/services/api/FlamegraphClient';
import FlamegraphData from '@/services/api/model/FlamegraphData';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import BothGraphData from '@/services/api/model/BothGraphData';
import TimeRange from '@/services/api/model/TimeRange';
import GraphComponents from '@/services/api/model/GraphComponents';
import ProtobufConverter from '@/services/flamegraphs/ProtobufConverter';

/**
 * Base class for flamegraph clients backed by a REST endpoint. It owns the shared
 * axios + Protobuf pipeline for graph requests ({@link postProtobuf}) and the JSON
 * pipeline for saving into the repository ({@link postRepository}), so concrete
 * clients only assemble their request content.
 *
 * Content-building contract: a `timeRange`/`search` argument of `undefined` means the
 * operation must NOT send the key at all — `JSON.stringify` (used by axios) drops
 * `undefined` properties, which keeps the serialized payloads identical to the
 * historical hand-built ones. `null` means the key is present with a `null` value.
 */
export default abstract class RemoteFlamegraphClient extends FlamegraphClient {
  private static readonly REPOSITORY_PATH = '/repository';

  private readonly baseUrl: string;

  protected constructor(baseUrl: string) {
    super();
    this.baseUrl = baseUrl;
  }

  provideBoth(
    components: GraphComponents,
    timeRange: TimeRange | null,
    search: string | null
  ): Promise<BothGraphData> {
    return this.postProtobuf(this.bothContent(components, timeRange, search));
  }

  provide(timeRange: TimeRange | null): Promise<FlamegraphData> {
    return this.postProtobuf(this.flamegraphContent(timeRange)).then(data => data.flamegraph);
  }

  provideTimeseries(search: string | null): Promise<TimeseriesData> {
    return this.postProtobuf(this.timeseriesContent(search)).then(data => data.timeseries);
  }

  /**
   * Builds the request content shared by all graph operations of the concrete client.
   * `undefined` for `timeRange`/`search` omits the key from the serialized payload.
   */
  protected abstract bothContent(
    components: GraphComponents,
    timeRange: TimeRange | null | undefined,
    search: string | null | undefined
  ): Record<string, unknown>;

  /**
   * Content for the flamegraph-only request. Defaults to {@link bothContent} with the
   * `search` key omitted; override when the operation needs a different shape.
   */
  protected flamegraphContent(timeRange: TimeRange | null): Record<string, unknown> {
    return this.bothContent(GraphComponents.FLAMEGRAPH_ONLY, timeRange, undefined);
  }

  /**
   * Content for the timeseries-only request. Defaults to {@link bothContent} with the
   * `timeRange` key omitted; override when the operation needs a different shape.
   */
  protected timeseriesContent(search: string | null): Record<string, unknown> {
    return this.bothContent(GraphComponents.TIMESERIES_ONLY, undefined, search);
  }

  /**
   * Posts the content to the graph endpoint and decodes the Protocol Buffers response.
   * Protobuf is the most efficient serialization (50-60% smaller than JSON).
   */
  protected postProtobuf(content: Record<string, unknown>): Promise<BothGraphData> {
    return axios
      .post<ArrayBuffer>(this.baseUrl, content, HttpUtils.PROTOBUF_HEADERS)
      .then(response => ProtobufConverter.decode(response.data));
  }

  /**
   * Posts the content as JSON to the repository endpoint to save a generated flamegraph.
   */
  protected postRepository(content: Record<string, unknown>): Promise<void> {
    return axios
      .post<void>(
        this.baseUrl + RemoteFlamegraphClient.REPOSITORY_PATH,
        content,
        HttpUtils.JSON_HEADERS
      )
      .then(HttpUtils.RETURN_DATA);
  }
}
