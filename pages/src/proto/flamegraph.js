/*eslint-disable block-scoped-var, id-length, no-control-regex, no-magic-numbers, no-prototype-builtins, no-redeclare, no-shadow, no-var, sort-vars*/
import * as $protobuf from "protobufjs/minimal";

// Common aliases
const $Reader = $protobuf.Reader, $Writer = $protobuf.Writer, $util = $protobuf.util;

// Exported root namespace
const $root = $protobuf.roots["default"] || ($protobuf.roots["default"] = {});

export const pbouda = $root.pbouda = (() => {

    /**
     * Namespace pbouda.
     * @exports pbouda
     * @namespace
     */
    const pbouda = {};

    pbouda.jeffrey = (function() {

        /**
         * Namespace jeffrey.
         * @memberof pbouda
         * @namespace
         */
        const jeffrey = {};

        jeffrey.flamegraph = (function() {

            /**
             * Namespace flamegraph.
             * @memberof pbouda.jeffrey
             * @namespace
             */
            const flamegraph = {};

            flamegraph.proto = (function() {

                /**
                 * Namespace proto.
                 * @memberof pbouda.jeffrey.flamegraph
                 * @namespace
                 */
                const proto = {};

                proto.GraphData = (function() {

                    /**
                     * Properties of a GraphData.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @interface IGraphData
                     * @property {pbouda.jeffrey.flamegraph.proto.IFlamegraphData|null} [flamegraph] GraphData flamegraph
                     * @property {pbouda.jeffrey.flamegraph.proto.ITimeseriesData|null} [timeseries] GraphData timeseries
                     */

                    /**
                     * Constructs a new GraphData.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @classdesc Represents a GraphData.
                     * @implements IGraphData
                     * @constructor
                     * @param {pbouda.jeffrey.flamegraph.proto.IGraphData=} [properties] Properties to set
                     */
                    function GraphData(properties) {
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null)
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * GraphData flamegraph.
                     * @member {pbouda.jeffrey.flamegraph.proto.IFlamegraphData|null|undefined} flamegraph
                     * @memberof pbouda.jeffrey.flamegraph.proto.GraphData
                     * @instance
                     */
                    GraphData.prototype.flamegraph = null;

                    /**
                     * GraphData timeseries.
                     * @member {pbouda.jeffrey.flamegraph.proto.ITimeseriesData|null|undefined} timeseries
                     * @memberof pbouda.jeffrey.flamegraph.proto.GraphData
                     * @instance
                     */
                    GraphData.prototype.timeseries = null;

                    /**
                     * Creates a new GraphData instance using the specified properties.
                     * @function create
                     * @memberof pbouda.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IGraphData=} [properties] Properties to set
                     * @returns {pbouda.jeffrey.flamegraph.proto.GraphData} GraphData instance
                     */
                    GraphData.create = function create(properties) {
                        return new GraphData(properties);
                    };

                    /**
                     * Encodes the specified GraphData message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.GraphData.verify|verify} messages.
                     * @function encode
                     * @memberof pbouda.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IGraphData} message GraphData message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    GraphData.encode = function encode(message, writer) {
                        if (!writer)
                            writer = $Writer.create();
                        if (message.flamegraph != null && Object.hasOwnProperty.call(message, "flamegraph"))
                            $root.pbouda.jeffrey.flamegraph.proto.FlamegraphData.encode(message.flamegraph, writer.uint32(/* id 1, wireType 2 =*/10).fork()).ldelim();
                        if (message.timeseries != null && Object.hasOwnProperty.call(message, "timeseries"))
                            $root.pbouda.jeffrey.flamegraph.proto.TimeseriesData.encode(message.timeseries, writer.uint32(/* id 2, wireType 2 =*/18).fork()).ldelim();
                        return writer;
                    };

                    /**
                     * Encodes the specified GraphData message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.GraphData.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IGraphData} message GraphData message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    GraphData.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer).ldelim();
                    };

                    /**
                     * Decodes a GraphData message from the specified reader or buffer.
                     * @function decode
                     * @memberof pbouda.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {pbouda.jeffrey.flamegraph.proto.GraphData} GraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    GraphData.decode = function decode(reader, length, error) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.pbouda.jeffrey.flamegraph.proto.GraphData();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    message.flamegraph = $root.pbouda.jeffrey.flamegraph.proto.FlamegraphData.decode(reader, reader.uint32());
                                    break;
                                }
                            case 2: {
                                    message.timeseries = $root.pbouda.jeffrey.flamegraph.proto.TimeseriesData.decode(reader, reader.uint32());
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes a GraphData message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {pbouda.jeffrey.flamegraph.proto.GraphData} GraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    GraphData.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a GraphData message.
                     * @function verify
                     * @memberof pbouda.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    GraphData.verify = function verify(message) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (message.flamegraph != null && message.hasOwnProperty("flamegraph")) {
                            let error = $root.pbouda.jeffrey.flamegraph.proto.FlamegraphData.verify(message.flamegraph);
                            if (error)
                                return "flamegraph." + error;
                        }
                        if (message.timeseries != null && message.hasOwnProperty("timeseries")) {
                            let error = $root.pbouda.jeffrey.flamegraph.proto.TimeseriesData.verify(message.timeseries);
                            if (error)
                                return "timeseries." + error;
                        }
                        return null;
                    };

                    /**
                     * Creates a GraphData message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {pbouda.jeffrey.flamegraph.proto.GraphData} GraphData
                     */
                    GraphData.fromObject = function fromObject(object) {
                        if (object instanceof $root.pbouda.jeffrey.flamegraph.proto.GraphData)
                            return object;
                        let message = new $root.pbouda.jeffrey.flamegraph.proto.GraphData();
                        if (object.flamegraph != null) {
                            if (typeof object.flamegraph !== "object")
                                throw TypeError(".pbouda.jeffrey.flamegraph.proto.GraphData.flamegraph: object expected");
                            message.flamegraph = $root.pbouda.jeffrey.flamegraph.proto.FlamegraphData.fromObject(object.flamegraph);
                        }
                        if (object.timeseries != null) {
                            if (typeof object.timeseries !== "object")
                                throw TypeError(".pbouda.jeffrey.flamegraph.proto.GraphData.timeseries: object expected");
                            message.timeseries = $root.pbouda.jeffrey.flamegraph.proto.TimeseriesData.fromObject(object.timeseries);
                        }
                        return message;
                    };

                    /**
                     * Creates a plain object from a GraphData message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.GraphData} message GraphData
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    GraphData.toObject = function toObject(message, options) {
                        if (!options)
                            options = {};
                        let object = {};
                        if (options.defaults) {
                            object.flamegraph = null;
                            object.timeseries = null;
                        }
                        if (message.flamegraph != null && message.hasOwnProperty("flamegraph"))
                            object.flamegraph = $root.pbouda.jeffrey.flamegraph.proto.FlamegraphData.toObject(message.flamegraph, options);
                        if (message.timeseries != null && message.hasOwnProperty("timeseries"))
                            object.timeseries = $root.pbouda.jeffrey.flamegraph.proto.TimeseriesData.toObject(message.timeseries, options);
                        return object;
                    };

                    /**
                     * Converts this GraphData to JSON.
                     * @function toJSON
                     * @memberof pbouda.jeffrey.flamegraph.proto.GraphData
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    GraphData.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for GraphData
                     * @function getTypeUrl
                     * @memberof pbouda.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    GraphData.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/pbouda.jeffrey.flamegraph.proto.GraphData";
                    };

                    return GraphData;
                })();

                proto.FlamegraphData = (function() {

                    /**
                     * Properties of a FlamegraphData.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @interface IFlamegraphData
                     * @property {number|null} [depth] FlamegraphData depth
                     * @property {Array.<pbouda.jeffrey.flamegraph.proto.ILevel>|null} [levels] FlamegraphData levels
                     * @property {Array.<string>|null} [titlePool] FlamegraphData titlePool
                     */

                    /**
                     * Constructs a new FlamegraphData.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @classdesc Represents a FlamegraphData.
                     * @implements IFlamegraphData
                     * @constructor
                     * @param {pbouda.jeffrey.flamegraph.proto.IFlamegraphData=} [properties] Properties to set
                     */
                    function FlamegraphData(properties) {
                        this.levels = [];
                        this.titlePool = [];
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null)
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * FlamegraphData depth.
                     * @member {number} depth
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @instance
                     */
                    FlamegraphData.prototype.depth = 0;

                    /**
                     * FlamegraphData levels.
                     * @member {Array.<pbouda.jeffrey.flamegraph.proto.ILevel>} levels
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @instance
                     */
                    FlamegraphData.prototype.levels = $util.emptyArray;

                    /**
                     * FlamegraphData titlePool.
                     * @member {Array.<string>} titlePool
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @instance
                     */
                    FlamegraphData.prototype.titlePool = $util.emptyArray;

                    /**
                     * Creates a new FlamegraphData instance using the specified properties.
                     * @function create
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IFlamegraphData=} [properties] Properties to set
                     * @returns {pbouda.jeffrey.flamegraph.proto.FlamegraphData} FlamegraphData instance
                     */
                    FlamegraphData.create = function create(properties) {
                        return new FlamegraphData(properties);
                    };

                    /**
                     * Encodes the specified FlamegraphData message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.FlamegraphData.verify|verify} messages.
                     * @function encode
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IFlamegraphData} message FlamegraphData message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    FlamegraphData.encode = function encode(message, writer) {
                        if (!writer)
                            writer = $Writer.create();
                        if (message.depth != null && Object.hasOwnProperty.call(message, "depth"))
                            writer.uint32(/* id 1, wireType 0 =*/8).int32(message.depth);
                        if (message.levels != null && message.levels.length)
                            for (let i = 0; i < message.levels.length; ++i)
                                $root.pbouda.jeffrey.flamegraph.proto.Level.encode(message.levels[i], writer.uint32(/* id 2, wireType 2 =*/18).fork()).ldelim();
                        if (message.titlePool != null && message.titlePool.length)
                            for (let i = 0; i < message.titlePool.length; ++i)
                                writer.uint32(/* id 3, wireType 2 =*/26).string(message.titlePool[i]);
                        return writer;
                    };

                    /**
                     * Encodes the specified FlamegraphData message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.FlamegraphData.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IFlamegraphData} message FlamegraphData message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    FlamegraphData.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer).ldelim();
                    };

                    /**
                     * Decodes a FlamegraphData message from the specified reader or buffer.
                     * @function decode
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {pbouda.jeffrey.flamegraph.proto.FlamegraphData} FlamegraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    FlamegraphData.decode = function decode(reader, length, error) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.pbouda.jeffrey.flamegraph.proto.FlamegraphData();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    message.depth = reader.int32();
                                    break;
                                }
                            case 2: {
                                    if (!(message.levels && message.levels.length))
                                        message.levels = [];
                                    message.levels.push($root.pbouda.jeffrey.flamegraph.proto.Level.decode(reader, reader.uint32()));
                                    break;
                                }
                            case 3: {
                                    if (!(message.titlePool && message.titlePool.length))
                                        message.titlePool = [];
                                    message.titlePool.push(reader.string());
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes a FlamegraphData message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {pbouda.jeffrey.flamegraph.proto.FlamegraphData} FlamegraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    FlamegraphData.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a FlamegraphData message.
                     * @function verify
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    FlamegraphData.verify = function verify(message) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (message.depth != null && message.hasOwnProperty("depth"))
                            if (!$util.isInteger(message.depth))
                                return "depth: integer expected";
                        if (message.levels != null && message.hasOwnProperty("levels")) {
                            if (!Array.isArray(message.levels))
                                return "levels: array expected";
                            for (let i = 0; i < message.levels.length; ++i) {
                                let error = $root.pbouda.jeffrey.flamegraph.proto.Level.verify(message.levels[i]);
                                if (error)
                                    return "levels." + error;
                            }
                        }
                        if (message.titlePool != null && message.hasOwnProperty("titlePool")) {
                            if (!Array.isArray(message.titlePool))
                                return "titlePool: array expected";
                            for (let i = 0; i < message.titlePool.length; ++i)
                                if (!$util.isString(message.titlePool[i]))
                                    return "titlePool: string[] expected";
                        }
                        return null;
                    };

                    /**
                     * Creates a FlamegraphData message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {pbouda.jeffrey.flamegraph.proto.FlamegraphData} FlamegraphData
                     */
                    FlamegraphData.fromObject = function fromObject(object) {
                        if (object instanceof $root.pbouda.jeffrey.flamegraph.proto.FlamegraphData)
                            return object;
                        let message = new $root.pbouda.jeffrey.flamegraph.proto.FlamegraphData();
                        if (object.depth != null)
                            message.depth = object.depth | 0;
                        if (object.levels) {
                            if (!Array.isArray(object.levels))
                                throw TypeError(".pbouda.jeffrey.flamegraph.proto.FlamegraphData.levels: array expected");
                            message.levels = [];
                            for (let i = 0; i < object.levels.length; ++i) {
                                if (typeof object.levels[i] !== "object")
                                    throw TypeError(".pbouda.jeffrey.flamegraph.proto.FlamegraphData.levels: object expected");
                                message.levels[i] = $root.pbouda.jeffrey.flamegraph.proto.Level.fromObject(object.levels[i]);
                            }
                        }
                        if (object.titlePool) {
                            if (!Array.isArray(object.titlePool))
                                throw TypeError(".pbouda.jeffrey.flamegraph.proto.FlamegraphData.titlePool: array expected");
                            message.titlePool = [];
                            for (let i = 0; i < object.titlePool.length; ++i)
                                message.titlePool[i] = String(object.titlePool[i]);
                        }
                        return message;
                    };

                    /**
                     * Creates a plain object from a FlamegraphData message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.FlamegraphData} message FlamegraphData
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    FlamegraphData.toObject = function toObject(message, options) {
                        if (!options)
                            options = {};
                        let object = {};
                        if (options.arrays || options.defaults) {
                            object.levels = [];
                            object.titlePool = [];
                        }
                        if (options.defaults)
                            object.depth = 0;
                        if (message.depth != null && message.hasOwnProperty("depth"))
                            object.depth = message.depth;
                        if (message.levels && message.levels.length) {
                            object.levels = [];
                            for (let j = 0; j < message.levels.length; ++j)
                                object.levels[j] = $root.pbouda.jeffrey.flamegraph.proto.Level.toObject(message.levels[j], options);
                        }
                        if (message.titlePool && message.titlePool.length) {
                            object.titlePool = [];
                            for (let j = 0; j < message.titlePool.length; ++j)
                                object.titlePool[j] = message.titlePool[j];
                        }
                        return object;
                    };

                    /**
                     * Converts this FlamegraphData to JSON.
                     * @function toJSON
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    FlamegraphData.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for FlamegraphData
                     * @function getTypeUrl
                     * @memberof pbouda.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    FlamegraphData.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/pbouda.jeffrey.flamegraph.proto.FlamegraphData";
                    };

                    return FlamegraphData;
                })();

                proto.TimeseriesData = (function() {

                    /**
                     * Properties of a TimeseriesData.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @interface ITimeseriesData
                     * @property {Array.<pbouda.jeffrey.flamegraph.proto.ITimeseriesSeries>|null} [series] TimeseriesData series
                     */

                    /**
                     * Constructs a new TimeseriesData.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @classdesc Represents a TimeseriesData.
                     * @implements ITimeseriesData
                     * @constructor
                     * @param {pbouda.jeffrey.flamegraph.proto.ITimeseriesData=} [properties] Properties to set
                     */
                    function TimeseriesData(properties) {
                        this.series = [];
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null)
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * TimeseriesData series.
                     * @member {Array.<pbouda.jeffrey.flamegraph.proto.ITimeseriesSeries>} series
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesData
                     * @instance
                     */
                    TimeseriesData.prototype.series = $util.emptyArray;

                    /**
                     * Creates a new TimeseriesData instance using the specified properties.
                     * @function create
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.ITimeseriesData=} [properties] Properties to set
                     * @returns {pbouda.jeffrey.flamegraph.proto.TimeseriesData} TimeseriesData instance
                     */
                    TimeseriesData.create = function create(properties) {
                        return new TimeseriesData(properties);
                    };

                    /**
                     * Encodes the specified TimeseriesData message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.TimeseriesData.verify|verify} messages.
                     * @function encode
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.ITimeseriesData} message TimeseriesData message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    TimeseriesData.encode = function encode(message, writer) {
                        if (!writer)
                            writer = $Writer.create();
                        if (message.series != null && message.series.length)
                            for (let i = 0; i < message.series.length; ++i)
                                $root.pbouda.jeffrey.flamegraph.proto.TimeseriesSeries.encode(message.series[i], writer.uint32(/* id 1, wireType 2 =*/10).fork()).ldelim();
                        return writer;
                    };

                    /**
                     * Encodes the specified TimeseriesData message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.TimeseriesData.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.ITimeseriesData} message TimeseriesData message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    TimeseriesData.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer).ldelim();
                    };

                    /**
                     * Decodes a TimeseriesData message from the specified reader or buffer.
                     * @function decode
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {pbouda.jeffrey.flamegraph.proto.TimeseriesData} TimeseriesData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    TimeseriesData.decode = function decode(reader, length, error) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.pbouda.jeffrey.flamegraph.proto.TimeseriesData();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    if (!(message.series && message.series.length))
                                        message.series = [];
                                    message.series.push($root.pbouda.jeffrey.flamegraph.proto.TimeseriesSeries.decode(reader, reader.uint32()));
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes a TimeseriesData message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {pbouda.jeffrey.flamegraph.proto.TimeseriesData} TimeseriesData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    TimeseriesData.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a TimeseriesData message.
                     * @function verify
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    TimeseriesData.verify = function verify(message) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (message.series != null && message.hasOwnProperty("series")) {
                            if (!Array.isArray(message.series))
                                return "series: array expected";
                            for (let i = 0; i < message.series.length; ++i) {
                                let error = $root.pbouda.jeffrey.flamegraph.proto.TimeseriesSeries.verify(message.series[i]);
                                if (error)
                                    return "series." + error;
                            }
                        }
                        return null;
                    };

                    /**
                     * Creates a TimeseriesData message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {pbouda.jeffrey.flamegraph.proto.TimeseriesData} TimeseriesData
                     */
                    TimeseriesData.fromObject = function fromObject(object) {
                        if (object instanceof $root.pbouda.jeffrey.flamegraph.proto.TimeseriesData)
                            return object;
                        let message = new $root.pbouda.jeffrey.flamegraph.proto.TimeseriesData();
                        if (object.series) {
                            if (!Array.isArray(object.series))
                                throw TypeError(".pbouda.jeffrey.flamegraph.proto.TimeseriesData.series: array expected");
                            message.series = [];
                            for (let i = 0; i < object.series.length; ++i) {
                                if (typeof object.series[i] !== "object")
                                    throw TypeError(".pbouda.jeffrey.flamegraph.proto.TimeseriesData.series: object expected");
                                message.series[i] = $root.pbouda.jeffrey.flamegraph.proto.TimeseriesSeries.fromObject(object.series[i]);
                            }
                        }
                        return message;
                    };

                    /**
                     * Creates a plain object from a TimeseriesData message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.TimeseriesData} message TimeseriesData
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    TimeseriesData.toObject = function toObject(message, options) {
                        if (!options)
                            options = {};
                        let object = {};
                        if (options.arrays || options.defaults)
                            object.series = [];
                        if (message.series && message.series.length) {
                            object.series = [];
                            for (let j = 0; j < message.series.length; ++j)
                                object.series[j] = $root.pbouda.jeffrey.flamegraph.proto.TimeseriesSeries.toObject(message.series[j], options);
                        }
                        return object;
                    };

                    /**
                     * Converts this TimeseriesData to JSON.
                     * @function toJSON
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesData
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    TimeseriesData.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for TimeseriesData
                     * @function getTypeUrl
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    TimeseriesData.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/pbouda.jeffrey.flamegraph.proto.TimeseriesData";
                    };

                    return TimeseriesData;
                })();

                proto.TimeseriesSeries = (function() {

                    /**
                     * Properties of a TimeseriesSeries.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @interface ITimeseriesSeries
                     * @property {string|null} [name] TimeseriesSeries name
                     * @property {Array.<pbouda.jeffrey.flamegraph.proto.ITimeseriesPoint>|null} [data] TimeseriesSeries data
                     */

                    /**
                     * Constructs a new TimeseriesSeries.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @classdesc Represents a TimeseriesSeries.
                     * @implements ITimeseriesSeries
                     * @constructor
                     * @param {pbouda.jeffrey.flamegraph.proto.ITimeseriesSeries=} [properties] Properties to set
                     */
                    function TimeseriesSeries(properties) {
                        this.data = [];
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null)
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * TimeseriesSeries name.
                     * @member {string} name
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @instance
                     */
                    TimeseriesSeries.prototype.name = "";

                    /**
                     * TimeseriesSeries data.
                     * @member {Array.<pbouda.jeffrey.flamegraph.proto.ITimeseriesPoint>} data
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @instance
                     */
                    TimeseriesSeries.prototype.data = $util.emptyArray;

                    /**
                     * Creates a new TimeseriesSeries instance using the specified properties.
                     * @function create
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.ITimeseriesSeries=} [properties] Properties to set
                     * @returns {pbouda.jeffrey.flamegraph.proto.TimeseriesSeries} TimeseriesSeries instance
                     */
                    TimeseriesSeries.create = function create(properties) {
                        return new TimeseriesSeries(properties);
                    };

                    /**
                     * Encodes the specified TimeseriesSeries message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.TimeseriesSeries.verify|verify} messages.
                     * @function encode
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.ITimeseriesSeries} message TimeseriesSeries message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    TimeseriesSeries.encode = function encode(message, writer) {
                        if (!writer)
                            writer = $Writer.create();
                        if (message.name != null && Object.hasOwnProperty.call(message, "name"))
                            writer.uint32(/* id 1, wireType 2 =*/10).string(message.name);
                        if (message.data != null && message.data.length)
                            for (let i = 0; i < message.data.length; ++i)
                                $root.pbouda.jeffrey.flamegraph.proto.TimeseriesPoint.encode(message.data[i], writer.uint32(/* id 2, wireType 2 =*/18).fork()).ldelim();
                        return writer;
                    };

                    /**
                     * Encodes the specified TimeseriesSeries message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.TimeseriesSeries.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.ITimeseriesSeries} message TimeseriesSeries message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    TimeseriesSeries.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer).ldelim();
                    };

                    /**
                     * Decodes a TimeseriesSeries message from the specified reader or buffer.
                     * @function decode
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {pbouda.jeffrey.flamegraph.proto.TimeseriesSeries} TimeseriesSeries
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    TimeseriesSeries.decode = function decode(reader, length, error) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.pbouda.jeffrey.flamegraph.proto.TimeseriesSeries();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    message.name = reader.string();
                                    break;
                                }
                            case 2: {
                                    if (!(message.data && message.data.length))
                                        message.data = [];
                                    message.data.push($root.pbouda.jeffrey.flamegraph.proto.TimeseriesPoint.decode(reader, reader.uint32()));
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes a TimeseriesSeries message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {pbouda.jeffrey.flamegraph.proto.TimeseriesSeries} TimeseriesSeries
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    TimeseriesSeries.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a TimeseriesSeries message.
                     * @function verify
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    TimeseriesSeries.verify = function verify(message) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (message.name != null && message.hasOwnProperty("name"))
                            if (!$util.isString(message.name))
                                return "name: string expected";
                        if (message.data != null && message.hasOwnProperty("data")) {
                            if (!Array.isArray(message.data))
                                return "data: array expected";
                            for (let i = 0; i < message.data.length; ++i) {
                                let error = $root.pbouda.jeffrey.flamegraph.proto.TimeseriesPoint.verify(message.data[i]);
                                if (error)
                                    return "data." + error;
                            }
                        }
                        return null;
                    };

                    /**
                     * Creates a TimeseriesSeries message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {pbouda.jeffrey.flamegraph.proto.TimeseriesSeries} TimeseriesSeries
                     */
                    TimeseriesSeries.fromObject = function fromObject(object) {
                        if (object instanceof $root.pbouda.jeffrey.flamegraph.proto.TimeseriesSeries)
                            return object;
                        let message = new $root.pbouda.jeffrey.flamegraph.proto.TimeseriesSeries();
                        if (object.name != null)
                            message.name = String(object.name);
                        if (object.data) {
                            if (!Array.isArray(object.data))
                                throw TypeError(".pbouda.jeffrey.flamegraph.proto.TimeseriesSeries.data: array expected");
                            message.data = [];
                            for (let i = 0; i < object.data.length; ++i) {
                                if (typeof object.data[i] !== "object")
                                    throw TypeError(".pbouda.jeffrey.flamegraph.proto.TimeseriesSeries.data: object expected");
                                message.data[i] = $root.pbouda.jeffrey.flamegraph.proto.TimeseriesPoint.fromObject(object.data[i]);
                            }
                        }
                        return message;
                    };

                    /**
                     * Creates a plain object from a TimeseriesSeries message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.TimeseriesSeries} message TimeseriesSeries
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    TimeseriesSeries.toObject = function toObject(message, options) {
                        if (!options)
                            options = {};
                        let object = {};
                        if (options.arrays || options.defaults)
                            object.data = [];
                        if (options.defaults)
                            object.name = "";
                        if (message.name != null && message.hasOwnProperty("name"))
                            object.name = message.name;
                        if (message.data && message.data.length) {
                            object.data = [];
                            for (let j = 0; j < message.data.length; ++j)
                                object.data[j] = $root.pbouda.jeffrey.flamegraph.proto.TimeseriesPoint.toObject(message.data[j], options);
                        }
                        return object;
                    };

                    /**
                     * Converts this TimeseriesSeries to JSON.
                     * @function toJSON
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    TimeseriesSeries.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for TimeseriesSeries
                     * @function getTypeUrl
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    TimeseriesSeries.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/pbouda.jeffrey.flamegraph.proto.TimeseriesSeries";
                    };

                    return TimeseriesSeries;
                })();

                proto.TimeseriesPoint = (function() {

                    /**
                     * Properties of a TimeseriesPoint.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @interface ITimeseriesPoint
                     * @property {number|Long|null} [timestamp] TimeseriesPoint timestamp
                     * @property {number|Long|null} [value] TimeseriesPoint value
                     */

                    /**
                     * Constructs a new TimeseriesPoint.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @classdesc Represents a TimeseriesPoint.
                     * @implements ITimeseriesPoint
                     * @constructor
                     * @param {pbouda.jeffrey.flamegraph.proto.ITimeseriesPoint=} [properties] Properties to set
                     */
                    function TimeseriesPoint(properties) {
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null)
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * TimeseriesPoint timestamp.
                     * @member {number|Long} timestamp
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @instance
                     */
                    TimeseriesPoint.prototype.timestamp = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * TimeseriesPoint value.
                     * @member {number|Long} value
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @instance
                     */
                    TimeseriesPoint.prototype.value = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Creates a new TimeseriesPoint instance using the specified properties.
                     * @function create
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.ITimeseriesPoint=} [properties] Properties to set
                     * @returns {pbouda.jeffrey.flamegraph.proto.TimeseriesPoint} TimeseriesPoint instance
                     */
                    TimeseriesPoint.create = function create(properties) {
                        return new TimeseriesPoint(properties);
                    };

                    /**
                     * Encodes the specified TimeseriesPoint message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.TimeseriesPoint.verify|verify} messages.
                     * @function encode
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.ITimeseriesPoint} message TimeseriesPoint message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    TimeseriesPoint.encode = function encode(message, writer) {
                        if (!writer)
                            writer = $Writer.create();
                        if (message.timestamp != null && Object.hasOwnProperty.call(message, "timestamp"))
                            writer.uint32(/* id 1, wireType 0 =*/8).int64(message.timestamp);
                        if (message.value != null && Object.hasOwnProperty.call(message, "value"))
                            writer.uint32(/* id 2, wireType 0 =*/16).int64(message.value);
                        return writer;
                    };

                    /**
                     * Encodes the specified TimeseriesPoint message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.TimeseriesPoint.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.ITimeseriesPoint} message TimeseriesPoint message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    TimeseriesPoint.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer).ldelim();
                    };

                    /**
                     * Decodes a TimeseriesPoint message from the specified reader or buffer.
                     * @function decode
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {pbouda.jeffrey.flamegraph.proto.TimeseriesPoint} TimeseriesPoint
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    TimeseriesPoint.decode = function decode(reader, length, error) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.pbouda.jeffrey.flamegraph.proto.TimeseriesPoint();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    message.timestamp = reader.int64();
                                    break;
                                }
                            case 2: {
                                    message.value = reader.int64();
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes a TimeseriesPoint message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {pbouda.jeffrey.flamegraph.proto.TimeseriesPoint} TimeseriesPoint
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    TimeseriesPoint.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a TimeseriesPoint message.
                     * @function verify
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    TimeseriesPoint.verify = function verify(message) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (message.timestamp != null && message.hasOwnProperty("timestamp"))
                            if (!$util.isInteger(message.timestamp) && !(message.timestamp && $util.isInteger(message.timestamp.low) && $util.isInteger(message.timestamp.high)))
                                return "timestamp: integer|Long expected";
                        if (message.value != null && message.hasOwnProperty("value"))
                            if (!$util.isInteger(message.value) && !(message.value && $util.isInteger(message.value.low) && $util.isInteger(message.value.high)))
                                return "value: integer|Long expected";
                        return null;
                    };

                    /**
                     * Creates a TimeseriesPoint message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {pbouda.jeffrey.flamegraph.proto.TimeseriesPoint} TimeseriesPoint
                     */
                    TimeseriesPoint.fromObject = function fromObject(object) {
                        if (object instanceof $root.pbouda.jeffrey.flamegraph.proto.TimeseriesPoint)
                            return object;
                        let message = new $root.pbouda.jeffrey.flamegraph.proto.TimeseriesPoint();
                        if (object.timestamp != null)
                            if ($util.Long)
                                (message.timestamp = $util.Long.fromValue(object.timestamp)).unsigned = false;
                            else if (typeof object.timestamp === "string")
                                message.timestamp = parseInt(object.timestamp, 10);
                            else if (typeof object.timestamp === "number")
                                message.timestamp = object.timestamp;
                            else if (typeof object.timestamp === "object")
                                message.timestamp = new $util.LongBits(object.timestamp.low >>> 0, object.timestamp.high >>> 0).toNumber();
                        if (object.value != null)
                            if ($util.Long)
                                (message.value = $util.Long.fromValue(object.value)).unsigned = false;
                            else if (typeof object.value === "string")
                                message.value = parseInt(object.value, 10);
                            else if (typeof object.value === "number")
                                message.value = object.value;
                            else if (typeof object.value === "object")
                                message.value = new $util.LongBits(object.value.low >>> 0, object.value.high >>> 0).toNumber();
                        return message;
                    };

                    /**
                     * Creates a plain object from a TimeseriesPoint message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.TimeseriesPoint} message TimeseriesPoint
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    TimeseriesPoint.toObject = function toObject(message, options) {
                        if (!options)
                            options = {};
                        let object = {};
                        if (options.defaults) {
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.timestamp = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.timestamp = options.longs === String ? "0" : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.value = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.value = options.longs === String ? "0" : 0;
                        }
                        if (message.timestamp != null && message.hasOwnProperty("timestamp"))
                            if (typeof message.timestamp === "number")
                                object.timestamp = options.longs === String ? String(message.timestamp) : message.timestamp;
                            else
                                object.timestamp = options.longs === String ? $util.Long.prototype.toString.call(message.timestamp) : options.longs === Number ? new $util.LongBits(message.timestamp.low >>> 0, message.timestamp.high >>> 0).toNumber() : message.timestamp;
                        if (message.value != null && message.hasOwnProperty("value"))
                            if (typeof message.value === "number")
                                object.value = options.longs === String ? String(message.value) : message.value;
                            else
                                object.value = options.longs === String ? $util.Long.prototype.toString.call(message.value) : options.longs === Number ? new $util.LongBits(message.value.low >>> 0, message.value.high >>> 0).toNumber() : message.value;
                        return object;
                    };

                    /**
                     * Converts this TimeseriesPoint to JSON.
                     * @function toJSON
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    TimeseriesPoint.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for TimeseriesPoint
                     * @function getTypeUrl
                     * @memberof pbouda.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    TimeseriesPoint.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/pbouda.jeffrey.flamegraph.proto.TimeseriesPoint";
                    };

                    return TimeseriesPoint;
                })();

                proto.Level = (function() {

                    /**
                     * Properties of a Level.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @interface ILevel
                     * @property {Array.<pbouda.jeffrey.flamegraph.proto.IFrame>|null} [frames] Level frames
                     */

                    /**
                     * Constructs a new Level.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @classdesc Represents a Level.
                     * @implements ILevel
                     * @constructor
                     * @param {pbouda.jeffrey.flamegraph.proto.ILevel=} [properties] Properties to set
                     */
                    function Level(properties) {
                        this.frames = [];
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null)
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * Level frames.
                     * @member {Array.<pbouda.jeffrey.flamegraph.proto.IFrame>} frames
                     * @memberof pbouda.jeffrey.flamegraph.proto.Level
                     * @instance
                     */
                    Level.prototype.frames = $util.emptyArray;

                    /**
                     * Creates a new Level instance using the specified properties.
                     * @function create
                     * @memberof pbouda.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.ILevel=} [properties] Properties to set
                     * @returns {pbouda.jeffrey.flamegraph.proto.Level} Level instance
                     */
                    Level.create = function create(properties) {
                        return new Level(properties);
                    };

                    /**
                     * Encodes the specified Level message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.Level.verify|verify} messages.
                     * @function encode
                     * @memberof pbouda.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.ILevel} message Level message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    Level.encode = function encode(message, writer) {
                        if (!writer)
                            writer = $Writer.create();
                        if (message.frames != null && message.frames.length)
                            for (let i = 0; i < message.frames.length; ++i)
                                $root.pbouda.jeffrey.flamegraph.proto.Frame.encode(message.frames[i], writer.uint32(/* id 1, wireType 2 =*/10).fork()).ldelim();
                        return writer;
                    };

                    /**
                     * Encodes the specified Level message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.Level.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.ILevel} message Level message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    Level.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer).ldelim();
                    };

                    /**
                     * Decodes a Level message from the specified reader or buffer.
                     * @function decode
                     * @memberof pbouda.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {pbouda.jeffrey.flamegraph.proto.Level} Level
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    Level.decode = function decode(reader, length, error) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.pbouda.jeffrey.flamegraph.proto.Level();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    if (!(message.frames && message.frames.length))
                                        message.frames = [];
                                    message.frames.push($root.pbouda.jeffrey.flamegraph.proto.Frame.decode(reader, reader.uint32()));
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes a Level message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {pbouda.jeffrey.flamegraph.proto.Level} Level
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    Level.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a Level message.
                     * @function verify
                     * @memberof pbouda.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    Level.verify = function verify(message) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (message.frames != null && message.hasOwnProperty("frames")) {
                            if (!Array.isArray(message.frames))
                                return "frames: array expected";
                            for (let i = 0; i < message.frames.length; ++i) {
                                let error = $root.pbouda.jeffrey.flamegraph.proto.Frame.verify(message.frames[i]);
                                if (error)
                                    return "frames." + error;
                            }
                        }
                        return null;
                    };

                    /**
                     * Creates a Level message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {pbouda.jeffrey.flamegraph.proto.Level} Level
                     */
                    Level.fromObject = function fromObject(object) {
                        if (object instanceof $root.pbouda.jeffrey.flamegraph.proto.Level)
                            return object;
                        let message = new $root.pbouda.jeffrey.flamegraph.proto.Level();
                        if (object.frames) {
                            if (!Array.isArray(object.frames))
                                throw TypeError(".pbouda.jeffrey.flamegraph.proto.Level.frames: array expected");
                            message.frames = [];
                            for (let i = 0; i < object.frames.length; ++i) {
                                if (typeof object.frames[i] !== "object")
                                    throw TypeError(".pbouda.jeffrey.flamegraph.proto.Level.frames: object expected");
                                message.frames[i] = $root.pbouda.jeffrey.flamegraph.proto.Frame.fromObject(object.frames[i]);
                            }
                        }
                        return message;
                    };

                    /**
                     * Creates a plain object from a Level message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.Level} message Level
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    Level.toObject = function toObject(message, options) {
                        if (!options)
                            options = {};
                        let object = {};
                        if (options.arrays || options.defaults)
                            object.frames = [];
                        if (message.frames && message.frames.length) {
                            object.frames = [];
                            for (let j = 0; j < message.frames.length; ++j)
                                object.frames[j] = $root.pbouda.jeffrey.flamegraph.proto.Frame.toObject(message.frames[j], options);
                        }
                        return object;
                    };

                    /**
                     * Converts this Level to JSON.
                     * @function toJSON
                     * @memberof pbouda.jeffrey.flamegraph.proto.Level
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    Level.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for Level
                     * @function getTypeUrl
                     * @memberof pbouda.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    Level.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/pbouda.jeffrey.flamegraph.proto.Level";
                    };

                    return Level;
                })();

                proto.Frame = (function() {

                    /**
                     * Properties of a Frame.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @interface IFrame
                     * @property {number|Long|null} [leftSamples] Frame leftSamples
                     * @property {number|Long|null} [totalSamples] Frame totalSamples
                     * @property {number|null} [titleIndex] Frame titleIndex
                     * @property {pbouda.jeffrey.flamegraph.proto.FrameType|null} [type] Frame type
                     * @property {number|Long|null} [leftWeight] Frame leftWeight
                     * @property {number|Long|null} [totalWeight] Frame totalWeight
                     * @property {number|Long|null} [selfSamples] Frame selfSamples
                     * @property {pbouda.jeffrey.flamegraph.proto.IFramePosition|null} [position] Frame position
                     * @property {pbouda.jeffrey.flamegraph.proto.IFrameSampleTypes|null} [sampleTypes] Frame sampleTypes
                     * @property {pbouda.jeffrey.flamegraph.proto.IDiffDetails|null} [diffDetails] Frame diffDetails
                     * @property {boolean|null} [beforeMarker] Frame beforeMarker
                     */

                    /**
                     * Constructs a new Frame.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @classdesc Represents a Frame.
                     * @implements IFrame
                     * @constructor
                     * @param {pbouda.jeffrey.flamegraph.proto.IFrame=} [properties] Properties to set
                     */
                    function Frame(properties) {
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null)
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * Frame leftSamples.
                     * @member {number|Long} leftSamples
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.leftSamples = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Frame totalSamples.
                     * @member {number|Long} totalSamples
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.totalSamples = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Frame titleIndex.
                     * @member {number} titleIndex
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.titleIndex = 0;

                    /**
                     * Frame type.
                     * @member {pbouda.jeffrey.flamegraph.proto.FrameType} type
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.type = 0;

                    /**
                     * Frame leftWeight.
                     * @member {number|Long} leftWeight
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.leftWeight = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Frame totalWeight.
                     * @member {number|Long} totalWeight
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.totalWeight = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Frame selfSamples.
                     * @member {number|Long} selfSamples
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.selfSamples = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Frame position.
                     * @member {pbouda.jeffrey.flamegraph.proto.IFramePosition|null|undefined} position
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.position = null;

                    /**
                     * Frame sampleTypes.
                     * @member {pbouda.jeffrey.flamegraph.proto.IFrameSampleTypes|null|undefined} sampleTypes
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.sampleTypes = null;

                    /**
                     * Frame diffDetails.
                     * @member {pbouda.jeffrey.flamegraph.proto.IDiffDetails|null|undefined} diffDetails
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.diffDetails = null;

                    /**
                     * Frame beforeMarker.
                     * @member {boolean} beforeMarker
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.beforeMarker = false;

                    /**
                     * Creates a new Frame instance using the specified properties.
                     * @function create
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IFrame=} [properties] Properties to set
                     * @returns {pbouda.jeffrey.flamegraph.proto.Frame} Frame instance
                     */
                    Frame.create = function create(properties) {
                        return new Frame(properties);
                    };

                    /**
                     * Encodes the specified Frame message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.Frame.verify|verify} messages.
                     * @function encode
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IFrame} message Frame message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    Frame.encode = function encode(message, writer) {
                        if (!writer)
                            writer = $Writer.create();
                        if (message.leftSamples != null && Object.hasOwnProperty.call(message, "leftSamples"))
                            writer.uint32(/* id 1, wireType 0 =*/8).int64(message.leftSamples);
                        if (message.totalSamples != null && Object.hasOwnProperty.call(message, "totalSamples"))
                            writer.uint32(/* id 2, wireType 0 =*/16).int64(message.totalSamples);
                        if (message.titleIndex != null && Object.hasOwnProperty.call(message, "titleIndex"))
                            writer.uint32(/* id 3, wireType 0 =*/24).int32(message.titleIndex);
                        if (message.type != null && Object.hasOwnProperty.call(message, "type"))
                            writer.uint32(/* id 4, wireType 0 =*/32).int32(message.type);
                        if (message.leftWeight != null && Object.hasOwnProperty.call(message, "leftWeight"))
                            writer.uint32(/* id 5, wireType 0 =*/40).int64(message.leftWeight);
                        if (message.totalWeight != null && Object.hasOwnProperty.call(message, "totalWeight"))
                            writer.uint32(/* id 6, wireType 0 =*/48).int64(message.totalWeight);
                        if (message.selfSamples != null && Object.hasOwnProperty.call(message, "selfSamples"))
                            writer.uint32(/* id 7, wireType 0 =*/56).int64(message.selfSamples);
                        if (message.position != null && Object.hasOwnProperty.call(message, "position"))
                            $root.pbouda.jeffrey.flamegraph.proto.FramePosition.encode(message.position, writer.uint32(/* id 8, wireType 2 =*/66).fork()).ldelim();
                        if (message.sampleTypes != null && Object.hasOwnProperty.call(message, "sampleTypes"))
                            $root.pbouda.jeffrey.flamegraph.proto.FrameSampleTypes.encode(message.sampleTypes, writer.uint32(/* id 9, wireType 2 =*/74).fork()).ldelim();
                        if (message.diffDetails != null && Object.hasOwnProperty.call(message, "diffDetails"))
                            $root.pbouda.jeffrey.flamegraph.proto.DiffDetails.encode(message.diffDetails, writer.uint32(/* id 10, wireType 2 =*/82).fork()).ldelim();
                        if (message.beforeMarker != null && Object.hasOwnProperty.call(message, "beforeMarker"))
                            writer.uint32(/* id 11, wireType 0 =*/88).bool(message.beforeMarker);
                        return writer;
                    };

                    /**
                     * Encodes the specified Frame message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.Frame.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IFrame} message Frame message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    Frame.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer).ldelim();
                    };

                    /**
                     * Decodes a Frame message from the specified reader or buffer.
                     * @function decode
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {pbouda.jeffrey.flamegraph.proto.Frame} Frame
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    Frame.decode = function decode(reader, length, error) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.pbouda.jeffrey.flamegraph.proto.Frame();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    message.leftSamples = reader.int64();
                                    break;
                                }
                            case 2: {
                                    message.totalSamples = reader.int64();
                                    break;
                                }
                            case 3: {
                                    message.titleIndex = reader.int32();
                                    break;
                                }
                            case 4: {
                                    message.type = reader.int32();
                                    break;
                                }
                            case 5: {
                                    message.leftWeight = reader.int64();
                                    break;
                                }
                            case 6: {
                                    message.totalWeight = reader.int64();
                                    break;
                                }
                            case 7: {
                                    message.selfSamples = reader.int64();
                                    break;
                                }
                            case 8: {
                                    message.position = $root.pbouda.jeffrey.flamegraph.proto.FramePosition.decode(reader, reader.uint32());
                                    break;
                                }
                            case 9: {
                                    message.sampleTypes = $root.pbouda.jeffrey.flamegraph.proto.FrameSampleTypes.decode(reader, reader.uint32());
                                    break;
                                }
                            case 10: {
                                    message.diffDetails = $root.pbouda.jeffrey.flamegraph.proto.DiffDetails.decode(reader, reader.uint32());
                                    break;
                                }
                            case 11: {
                                    message.beforeMarker = reader.bool();
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes a Frame message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {pbouda.jeffrey.flamegraph.proto.Frame} Frame
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    Frame.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a Frame message.
                     * @function verify
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    Frame.verify = function verify(message) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (message.leftSamples != null && message.hasOwnProperty("leftSamples"))
                            if (!$util.isInteger(message.leftSamples) && !(message.leftSamples && $util.isInteger(message.leftSamples.low) && $util.isInteger(message.leftSamples.high)))
                                return "leftSamples: integer|Long expected";
                        if (message.totalSamples != null && message.hasOwnProperty("totalSamples"))
                            if (!$util.isInteger(message.totalSamples) && !(message.totalSamples && $util.isInteger(message.totalSamples.low) && $util.isInteger(message.totalSamples.high)))
                                return "totalSamples: integer|Long expected";
                        if (message.titleIndex != null && message.hasOwnProperty("titleIndex"))
                            if (!$util.isInteger(message.titleIndex))
                                return "titleIndex: integer expected";
                        if (message.type != null && message.hasOwnProperty("type"))
                            switch (message.type) {
                            default:
                                return "type: enum value expected";
                            case 0:
                            case 1:
                            case 2:
                            case 3:
                            case 4:
                            case 5:
                            case 6:
                            case 7:
                            case 8:
                            case 9:
                            case 10:
                            case 11:
                            case 12:
                            case 13:
                            case 14:
                                break;
                            }
                        if (message.leftWeight != null && message.hasOwnProperty("leftWeight"))
                            if (!$util.isInteger(message.leftWeight) && !(message.leftWeight && $util.isInteger(message.leftWeight.low) && $util.isInteger(message.leftWeight.high)))
                                return "leftWeight: integer|Long expected";
                        if (message.totalWeight != null && message.hasOwnProperty("totalWeight"))
                            if (!$util.isInteger(message.totalWeight) && !(message.totalWeight && $util.isInteger(message.totalWeight.low) && $util.isInteger(message.totalWeight.high)))
                                return "totalWeight: integer|Long expected";
                        if (message.selfSamples != null && message.hasOwnProperty("selfSamples"))
                            if (!$util.isInteger(message.selfSamples) && !(message.selfSamples && $util.isInteger(message.selfSamples.low) && $util.isInteger(message.selfSamples.high)))
                                return "selfSamples: integer|Long expected";
                        if (message.position != null && message.hasOwnProperty("position")) {
                            let error = $root.pbouda.jeffrey.flamegraph.proto.FramePosition.verify(message.position);
                            if (error)
                                return "position." + error;
                        }
                        if (message.sampleTypes != null && message.hasOwnProperty("sampleTypes")) {
                            let error = $root.pbouda.jeffrey.flamegraph.proto.FrameSampleTypes.verify(message.sampleTypes);
                            if (error)
                                return "sampleTypes." + error;
                        }
                        if (message.diffDetails != null && message.hasOwnProperty("diffDetails")) {
                            let error = $root.pbouda.jeffrey.flamegraph.proto.DiffDetails.verify(message.diffDetails);
                            if (error)
                                return "diffDetails." + error;
                        }
                        if (message.beforeMarker != null && message.hasOwnProperty("beforeMarker"))
                            if (typeof message.beforeMarker !== "boolean")
                                return "beforeMarker: boolean expected";
                        return null;
                    };

                    /**
                     * Creates a Frame message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {pbouda.jeffrey.flamegraph.proto.Frame} Frame
                     */
                    Frame.fromObject = function fromObject(object) {
                        if (object instanceof $root.pbouda.jeffrey.flamegraph.proto.Frame)
                            return object;
                        let message = new $root.pbouda.jeffrey.flamegraph.proto.Frame();
                        if (object.leftSamples != null)
                            if ($util.Long)
                                (message.leftSamples = $util.Long.fromValue(object.leftSamples)).unsigned = false;
                            else if (typeof object.leftSamples === "string")
                                message.leftSamples = parseInt(object.leftSamples, 10);
                            else if (typeof object.leftSamples === "number")
                                message.leftSamples = object.leftSamples;
                            else if (typeof object.leftSamples === "object")
                                message.leftSamples = new $util.LongBits(object.leftSamples.low >>> 0, object.leftSamples.high >>> 0).toNumber();
                        if (object.totalSamples != null)
                            if ($util.Long)
                                (message.totalSamples = $util.Long.fromValue(object.totalSamples)).unsigned = false;
                            else if (typeof object.totalSamples === "string")
                                message.totalSamples = parseInt(object.totalSamples, 10);
                            else if (typeof object.totalSamples === "number")
                                message.totalSamples = object.totalSamples;
                            else if (typeof object.totalSamples === "object")
                                message.totalSamples = new $util.LongBits(object.totalSamples.low >>> 0, object.totalSamples.high >>> 0).toNumber();
                        if (object.titleIndex != null)
                            message.titleIndex = object.titleIndex | 0;
                        switch (object.type) {
                        default:
                            if (typeof object.type === "number") {
                                message.type = object.type;
                                break;
                            }
                            break;
                        case "FRAME_TYPE_UNKNOWN":
                        case 0:
                            message.type = 0;
                            break;
                        case "FRAME_TYPE_C1_COMPILED":
                        case 1:
                            message.type = 1;
                            break;
                        case "FRAME_TYPE_NATIVE":
                        case 2:
                            message.type = 2;
                            break;
                        case "FRAME_TYPE_CPP":
                        case 3:
                            message.type = 3;
                            break;
                        case "FRAME_TYPE_INTERPRETED":
                        case 4:
                            message.type = 4;
                            break;
                        case "FRAME_TYPE_JIT_COMPILED":
                        case 5:
                            message.type = 5;
                            break;
                        case "FRAME_TYPE_INLINED":
                        case 6:
                            message.type = 6;
                            break;
                        case "FRAME_TYPE_KERNEL":
                        case 7:
                            message.type = 7;
                            break;
                        case "FRAME_TYPE_THREAD_NAME_SYNTHETIC":
                        case 8:
                            message.type = 8;
                            break;
                        case "FRAME_TYPE_ALLOCATED_OBJECT_SYNTHETIC":
                        case 9:
                            message.type = 9;
                            break;
                        case "FRAME_TYPE_ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC":
                        case 10:
                            message.type = 10;
                            break;
                        case "FRAME_TYPE_ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC":
                        case 11:
                            message.type = 11;
                            break;
                        case "FRAME_TYPE_BLOCKING_OBJECT_SYNTHETIC":
                        case 12:
                            message.type = 12;
                            break;
                        case "FRAME_TYPE_LAMBDA_SYNTHETIC":
                        case 13:
                            message.type = 13;
                            break;
                        case "FRAME_TYPE_HIGHLIGHTED_WARNING":
                        case 14:
                            message.type = 14;
                            break;
                        }
                        if (object.leftWeight != null)
                            if ($util.Long)
                                (message.leftWeight = $util.Long.fromValue(object.leftWeight)).unsigned = false;
                            else if (typeof object.leftWeight === "string")
                                message.leftWeight = parseInt(object.leftWeight, 10);
                            else if (typeof object.leftWeight === "number")
                                message.leftWeight = object.leftWeight;
                            else if (typeof object.leftWeight === "object")
                                message.leftWeight = new $util.LongBits(object.leftWeight.low >>> 0, object.leftWeight.high >>> 0).toNumber();
                        if (object.totalWeight != null)
                            if ($util.Long)
                                (message.totalWeight = $util.Long.fromValue(object.totalWeight)).unsigned = false;
                            else if (typeof object.totalWeight === "string")
                                message.totalWeight = parseInt(object.totalWeight, 10);
                            else if (typeof object.totalWeight === "number")
                                message.totalWeight = object.totalWeight;
                            else if (typeof object.totalWeight === "object")
                                message.totalWeight = new $util.LongBits(object.totalWeight.low >>> 0, object.totalWeight.high >>> 0).toNumber();
                        if (object.selfSamples != null)
                            if ($util.Long)
                                (message.selfSamples = $util.Long.fromValue(object.selfSamples)).unsigned = false;
                            else if (typeof object.selfSamples === "string")
                                message.selfSamples = parseInt(object.selfSamples, 10);
                            else if (typeof object.selfSamples === "number")
                                message.selfSamples = object.selfSamples;
                            else if (typeof object.selfSamples === "object")
                                message.selfSamples = new $util.LongBits(object.selfSamples.low >>> 0, object.selfSamples.high >>> 0).toNumber();
                        if (object.position != null) {
                            if (typeof object.position !== "object")
                                throw TypeError(".pbouda.jeffrey.flamegraph.proto.Frame.position: object expected");
                            message.position = $root.pbouda.jeffrey.flamegraph.proto.FramePosition.fromObject(object.position);
                        }
                        if (object.sampleTypes != null) {
                            if (typeof object.sampleTypes !== "object")
                                throw TypeError(".pbouda.jeffrey.flamegraph.proto.Frame.sampleTypes: object expected");
                            message.sampleTypes = $root.pbouda.jeffrey.flamegraph.proto.FrameSampleTypes.fromObject(object.sampleTypes);
                        }
                        if (object.diffDetails != null) {
                            if (typeof object.diffDetails !== "object")
                                throw TypeError(".pbouda.jeffrey.flamegraph.proto.Frame.diffDetails: object expected");
                            message.diffDetails = $root.pbouda.jeffrey.flamegraph.proto.DiffDetails.fromObject(object.diffDetails);
                        }
                        if (object.beforeMarker != null)
                            message.beforeMarker = Boolean(object.beforeMarker);
                        return message;
                    };

                    /**
                     * Creates a plain object from a Frame message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.Frame} message Frame
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    Frame.toObject = function toObject(message, options) {
                        if (!options)
                            options = {};
                        let object = {};
                        if (options.defaults) {
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.leftSamples = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.leftSamples = options.longs === String ? "0" : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.totalSamples = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.totalSamples = options.longs === String ? "0" : 0;
                            object.titleIndex = 0;
                            object.type = options.enums === String ? "FRAME_TYPE_UNKNOWN" : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.leftWeight = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.leftWeight = options.longs === String ? "0" : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.totalWeight = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.totalWeight = options.longs === String ? "0" : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.selfSamples = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.selfSamples = options.longs === String ? "0" : 0;
                            object.position = null;
                            object.sampleTypes = null;
                            object.diffDetails = null;
                            object.beforeMarker = false;
                        }
                        if (message.leftSamples != null && message.hasOwnProperty("leftSamples"))
                            if (typeof message.leftSamples === "number")
                                object.leftSamples = options.longs === String ? String(message.leftSamples) : message.leftSamples;
                            else
                                object.leftSamples = options.longs === String ? $util.Long.prototype.toString.call(message.leftSamples) : options.longs === Number ? new $util.LongBits(message.leftSamples.low >>> 0, message.leftSamples.high >>> 0).toNumber() : message.leftSamples;
                        if (message.totalSamples != null && message.hasOwnProperty("totalSamples"))
                            if (typeof message.totalSamples === "number")
                                object.totalSamples = options.longs === String ? String(message.totalSamples) : message.totalSamples;
                            else
                                object.totalSamples = options.longs === String ? $util.Long.prototype.toString.call(message.totalSamples) : options.longs === Number ? new $util.LongBits(message.totalSamples.low >>> 0, message.totalSamples.high >>> 0).toNumber() : message.totalSamples;
                        if (message.titleIndex != null && message.hasOwnProperty("titleIndex"))
                            object.titleIndex = message.titleIndex;
                        if (message.type != null && message.hasOwnProperty("type"))
                            object.type = options.enums === String ? $root.pbouda.jeffrey.flamegraph.proto.FrameType[message.type] === undefined ? message.type : $root.pbouda.jeffrey.flamegraph.proto.FrameType[message.type] : message.type;
                        if (message.leftWeight != null && message.hasOwnProperty("leftWeight"))
                            if (typeof message.leftWeight === "number")
                                object.leftWeight = options.longs === String ? String(message.leftWeight) : message.leftWeight;
                            else
                                object.leftWeight = options.longs === String ? $util.Long.prototype.toString.call(message.leftWeight) : options.longs === Number ? new $util.LongBits(message.leftWeight.low >>> 0, message.leftWeight.high >>> 0).toNumber() : message.leftWeight;
                        if (message.totalWeight != null && message.hasOwnProperty("totalWeight"))
                            if (typeof message.totalWeight === "number")
                                object.totalWeight = options.longs === String ? String(message.totalWeight) : message.totalWeight;
                            else
                                object.totalWeight = options.longs === String ? $util.Long.prototype.toString.call(message.totalWeight) : options.longs === Number ? new $util.LongBits(message.totalWeight.low >>> 0, message.totalWeight.high >>> 0).toNumber() : message.totalWeight;
                        if (message.selfSamples != null && message.hasOwnProperty("selfSamples"))
                            if (typeof message.selfSamples === "number")
                                object.selfSamples = options.longs === String ? String(message.selfSamples) : message.selfSamples;
                            else
                                object.selfSamples = options.longs === String ? $util.Long.prototype.toString.call(message.selfSamples) : options.longs === Number ? new $util.LongBits(message.selfSamples.low >>> 0, message.selfSamples.high >>> 0).toNumber() : message.selfSamples;
                        if (message.position != null && message.hasOwnProperty("position"))
                            object.position = $root.pbouda.jeffrey.flamegraph.proto.FramePosition.toObject(message.position, options);
                        if (message.sampleTypes != null && message.hasOwnProperty("sampleTypes"))
                            object.sampleTypes = $root.pbouda.jeffrey.flamegraph.proto.FrameSampleTypes.toObject(message.sampleTypes, options);
                        if (message.diffDetails != null && message.hasOwnProperty("diffDetails"))
                            object.diffDetails = $root.pbouda.jeffrey.flamegraph.proto.DiffDetails.toObject(message.diffDetails, options);
                        if (message.beforeMarker != null && message.hasOwnProperty("beforeMarker"))
                            object.beforeMarker = message.beforeMarker;
                        return object;
                    };

                    /**
                     * Converts this Frame to JSON.
                     * @function toJSON
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    Frame.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for Frame
                     * @function getTypeUrl
                     * @memberof pbouda.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    Frame.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/pbouda.jeffrey.flamegraph.proto.Frame";
                    };

                    return Frame;
                })();

                /**
                 * FrameType enum.
                 * @name pbouda.jeffrey.flamegraph.proto.FrameType
                 * @enum {number}
                 * @property {number} FRAME_TYPE_UNKNOWN=0 FRAME_TYPE_UNKNOWN value
                 * @property {number} FRAME_TYPE_C1_COMPILED=1 FRAME_TYPE_C1_COMPILED value
                 * @property {number} FRAME_TYPE_NATIVE=2 FRAME_TYPE_NATIVE value
                 * @property {number} FRAME_TYPE_CPP=3 FRAME_TYPE_CPP value
                 * @property {number} FRAME_TYPE_INTERPRETED=4 FRAME_TYPE_INTERPRETED value
                 * @property {number} FRAME_TYPE_JIT_COMPILED=5 FRAME_TYPE_JIT_COMPILED value
                 * @property {number} FRAME_TYPE_INLINED=6 FRAME_TYPE_INLINED value
                 * @property {number} FRAME_TYPE_KERNEL=7 FRAME_TYPE_KERNEL value
                 * @property {number} FRAME_TYPE_THREAD_NAME_SYNTHETIC=8 FRAME_TYPE_THREAD_NAME_SYNTHETIC value
                 * @property {number} FRAME_TYPE_ALLOCATED_OBJECT_SYNTHETIC=9 FRAME_TYPE_ALLOCATED_OBJECT_SYNTHETIC value
                 * @property {number} FRAME_TYPE_ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC=10 FRAME_TYPE_ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC value
                 * @property {number} FRAME_TYPE_ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC=11 FRAME_TYPE_ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC value
                 * @property {number} FRAME_TYPE_BLOCKING_OBJECT_SYNTHETIC=12 FRAME_TYPE_BLOCKING_OBJECT_SYNTHETIC value
                 * @property {number} FRAME_TYPE_LAMBDA_SYNTHETIC=13 FRAME_TYPE_LAMBDA_SYNTHETIC value
                 * @property {number} FRAME_TYPE_HIGHLIGHTED_WARNING=14 FRAME_TYPE_HIGHLIGHTED_WARNING value
                 */
                proto.FrameType = (function() {
                    const valuesById = {}, values = Object.create(valuesById);
                    values[valuesById[0] = "FRAME_TYPE_UNKNOWN"] = 0;
                    values[valuesById[1] = "FRAME_TYPE_C1_COMPILED"] = 1;
                    values[valuesById[2] = "FRAME_TYPE_NATIVE"] = 2;
                    values[valuesById[3] = "FRAME_TYPE_CPP"] = 3;
                    values[valuesById[4] = "FRAME_TYPE_INTERPRETED"] = 4;
                    values[valuesById[5] = "FRAME_TYPE_JIT_COMPILED"] = 5;
                    values[valuesById[6] = "FRAME_TYPE_INLINED"] = 6;
                    values[valuesById[7] = "FRAME_TYPE_KERNEL"] = 7;
                    values[valuesById[8] = "FRAME_TYPE_THREAD_NAME_SYNTHETIC"] = 8;
                    values[valuesById[9] = "FRAME_TYPE_ALLOCATED_OBJECT_SYNTHETIC"] = 9;
                    values[valuesById[10] = "FRAME_TYPE_ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC"] = 10;
                    values[valuesById[11] = "FRAME_TYPE_ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC"] = 11;
                    values[valuesById[12] = "FRAME_TYPE_BLOCKING_OBJECT_SYNTHETIC"] = 12;
                    values[valuesById[13] = "FRAME_TYPE_LAMBDA_SYNTHETIC"] = 13;
                    values[valuesById[14] = "FRAME_TYPE_HIGHLIGHTED_WARNING"] = 14;
                    return values;
                })();

                proto.FramePosition = (function() {

                    /**
                     * Properties of a FramePosition.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @interface IFramePosition
                     * @property {number|null} [bci] FramePosition bci
                     * @property {number|null} [line] FramePosition line
                     */

                    /**
                     * Constructs a new FramePosition.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @classdesc Represents a FramePosition.
                     * @implements IFramePosition
                     * @constructor
                     * @param {pbouda.jeffrey.flamegraph.proto.IFramePosition=} [properties] Properties to set
                     */
                    function FramePosition(properties) {
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null)
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * FramePosition bci.
                     * @member {number} bci
                     * @memberof pbouda.jeffrey.flamegraph.proto.FramePosition
                     * @instance
                     */
                    FramePosition.prototype.bci = 0;

                    /**
                     * FramePosition line.
                     * @member {number} line
                     * @memberof pbouda.jeffrey.flamegraph.proto.FramePosition
                     * @instance
                     */
                    FramePosition.prototype.line = 0;

                    /**
                     * Creates a new FramePosition instance using the specified properties.
                     * @function create
                     * @memberof pbouda.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IFramePosition=} [properties] Properties to set
                     * @returns {pbouda.jeffrey.flamegraph.proto.FramePosition} FramePosition instance
                     */
                    FramePosition.create = function create(properties) {
                        return new FramePosition(properties);
                    };

                    /**
                     * Encodes the specified FramePosition message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.FramePosition.verify|verify} messages.
                     * @function encode
                     * @memberof pbouda.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IFramePosition} message FramePosition message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    FramePosition.encode = function encode(message, writer) {
                        if (!writer)
                            writer = $Writer.create();
                        if (message.bci != null && Object.hasOwnProperty.call(message, "bci"))
                            writer.uint32(/* id 1, wireType 0 =*/8).int32(message.bci);
                        if (message.line != null && Object.hasOwnProperty.call(message, "line"))
                            writer.uint32(/* id 2, wireType 0 =*/16).int32(message.line);
                        return writer;
                    };

                    /**
                     * Encodes the specified FramePosition message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.FramePosition.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IFramePosition} message FramePosition message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    FramePosition.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer).ldelim();
                    };

                    /**
                     * Decodes a FramePosition message from the specified reader or buffer.
                     * @function decode
                     * @memberof pbouda.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {pbouda.jeffrey.flamegraph.proto.FramePosition} FramePosition
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    FramePosition.decode = function decode(reader, length, error) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.pbouda.jeffrey.flamegraph.proto.FramePosition();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    message.bci = reader.int32();
                                    break;
                                }
                            case 2: {
                                    message.line = reader.int32();
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes a FramePosition message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {pbouda.jeffrey.flamegraph.proto.FramePosition} FramePosition
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    FramePosition.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a FramePosition message.
                     * @function verify
                     * @memberof pbouda.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    FramePosition.verify = function verify(message) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (message.bci != null && message.hasOwnProperty("bci"))
                            if (!$util.isInteger(message.bci))
                                return "bci: integer expected";
                        if (message.line != null && message.hasOwnProperty("line"))
                            if (!$util.isInteger(message.line))
                                return "line: integer expected";
                        return null;
                    };

                    /**
                     * Creates a FramePosition message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {pbouda.jeffrey.flamegraph.proto.FramePosition} FramePosition
                     */
                    FramePosition.fromObject = function fromObject(object) {
                        if (object instanceof $root.pbouda.jeffrey.flamegraph.proto.FramePosition)
                            return object;
                        let message = new $root.pbouda.jeffrey.flamegraph.proto.FramePosition();
                        if (object.bci != null)
                            message.bci = object.bci | 0;
                        if (object.line != null)
                            message.line = object.line | 0;
                        return message;
                    };

                    /**
                     * Creates a plain object from a FramePosition message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.FramePosition} message FramePosition
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    FramePosition.toObject = function toObject(message, options) {
                        if (!options)
                            options = {};
                        let object = {};
                        if (options.defaults) {
                            object.bci = 0;
                            object.line = 0;
                        }
                        if (message.bci != null && message.hasOwnProperty("bci"))
                            object.bci = message.bci;
                        if (message.line != null && message.hasOwnProperty("line"))
                            object.line = message.line;
                        return object;
                    };

                    /**
                     * Converts this FramePosition to JSON.
                     * @function toJSON
                     * @memberof pbouda.jeffrey.flamegraph.proto.FramePosition
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    FramePosition.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for FramePosition
                     * @function getTypeUrl
                     * @memberof pbouda.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    FramePosition.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/pbouda.jeffrey.flamegraph.proto.FramePosition";
                    };

                    return FramePosition;
                })();

                proto.FrameSampleTypes = (function() {

                    /**
                     * Properties of a FrameSampleTypes.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @interface IFrameSampleTypes
                     * @property {number|Long|null} [inlined] FrameSampleTypes inlined
                     * @property {number|Long|null} [c1] FrameSampleTypes c1
                     * @property {number|Long|null} [interpret] FrameSampleTypes interpret
                     * @property {number|Long|null} [jit] FrameSampleTypes jit
                     */

                    /**
                     * Constructs a new FrameSampleTypes.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @classdesc Represents a FrameSampleTypes.
                     * @implements IFrameSampleTypes
                     * @constructor
                     * @param {pbouda.jeffrey.flamegraph.proto.IFrameSampleTypes=} [properties] Properties to set
                     */
                    function FrameSampleTypes(properties) {
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null)
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * FrameSampleTypes inlined.
                     * @member {number|Long} inlined
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @instance
                     */
                    FrameSampleTypes.prototype.inlined = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * FrameSampleTypes c1.
                     * @member {number|Long} c1
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @instance
                     */
                    FrameSampleTypes.prototype.c1 = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * FrameSampleTypes interpret.
                     * @member {number|Long} interpret
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @instance
                     */
                    FrameSampleTypes.prototype.interpret = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * FrameSampleTypes jit.
                     * @member {number|Long} jit
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @instance
                     */
                    FrameSampleTypes.prototype.jit = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Creates a new FrameSampleTypes instance using the specified properties.
                     * @function create
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IFrameSampleTypes=} [properties] Properties to set
                     * @returns {pbouda.jeffrey.flamegraph.proto.FrameSampleTypes} FrameSampleTypes instance
                     */
                    FrameSampleTypes.create = function create(properties) {
                        return new FrameSampleTypes(properties);
                    };

                    /**
                     * Encodes the specified FrameSampleTypes message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.FrameSampleTypes.verify|verify} messages.
                     * @function encode
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IFrameSampleTypes} message FrameSampleTypes message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    FrameSampleTypes.encode = function encode(message, writer) {
                        if (!writer)
                            writer = $Writer.create();
                        if (message.inlined != null && Object.hasOwnProperty.call(message, "inlined"))
                            writer.uint32(/* id 1, wireType 0 =*/8).int64(message.inlined);
                        if (message.c1 != null && Object.hasOwnProperty.call(message, "c1"))
                            writer.uint32(/* id 2, wireType 0 =*/16).int64(message.c1);
                        if (message.interpret != null && Object.hasOwnProperty.call(message, "interpret"))
                            writer.uint32(/* id 3, wireType 0 =*/24).int64(message.interpret);
                        if (message.jit != null && Object.hasOwnProperty.call(message, "jit"))
                            writer.uint32(/* id 4, wireType 0 =*/32).int64(message.jit);
                        return writer;
                    };

                    /**
                     * Encodes the specified FrameSampleTypes message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.FrameSampleTypes.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IFrameSampleTypes} message FrameSampleTypes message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    FrameSampleTypes.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer).ldelim();
                    };

                    /**
                     * Decodes a FrameSampleTypes message from the specified reader or buffer.
                     * @function decode
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {pbouda.jeffrey.flamegraph.proto.FrameSampleTypes} FrameSampleTypes
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    FrameSampleTypes.decode = function decode(reader, length, error) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.pbouda.jeffrey.flamegraph.proto.FrameSampleTypes();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    message.inlined = reader.int64();
                                    break;
                                }
                            case 2: {
                                    message.c1 = reader.int64();
                                    break;
                                }
                            case 3: {
                                    message.interpret = reader.int64();
                                    break;
                                }
                            case 4: {
                                    message.jit = reader.int64();
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes a FrameSampleTypes message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {pbouda.jeffrey.flamegraph.proto.FrameSampleTypes} FrameSampleTypes
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    FrameSampleTypes.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a FrameSampleTypes message.
                     * @function verify
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    FrameSampleTypes.verify = function verify(message) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (message.inlined != null && message.hasOwnProperty("inlined"))
                            if (!$util.isInteger(message.inlined) && !(message.inlined && $util.isInteger(message.inlined.low) && $util.isInteger(message.inlined.high)))
                                return "inlined: integer|Long expected";
                        if (message.c1 != null && message.hasOwnProperty("c1"))
                            if (!$util.isInteger(message.c1) && !(message.c1 && $util.isInteger(message.c1.low) && $util.isInteger(message.c1.high)))
                                return "c1: integer|Long expected";
                        if (message.interpret != null && message.hasOwnProperty("interpret"))
                            if (!$util.isInteger(message.interpret) && !(message.interpret && $util.isInteger(message.interpret.low) && $util.isInteger(message.interpret.high)))
                                return "interpret: integer|Long expected";
                        if (message.jit != null && message.hasOwnProperty("jit"))
                            if (!$util.isInteger(message.jit) && !(message.jit && $util.isInteger(message.jit.low) && $util.isInteger(message.jit.high)))
                                return "jit: integer|Long expected";
                        return null;
                    };

                    /**
                     * Creates a FrameSampleTypes message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {pbouda.jeffrey.flamegraph.proto.FrameSampleTypes} FrameSampleTypes
                     */
                    FrameSampleTypes.fromObject = function fromObject(object) {
                        if (object instanceof $root.pbouda.jeffrey.flamegraph.proto.FrameSampleTypes)
                            return object;
                        let message = new $root.pbouda.jeffrey.flamegraph.proto.FrameSampleTypes();
                        if (object.inlined != null)
                            if ($util.Long)
                                (message.inlined = $util.Long.fromValue(object.inlined)).unsigned = false;
                            else if (typeof object.inlined === "string")
                                message.inlined = parseInt(object.inlined, 10);
                            else if (typeof object.inlined === "number")
                                message.inlined = object.inlined;
                            else if (typeof object.inlined === "object")
                                message.inlined = new $util.LongBits(object.inlined.low >>> 0, object.inlined.high >>> 0).toNumber();
                        if (object.c1 != null)
                            if ($util.Long)
                                (message.c1 = $util.Long.fromValue(object.c1)).unsigned = false;
                            else if (typeof object.c1 === "string")
                                message.c1 = parseInt(object.c1, 10);
                            else if (typeof object.c1 === "number")
                                message.c1 = object.c1;
                            else if (typeof object.c1 === "object")
                                message.c1 = new $util.LongBits(object.c1.low >>> 0, object.c1.high >>> 0).toNumber();
                        if (object.interpret != null)
                            if ($util.Long)
                                (message.interpret = $util.Long.fromValue(object.interpret)).unsigned = false;
                            else if (typeof object.interpret === "string")
                                message.interpret = parseInt(object.interpret, 10);
                            else if (typeof object.interpret === "number")
                                message.interpret = object.interpret;
                            else if (typeof object.interpret === "object")
                                message.interpret = new $util.LongBits(object.interpret.low >>> 0, object.interpret.high >>> 0).toNumber();
                        if (object.jit != null)
                            if ($util.Long)
                                (message.jit = $util.Long.fromValue(object.jit)).unsigned = false;
                            else if (typeof object.jit === "string")
                                message.jit = parseInt(object.jit, 10);
                            else if (typeof object.jit === "number")
                                message.jit = object.jit;
                            else if (typeof object.jit === "object")
                                message.jit = new $util.LongBits(object.jit.low >>> 0, object.jit.high >>> 0).toNumber();
                        return message;
                    };

                    /**
                     * Creates a plain object from a FrameSampleTypes message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.FrameSampleTypes} message FrameSampleTypes
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    FrameSampleTypes.toObject = function toObject(message, options) {
                        if (!options)
                            options = {};
                        let object = {};
                        if (options.defaults) {
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.inlined = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.inlined = options.longs === String ? "0" : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.c1 = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.c1 = options.longs === String ? "0" : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.interpret = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.interpret = options.longs === String ? "0" : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.jit = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.jit = options.longs === String ? "0" : 0;
                        }
                        if (message.inlined != null && message.hasOwnProperty("inlined"))
                            if (typeof message.inlined === "number")
                                object.inlined = options.longs === String ? String(message.inlined) : message.inlined;
                            else
                                object.inlined = options.longs === String ? $util.Long.prototype.toString.call(message.inlined) : options.longs === Number ? new $util.LongBits(message.inlined.low >>> 0, message.inlined.high >>> 0).toNumber() : message.inlined;
                        if (message.c1 != null && message.hasOwnProperty("c1"))
                            if (typeof message.c1 === "number")
                                object.c1 = options.longs === String ? String(message.c1) : message.c1;
                            else
                                object.c1 = options.longs === String ? $util.Long.prototype.toString.call(message.c1) : options.longs === Number ? new $util.LongBits(message.c1.low >>> 0, message.c1.high >>> 0).toNumber() : message.c1;
                        if (message.interpret != null && message.hasOwnProperty("interpret"))
                            if (typeof message.interpret === "number")
                                object.interpret = options.longs === String ? String(message.interpret) : message.interpret;
                            else
                                object.interpret = options.longs === String ? $util.Long.prototype.toString.call(message.interpret) : options.longs === Number ? new $util.LongBits(message.interpret.low >>> 0, message.interpret.high >>> 0).toNumber() : message.interpret;
                        if (message.jit != null && message.hasOwnProperty("jit"))
                            if (typeof message.jit === "number")
                                object.jit = options.longs === String ? String(message.jit) : message.jit;
                            else
                                object.jit = options.longs === String ? $util.Long.prototype.toString.call(message.jit) : options.longs === Number ? new $util.LongBits(message.jit.low >>> 0, message.jit.high >>> 0).toNumber() : message.jit;
                        return object;
                    };

                    /**
                     * Converts this FrameSampleTypes to JSON.
                     * @function toJSON
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    FrameSampleTypes.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for FrameSampleTypes
                     * @function getTypeUrl
                     * @memberof pbouda.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    FrameSampleTypes.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/pbouda.jeffrey.flamegraph.proto.FrameSampleTypes";
                    };

                    return FrameSampleTypes;
                })();

                proto.DiffDetails = (function() {

                    /**
                     * Properties of a DiffDetails.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @interface IDiffDetails
                     * @property {number|Long|null} [samples] DiffDetails samples
                     * @property {number|Long|null} [weight] DiffDetails weight
                     * @property {number|null} [percentSamples] DiffDetails percentSamples
                     * @property {number|null} [percentWeight] DiffDetails percentWeight
                     */

                    /**
                     * Constructs a new DiffDetails.
                     * @memberof pbouda.jeffrey.flamegraph.proto
                     * @classdesc Represents a DiffDetails.
                     * @implements IDiffDetails
                     * @constructor
                     * @param {pbouda.jeffrey.flamegraph.proto.IDiffDetails=} [properties] Properties to set
                     */
                    function DiffDetails(properties) {
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null)
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * DiffDetails samples.
                     * @member {number|Long} samples
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @instance
                     */
                    DiffDetails.prototype.samples = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * DiffDetails weight.
                     * @member {number|Long} weight
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @instance
                     */
                    DiffDetails.prototype.weight = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * DiffDetails percentSamples.
                     * @member {number} percentSamples
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @instance
                     */
                    DiffDetails.prototype.percentSamples = 0;

                    /**
                     * DiffDetails percentWeight.
                     * @member {number} percentWeight
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @instance
                     */
                    DiffDetails.prototype.percentWeight = 0;

                    /**
                     * Creates a new DiffDetails instance using the specified properties.
                     * @function create
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IDiffDetails=} [properties] Properties to set
                     * @returns {pbouda.jeffrey.flamegraph.proto.DiffDetails} DiffDetails instance
                     */
                    DiffDetails.create = function create(properties) {
                        return new DiffDetails(properties);
                    };

                    /**
                     * Encodes the specified DiffDetails message. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.DiffDetails.verify|verify} messages.
                     * @function encode
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IDiffDetails} message DiffDetails message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    DiffDetails.encode = function encode(message, writer) {
                        if (!writer)
                            writer = $Writer.create();
                        if (message.samples != null && Object.hasOwnProperty.call(message, "samples"))
                            writer.uint32(/* id 1, wireType 0 =*/8).int64(message.samples);
                        if (message.weight != null && Object.hasOwnProperty.call(message, "weight"))
                            writer.uint32(/* id 2, wireType 0 =*/16).int64(message.weight);
                        if (message.percentSamples != null && Object.hasOwnProperty.call(message, "percentSamples"))
                            writer.uint32(/* id 3, wireType 5 =*/29).float(message.percentSamples);
                        if (message.percentWeight != null && Object.hasOwnProperty.call(message, "percentWeight"))
                            writer.uint32(/* id 4, wireType 5 =*/37).float(message.percentWeight);
                        return writer;
                    };

                    /**
                     * Encodes the specified DiffDetails message, length delimited. Does not implicitly {@link pbouda.jeffrey.flamegraph.proto.DiffDetails.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.IDiffDetails} message DiffDetails message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    DiffDetails.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer).ldelim();
                    };

                    /**
                     * Decodes a DiffDetails message from the specified reader or buffer.
                     * @function decode
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {pbouda.jeffrey.flamegraph.proto.DiffDetails} DiffDetails
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    DiffDetails.decode = function decode(reader, length, error) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.pbouda.jeffrey.flamegraph.proto.DiffDetails();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    message.samples = reader.int64();
                                    break;
                                }
                            case 2: {
                                    message.weight = reader.int64();
                                    break;
                                }
                            case 3: {
                                    message.percentSamples = reader.float();
                                    break;
                                }
                            case 4: {
                                    message.percentWeight = reader.float();
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes a DiffDetails message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {pbouda.jeffrey.flamegraph.proto.DiffDetails} DiffDetails
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    DiffDetails.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a DiffDetails message.
                     * @function verify
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    DiffDetails.verify = function verify(message) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (message.samples != null && message.hasOwnProperty("samples"))
                            if (!$util.isInteger(message.samples) && !(message.samples && $util.isInteger(message.samples.low) && $util.isInteger(message.samples.high)))
                                return "samples: integer|Long expected";
                        if (message.weight != null && message.hasOwnProperty("weight"))
                            if (!$util.isInteger(message.weight) && !(message.weight && $util.isInteger(message.weight.low) && $util.isInteger(message.weight.high)))
                                return "weight: integer|Long expected";
                        if (message.percentSamples != null && message.hasOwnProperty("percentSamples"))
                            if (typeof message.percentSamples !== "number")
                                return "percentSamples: number expected";
                        if (message.percentWeight != null && message.hasOwnProperty("percentWeight"))
                            if (typeof message.percentWeight !== "number")
                                return "percentWeight: number expected";
                        return null;
                    };

                    /**
                     * Creates a DiffDetails message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {pbouda.jeffrey.flamegraph.proto.DiffDetails} DiffDetails
                     */
                    DiffDetails.fromObject = function fromObject(object) {
                        if (object instanceof $root.pbouda.jeffrey.flamegraph.proto.DiffDetails)
                            return object;
                        let message = new $root.pbouda.jeffrey.flamegraph.proto.DiffDetails();
                        if (object.samples != null)
                            if ($util.Long)
                                (message.samples = $util.Long.fromValue(object.samples)).unsigned = false;
                            else if (typeof object.samples === "string")
                                message.samples = parseInt(object.samples, 10);
                            else if (typeof object.samples === "number")
                                message.samples = object.samples;
                            else if (typeof object.samples === "object")
                                message.samples = new $util.LongBits(object.samples.low >>> 0, object.samples.high >>> 0).toNumber();
                        if (object.weight != null)
                            if ($util.Long)
                                (message.weight = $util.Long.fromValue(object.weight)).unsigned = false;
                            else if (typeof object.weight === "string")
                                message.weight = parseInt(object.weight, 10);
                            else if (typeof object.weight === "number")
                                message.weight = object.weight;
                            else if (typeof object.weight === "object")
                                message.weight = new $util.LongBits(object.weight.low >>> 0, object.weight.high >>> 0).toNumber();
                        if (object.percentSamples != null)
                            message.percentSamples = Number(object.percentSamples);
                        if (object.percentWeight != null)
                            message.percentWeight = Number(object.percentWeight);
                        return message;
                    };

                    /**
                     * Creates a plain object from a DiffDetails message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {pbouda.jeffrey.flamegraph.proto.DiffDetails} message DiffDetails
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    DiffDetails.toObject = function toObject(message, options) {
                        if (!options)
                            options = {};
                        let object = {};
                        if (options.defaults) {
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.samples = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.samples = options.longs === String ? "0" : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.weight = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : long;
                            } else
                                object.weight = options.longs === String ? "0" : 0;
                            object.percentSamples = 0;
                            object.percentWeight = 0;
                        }
                        if (message.samples != null && message.hasOwnProperty("samples"))
                            if (typeof message.samples === "number")
                                object.samples = options.longs === String ? String(message.samples) : message.samples;
                            else
                                object.samples = options.longs === String ? $util.Long.prototype.toString.call(message.samples) : options.longs === Number ? new $util.LongBits(message.samples.low >>> 0, message.samples.high >>> 0).toNumber() : message.samples;
                        if (message.weight != null && message.hasOwnProperty("weight"))
                            if (typeof message.weight === "number")
                                object.weight = options.longs === String ? String(message.weight) : message.weight;
                            else
                                object.weight = options.longs === String ? $util.Long.prototype.toString.call(message.weight) : options.longs === Number ? new $util.LongBits(message.weight.low >>> 0, message.weight.high >>> 0).toNumber() : message.weight;
                        if (message.percentSamples != null && message.hasOwnProperty("percentSamples"))
                            object.percentSamples = options.json && !isFinite(message.percentSamples) ? String(message.percentSamples) : message.percentSamples;
                        if (message.percentWeight != null && message.hasOwnProperty("percentWeight"))
                            object.percentWeight = options.json && !isFinite(message.percentWeight) ? String(message.percentWeight) : message.percentWeight;
                        return object;
                    };

                    /**
                     * Converts this DiffDetails to JSON.
                     * @function toJSON
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    DiffDetails.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for DiffDetails
                     * @function getTypeUrl
                     * @memberof pbouda.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    DiffDetails.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/pbouda.jeffrey.flamegraph.proto.DiffDetails";
                    };

                    return DiffDetails;
                })();

                return proto;
            })();

            return flamegraph;
        })();

        return jeffrey;
    })();

    return pbouda;
})();

export { $root as default };
