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

import BasePlatformClient from '@/services/api/BasePlatformClient';

export interface GrpcField {
    name: string;
    description: string;
    label: string;
    type: string;
    longType: string;
    fullType: string;
    defaultValue: string;
}

export interface GrpcMessage {
    name: string;
    longName: string;
    fullName: string;
    description: string;
    fields: GrpcField[];
}

export interface GrpcEnumValue {
    name: string;
    number: number;
    description: string;
}

export interface GrpcEnum {
    name: string;
    longName: string;
    description: string;
    values: GrpcEnumValue[];
}

export interface GrpcMethod {
    name: string;
    description: string;
    requestType: string;
    requestLongType: string;
    requestFullType: string;
    responseType: string;
    responseLongType: string;
    responseFullType: string;
    requestStreaming: boolean;
    responseStreaming: boolean;
}

export interface GrpcService {
    name: string;
    longName: string;
    fullName: string;
    description: string;
    methods: GrpcMethod[];
}

export interface GrpcFile {
    name: string;
    description: string;
    package: string;
    services: GrpcService[];
    messages: GrpcMessage[];
    enums: GrpcEnum[];
}

export interface GrpcDocs {
    files: GrpcFile[];
}

export default class GrpcDocsClient extends BasePlatformClient {

    constructor() {
        super('/grpc-docs');
    }

    async getDocs(): Promise<GrpcDocs> {
        return super.get<GrpcDocs>();
    }
}
