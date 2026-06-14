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

import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';

export interface SecurityHeader {
  tlsHandshakes: number;
  distinctPeers: number;
  certificates: number;
  flaggedCertificates: number;
  deserializationEvents: number;
  deserializationRejected: number;
}

export interface NamedCount {
  name: string;
  count: number;
}

export interface CertificateStat {
  subject: string;
  issuer: string;
  keyType: string;
  keyLength: number;
  signatureAlgorithm: string;
  validFrom: number;
  validUntil: number;
  validationCount: number;
  weakKey: boolean;
  weakSignature: boolean;
  expired: boolean;
  expiringSoon: boolean;
}

export interface DeserializationSummary {
  totalEvents: number;
  filterConfiguredEvents: number;
  rejectedEvents: number;
  exceptionEvents: number;
}

export interface DeserializationTypeStat {
  type: string;
  count: number;
  totalBytes: number;
  maxBytes: number;
  maxDepth: number;
}

export interface ProviderServiceStat {
  provider: string;
  type: string;
  algorithm: string;
  count: number;
}

export default interface SecurityData {
  header: SecurityHeader;
  tlsTimeline: TimeseriesData;
  protocols: NamedCount[];
  ciphers: NamedCount[];
  peers: NamedCount[];
  certificates: CertificateStat[];
  deserialization: DeserializationSummary;
  deserializationTypes: DeserializationTypeStat[];
  cryptoProviders: ProviderServiceStat[];
}
