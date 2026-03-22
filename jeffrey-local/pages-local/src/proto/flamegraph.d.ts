import * as $protobuf from "protobufjs";
import Long = require("long");
/** Namespace pbouda. */
export namespace pbouda {

    /** Namespace jeffrey. */
    namespace jeffrey {

        /** Namespace flamegraph. */
        namespace flamegraph {

            /** Namespace proto. */
            namespace proto {

                /** Properties of a GraphData. */
                interface IGraphData {

                    /** GraphData flamegraph */
                    flamegraph?: (pbouda.jeffrey.flamegraph.proto.IFlamegraphData|null);

                    /** GraphData timeseries */
                    timeseries?: (pbouda.jeffrey.flamegraph.proto.ITimeseriesData|null);
                }

                /** Represents a GraphData. */
                class GraphData implements IGraphData {

                    /**
                     * Constructs a new GraphData.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: pbouda.jeffrey.flamegraph.proto.IGraphData);

                    /** GraphData flamegraph. */
                    public flamegraph?: (pbouda.jeffrey.flamegraph.proto.IFlamegraphData|null);

                    /** GraphData timeseries. */
                    public timeseries?: (pbouda.jeffrey.flamegraph.proto.ITimeseriesData|null);

                    /**
                     * Creates a new GraphData instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns GraphData instance
                     */
                    public static create(properties?: pbouda.jeffrey.flamegraph.proto.IGraphData): pbouda.jeffrey.flamegraph.proto.GraphData;

                    /**
                     * Encodes the specified GraphData message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.GraphData.verify|verify} messages.
                     * @param message GraphData message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: pbouda.jeffrey.flamegraph.proto.IGraphData, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified GraphData message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.GraphData.verify|verify} messages.
                     * @param message GraphData message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: pbouda.jeffrey.flamegraph.proto.IGraphData, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a GraphData message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns GraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): pbouda.jeffrey.flamegraph.proto.GraphData;

                    /**
                     * Decodes a GraphData message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns GraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): pbouda.jeffrey.flamegraph.proto.GraphData;

                    /**
                     * Verifies a GraphData message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a GraphData message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns GraphData
                     */
                    public static fromObject(object: { [k: string]: any }): pbouda.jeffrey.flamegraph.proto.GraphData;

                    /**
                     * Creates a plain object from a GraphData message. Also converts values to other types if specified.
                     * @param message GraphData
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: pbouda.jeffrey.flamegraph.proto.GraphData, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this GraphData to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for GraphData
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }

                /** Properties of a FlamegraphData. */
                interface IFlamegraphData {

                    /** FlamegraphData depth */
                    depth?: (number|null);

                    /** FlamegraphData levels */
                    levels?: (pbouda.jeffrey.flamegraph.proto.ILevel[]|null);

                    /** FlamegraphData titlePool */
                    titlePool?: (string[]|null);
                }

                /** Represents a FlamegraphData. */
                class FlamegraphData implements IFlamegraphData {

                    /**
                     * Constructs a new FlamegraphData.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: pbouda.jeffrey.flamegraph.proto.IFlamegraphData);

                    /** FlamegraphData depth. */
                    public depth: number;

                    /** FlamegraphData levels. */
                    public levels: pbouda.jeffrey.flamegraph.proto.ILevel[];

                    /** FlamegraphData titlePool. */
                    public titlePool: string[];

                    /**
                     * Creates a new FlamegraphData instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns FlamegraphData instance
                     */
                    public static create(properties?: pbouda.jeffrey.flamegraph.proto.IFlamegraphData): pbouda.jeffrey.flamegraph.proto.FlamegraphData;

                    /**
                     * Encodes the specified FlamegraphData message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.FlamegraphData.verify|verify} messages.
                     * @param message FlamegraphData message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: pbouda.jeffrey.flamegraph.proto.IFlamegraphData, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified FlamegraphData message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.FlamegraphData.verify|verify} messages.
                     * @param message FlamegraphData message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: pbouda.jeffrey.flamegraph.proto.IFlamegraphData, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a FlamegraphData message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns FlamegraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): pbouda.jeffrey.flamegraph.proto.FlamegraphData;

                    /**
                     * Decodes a FlamegraphData message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns FlamegraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): pbouda.jeffrey.flamegraph.proto.FlamegraphData;

                    /**
                     * Verifies a FlamegraphData message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a FlamegraphData message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns FlamegraphData
                     */
                    public static fromObject(object: { [k: string]: any }): pbouda.jeffrey.flamegraph.proto.FlamegraphData;

                    /**
                     * Creates a plain object from a FlamegraphData message. Also converts values to other types if specified.
                     * @param message FlamegraphData
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: pbouda.jeffrey.flamegraph.proto.FlamegraphData, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this FlamegraphData to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for FlamegraphData
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }

                /** Properties of a TimeseriesData. */
                interface ITimeseriesData {

                    /** TimeseriesData series */
                    series?: (pbouda.jeffrey.flamegraph.proto.ITimeseriesSeries[]|null);
                }

                /** Represents a TimeseriesData. */
                class TimeseriesData implements ITimeseriesData {

                    /**
                     * Constructs a new TimeseriesData.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: pbouda.jeffrey.flamegraph.proto.ITimeseriesData);

                    /** TimeseriesData series. */
                    public series: pbouda.jeffrey.flamegraph.proto.ITimeseriesSeries[];

                    /**
                     * Creates a new TimeseriesData instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns TimeseriesData instance
                     */
                    public static create(properties?: pbouda.jeffrey.flamegraph.proto.ITimeseriesData): pbouda.jeffrey.flamegraph.proto.TimeseriesData;

                    /**
                     * Encodes the specified TimeseriesData message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.TimeseriesData.verify|verify} messages.
                     * @param message TimeseriesData message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: pbouda.jeffrey.flamegraph.proto.ITimeseriesData, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified TimeseriesData message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.TimeseriesData.verify|verify} messages.
                     * @param message TimeseriesData message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: pbouda.jeffrey.flamegraph.proto.ITimeseriesData, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a TimeseriesData message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns TimeseriesData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): pbouda.jeffrey.flamegraph.proto.TimeseriesData;

                    /**
                     * Decodes a TimeseriesData message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns TimeseriesData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): pbouda.jeffrey.flamegraph.proto.TimeseriesData;

                    /**
                     * Verifies a TimeseriesData message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a TimeseriesData message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns TimeseriesData
                     */
                    public static fromObject(object: { [k: string]: any }): pbouda.jeffrey.flamegraph.proto.TimeseriesData;

                    /**
                     * Creates a plain object from a TimeseriesData message. Also converts values to other types if specified.
                     * @param message TimeseriesData
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: pbouda.jeffrey.flamegraph.proto.TimeseriesData, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this TimeseriesData to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for TimeseriesData
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }

                /** Properties of a TimeseriesSeries. */
                interface ITimeseriesSeries {

                    /** TimeseriesSeries name */
                    name?: (string|null);

                    /** TimeseriesSeries data */
                    data?: (pbouda.jeffrey.flamegraph.proto.ITimeseriesPoint[]|null);
                }

                /** Represents a TimeseriesSeries. */
                class TimeseriesSeries implements ITimeseriesSeries {

                    /**
                     * Constructs a new TimeseriesSeries.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: pbouda.jeffrey.flamegraph.proto.ITimeseriesSeries);

                    /** TimeseriesSeries name. */
                    public name: string;

                    /** TimeseriesSeries data. */
                    public data: pbouda.jeffrey.flamegraph.proto.ITimeseriesPoint[];

                    /**
                     * Creates a new TimeseriesSeries instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns TimeseriesSeries instance
                     */
                    public static create(properties?: pbouda.jeffrey.flamegraph.proto.ITimeseriesSeries): pbouda.jeffrey.flamegraph.proto.TimeseriesSeries;

                    /**
                     * Encodes the specified TimeseriesSeries message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.TimeseriesSeries.verify|verify} messages.
                     * @param message TimeseriesSeries message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: pbouda.jeffrey.flamegraph.proto.ITimeseriesSeries, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified TimeseriesSeries message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.TimeseriesSeries.verify|verify} messages.
                     * @param message TimeseriesSeries message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: pbouda.jeffrey.flamegraph.proto.ITimeseriesSeries, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a TimeseriesSeries message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns TimeseriesSeries
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): pbouda.jeffrey.flamegraph.proto.TimeseriesSeries;

                    /**
                     * Decodes a TimeseriesSeries message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns TimeseriesSeries
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): pbouda.jeffrey.flamegraph.proto.TimeseriesSeries;

                    /**
                     * Verifies a TimeseriesSeries message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a TimeseriesSeries message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns TimeseriesSeries
                     */
                    public static fromObject(object: { [k: string]: any }): pbouda.jeffrey.flamegraph.proto.TimeseriesSeries;

                    /**
                     * Creates a plain object from a TimeseriesSeries message. Also converts values to other types if specified.
                     * @param message TimeseriesSeries
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: pbouda.jeffrey.flamegraph.proto.TimeseriesSeries, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this TimeseriesSeries to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for TimeseriesSeries
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }

                /** Properties of a TimeseriesPoint. */
                interface ITimeseriesPoint {

                    /** TimeseriesPoint timestamp */
                    timestamp?: (number|Long|null);

                    /** TimeseriesPoint value */
                    value?: (number|Long|null);
                }

                /** Represents a TimeseriesPoint. */
                class TimeseriesPoint implements ITimeseriesPoint {

                    /**
                     * Constructs a new TimeseriesPoint.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: pbouda.jeffrey.flamegraph.proto.ITimeseriesPoint);

                    /** TimeseriesPoint timestamp. */
                    public timestamp: (number|Long);

                    /** TimeseriesPoint value. */
                    public value: (number|Long);

                    /**
                     * Creates a new TimeseriesPoint instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns TimeseriesPoint instance
                     */
                    public static create(properties?: pbouda.jeffrey.flamegraph.proto.ITimeseriesPoint): pbouda.jeffrey.flamegraph.proto.TimeseriesPoint;

                    /**
                     * Encodes the specified TimeseriesPoint message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.TimeseriesPoint.verify|verify} messages.
                     * @param message TimeseriesPoint message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: pbouda.jeffrey.flamegraph.proto.ITimeseriesPoint, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified TimeseriesPoint message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.TimeseriesPoint.verify|verify} messages.
                     * @param message TimeseriesPoint message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: pbouda.jeffrey.flamegraph.proto.ITimeseriesPoint, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a TimeseriesPoint message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns TimeseriesPoint
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): pbouda.jeffrey.flamegraph.proto.TimeseriesPoint;

                    /**
                     * Decodes a TimeseriesPoint message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns TimeseriesPoint
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): pbouda.jeffrey.flamegraph.proto.TimeseriesPoint;

                    /**
                     * Verifies a TimeseriesPoint message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a TimeseriesPoint message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns TimeseriesPoint
                     */
                    public static fromObject(object: { [k: string]: any }): pbouda.jeffrey.flamegraph.proto.TimeseriesPoint;

                    /**
                     * Creates a plain object from a TimeseriesPoint message. Also converts values to other types if specified.
                     * @param message TimeseriesPoint
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: pbouda.jeffrey.flamegraph.proto.TimeseriesPoint, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this TimeseriesPoint to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for TimeseriesPoint
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }

                /** Properties of a Level. */
                interface ILevel {

                    /** Level frames */
                    frames?: (pbouda.jeffrey.flamegraph.proto.IFrame[]|null);
                }

                /** Represents a Level. */
                class Level implements ILevel {

                    /**
                     * Constructs a new Level.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: pbouda.jeffrey.flamegraph.proto.ILevel);

                    /** Level frames. */
                    public frames: pbouda.jeffrey.flamegraph.proto.IFrame[];

                    /**
                     * Creates a new Level instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns Level instance
                     */
                    public static create(properties?: pbouda.jeffrey.flamegraph.proto.ILevel): pbouda.jeffrey.flamegraph.proto.Level;

                    /**
                     * Encodes the specified Level message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.Level.verify|verify} messages.
                     * @param message Level message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: pbouda.jeffrey.flamegraph.proto.ILevel, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified Level message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.Level.verify|verify} messages.
                     * @param message Level message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: pbouda.jeffrey.flamegraph.proto.ILevel, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a Level message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns Level
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): pbouda.jeffrey.flamegraph.proto.Level;

                    /**
                     * Decodes a Level message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns Level
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): pbouda.jeffrey.flamegraph.proto.Level;

                    /**
                     * Verifies a Level message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a Level message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns Level
                     */
                    public static fromObject(object: { [k: string]: any }): pbouda.jeffrey.flamegraph.proto.Level;

                    /**
                     * Creates a plain object from a Level message. Also converts values to other types if specified.
                     * @param message Level
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: pbouda.jeffrey.flamegraph.proto.Level, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this Level to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for Level
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }

                /** Properties of a Frame. */
                interface IFrame {

                    /** Frame leftSamples */
                    leftSamples?: (number|Long|null);

                    /** Frame totalSamples */
                    totalSamples?: (number|Long|null);

                    /** Frame titleIndex */
                    titleIndex?: (number|null);

                    /** Frame type */
                    type?: (pbouda.jeffrey.flamegraph.proto.FrameType|null);

                    /** Frame leftWeight */
                    leftWeight?: (number|Long|null);

                    /** Frame totalWeight */
                    totalWeight?: (number|Long|null);

                    /** Frame selfSamples */
                    selfSamples?: (number|Long|null);

                    /** Frame position */
                    position?: (pbouda.jeffrey.flamegraph.proto.IFramePosition|null);

                    /** Frame sampleTypes */
                    sampleTypes?: (pbouda.jeffrey.flamegraph.proto.IFrameSampleTypes|null);

                    /** Frame diffDetails */
                    diffDetails?: (pbouda.jeffrey.flamegraph.proto.IDiffDetails|null);

                    /** Frame beforeMarker */
                    beforeMarker?: (boolean|null);
                }

                /** Represents a Frame. */
                class Frame implements IFrame {

                    /**
                     * Constructs a new Frame.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: pbouda.jeffrey.flamegraph.proto.IFrame);

                    /** Frame leftSamples. */
                    public leftSamples: (number|Long);

                    /** Frame totalSamples. */
                    public totalSamples: (number|Long);

                    /** Frame titleIndex. */
                    public titleIndex: number;

                    /** Frame type. */
                    public type: pbouda.jeffrey.flamegraph.proto.FrameType;

                    /** Frame leftWeight. */
                    public leftWeight: (number|Long);

                    /** Frame totalWeight. */
                    public totalWeight: (number|Long);

                    /** Frame selfSamples. */
                    public selfSamples: (number|Long);

                    /** Frame position. */
                    public position?: (pbouda.jeffrey.flamegraph.proto.IFramePosition|null);

                    /** Frame sampleTypes. */
                    public sampleTypes?: (pbouda.jeffrey.flamegraph.proto.IFrameSampleTypes|null);

                    /** Frame diffDetails. */
                    public diffDetails?: (pbouda.jeffrey.flamegraph.proto.IDiffDetails|null);

                    /** Frame beforeMarker. */
                    public beforeMarker: boolean;

                    /**
                     * Creates a new Frame instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns Frame instance
                     */
                    public static create(properties?: pbouda.jeffrey.flamegraph.proto.IFrame): pbouda.jeffrey.flamegraph.proto.Frame;

                    /**
                     * Encodes the specified Frame message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.Frame.verify|verify} messages.
                     * @param message Frame message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: pbouda.jeffrey.flamegraph.proto.IFrame, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified Frame message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.Frame.verify|verify} messages.
                     * @param message Frame message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: pbouda.jeffrey.flamegraph.proto.IFrame, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a Frame message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns Frame
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): pbouda.jeffrey.flamegraph.proto.Frame;

                    /**
                     * Decodes a Frame message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns Frame
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): pbouda.jeffrey.flamegraph.proto.Frame;

                    /**
                     * Verifies a Frame message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a Frame message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns Frame
                     */
                    public static fromObject(object: { [k: string]: any }): pbouda.jeffrey.flamegraph.proto.Frame;

                    /**
                     * Creates a plain object from a Frame message. Also converts values to other types if specified.
                     * @param message Frame
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: pbouda.jeffrey.flamegraph.proto.Frame, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this Frame to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for Frame
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }

                /** FrameType enum. */
                enum FrameType {
                    FRAME_TYPE_UNKNOWN = 0,
                    FRAME_TYPE_C1_COMPILED = 1,
                    FRAME_TYPE_NATIVE = 2,
                    FRAME_TYPE_CPP = 3,
                    FRAME_TYPE_INTERPRETED = 4,
                    FRAME_TYPE_JIT_COMPILED = 5,
                    FRAME_TYPE_INLINED = 6,
                    FRAME_TYPE_KERNEL = 7,
                    FRAME_TYPE_THREAD_NAME_SYNTHETIC = 8,
                    FRAME_TYPE_ALLOCATED_OBJECT_SYNTHETIC = 9,
                    FRAME_TYPE_ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC = 10,
                    FRAME_TYPE_ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC = 11,
                    FRAME_TYPE_BLOCKING_OBJECT_SYNTHETIC = 12,
                    FRAME_TYPE_LAMBDA_SYNTHETIC = 13,
                    FRAME_TYPE_HIGHLIGHTED_WARNING = 14
                }

                /** Properties of a FramePosition. */
                interface IFramePosition {

                    /** FramePosition bci */
                    bci?: (number|null);

                    /** FramePosition line */
                    line?: (number|null);
                }

                /** Represents a FramePosition. */
                class FramePosition implements IFramePosition {

                    /**
                     * Constructs a new FramePosition.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: pbouda.jeffrey.flamegraph.proto.IFramePosition);

                    /** FramePosition bci. */
                    public bci: number;

                    /** FramePosition line. */
                    public line: number;

                    /**
                     * Creates a new FramePosition instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns FramePosition instance
                     */
                    public static create(properties?: pbouda.jeffrey.flamegraph.proto.IFramePosition): pbouda.jeffrey.flamegraph.proto.FramePosition;

                    /**
                     * Encodes the specified FramePosition message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.FramePosition.verify|verify} messages.
                     * @param message FramePosition message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: pbouda.jeffrey.flamegraph.proto.IFramePosition, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified FramePosition message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.FramePosition.verify|verify} messages.
                     * @param message FramePosition message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: pbouda.jeffrey.flamegraph.proto.IFramePosition, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a FramePosition message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns FramePosition
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): pbouda.jeffrey.flamegraph.proto.FramePosition;

                    /**
                     * Decodes a FramePosition message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns FramePosition
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): pbouda.jeffrey.flamegraph.proto.FramePosition;

                    /**
                     * Verifies a FramePosition message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a FramePosition message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns FramePosition
                     */
                    public static fromObject(object: { [k: string]: any }): pbouda.jeffrey.flamegraph.proto.FramePosition;

                    /**
                     * Creates a plain object from a FramePosition message. Also converts values to other types if specified.
                     * @param message FramePosition
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: pbouda.jeffrey.flamegraph.proto.FramePosition, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this FramePosition to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for FramePosition
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }

                /** Properties of a FrameSampleTypes. */
                interface IFrameSampleTypes {

                    /** FrameSampleTypes inlined */
                    inlined?: (number|Long|null);

                    /** FrameSampleTypes c1 */
                    c1?: (number|Long|null);

                    /** FrameSampleTypes interpret */
                    interpret?: (number|Long|null);

                    /** FrameSampleTypes jit */
                    jit?: (number|Long|null);
                }

                /** Represents a FrameSampleTypes. */
                class FrameSampleTypes implements IFrameSampleTypes {

                    /**
                     * Constructs a new FrameSampleTypes.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: pbouda.jeffrey.flamegraph.proto.IFrameSampleTypes);

                    /** FrameSampleTypes inlined. */
                    public inlined: (number|Long);

                    /** FrameSampleTypes c1. */
                    public c1: (number|Long);

                    /** FrameSampleTypes interpret. */
                    public interpret: (number|Long);

                    /** FrameSampleTypes jit. */
                    public jit: (number|Long);

                    /**
                     * Creates a new FrameSampleTypes instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns FrameSampleTypes instance
                     */
                    public static create(properties?: pbouda.jeffrey.flamegraph.proto.IFrameSampleTypes): pbouda.jeffrey.flamegraph.proto.FrameSampleTypes;

                    /**
                     * Encodes the specified FrameSampleTypes message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.FrameSampleTypes.verify|verify} messages.
                     * @param message FrameSampleTypes message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: pbouda.jeffrey.flamegraph.proto.IFrameSampleTypes, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified FrameSampleTypes message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.FrameSampleTypes.verify|verify} messages.
                     * @param message FrameSampleTypes message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: pbouda.jeffrey.flamegraph.proto.IFrameSampleTypes, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a FrameSampleTypes message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns FrameSampleTypes
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): pbouda.jeffrey.flamegraph.proto.FrameSampleTypes;

                    /**
                     * Decodes a FrameSampleTypes message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns FrameSampleTypes
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): pbouda.jeffrey.flamegraph.proto.FrameSampleTypes;

                    /**
                     * Verifies a FrameSampleTypes message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a FrameSampleTypes message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns FrameSampleTypes
                     */
                    public static fromObject(object: { [k: string]: any }): pbouda.jeffrey.flamegraph.proto.FrameSampleTypes;

                    /**
                     * Creates a plain object from a FrameSampleTypes message. Also converts values to other types if specified.
                     * @param message FrameSampleTypes
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: pbouda.jeffrey.flamegraph.proto.FrameSampleTypes, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this FrameSampleTypes to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for FrameSampleTypes
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }

                /** Properties of a DiffDetails. */
                interface IDiffDetails {

                    /** DiffDetails samples */
                    samples?: (number|Long|null);

                    /** DiffDetails weight */
                    weight?: (number|Long|null);

                    /** DiffDetails percentSamples */
                    percentSamples?: (number|null);

                    /** DiffDetails percentWeight */
                    percentWeight?: (number|null);
                }

                /** Represents a DiffDetails. */
                class DiffDetails implements IDiffDetails {

                    /**
                     * Constructs a new DiffDetails.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: pbouda.jeffrey.flamegraph.proto.IDiffDetails);

                    /** DiffDetails samples. */
                    public samples: (number|Long);

                    /** DiffDetails weight. */
                    public weight: (number|Long);

                    /** DiffDetails percentSamples. */
                    public percentSamples: number;

                    /** DiffDetails percentWeight. */
                    public percentWeight: number;

                    /**
                     * Creates a new DiffDetails instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns DiffDetails instance
                     */
                    public static create(properties?: pbouda.jeffrey.flamegraph.proto.IDiffDetails): pbouda.jeffrey.flamegraph.proto.DiffDetails;

                    /**
                     * Encodes the specified DiffDetails message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.DiffDetails.verify|verify} messages.
                     * @param message DiffDetails message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: pbouda.jeffrey.flamegraph.proto.IDiffDetails, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified DiffDetails message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.DiffDetails.verify|verify} messages.
                     * @param message DiffDetails message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: pbouda.jeffrey.flamegraph.proto.IDiffDetails, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a DiffDetails message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns DiffDetails
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): pbouda.jeffrey.flamegraph.proto.DiffDetails;

                    /**
                     * Decodes a DiffDetails message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns DiffDetails
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): pbouda.jeffrey.flamegraph.proto.DiffDetails;

                    /**
                     * Verifies a DiffDetails message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a DiffDetails message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns DiffDetails
                     */
                    public static fromObject(object: { [k: string]: any }): pbouda.jeffrey.flamegraph.proto.DiffDetails;

                    /**
                     * Creates a plain object from a DiffDetails message. Also converts values to other types if specified.
                     * @param message DiffDetails
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: pbouda.jeffrey.flamegraph.proto.DiffDetails, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this DiffDetails to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for DiffDetails
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }
            }
        }
    }
}
