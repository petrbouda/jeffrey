import * as $protobuf from "protobufjs";
import Long = require("long");

/** Namespace cafe. */
export namespace cafe {

    /** Namespace jeffrey. */
    namespace jeffrey {

        /** Namespace flamegraph. */
        namespace flamegraph {

            /** Namespace proto. */
            namespace proto {

                /**
                 * Properties of a GraphData.
                 * @deprecated Use cafe.jeffrey.flamegraph.proto.GraphData.$Properties instead.
                 */
                interface IGraphData extends cafe.jeffrey.flamegraph.proto.GraphData.$Properties {
                }

                /** Represents a GraphData. */
                class GraphData {

                    /**
                     * Constructs a new GraphData.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: cafe.jeffrey.flamegraph.proto.GraphData.$Properties);

                    /** Unknown fields preserved while decoding when enabled */
                    $unknowns?: Uint8Array[];

                    /** GraphData flamegraph. */
                    flamegraph?: (cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties|null);

                    /** GraphData timeseries. */
                    timeseries?: (cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties|null);

                    /**
                     * Creates a new GraphData instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns GraphData instance
                     */
                    static create(properties: cafe.jeffrey.flamegraph.proto.GraphData.$Shape): cafe.jeffrey.flamegraph.proto.GraphData & cafe.jeffrey.flamegraph.proto.GraphData.$Shape;
                    static create(properties?: cafe.jeffrey.flamegraph.proto.GraphData.$Properties): cafe.jeffrey.flamegraph.proto.GraphData;

                    /**
                     * Encodes the specified GraphData message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.GraphData.verify|verify} messages.
                     * @param message GraphData message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encode(message: cafe.jeffrey.flamegraph.proto.GraphData.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified GraphData message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.GraphData.verify|verify} messages.
                     * @param message GraphData message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encodeDelimited(message: cafe.jeffrey.flamegraph.proto.GraphData.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a GraphData message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.GraphData & cafe.jeffrey.flamegraph.proto.GraphData.$Shape} GraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): cafe.jeffrey.flamegraph.proto.GraphData & cafe.jeffrey.flamegraph.proto.GraphData.$Shape;

                    /**
                     * Decodes a GraphData message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.GraphData & cafe.jeffrey.flamegraph.proto.GraphData.$Shape} GraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): cafe.jeffrey.flamegraph.proto.GraphData & cafe.jeffrey.flamegraph.proto.GraphData.$Shape;

                    /**
                     * Verifies a GraphData message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a GraphData message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns GraphData
                     */
                    static fromObject(object: { [k: string]: any }): cafe.jeffrey.flamegraph.proto.GraphData;

                    /**
                     * Creates a plain object from a GraphData message. Also converts values to other types if specified.
                     * @param message GraphData
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    static toObject(message: cafe.jeffrey.flamegraph.proto.GraphData, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this GraphData to JSON.
                     * @returns JSON object
                     */
                    toJSON(): { [k: string]: any };

                    /**
                     * Gets the type url for GraphData
                     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns The type url
                     */
                    static getTypeUrl(prefix?: string): string;
                }

                namespace GraphData {

                    /** Properties of a GraphData. */
                    interface $Properties {

                        /** GraphData flamegraph */
                        flamegraph?: (cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties|null);

                        /** GraphData timeseries */
                        timeseries?: (cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties|null);

                        /** Unknown fields preserved while decoding when enabled */
                        $unknowns?: Uint8Array[];
                    }

                    /** Shape of a GraphData. */
                    type $Shape = cafe.jeffrey.flamegraph.proto.GraphData.$Properties;
                }

                /**
                 * Properties of a FlamegraphData.
                 * @deprecated Use cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties instead.
                 */
                interface IFlamegraphData extends cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties {
                }

                /** Represents a FlamegraphData. */
                class FlamegraphData {

                    /**
                     * Constructs a new FlamegraphData.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties);

                    /** Unknown fields preserved while decoding when enabled */
                    $unknowns?: Uint8Array[];

                    /** FlamegraphData depth. */
                    depth: number;

                    /** FlamegraphData levels. */
                    levels: cafe.jeffrey.flamegraph.proto.Level.$Properties[];

                    /** FlamegraphData titlePool. */
                    titlePool: string[];

                    /**
                     * Creates a new FlamegraphData instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns FlamegraphData instance
                     */
                    static create(properties: cafe.jeffrey.flamegraph.proto.FlamegraphData.$Shape): cafe.jeffrey.flamegraph.proto.FlamegraphData & cafe.jeffrey.flamegraph.proto.FlamegraphData.$Shape;
                    static create(properties?: cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties): cafe.jeffrey.flamegraph.proto.FlamegraphData;

                    /**
                     * Encodes the specified FlamegraphData message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.FlamegraphData.verify|verify} messages.
                     * @param message FlamegraphData message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encode(message: cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified FlamegraphData message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.FlamegraphData.verify|verify} messages.
                     * @param message FlamegraphData message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encodeDelimited(message: cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a FlamegraphData message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.FlamegraphData & cafe.jeffrey.flamegraph.proto.FlamegraphData.$Shape} FlamegraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): cafe.jeffrey.flamegraph.proto.FlamegraphData & cafe.jeffrey.flamegraph.proto.FlamegraphData.$Shape;

                    /**
                     * Decodes a FlamegraphData message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.FlamegraphData & cafe.jeffrey.flamegraph.proto.FlamegraphData.$Shape} FlamegraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): cafe.jeffrey.flamegraph.proto.FlamegraphData & cafe.jeffrey.flamegraph.proto.FlamegraphData.$Shape;

                    /**
                     * Verifies a FlamegraphData message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a FlamegraphData message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns FlamegraphData
                     */
                    static fromObject(object: { [k: string]: any }): cafe.jeffrey.flamegraph.proto.FlamegraphData;

                    /**
                     * Creates a plain object from a FlamegraphData message. Also converts values to other types if specified.
                     * @param message FlamegraphData
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    static toObject(message: cafe.jeffrey.flamegraph.proto.FlamegraphData, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this FlamegraphData to JSON.
                     * @returns JSON object
                     */
                    toJSON(): { [k: string]: any };

                    /**
                     * Gets the type url for FlamegraphData
                     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns The type url
                     */
                    static getTypeUrl(prefix?: string): string;
                }

                namespace FlamegraphData {

                    /** Properties of a FlamegraphData. */
                    interface $Properties {

                        /** FlamegraphData depth */
                        depth?: (number|null);

                        /** FlamegraphData levels */
                        levels?: (cafe.jeffrey.flamegraph.proto.Level.$Properties[]|null);

                        /** FlamegraphData titlePool */
                        titlePool?: (string[]|null);

                        /** Unknown fields preserved while decoding when enabled */
                        $unknowns?: Uint8Array[];
                    }

                    /** Shape of a FlamegraphData. */
                    type $Shape = cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties;
                }

                /**
                 * Properties of a TimeseriesData.
                 * @deprecated Use cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties instead.
                 */
                interface ITimeseriesData extends cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties {
                }

                /** Represents a TimeseriesData. */
                class TimeseriesData {

                    /**
                     * Constructs a new TimeseriesData.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties);

                    /** Unknown fields preserved while decoding when enabled */
                    $unknowns?: Uint8Array[];

                    /** TimeseriesData series. */
                    series: cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties[];

                    /**
                     * Creates a new TimeseriesData instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns TimeseriesData instance
                     */
                    static create(properties: cafe.jeffrey.flamegraph.proto.TimeseriesData.$Shape): cafe.jeffrey.flamegraph.proto.TimeseriesData & cafe.jeffrey.flamegraph.proto.TimeseriesData.$Shape;
                    static create(properties?: cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties): cafe.jeffrey.flamegraph.proto.TimeseriesData;

                    /**
                     * Encodes the specified TimeseriesData message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.TimeseriesData.verify|verify} messages.
                     * @param message TimeseriesData message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encode(message: cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified TimeseriesData message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.TimeseriesData.verify|verify} messages.
                     * @param message TimeseriesData message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encodeDelimited(message: cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a TimeseriesData message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesData & cafe.jeffrey.flamegraph.proto.TimeseriesData.$Shape} TimeseriesData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): cafe.jeffrey.flamegraph.proto.TimeseriesData & cafe.jeffrey.flamegraph.proto.TimeseriesData.$Shape;

                    /**
                     * Decodes a TimeseriesData message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesData & cafe.jeffrey.flamegraph.proto.TimeseriesData.$Shape} TimeseriesData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): cafe.jeffrey.flamegraph.proto.TimeseriesData & cafe.jeffrey.flamegraph.proto.TimeseriesData.$Shape;

                    /**
                     * Verifies a TimeseriesData message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a TimeseriesData message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns TimeseriesData
                     */
                    static fromObject(object: { [k: string]: any }): cafe.jeffrey.flamegraph.proto.TimeseriesData;

                    /**
                     * Creates a plain object from a TimeseriesData message. Also converts values to other types if specified.
                     * @param message TimeseriesData
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    static toObject(message: cafe.jeffrey.flamegraph.proto.TimeseriesData, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this TimeseriesData to JSON.
                     * @returns JSON object
                     */
                    toJSON(): { [k: string]: any };

                    /**
                     * Gets the type url for TimeseriesData
                     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns The type url
                     */
                    static getTypeUrl(prefix?: string): string;
                }

                namespace TimeseriesData {

                    /** Properties of a TimeseriesData. */
                    interface $Properties {

                        /** TimeseriesData series */
                        series?: (cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties[]|null);

                        /** Unknown fields preserved while decoding when enabled */
                        $unknowns?: Uint8Array[];
                    }

                    /** Shape of a TimeseriesData. */
                    type $Shape = cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties;
                }

                /**
                 * Properties of a TimeseriesSeries.
                 * @deprecated Use cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties instead.
                 */
                interface ITimeseriesSeries extends cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties {
                }

                /** Represents a TimeseriesSeries. */
                class TimeseriesSeries {

                    /**
                     * Constructs a new TimeseriesSeries.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties);

                    /** Unknown fields preserved while decoding when enabled */
                    $unknowns?: Uint8Array[];

                    /** TimeseriesSeries name. */
                    name: string;

                    /** TimeseriesSeries data. */
                    data: cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties[];

                    /**
                     * Creates a new TimeseriesSeries instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns TimeseriesSeries instance
                     */
                    static create(properties: cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Shape): cafe.jeffrey.flamegraph.proto.TimeseriesSeries & cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Shape;
                    static create(properties?: cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties): cafe.jeffrey.flamegraph.proto.TimeseriesSeries;

                    /**
                     * Encodes the specified TimeseriesSeries message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.TimeseriesSeries.verify|verify} messages.
                     * @param message TimeseriesSeries message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encode(message: cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified TimeseriesSeries message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.TimeseriesSeries.verify|verify} messages.
                     * @param message TimeseriesSeries message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encodeDelimited(message: cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a TimeseriesSeries message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesSeries & cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Shape} TimeseriesSeries
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): cafe.jeffrey.flamegraph.proto.TimeseriesSeries & cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Shape;

                    /**
                     * Decodes a TimeseriesSeries message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesSeries & cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Shape} TimeseriesSeries
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): cafe.jeffrey.flamegraph.proto.TimeseriesSeries & cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Shape;

                    /**
                     * Verifies a TimeseriesSeries message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a TimeseriesSeries message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns TimeseriesSeries
                     */
                    static fromObject(object: { [k: string]: any }): cafe.jeffrey.flamegraph.proto.TimeseriesSeries;

                    /**
                     * Creates a plain object from a TimeseriesSeries message. Also converts values to other types if specified.
                     * @param message TimeseriesSeries
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    static toObject(message: cafe.jeffrey.flamegraph.proto.TimeseriesSeries, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this TimeseriesSeries to JSON.
                     * @returns JSON object
                     */
                    toJSON(): { [k: string]: any };

                    /**
                     * Gets the type url for TimeseriesSeries
                     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns The type url
                     */
                    static getTypeUrl(prefix?: string): string;
                }

                namespace TimeseriesSeries {

                    /** Properties of a TimeseriesSeries. */
                    interface $Properties {

                        /** TimeseriesSeries name */
                        name?: (string|null);

                        /** TimeseriesSeries data */
                        data?: (cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties[]|null);

                        /** Unknown fields preserved while decoding when enabled */
                        $unknowns?: Uint8Array[];
                    }

                    /** Shape of a TimeseriesSeries. */
                    type $Shape = cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties;
                }

                /**
                 * Properties of a TimeseriesPoint.
                 * @deprecated Use cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties instead.
                 */
                interface ITimeseriesPoint extends cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties {
                }

                /** Represents a TimeseriesPoint. */
                class TimeseriesPoint {

                    /**
                     * Constructs a new TimeseriesPoint.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties);

                    /** Unknown fields preserved while decoding when enabled */
                    $unknowns?: Uint8Array[];

                    /** TimeseriesPoint timestamp. */
                    timestamp: (number|Long);

                    /** TimeseriesPoint value. */
                    value: (number|Long);

                    /**
                     * Creates a new TimeseriesPoint instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns TimeseriesPoint instance
                     */
                    static create(properties: cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Shape): cafe.jeffrey.flamegraph.proto.TimeseriesPoint & cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Shape;
                    static create(properties?: cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties): cafe.jeffrey.flamegraph.proto.TimeseriesPoint;

                    /**
                     * Encodes the specified TimeseriesPoint message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.TimeseriesPoint.verify|verify} messages.
                     * @param message TimeseriesPoint message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encode(message: cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified TimeseriesPoint message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.TimeseriesPoint.verify|verify} messages.
                     * @param message TimeseriesPoint message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encodeDelimited(message: cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a TimeseriesPoint message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesPoint & cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Shape} TimeseriesPoint
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): cafe.jeffrey.flamegraph.proto.TimeseriesPoint & cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Shape;

                    /**
                     * Decodes a TimeseriesPoint message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesPoint & cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Shape} TimeseriesPoint
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): cafe.jeffrey.flamegraph.proto.TimeseriesPoint & cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Shape;

                    /**
                     * Verifies a TimeseriesPoint message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a TimeseriesPoint message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns TimeseriesPoint
                     */
                    static fromObject(object: { [k: string]: any }): cafe.jeffrey.flamegraph.proto.TimeseriesPoint;

                    /**
                     * Creates a plain object from a TimeseriesPoint message. Also converts values to other types if specified.
                     * @param message TimeseriesPoint
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    static toObject(message: cafe.jeffrey.flamegraph.proto.TimeseriesPoint, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this TimeseriesPoint to JSON.
                     * @returns JSON object
                     */
                    toJSON(): { [k: string]: any };

                    /**
                     * Gets the type url for TimeseriesPoint
                     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns The type url
                     */
                    static getTypeUrl(prefix?: string): string;
                }

                namespace TimeseriesPoint {

                    /** Properties of a TimeseriesPoint. */
                    interface $Properties {

                        /** TimeseriesPoint timestamp */
                        timestamp?: (number|Long|null);

                        /** TimeseriesPoint value */
                        value?: (number|Long|null);

                        /** Unknown fields preserved while decoding when enabled */
                        $unknowns?: Uint8Array[];
                    }

                    /** Shape of a TimeseriesPoint. */
                    type $Shape = cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties;
                }

                /**
                 * Properties of a Level.
                 * @deprecated Use cafe.jeffrey.flamegraph.proto.Level.$Properties instead.
                 */
                interface ILevel extends cafe.jeffrey.flamegraph.proto.Level.$Properties {
                }

                /** Represents a Level. */
                class Level {

                    /**
                     * Constructs a new Level.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: cafe.jeffrey.flamegraph.proto.Level.$Properties);

                    /** Unknown fields preserved while decoding when enabled */
                    $unknowns?: Uint8Array[];

                    /** Level frames. */
                    frames: cafe.jeffrey.flamegraph.proto.Frame.$Properties[];

                    /**
                     * Creates a new Level instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns Level instance
                     */
                    static create(properties: cafe.jeffrey.flamegraph.proto.Level.$Shape): cafe.jeffrey.flamegraph.proto.Level & cafe.jeffrey.flamegraph.proto.Level.$Shape;
                    static create(properties?: cafe.jeffrey.flamegraph.proto.Level.$Properties): cafe.jeffrey.flamegraph.proto.Level;

                    /**
                     * Encodes the specified Level message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.Level.verify|verify} messages.
                     * @param message Level message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encode(message: cafe.jeffrey.flamegraph.proto.Level.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified Level message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.Level.verify|verify} messages.
                     * @param message Level message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encodeDelimited(message: cafe.jeffrey.flamegraph.proto.Level.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a Level message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.Level & cafe.jeffrey.flamegraph.proto.Level.$Shape} Level
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): cafe.jeffrey.flamegraph.proto.Level & cafe.jeffrey.flamegraph.proto.Level.$Shape;

                    /**
                     * Decodes a Level message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.Level & cafe.jeffrey.flamegraph.proto.Level.$Shape} Level
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): cafe.jeffrey.flamegraph.proto.Level & cafe.jeffrey.flamegraph.proto.Level.$Shape;

                    /**
                     * Verifies a Level message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a Level message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns Level
                     */
                    static fromObject(object: { [k: string]: any }): cafe.jeffrey.flamegraph.proto.Level;

                    /**
                     * Creates a plain object from a Level message. Also converts values to other types if specified.
                     * @param message Level
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    static toObject(message: cafe.jeffrey.flamegraph.proto.Level, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this Level to JSON.
                     * @returns JSON object
                     */
                    toJSON(): { [k: string]: any };

                    /**
                     * Gets the type url for Level
                     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns The type url
                     */
                    static getTypeUrl(prefix?: string): string;
                }

                namespace Level {

                    /** Properties of a Level. */
                    interface $Properties {

                        /** Level frames */
                        frames?: (cafe.jeffrey.flamegraph.proto.Frame.$Properties[]|null);

                        /** Unknown fields preserved while decoding when enabled */
                        $unknowns?: Uint8Array[];
                    }

                    /** Shape of a Level. */
                    type $Shape = cafe.jeffrey.flamegraph.proto.Level.$Properties;
                }

                /**
                 * Properties of a Frame.
                 * @deprecated Use cafe.jeffrey.flamegraph.proto.Frame.$Properties instead.
                 */
                interface IFrame extends cafe.jeffrey.flamegraph.proto.Frame.$Properties {
                }

                /** Represents a Frame. */
                class Frame {

                    /**
                     * Constructs a new Frame.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: cafe.jeffrey.flamegraph.proto.Frame.$Properties);

                    /** Unknown fields preserved while decoding when enabled */
                    $unknowns?: Uint8Array[];

                    /** Frame leftSamples. */
                    leftSamples: (number|Long);

                    /** Frame totalSamples. */
                    totalSamples: (number|Long);

                    /** Frame titleIndex. */
                    titleIndex: number;

                    /** Frame type. */
                    type: cafe.jeffrey.flamegraph.proto.FrameType;

                    /** Frame leftWeight. */
                    leftWeight: (number|Long);

                    /** Frame totalWeight. */
                    totalWeight: (number|Long);

                    /** Frame selfSamples. */
                    selfSamples: (number|Long);

                    /** Frame position. */
                    position?: (cafe.jeffrey.flamegraph.proto.FramePosition.$Properties|null);

                    /** Frame sampleTypes. */
                    sampleTypes?: (cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties|null);

                    /** Frame diffDetails. */
                    diffDetails?: (cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties|null);

                    /** Frame beforeMarker. */
                    beforeMarker: boolean;

                    /** Frame prunedChildrenCount. */
                    prunedChildrenCount: number;

                    /**
                     * Creates a new Frame instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns Frame instance
                     */
                    static create(properties: cafe.jeffrey.flamegraph.proto.Frame.$Shape): cafe.jeffrey.flamegraph.proto.Frame & cafe.jeffrey.flamegraph.proto.Frame.$Shape;
                    static create(properties?: cafe.jeffrey.flamegraph.proto.Frame.$Properties): cafe.jeffrey.flamegraph.proto.Frame;

                    /**
                     * Encodes the specified Frame message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.Frame.verify|verify} messages.
                     * @param message Frame message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encode(message: cafe.jeffrey.flamegraph.proto.Frame.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified Frame message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.Frame.verify|verify} messages.
                     * @param message Frame message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encodeDelimited(message: cafe.jeffrey.flamegraph.proto.Frame.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a Frame message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.Frame & cafe.jeffrey.flamegraph.proto.Frame.$Shape} Frame
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): cafe.jeffrey.flamegraph.proto.Frame & cafe.jeffrey.flamegraph.proto.Frame.$Shape;

                    /**
                     * Decodes a Frame message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.Frame & cafe.jeffrey.flamegraph.proto.Frame.$Shape} Frame
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): cafe.jeffrey.flamegraph.proto.Frame & cafe.jeffrey.flamegraph.proto.Frame.$Shape;

                    /**
                     * Verifies a Frame message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a Frame message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns Frame
                     */
                    static fromObject(object: { [k: string]: any }): cafe.jeffrey.flamegraph.proto.Frame;

                    /**
                     * Creates a plain object from a Frame message. Also converts values to other types if specified.
                     * @param message Frame
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    static toObject(message: cafe.jeffrey.flamegraph.proto.Frame, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this Frame to JSON.
                     * @returns JSON object
                     */
                    toJSON(): { [k: string]: any };

                    /**
                     * Gets the type url for Frame
                     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns The type url
                     */
                    static getTypeUrl(prefix?: string): string;
                }

                namespace Frame {

                    /** Properties of a Frame. */
                    interface $Properties {

                        /** Frame leftSamples */
                        leftSamples?: (number|Long|null);

                        /** Frame totalSamples */
                        totalSamples?: (number|Long|null);

                        /** Frame titleIndex */
                        titleIndex?: (number|null);

                        /** Frame type */
                        type?: (cafe.jeffrey.flamegraph.proto.FrameType|null);

                        /** Frame leftWeight */
                        leftWeight?: (number|Long|null);

                        /** Frame totalWeight */
                        totalWeight?: (number|Long|null);

                        /** Frame selfSamples */
                        selfSamples?: (number|Long|null);

                        /** Frame position */
                        position?: (cafe.jeffrey.flamegraph.proto.FramePosition.$Properties|null);

                        /** Frame sampleTypes */
                        sampleTypes?: (cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties|null);

                        /** Frame diffDetails */
                        diffDetails?: (cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties|null);

                        /** Frame beforeMarker */
                        beforeMarker?: (boolean|null);

                        /** Frame prunedChildrenCount */
                        prunedChildrenCount?: (number|null);

                        /** Unknown fields preserved while decoding when enabled */
                        $unknowns?: Uint8Array[];
                    }

                    /** Shape of a Frame. */
                    type $Shape = cafe.jeffrey.flamegraph.proto.Frame.$Properties;
                }

                /** FrameType enum. */
                enum FrameType {

                    /** FRAME_TYPE_UNKNOWN value */
                    FRAME_TYPE_UNKNOWN = 0,

                    /** FRAME_TYPE_C1_COMPILED value */
                    FRAME_TYPE_C1_COMPILED = 1,

                    /** FRAME_TYPE_NATIVE value */
                    FRAME_TYPE_NATIVE = 2,

                    /** FRAME_TYPE_CPP value */
                    FRAME_TYPE_CPP = 3,

                    /** FRAME_TYPE_INTERPRETED value */
                    FRAME_TYPE_INTERPRETED = 4,

                    /** FRAME_TYPE_JIT_COMPILED value */
                    FRAME_TYPE_JIT_COMPILED = 5,

                    /** FRAME_TYPE_INLINED value */
                    FRAME_TYPE_INLINED = 6,

                    /** FRAME_TYPE_KERNEL value */
                    FRAME_TYPE_KERNEL = 7,

                    /** FRAME_TYPE_THREAD_NAME_SYNTHETIC value */
                    FRAME_TYPE_THREAD_NAME_SYNTHETIC = 8,

                    /** FRAME_TYPE_ALLOCATED_OBJECT_SYNTHETIC value */
                    FRAME_TYPE_ALLOCATED_OBJECT_SYNTHETIC = 9,

                    /** FRAME_TYPE_ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC value */
                    FRAME_TYPE_ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC = 10,

                    /** FRAME_TYPE_ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC value */
                    FRAME_TYPE_ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC = 11,

                    /** FRAME_TYPE_BLOCKING_OBJECT_SYNTHETIC value */
                    FRAME_TYPE_BLOCKING_OBJECT_SYNTHETIC = 12,

                    /** FRAME_TYPE_LAMBDA_SYNTHETIC value */
                    FRAME_TYPE_LAMBDA_SYNTHETIC = 13,

                    /** FRAME_TYPE_HIGHLIGHTED_WARNING value */
                    FRAME_TYPE_HIGHLIGHTED_WARNING = 14,

                    /** FRAME_TYPE_COLLAPSED_SYNTHETIC value */
                    FRAME_TYPE_COLLAPSED_SYNTHETIC = 15,

                    /** FRAME_TYPE_TRUNCATED_SYNTHETIC value */
                    FRAME_TYPE_TRUNCATED_SYNTHETIC = 16,

                    /** FRAME_TYPE_PYTHON value */
                    FRAME_TYPE_PYTHON = 17,

                    /** FRAME_TYPE_JAVASCRIPT value */
                    FRAME_TYPE_JAVASCRIPT = 18,

                    /** FRAME_TYPE_GO value */
                    FRAME_TYPE_GO = 19,

                    /** FRAME_TYPE_DOTNET value */
                    FRAME_TYPE_DOTNET = 20,

                    /** FRAME_TYPE_RUBY value */
                    FRAME_TYPE_RUBY = 21,

                    /** FRAME_TYPE_PHP value */
                    FRAME_TYPE_PHP = 22,

                    /** FRAME_TYPE_PERL value */
                    FRAME_TYPE_PERL = 23,

                    /** FRAME_TYPE_BEAM value */
                    FRAME_TYPE_BEAM = 24,

                    /** FRAME_TYPE_RUST value */
                    FRAME_TYPE_RUST = 25,

                    /** FRAME_TYPE_LUA value */
                    FRAME_TYPE_LUA = 26
                }

                /**
                 * Properties of a FramePosition.
                 * @deprecated Use cafe.jeffrey.flamegraph.proto.FramePosition.$Properties instead.
                 */
                interface IFramePosition extends cafe.jeffrey.flamegraph.proto.FramePosition.$Properties {
                }

                /** Represents a FramePosition. */
                class FramePosition {

                    /**
                     * Constructs a new FramePosition.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: cafe.jeffrey.flamegraph.proto.FramePosition.$Properties);

                    /** Unknown fields preserved while decoding when enabled */
                    $unknowns?: Uint8Array[];

                    /** FramePosition bci. */
                    bci: number;

                    /** FramePosition line. */
                    line: number;

                    /**
                     * Creates a new FramePosition instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns FramePosition instance
                     */
                    static create(properties: cafe.jeffrey.flamegraph.proto.FramePosition.$Shape): cafe.jeffrey.flamegraph.proto.FramePosition & cafe.jeffrey.flamegraph.proto.FramePosition.$Shape;
                    static create(properties?: cafe.jeffrey.flamegraph.proto.FramePosition.$Properties): cafe.jeffrey.flamegraph.proto.FramePosition;

                    /**
                     * Encodes the specified FramePosition message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.FramePosition.verify|verify} messages.
                     * @param message FramePosition message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encode(message: cafe.jeffrey.flamegraph.proto.FramePosition.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified FramePosition message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.FramePosition.verify|verify} messages.
                     * @param message FramePosition message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encodeDelimited(message: cafe.jeffrey.flamegraph.proto.FramePosition.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a FramePosition message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.FramePosition & cafe.jeffrey.flamegraph.proto.FramePosition.$Shape} FramePosition
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): cafe.jeffrey.flamegraph.proto.FramePosition & cafe.jeffrey.flamegraph.proto.FramePosition.$Shape;

                    /**
                     * Decodes a FramePosition message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.FramePosition & cafe.jeffrey.flamegraph.proto.FramePosition.$Shape} FramePosition
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): cafe.jeffrey.flamegraph.proto.FramePosition & cafe.jeffrey.flamegraph.proto.FramePosition.$Shape;

                    /**
                     * Verifies a FramePosition message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a FramePosition message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns FramePosition
                     */
                    static fromObject(object: { [k: string]: any }): cafe.jeffrey.flamegraph.proto.FramePosition;

                    /**
                     * Creates a plain object from a FramePosition message. Also converts values to other types if specified.
                     * @param message FramePosition
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    static toObject(message: cafe.jeffrey.flamegraph.proto.FramePosition, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this FramePosition to JSON.
                     * @returns JSON object
                     */
                    toJSON(): { [k: string]: any };

                    /**
                     * Gets the type url for FramePosition
                     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns The type url
                     */
                    static getTypeUrl(prefix?: string): string;
                }

                namespace FramePosition {

                    /** Properties of a FramePosition. */
                    interface $Properties {

                        /** FramePosition bci */
                        bci?: (number|null);

                        /** FramePosition line */
                        line?: (number|null);

                        /** Unknown fields preserved while decoding when enabled */
                        $unknowns?: Uint8Array[];
                    }

                    /** Shape of a FramePosition. */
                    type $Shape = cafe.jeffrey.flamegraph.proto.FramePosition.$Properties;
                }

                /**
                 * Properties of a FrameSampleTypes.
                 * @deprecated Use cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties instead.
                 */
                interface IFrameSampleTypes extends cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties {
                }

                /** Represents a FrameSampleTypes. */
                class FrameSampleTypes {

                    /**
                     * Constructs a new FrameSampleTypes.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties);

                    /** Unknown fields preserved while decoding when enabled */
                    $unknowns?: Uint8Array[];

                    /** FrameSampleTypes inlined. */
                    inlined: (number|Long);

                    /** FrameSampleTypes c1. */
                    c1: (number|Long);

                    /** FrameSampleTypes interpret. */
                    interpret: (number|Long);

                    /** FrameSampleTypes jit. */
                    jit: (number|Long);

                    /**
                     * Creates a new FrameSampleTypes instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns FrameSampleTypes instance
                     */
                    static create(properties: cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Shape): cafe.jeffrey.flamegraph.proto.FrameSampleTypes & cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Shape;
                    static create(properties?: cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties): cafe.jeffrey.flamegraph.proto.FrameSampleTypes;

                    /**
                     * Encodes the specified FrameSampleTypes message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.FrameSampleTypes.verify|verify} messages.
                     * @param message FrameSampleTypes message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encode(message: cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified FrameSampleTypes message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.FrameSampleTypes.verify|verify} messages.
                     * @param message FrameSampleTypes message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encodeDelimited(message: cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a FrameSampleTypes message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.FrameSampleTypes & cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Shape} FrameSampleTypes
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): cafe.jeffrey.flamegraph.proto.FrameSampleTypes & cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Shape;

                    /**
                     * Decodes a FrameSampleTypes message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.FrameSampleTypes & cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Shape} FrameSampleTypes
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): cafe.jeffrey.flamegraph.proto.FrameSampleTypes & cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Shape;

                    /**
                     * Verifies a FrameSampleTypes message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a FrameSampleTypes message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns FrameSampleTypes
                     */
                    static fromObject(object: { [k: string]: any }): cafe.jeffrey.flamegraph.proto.FrameSampleTypes;

                    /**
                     * Creates a plain object from a FrameSampleTypes message. Also converts values to other types if specified.
                     * @param message FrameSampleTypes
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    static toObject(message: cafe.jeffrey.flamegraph.proto.FrameSampleTypes, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this FrameSampleTypes to JSON.
                     * @returns JSON object
                     */
                    toJSON(): { [k: string]: any };

                    /**
                     * Gets the type url for FrameSampleTypes
                     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns The type url
                     */
                    static getTypeUrl(prefix?: string): string;
                }

                namespace FrameSampleTypes {

                    /** Properties of a FrameSampleTypes. */
                    interface $Properties {

                        /** FrameSampleTypes inlined */
                        inlined?: (number|Long|null);

                        /** FrameSampleTypes c1 */
                        c1?: (number|Long|null);

                        /** FrameSampleTypes interpret */
                        interpret?: (number|Long|null);

                        /** FrameSampleTypes jit */
                        jit?: (number|Long|null);

                        /** Unknown fields preserved while decoding when enabled */
                        $unknowns?: Uint8Array[];
                    }

                    /** Shape of a FrameSampleTypes. */
                    type $Shape = cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties;
                }

                /**
                 * Properties of a DiffDetails.
                 * @deprecated Use cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties instead.
                 */
                interface IDiffDetails extends cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties {
                }

                /** Represents a DiffDetails. */
                class DiffDetails {

                    /**
                     * Constructs a new DiffDetails.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties);

                    /** Unknown fields preserved while decoding when enabled */
                    $unknowns?: Uint8Array[];

                    /** DiffDetails samples. */
                    samples: (number|Long);

                    /** DiffDetails weight. */
                    weight: (number|Long);

                    /** DiffDetails percentSamples. */
                    percentSamples: number;

                    /** DiffDetails percentWeight. */
                    percentWeight: number;

                    /** DiffDetails secondarySamples. */
                    secondarySamples: (number|Long);

                    /** DiffDetails secondaryWeight. */
                    secondaryWeight: (number|Long);

                    /**
                     * Creates a new DiffDetails instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns DiffDetails instance
                     */
                    static create(properties: cafe.jeffrey.flamegraph.proto.DiffDetails.$Shape): cafe.jeffrey.flamegraph.proto.DiffDetails & cafe.jeffrey.flamegraph.proto.DiffDetails.$Shape;
                    static create(properties?: cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties): cafe.jeffrey.flamegraph.proto.DiffDetails;

                    /**
                     * Encodes the specified DiffDetails message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.DiffDetails.verify|verify} messages.
                     * @param message DiffDetails message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encode(message: cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified DiffDetails message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.DiffDetails.verify|verify} messages.
                     * @param message DiffDetails message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    static encodeDelimited(message: cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a DiffDetails message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.DiffDetails & cafe.jeffrey.flamegraph.proto.DiffDetails.$Shape} DiffDetails
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): cafe.jeffrey.flamegraph.proto.DiffDetails & cafe.jeffrey.flamegraph.proto.DiffDetails.$Shape;

                    /**
                     * Decodes a DiffDetails message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.DiffDetails & cafe.jeffrey.flamegraph.proto.DiffDetails.$Shape} DiffDetails
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): cafe.jeffrey.flamegraph.proto.DiffDetails & cafe.jeffrey.flamegraph.proto.DiffDetails.$Shape;

                    /**
                     * Verifies a DiffDetails message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a DiffDetails message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns DiffDetails
                     */
                    static fromObject(object: { [k: string]: any }): cafe.jeffrey.flamegraph.proto.DiffDetails;

                    /**
                     * Creates a plain object from a DiffDetails message. Also converts values to other types if specified.
                     * @param message DiffDetails
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    static toObject(message: cafe.jeffrey.flamegraph.proto.DiffDetails, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this DiffDetails to JSON.
                     * @returns JSON object
                     */
                    toJSON(): { [k: string]: any };

                    /**
                     * Gets the type url for DiffDetails
                     * @param [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns The type url
                     */
                    static getTypeUrl(prefix?: string): string;
                }

                namespace DiffDetails {

                    /** Properties of a DiffDetails. */
                    interface $Properties {

                        /** DiffDetails samples */
                        samples?: (number|Long|null);

                        /** DiffDetails weight */
                        weight?: (number|Long|null);

                        /** DiffDetails percentSamples */
                        percentSamples?: (number|null);

                        /** DiffDetails percentWeight */
                        percentWeight?: (number|null);

                        /** DiffDetails secondarySamples */
                        secondarySamples?: (number|Long|null);

                        /** DiffDetails secondaryWeight */
                        secondaryWeight?: (number|Long|null);

                        /** Unknown fields preserved while decoding when enabled */
                        $unknowns?: Uint8Array[];
                    }

                    /** Shape of a DiffDetails. */
                    type $Shape = cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties;
                }
            }
        }
    }
}
