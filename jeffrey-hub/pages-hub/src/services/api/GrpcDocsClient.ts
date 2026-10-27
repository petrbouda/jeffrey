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

import BasePlatformClient from '@shared/services/api/BasePlatformClient';

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
