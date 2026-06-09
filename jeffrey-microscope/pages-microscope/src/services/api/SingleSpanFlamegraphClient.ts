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

import GlobalVars from '@/services/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/services/HttpUtils';
import FlamegraphData from '@/services/api/model/FlamegraphData';
import FlamegraphClient from '@/services/api/FlamegraphClient';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import BothGraphData from '@/services/api/model/BothGraphData';
import TimeRange from '@/services/api/model/TimeRange';
import GraphComponents from '@/services/api/model/GraphComponents';
import ProtobufConverter from '@/services/flamegraphs/ProtobufConverter';

/**
 * Flamegraph client scoped to a single async-profiler span. Like {@link SpanFlamegraphClient} it sends no
 * {@link TimeRange} or thread filter through the {@link FlamegraphClient} contract — instead it carries the
 * span's own interval (thread hash + start/end window), and the backend turns that into a single span
 * interval so the result contains only the samples this one span covers. The {@code threadHash} is kept as a
 * string to preserve full 64-bit precision over the wire.
 */
export default class SingleSpanFlamegraphClient extends FlamegraphClient {
  private readonly baseUrl: string;
  private readonly threadHash: string;
  private readonly fromMillis: number;
  private readonly toMillis: number;
  private readonly eventType: string;
  private useThreadMode: boolean;
  private useWeight: boolean | null;
  private readonly excludeNonJavaSamples: boolean;
  private readonly excludeIdleSamples: boolean;
  private readonly onlyUnsafeAllocationSamples: boolean;

  constructor(
    profileId: string,
    threadHash: string,
    fromMillis: number,
    toMillis: number,
    eventType: string,
    useThreadMode: boolean,
    useWeight: boolean | null,
    excludeNonJavaSamples: boolean,
    excludeIdleSamples: boolean,
    onlyUnsafeAllocationSamples: boolean
  ) {
    super();
    this.baseUrl =
      GlobalVars.internalUrl + '/profiles/' + profileId + '/async-profiler/spans/single/flamegraph';
    this.threadHash = threadHash;
    this.fromMillis = fromMillis;
    this.toMillis = toMillis;
    this.eventType = eventType;
    this.useThreadMode = useThreadMode;
    this.useWeight = useWeight;
    this.excludeNonJavaSamples = excludeNonJavaSamples;
    this.excludeIdleSamples = excludeIdleSamples;
    this.onlyUnsafeAllocationSamples = onlyUnsafeAllocationSamples;
  }

  private requestBody(components: GraphComponents): Record<string, unknown> {
    return {
      threadHash: this.threadHash,
      fromMillis: this.fromMillis,
      toMillis: this.toMillis,
      eventType: this.eventType,
      useWeight: this.useWeight,
      useThreadMode: this.useThreadMode,
      excludeNonJavaSamples: this.excludeNonJavaSamples,
      excludeIdleSamples: this.excludeIdleSamples,
      onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
      components: components
    };
  }

  provideBoth(
    components: GraphComponents,
    _timeRange: TimeRange | null,
    _search: string | null
  ): Promise<BothGraphData> {
    return axios
      .post<ArrayBuffer>(this.baseUrl, this.requestBody(components), HttpUtils.PROTOBUF_HEADERS)
      .then(response => ProtobufConverter.decode(response.data));
  }

  provide(_timeRange: TimeRange | null): Promise<FlamegraphData> {
    return axios
      .post<ArrayBuffer>(
        this.baseUrl,
        this.requestBody(GraphComponents.FLAMEGRAPH_ONLY),
        HttpUtils.PROTOBUF_HEADERS
      )
      .then(response => ProtobufConverter.decode(response.data))
      .then(data => data.flamegraph);
  }

  provideTimeseries(_search: string | null): Promise<TimeseriesData> {
    return axios
      .post<ArrayBuffer>(
        this.baseUrl,
        this.requestBody(GraphComponents.TIMESERIES_ONLY),
        HttpUtils.PROTOBUF_HEADERS
      )
      .then(response => ProtobufConverter.decode(response.data))
      .then(data => data.timeseries);
  }

  save(): Promise<void> {
    return Promise.reject(new Error('Saving span-scoped flamegraphs is not supported'));
  }

  override supportsModeToggle(): boolean {
    return true;
  }

  override setUseThreadMode(value: boolean): void {
    this.useThreadMode = value;
  }

  override setUseWeight(value: boolean | null): void {
    this.useWeight = value;
  }
}
