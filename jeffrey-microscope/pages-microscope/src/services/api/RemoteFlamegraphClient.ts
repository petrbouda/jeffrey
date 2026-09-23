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

import axios from 'axios';
import HttpUtils from '@shared/services/HttpUtils';
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
      .then(response => ProtobufConverter.decode(response.data))
      .catch(error => {
        throw RemoteFlamegraphClient.toGraphRequestError(error);
      });
  }

  /**
   * A failed graph request answers with the server's JSON ErrorResponse, but this request asks
   * for a Protobuf body, so axios hands the payload over as raw bytes. Decode it back to text
   * and wrap it into the thrown error, so the console shows the server's message instead of an
   * opaque buffer; the original error stays attached as the cause to keep its stacktrace. Any
   * other failure (e.g. a Protobuf decoding error) passes through untouched.
   */
  private static toGraphRequestError(error: unknown): Error {
    if (axios.isAxiosError(error) && error.response && error.response.data instanceof ArrayBuffer) {
      const body = new TextDecoder().decode(error.response.data);
      return new Error(`Graph request failed: status=${error.response.status} body=${body}`, {
        cause: error
      });
    }
    if (error instanceof Error) {
      return error;
    }
    return new Error(String(error));
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
