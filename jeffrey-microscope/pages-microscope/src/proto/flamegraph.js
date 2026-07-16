/*eslint-disable block-scoped-var, id-length, no-control-regex, no-magic-numbers, no-mixed-operators, no-prototype-builtins, no-redeclare, no-shadow, no-var, sort-vars, default-case, jsdoc/require-param*/
import $protobuf from "protobufjs/minimal.js";

// Common aliases
const $Reader = $protobuf.Reader, $Writer = $protobuf.Writer, $util = $protobuf.util;
const $Object = $util.global.Object, $undefined = $util.global.undefined, $Error = $util.global.Error, $TypeError = $util.global.TypeError, $Array = $util.global.Array, $Number = $util.global.Number, $String = $util.global.String, $parseInt = $util.global.parseInt, $BigInt = $util.global.BigInt, $Boolean = $util.global.Boolean, $isFinite = $util.global.isFinite;

// Exported root namespace
const $root = $protobuf.roots["default"] || ($protobuf.roots["default"] = {});

export const cafe = $root.cafe = (() => {

    /**
     * Namespace cafe.
     * @exports cafe
     * @namespace
     */
    const cafe = {};

    cafe.jeffrey = (function() {

        /**
         * Namespace jeffrey.
         * @memberof cafe
         * @namespace
         */
        const jeffrey = {};

        jeffrey.flamegraph = (function() {

            /**
             * Namespace flamegraph.
             * @memberof cafe.jeffrey
             * @namespace
             */
            const flamegraph = {};

            flamegraph.proto = (function() {

                /**
                 * Namespace proto.
                 * @memberof cafe.jeffrey.flamegraph
                 * @namespace
                 */
                const proto = {};

                proto.GraphData = (function() {

                    /**
                     * Properties of a GraphData.
                     * @typedef {Object} cafe.jeffrey.flamegraph.proto.GraphData.$Properties
                     * @property {cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties|null} [flamegraph] GraphData flamegraph
                     * @property {cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties|null} [timeseries] GraphData timeseries
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */

                    /**
                     * Properties of a GraphData.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @interface IGraphData
                     * @augments cafe.jeffrey.flamegraph.proto.GraphData.$Properties
                     * @deprecated Use cafe.jeffrey.flamegraph.proto.GraphData.$Properties instead.
                     */

                    /**
                     * Shape of a GraphData.
                     * @typedef {cafe.jeffrey.flamegraph.proto.GraphData.$Properties} cafe.jeffrey.flamegraph.proto.GraphData.$Shape
                     */

                    /**
                     * Constructs a new GraphData.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @classdesc Represents a GraphData.
                     * @constructor
                     * @param {cafe.jeffrey.flamegraph.proto.GraphData.$Properties=} [properties] Properties to set
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */
                    const GraphData = function (properties) {
                        if (properties)
                            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    };

                    /**
                     * GraphData flamegraph.
                     * @member {cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties|null|undefined} flamegraph
                     * @memberof cafe.jeffrey.flamegraph.proto.GraphData
                     * @instance
                     */
                    GraphData.prototype.flamegraph = null;

                    /**
                     * GraphData timeseries.
                     * @member {cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties|null|undefined} timeseries
                     * @memberof cafe.jeffrey.flamegraph.proto.GraphData
                     * @instance
                     */
                    GraphData.prototype.timeseries = null;

                    /**
                     * Creates a new GraphData instance using the specified properties.
                     * @function create
                     * @memberof cafe.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.GraphData.$Properties=} [properties] Properties to set
                     * @returns {cafe.jeffrey.flamegraph.proto.GraphData} GraphData instance
                     * @type {{
                     *   (properties: cafe.jeffrey.flamegraph.proto.GraphData.$Shape): cafe.jeffrey.flamegraph.proto.GraphData & cafe.jeffrey.flamegraph.proto.GraphData.$Shape;
                     *   (properties?: cafe.jeffrey.flamegraph.proto.GraphData.$Properties): cafe.jeffrey.flamegraph.proto.GraphData;
                     * }}
                     */
                    GraphData.create = function(properties) {
                        return new GraphData(properties);
                    };

                    /**
                     * Encodes the specified GraphData message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.GraphData.verify|verify} messages.
                     * @function encode
                     * @memberof cafe.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.GraphData.$Properties} message GraphData message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    GraphData.encode = function (message, writer, _depth) {
                        if (!writer)
                            writer = $Writer.create();
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        if (message.flamegraph != null && $Object.hasOwnProperty.call(message, "flamegraph"))
                            $root.cafe.jeffrey.flamegraph.proto.FlamegraphData.encode(message.flamegraph, writer.uint32(/* id 1, wireType 2 =*/10).fork(), _depth + 1).ldelim();
                        if (message.timeseries != null && $Object.hasOwnProperty.call(message, "timeseries"))
                            $root.cafe.jeffrey.flamegraph.proto.TimeseriesData.encode(message.timeseries, writer.uint32(/* id 2, wireType 2 =*/18).fork(), _depth + 1).ldelim();
                        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
                            for (let i = 0; i < message.$unknowns.length; ++i)
                                writer.raw(message.$unknowns[i]);
                        return writer;
                    };

                    /**
                     * Encodes the specified GraphData message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.GraphData.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.GraphData.$Properties} message GraphData message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    GraphData.encodeDelimited = function(message, writer) {
                        return this.encode(message, (writer || $Writer.create()).fork()).ldelim();
                    };

                    /**
                     * Decodes a GraphData message from the specified reader or buffer.
                     * @function decode
                     * @memberof cafe.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.GraphData & cafe.jeffrey.flamegraph.proto.GraphData.$Shape} GraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    GraphData.decode = function (reader, length, _end, _depth, _target) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $Reader.recursionLimit)
                            throw $Error("max depth exceeded");
                        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.cafe.jeffrey.flamegraph.proto.GraphData(), value;
                        while (reader.pos < end) {
                            let start = reader.pos;
                            let tag = reader.tag();
                            if (tag === _end) {
                                _end = $undefined;
                                break;
                            }
                            let wireType = tag & 7;
                            switch (tag >>>= 3) {
                            case 1: {
                                    if (wireType !== 2)
                                        break;
                                    message.flamegraph = $root.cafe.jeffrey.flamegraph.proto.FlamegraphData.decode(reader, reader.uint32(), $undefined, _depth + 1, message.flamegraph);
                                    continue;
                                }
                            case 2: {
                                    if (wireType !== 2)
                                        break;
                                    message.timeseries = $root.cafe.jeffrey.flamegraph.proto.TimeseriesData.decode(reader, reader.uint32(), $undefined, _depth + 1, message.timeseries);
                                    continue;
                                }
                            }
                            reader.skipType(wireType, _depth, tag);
                            if (!reader.discardUnknown) {
                                $util.makeProp(message, "$unknowns", false);
                                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
                            }
                        }
                        if (_end !== $undefined)
                            throw $Error("missing end group");
                        return message;
                    };

                    /**
                     * Decodes a GraphData message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.GraphData & cafe.jeffrey.flamegraph.proto.GraphData.$Shape} GraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    GraphData.decodeDelimited = function(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a GraphData message.
                     * @function verify
                     * @memberof cafe.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    GraphData.verify = function (message, _depth) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            return "max depth exceeded";
                        if (message.flamegraph != null && $Object.hasOwnProperty.call(message, "flamegraph")) {
                            let error = $root.cafe.jeffrey.flamegraph.proto.FlamegraphData.verify(message.flamegraph, _depth + 1);
                            if (error)
                                return "flamegraph." + error;
                        }
                        if (message.timeseries != null && $Object.hasOwnProperty.call(message, "timeseries")) {
                            let error = $root.cafe.jeffrey.flamegraph.proto.TimeseriesData.verify(message.timeseries, _depth + 1);
                            if (error)
                                return "timeseries." + error;
                        }
                        return null;
                    };

                    /**
                     * Creates a GraphData message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof cafe.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {cafe.jeffrey.flamegraph.proto.GraphData} GraphData
                     */
                    GraphData.fromObject = function (object, _depth) {
                        if (object instanceof $root.cafe.jeffrey.flamegraph.proto.GraphData)
                            return object;
                        if (!$util.isObject(object))
                            throw $TypeError(".cafe.jeffrey.flamegraph.proto.GraphData: object expected");
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let message = new $root.cafe.jeffrey.flamegraph.proto.GraphData();
                        if (object.flamegraph != null) {
                            if (!$util.isObject(object.flamegraph))
                                throw $TypeError(".cafe.jeffrey.flamegraph.proto.GraphData.flamegraph: object expected");
                            message.flamegraph = $root.cafe.jeffrey.flamegraph.proto.FlamegraphData.fromObject(object.flamegraph, _depth + 1);
                        }
                        if (object.timeseries != null) {
                            if (!$util.isObject(object.timeseries))
                                throw $TypeError(".cafe.jeffrey.flamegraph.proto.GraphData.timeseries: object expected");
                            message.timeseries = $root.cafe.jeffrey.flamegraph.proto.TimeseriesData.fromObject(object.timeseries, _depth + 1);
                        }
                        return message;
                    };

                    /**
                     * Creates a plain object from a GraphData message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof cafe.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.GraphData} message GraphData
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    GraphData.toObject = function (message, options, _depth) {
                        if (!options)
                            options = {};
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let object = {};
                        if (options.defaults) {
                            object.flamegraph = null;
                            object.timeseries = null;
                        }
                        if (message.flamegraph != null && $Object.hasOwnProperty.call(message, "flamegraph"))
                            object.flamegraph = $root.cafe.jeffrey.flamegraph.proto.FlamegraphData.toObject(message.flamegraph, options, _depth + 1);
                        if (message.timeseries != null && $Object.hasOwnProperty.call(message, "timeseries"))
                            object.timeseries = $root.cafe.jeffrey.flamegraph.proto.TimeseriesData.toObject(message.timeseries, options, _depth + 1);
                        return object;
                    };

                    /**
                     * Converts this GraphData to JSON.
                     * @function toJSON
                     * @memberof cafe.jeffrey.flamegraph.proto.GraphData
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    GraphData.prototype.toJSON = function() {
                        return GraphData.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the type url for GraphData
                     * @function getTypeUrl
                     * @memberof cafe.jeffrey.flamegraph.proto.GraphData
                     * @static
                     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns {string} The type url
                     */
                    GraphData.getTypeUrl = function(prefix) {
                        if (prefix === $undefined)
                            prefix = "type.googleapis.com";
                        return prefix + "/cafe.jeffrey.flamegraph.proto.GraphData";
                    };

                    return GraphData;
                })();

                proto.FlamegraphData = (function() {

                    /**
                     * Properties of a FlamegraphData.
                     * @typedef {Object} cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties
                     * @property {number|null} [depth] FlamegraphData depth
                     * @property {Array.<cafe.jeffrey.flamegraph.proto.Level.$Properties>|null} [levels] FlamegraphData levels
                     * @property {Array.<string>|null} [titlePool] FlamegraphData titlePool
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */

                    /**
                     * Properties of a FlamegraphData.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @interface IFlamegraphData
                     * @augments cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties
                     * @deprecated Use cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties instead.
                     */

                    /**
                     * Shape of a FlamegraphData.
                     * @typedef {cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties} cafe.jeffrey.flamegraph.proto.FlamegraphData.$Shape
                     */

                    /**
                     * Constructs a new FlamegraphData.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @classdesc Represents a FlamegraphData.
                     * @constructor
                     * @param {cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties=} [properties] Properties to set
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */
                    const FlamegraphData = function (properties) {
                        this.levels = [];
                        this.titlePool = [];
                        if (properties)
                            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    };

                    /**
                     * FlamegraphData depth.
                     * @member {number} depth
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @instance
                     */
                    FlamegraphData.prototype.depth = 0;

                    /**
                     * FlamegraphData levels.
                     * @member {Array.<cafe.jeffrey.flamegraph.proto.Level.$Properties>} levels
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @instance
                     */
                    FlamegraphData.prototype.levels = $util.emptyArray;

                    /**
                     * FlamegraphData titlePool.
                     * @member {Array.<string>} titlePool
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @instance
                     */
                    FlamegraphData.prototype.titlePool = $util.emptyArray;

                    /**
                     * Creates a new FlamegraphData instance using the specified properties.
                     * @function create
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties=} [properties] Properties to set
                     * @returns {cafe.jeffrey.flamegraph.proto.FlamegraphData} FlamegraphData instance
                     * @type {{
                     *   (properties: cafe.jeffrey.flamegraph.proto.FlamegraphData.$Shape): cafe.jeffrey.flamegraph.proto.FlamegraphData & cafe.jeffrey.flamegraph.proto.FlamegraphData.$Shape;
                     *   (properties?: cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties): cafe.jeffrey.flamegraph.proto.FlamegraphData;
                     * }}
                     */
                    FlamegraphData.create = function(properties) {
                        return new FlamegraphData(properties);
                    };

                    /**
                     * Encodes the specified FlamegraphData message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.FlamegraphData.verify|verify} messages.
                     * @function encode
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties} message FlamegraphData message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    FlamegraphData.encode = function (message, writer, _depth) {
                        if (!writer)
                            writer = $Writer.create();
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        if (message.depth != null && $Object.hasOwnProperty.call(message, "depth") && message.depth !== 0)
                            writer.uint32(/* id 1, wireType 0 =*/8).int32(message.depth);
                        if (message.levels != null && message.levels.length)
                            for (let i = 0; i < message.levels.length; ++i)
                                $root.cafe.jeffrey.flamegraph.proto.Level.encode(message.levels[i], writer.uint32(/* id 2, wireType 2 =*/18).fork(), _depth + 1).ldelim();
                        if (message.titlePool != null && message.titlePool.length)
                            for (let i = 0; i < message.titlePool.length; ++i)
                                writer.uint32(/* id 3, wireType 2 =*/26).string(message.titlePool[i]);
                        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
                            for (let i = 0; i < message.$unknowns.length; ++i)
                                writer.raw(message.$unknowns[i]);
                        return writer;
                    };

                    /**
                     * Encodes the specified FlamegraphData message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.FlamegraphData.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.FlamegraphData.$Properties} message FlamegraphData message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    FlamegraphData.encodeDelimited = function(message, writer) {
                        return this.encode(message, (writer || $Writer.create()).fork()).ldelim();
                    };

                    /**
                     * Decodes a FlamegraphData message from the specified reader or buffer.
                     * @function decode
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.FlamegraphData & cafe.jeffrey.flamegraph.proto.FlamegraphData.$Shape} FlamegraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    FlamegraphData.decode = function (reader, length, _end, _depth, _target) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $Reader.recursionLimit)
                            throw $Error("max depth exceeded");
                        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.cafe.jeffrey.flamegraph.proto.FlamegraphData(), value;
                        while (reader.pos < end) {
                            let start = reader.pos;
                            let tag = reader.tag();
                            if (tag === _end) {
                                _end = $undefined;
                                break;
                            }
                            let wireType = tag & 7;
                            switch (tag >>>= 3) {
                            case 1: {
                                    if (wireType !== 0)
                                        break;
                                    if (value = reader.int32())
                                        message.depth = value;
                                    else
                                        delete message.depth;
                                    continue;
                                }
                            case 2: {
                                    if (wireType !== 2)
                                        break;
                                    if (!(message.levels && message.levels.length))
                                        message.levels = [];
                                    message.levels.push($root.cafe.jeffrey.flamegraph.proto.Level.decode(reader, reader.uint32(), $undefined, _depth + 1));
                                    continue;
                                }
                            case 3: {
                                    if (wireType !== 2)
                                        break;
                                    if (!(message.titlePool && message.titlePool.length))
                                        message.titlePool = [];
                                    message.titlePool.push(reader.stringVerify());
                                    continue;
                                }
                            }
                            reader.skipType(wireType, _depth, tag);
                            if (!reader.discardUnknown) {
                                $util.makeProp(message, "$unknowns", false);
                                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
                            }
                        }
                        if (_end !== $undefined)
                            throw $Error("missing end group");
                        return message;
                    };

                    /**
                     * Decodes a FlamegraphData message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.FlamegraphData & cafe.jeffrey.flamegraph.proto.FlamegraphData.$Shape} FlamegraphData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    FlamegraphData.decodeDelimited = function(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a FlamegraphData message.
                     * @function verify
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    FlamegraphData.verify = function (message, _depth) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            return "max depth exceeded";
                        if (message.depth != null && $Object.hasOwnProperty.call(message, "depth"))
                            if (!$util.isInteger(message.depth))
                                return "depth: integer expected";
                        if (message.levels != null && $Object.hasOwnProperty.call(message, "levels")) {
                            if (!$Array.isArray(message.levels))
                                return "levels: array expected";
                            for (let i = 0; i < message.levels.length; ++i) {
                                let error = $root.cafe.jeffrey.flamegraph.proto.Level.verify(message.levels[i], _depth + 1);
                                if (error)
                                    return "levels." + error;
                            }
                        }
                        if (message.titlePool != null && $Object.hasOwnProperty.call(message, "titlePool")) {
                            if (!$Array.isArray(message.titlePool))
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
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {cafe.jeffrey.flamegraph.proto.FlamegraphData} FlamegraphData
                     */
                    FlamegraphData.fromObject = function (object, _depth) {
                        if (object instanceof $root.cafe.jeffrey.flamegraph.proto.FlamegraphData)
                            return object;
                        if (!$util.isObject(object))
                            throw $TypeError(".cafe.jeffrey.flamegraph.proto.FlamegraphData: object expected");
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let message = new $root.cafe.jeffrey.flamegraph.proto.FlamegraphData();
                        if (object.depth != null)
                            if ($Number(object.depth) !== 0)
                                message.depth = object.depth | 0;
                        if (object.levels) {
                            if (!$Array.isArray(object.levels))
                                throw $TypeError(".cafe.jeffrey.flamegraph.proto.FlamegraphData.levels: array expected");
                            message.levels = $Array(object.levels.length);
                            for (let i = 0; i < object.levels.length; ++i) {
                                if (!$util.isObject(object.levels[i]))
                                    throw $TypeError(".cafe.jeffrey.flamegraph.proto.FlamegraphData.levels: object expected");
                                message.levels[i] = $root.cafe.jeffrey.flamegraph.proto.Level.fromObject(object.levels[i], _depth + 1);
                            }
                        }
                        if (object.titlePool) {
                            if (!$Array.isArray(object.titlePool))
                                throw $TypeError(".cafe.jeffrey.flamegraph.proto.FlamegraphData.titlePool: array expected");
                            message.titlePool = $Array(object.titlePool.length);
                            for (let i = 0; i < object.titlePool.length; ++i)
                                message.titlePool[i] = $String(object.titlePool[i]);
                        }
                        return message;
                    };

                    /**
                     * Creates a plain object from a FlamegraphData message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.FlamegraphData} message FlamegraphData
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    FlamegraphData.toObject = function (message, options, _depth) {
                        if (!options)
                            options = {};
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let object = {};
                        if (options.arrays || options.defaults) {
                            object.levels = [];
                            object.titlePool = [];
                        }
                        if (options.defaults)
                            object.depth = 0;
                        if (message.depth != null && $Object.hasOwnProperty.call(message, "depth"))
                            object.depth = message.depth;
                        if (message.levels && message.levels.length) {
                            object.levels = $Array(message.levels.length);
                            for (let j = 0; j < message.levels.length; ++j)
                                object.levels[j] = $root.cafe.jeffrey.flamegraph.proto.Level.toObject(message.levels[j], options, _depth + 1);
                        }
                        if (message.titlePool && message.titlePool.length) {
                            object.titlePool = $Array(message.titlePool.length);
                            for (let j = 0; j < message.titlePool.length; ++j)
                                object.titlePool[j] = message.titlePool[j];
                        }
                        return object;
                    };

                    /**
                     * Converts this FlamegraphData to JSON.
                     * @function toJSON
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    FlamegraphData.prototype.toJSON = function() {
                        return FlamegraphData.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the type url for FlamegraphData
                     * @function getTypeUrl
                     * @memberof cafe.jeffrey.flamegraph.proto.FlamegraphData
                     * @static
                     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns {string} The type url
                     */
                    FlamegraphData.getTypeUrl = function(prefix) {
                        if (prefix === $undefined)
                            prefix = "type.googleapis.com";
                        return prefix + "/cafe.jeffrey.flamegraph.proto.FlamegraphData";
                    };

                    return FlamegraphData;
                })();

                proto.TimeseriesData = (function() {

                    /**
                     * Properties of a TimeseriesData.
                     * @typedef {Object} cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties
                     * @property {Array.<cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties>|null} [series] TimeseriesData series
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */

                    /**
                     * Properties of a TimeseriesData.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @interface ITimeseriesData
                     * @augments cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties
                     * @deprecated Use cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties instead.
                     */

                    /**
                     * Shape of a TimeseriesData.
                     * @typedef {cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties} cafe.jeffrey.flamegraph.proto.TimeseriesData.$Shape
                     */

                    /**
                     * Constructs a new TimeseriesData.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @classdesc Represents a TimeseriesData.
                     * @constructor
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties=} [properties] Properties to set
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */
                    const TimeseriesData = function (properties) {
                        this.series = [];
                        if (properties)
                            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    };

                    /**
                     * TimeseriesData series.
                     * @member {Array.<cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties>} series
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesData
                     * @instance
                     */
                    TimeseriesData.prototype.series = $util.emptyArray;

                    /**
                     * Creates a new TimeseriesData instance using the specified properties.
                     * @function create
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties=} [properties] Properties to set
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesData} TimeseriesData instance
                     * @type {{
                     *   (properties: cafe.jeffrey.flamegraph.proto.TimeseriesData.$Shape): cafe.jeffrey.flamegraph.proto.TimeseriesData & cafe.jeffrey.flamegraph.proto.TimeseriesData.$Shape;
                     *   (properties?: cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties): cafe.jeffrey.flamegraph.proto.TimeseriesData;
                     * }}
                     */
                    TimeseriesData.create = function(properties) {
                        return new TimeseriesData(properties);
                    };

                    /**
                     * Encodes the specified TimeseriesData message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.TimeseriesData.verify|verify} messages.
                     * @function encode
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties} message TimeseriesData message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    TimeseriesData.encode = function (message, writer, _depth) {
                        if (!writer)
                            writer = $Writer.create();
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        if (message.series != null && message.series.length)
                            for (let i = 0; i < message.series.length; ++i)
                                $root.cafe.jeffrey.flamegraph.proto.TimeseriesSeries.encode(message.series[i], writer.uint32(/* id 1, wireType 2 =*/10).fork(), _depth + 1).ldelim();
                        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
                            for (let i = 0; i < message.$unknowns.length; ++i)
                                writer.raw(message.$unknowns[i]);
                        return writer;
                    };

                    /**
                     * Encodes the specified TimeseriesData message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.TimeseriesData.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesData.$Properties} message TimeseriesData message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    TimeseriesData.encodeDelimited = function(message, writer) {
                        return this.encode(message, (writer || $Writer.create()).fork()).ldelim();
                    };

                    /**
                     * Decodes a TimeseriesData message from the specified reader or buffer.
                     * @function decode
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesData & cafe.jeffrey.flamegraph.proto.TimeseriesData.$Shape} TimeseriesData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    TimeseriesData.decode = function (reader, length, _end, _depth, _target) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $Reader.recursionLimit)
                            throw $Error("max depth exceeded");
                        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.cafe.jeffrey.flamegraph.proto.TimeseriesData();
                        while (reader.pos < end) {
                            let start = reader.pos;
                            let tag = reader.tag();
                            if (tag === _end) {
                                _end = $undefined;
                                break;
                            }
                            let wireType = tag & 7;
                            switch (tag >>>= 3) {
                            case 1: {
                                    if (wireType !== 2)
                                        break;
                                    if (!(message.series && message.series.length))
                                        message.series = [];
                                    message.series.push($root.cafe.jeffrey.flamegraph.proto.TimeseriesSeries.decode(reader, reader.uint32(), $undefined, _depth + 1));
                                    continue;
                                }
                            }
                            reader.skipType(wireType, _depth, tag);
                            if (!reader.discardUnknown) {
                                $util.makeProp(message, "$unknowns", false);
                                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
                            }
                        }
                        if (_end !== $undefined)
                            throw $Error("missing end group");
                        return message;
                    };

                    /**
                     * Decodes a TimeseriesData message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesData & cafe.jeffrey.flamegraph.proto.TimeseriesData.$Shape} TimeseriesData
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    TimeseriesData.decodeDelimited = function(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a TimeseriesData message.
                     * @function verify
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    TimeseriesData.verify = function (message, _depth) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            return "max depth exceeded";
                        if (message.series != null && $Object.hasOwnProperty.call(message, "series")) {
                            if (!$Array.isArray(message.series))
                                return "series: array expected";
                            for (let i = 0; i < message.series.length; ++i) {
                                let error = $root.cafe.jeffrey.flamegraph.proto.TimeseriesSeries.verify(message.series[i], _depth + 1);
                                if (error)
                                    return "series." + error;
                            }
                        }
                        return null;
                    };

                    /**
                     * Creates a TimeseriesData message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesData} TimeseriesData
                     */
                    TimeseriesData.fromObject = function (object, _depth) {
                        if (object instanceof $root.cafe.jeffrey.flamegraph.proto.TimeseriesData)
                            return object;
                        if (!$util.isObject(object))
                            throw $TypeError(".cafe.jeffrey.flamegraph.proto.TimeseriesData: object expected");
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let message = new $root.cafe.jeffrey.flamegraph.proto.TimeseriesData();
                        if (object.series) {
                            if (!$Array.isArray(object.series))
                                throw $TypeError(".cafe.jeffrey.flamegraph.proto.TimeseriesData.series: array expected");
                            message.series = $Array(object.series.length);
                            for (let i = 0; i < object.series.length; ++i) {
                                if (!$util.isObject(object.series[i]))
                                    throw $TypeError(".cafe.jeffrey.flamegraph.proto.TimeseriesData.series: object expected");
                                message.series[i] = $root.cafe.jeffrey.flamegraph.proto.TimeseriesSeries.fromObject(object.series[i], _depth + 1);
                            }
                        }
                        return message;
                    };

                    /**
                     * Creates a plain object from a TimeseriesData message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesData} message TimeseriesData
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    TimeseriesData.toObject = function (message, options, _depth) {
                        if (!options)
                            options = {};
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let object = {};
                        if (options.arrays || options.defaults)
                            object.series = [];
                        if (message.series && message.series.length) {
                            object.series = $Array(message.series.length);
                            for (let j = 0; j < message.series.length; ++j)
                                object.series[j] = $root.cafe.jeffrey.flamegraph.proto.TimeseriesSeries.toObject(message.series[j], options, _depth + 1);
                        }
                        return object;
                    };

                    /**
                     * Converts this TimeseriesData to JSON.
                     * @function toJSON
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesData
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    TimeseriesData.prototype.toJSON = function() {
                        return TimeseriesData.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the type url for TimeseriesData
                     * @function getTypeUrl
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesData
                     * @static
                     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns {string} The type url
                     */
                    TimeseriesData.getTypeUrl = function(prefix) {
                        if (prefix === $undefined)
                            prefix = "type.googleapis.com";
                        return prefix + "/cafe.jeffrey.flamegraph.proto.TimeseriesData";
                    };

                    return TimeseriesData;
                })();

                proto.TimeseriesSeries = (function() {

                    /**
                     * Properties of a TimeseriesSeries.
                     * @typedef {Object} cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties
                     * @property {string|null} [name] TimeseriesSeries name
                     * @property {Array.<cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties>|null} [data] TimeseriesSeries data
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */

                    /**
                     * Properties of a TimeseriesSeries.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @interface ITimeseriesSeries
                     * @augments cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties
                     * @deprecated Use cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties instead.
                     */

                    /**
                     * Shape of a TimeseriesSeries.
                     * @typedef {cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties} cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Shape
                     */

                    /**
                     * Constructs a new TimeseriesSeries.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @classdesc Represents a TimeseriesSeries.
                     * @constructor
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties=} [properties] Properties to set
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */
                    const TimeseriesSeries = function (properties) {
                        this.data = [];
                        if (properties)
                            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    };

                    /**
                     * TimeseriesSeries name.
                     * @member {string} name
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @instance
                     */
                    TimeseriesSeries.prototype.name = "";

                    /**
                     * TimeseriesSeries data.
                     * @member {Array.<cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties>} data
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @instance
                     */
                    TimeseriesSeries.prototype.data = $util.emptyArray;

                    /**
                     * Creates a new TimeseriesSeries instance using the specified properties.
                     * @function create
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties=} [properties] Properties to set
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesSeries} TimeseriesSeries instance
                     * @type {{
                     *   (properties: cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Shape): cafe.jeffrey.flamegraph.proto.TimeseriesSeries & cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Shape;
                     *   (properties?: cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties): cafe.jeffrey.flamegraph.proto.TimeseriesSeries;
                     * }}
                     */
                    TimeseriesSeries.create = function(properties) {
                        return new TimeseriesSeries(properties);
                    };

                    /**
                     * Encodes the specified TimeseriesSeries message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.TimeseriesSeries.verify|verify} messages.
                     * @function encode
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties} message TimeseriesSeries message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    TimeseriesSeries.encode = function (message, writer, _depth) {
                        if (!writer)
                            writer = $Writer.create();
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        if (message.name != null && $Object.hasOwnProperty.call(message, "name") && message.name !== "")
                            writer.uint32(/* id 1, wireType 2 =*/10).string(message.name);
                        if (message.data != null && message.data.length)
                            for (let i = 0; i < message.data.length; ++i)
                                $root.cafe.jeffrey.flamegraph.proto.TimeseriesPoint.encode(message.data[i], writer.uint32(/* id 2, wireType 2 =*/18).fork(), _depth + 1).ldelim();
                        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
                            for (let i = 0; i < message.$unknowns.length; ++i)
                                writer.raw(message.$unknowns[i]);
                        return writer;
                    };

                    /**
                     * Encodes the specified TimeseriesSeries message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.TimeseriesSeries.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Properties} message TimeseriesSeries message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    TimeseriesSeries.encodeDelimited = function(message, writer) {
                        return this.encode(message, (writer || $Writer.create()).fork()).ldelim();
                    };

                    /**
                     * Decodes a TimeseriesSeries message from the specified reader or buffer.
                     * @function decode
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesSeries & cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Shape} TimeseriesSeries
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    TimeseriesSeries.decode = function (reader, length, _end, _depth, _target) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $Reader.recursionLimit)
                            throw $Error("max depth exceeded");
                        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.cafe.jeffrey.flamegraph.proto.TimeseriesSeries(), value;
                        while (reader.pos < end) {
                            let start = reader.pos;
                            let tag = reader.tag();
                            if (tag === _end) {
                                _end = $undefined;
                                break;
                            }
                            let wireType = tag & 7;
                            switch (tag >>>= 3) {
                            case 1: {
                                    if (wireType !== 2)
                                        break;
                                    if ((value = reader.stringVerify()).length)
                                        message.name = value;
                                    else
                                        delete message.name;
                                    continue;
                                }
                            case 2: {
                                    if (wireType !== 2)
                                        break;
                                    if (!(message.data && message.data.length))
                                        message.data = [];
                                    message.data.push($root.cafe.jeffrey.flamegraph.proto.TimeseriesPoint.decode(reader, reader.uint32(), $undefined, _depth + 1));
                                    continue;
                                }
                            }
                            reader.skipType(wireType, _depth, tag);
                            if (!reader.discardUnknown) {
                                $util.makeProp(message, "$unknowns", false);
                                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
                            }
                        }
                        if (_end !== $undefined)
                            throw $Error("missing end group");
                        return message;
                    };

                    /**
                     * Decodes a TimeseriesSeries message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesSeries & cafe.jeffrey.flamegraph.proto.TimeseriesSeries.$Shape} TimeseriesSeries
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    TimeseriesSeries.decodeDelimited = function(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a TimeseriesSeries message.
                     * @function verify
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    TimeseriesSeries.verify = function (message, _depth) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            return "max depth exceeded";
                        if (message.name != null && $Object.hasOwnProperty.call(message, "name"))
                            if (!$util.isString(message.name))
                                return "name: string expected";
                        if (message.data != null && $Object.hasOwnProperty.call(message, "data")) {
                            if (!$Array.isArray(message.data))
                                return "data: array expected";
                            for (let i = 0; i < message.data.length; ++i) {
                                let error = $root.cafe.jeffrey.flamegraph.proto.TimeseriesPoint.verify(message.data[i], _depth + 1);
                                if (error)
                                    return "data." + error;
                            }
                        }
                        return null;
                    };

                    /**
                     * Creates a TimeseriesSeries message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesSeries} TimeseriesSeries
                     */
                    TimeseriesSeries.fromObject = function (object, _depth) {
                        if (object instanceof $root.cafe.jeffrey.flamegraph.proto.TimeseriesSeries)
                            return object;
                        if (!$util.isObject(object))
                            throw $TypeError(".cafe.jeffrey.flamegraph.proto.TimeseriesSeries: object expected");
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let message = new $root.cafe.jeffrey.flamegraph.proto.TimeseriesSeries();
                        if (object.name != null)
                            if (typeof object.name !== "string" || object.name.length)
                                message.name = $String(object.name);
                        if (object.data) {
                            if (!$Array.isArray(object.data))
                                throw $TypeError(".cafe.jeffrey.flamegraph.proto.TimeseriesSeries.data: array expected");
                            message.data = $Array(object.data.length);
                            for (let i = 0; i < object.data.length; ++i) {
                                if (!$util.isObject(object.data[i]))
                                    throw $TypeError(".cafe.jeffrey.flamegraph.proto.TimeseriesSeries.data: object expected");
                                message.data[i] = $root.cafe.jeffrey.flamegraph.proto.TimeseriesPoint.fromObject(object.data[i], _depth + 1);
                            }
                        }
                        return message;
                    };

                    /**
                     * Creates a plain object from a TimeseriesSeries message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesSeries} message TimeseriesSeries
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    TimeseriesSeries.toObject = function (message, options, _depth) {
                        if (!options)
                            options = {};
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let object = {};
                        if (options.arrays || options.defaults)
                            object.data = [];
                        if (options.defaults)
                            object.name = "";
                        if (message.name != null && $Object.hasOwnProperty.call(message, "name"))
                            object.name = message.name;
                        if (message.data && message.data.length) {
                            object.data = $Array(message.data.length);
                            for (let j = 0; j < message.data.length; ++j)
                                object.data[j] = $root.cafe.jeffrey.flamegraph.proto.TimeseriesPoint.toObject(message.data[j], options, _depth + 1);
                        }
                        return object;
                    };

                    /**
                     * Converts this TimeseriesSeries to JSON.
                     * @function toJSON
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    TimeseriesSeries.prototype.toJSON = function() {
                        return TimeseriesSeries.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the type url for TimeseriesSeries
                     * @function getTypeUrl
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesSeries
                     * @static
                     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns {string} The type url
                     */
                    TimeseriesSeries.getTypeUrl = function(prefix) {
                        if (prefix === $undefined)
                            prefix = "type.googleapis.com";
                        return prefix + "/cafe.jeffrey.flamegraph.proto.TimeseriesSeries";
                    };

                    return TimeseriesSeries;
                })();

                proto.TimeseriesPoint = (function() {

                    /**
                     * Properties of a TimeseriesPoint.
                     * @typedef {Object} cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties
                     * @property {number|Long|null} [timestamp] TimeseriesPoint timestamp
                     * @property {number|Long|null} [value] TimeseriesPoint value
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */

                    /**
                     * Properties of a TimeseriesPoint.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @interface ITimeseriesPoint
                     * @augments cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties
                     * @deprecated Use cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties instead.
                     */

                    /**
                     * Shape of a TimeseriesPoint.
                     * @typedef {cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties} cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Shape
                     */

                    /**
                     * Constructs a new TimeseriesPoint.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @classdesc Represents a TimeseriesPoint.
                     * @constructor
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties=} [properties] Properties to set
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */
                    const TimeseriesPoint = function (properties) {
                        if (properties)
                            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    };

                    /**
                     * TimeseriesPoint timestamp.
                     * @member {number|Long} timestamp
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @instance
                     */
                    TimeseriesPoint.prototype.timestamp = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * TimeseriesPoint value.
                     * @member {number|Long} value
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @instance
                     */
                    TimeseriesPoint.prototype.value = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Creates a new TimeseriesPoint instance using the specified properties.
                     * @function create
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties=} [properties] Properties to set
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesPoint} TimeseriesPoint instance
                     * @type {{
                     *   (properties: cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Shape): cafe.jeffrey.flamegraph.proto.TimeseriesPoint & cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Shape;
                     *   (properties?: cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties): cafe.jeffrey.flamegraph.proto.TimeseriesPoint;
                     * }}
                     */
                    TimeseriesPoint.create = function(properties) {
                        return new TimeseriesPoint(properties);
                    };

                    /**
                     * Encodes the specified TimeseriesPoint message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.TimeseriesPoint.verify|verify} messages.
                     * @function encode
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties} message TimeseriesPoint message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    TimeseriesPoint.encode = function (message, writer, _depth) {
                        if (!writer)
                            writer = $Writer.create();
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        if (message.timestamp != null && $Object.hasOwnProperty.call(message, "timestamp") && (typeof message.timestamp === "object" ? message.timestamp.low || message.timestamp.high : message.timestamp !== 0))
                            writer.uint32(/* id 1, wireType 0 =*/8).int64(message.timestamp);
                        if (message.value != null && $Object.hasOwnProperty.call(message, "value") && (typeof message.value === "object" ? message.value.low || message.value.high : message.value !== 0))
                            writer.uint32(/* id 2, wireType 0 =*/16).int64(message.value);
                        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
                            for (let i = 0; i < message.$unknowns.length; ++i)
                                writer.raw(message.$unknowns[i]);
                        return writer;
                    };

                    /**
                     * Encodes the specified TimeseriesPoint message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.TimeseriesPoint.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Properties} message TimeseriesPoint message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    TimeseriesPoint.encodeDelimited = function(message, writer) {
                        return this.encode(message, (writer || $Writer.create()).fork()).ldelim();
                    };

                    /**
                     * Decodes a TimeseriesPoint message from the specified reader or buffer.
                     * @function decode
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesPoint & cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Shape} TimeseriesPoint
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    TimeseriesPoint.decode = function (reader, length, _end, _depth, _target) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $Reader.recursionLimit)
                            throw $Error("max depth exceeded");
                        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.cafe.jeffrey.flamegraph.proto.TimeseriesPoint(), value;
                        while (reader.pos < end) {
                            let start = reader.pos;
                            let tag = reader.tag();
                            if (tag === _end) {
                                _end = $undefined;
                                break;
                            }
                            let wireType = tag & 7;
                            switch (tag >>>= 3) {
                            case 1: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.timestamp = value;
                                    else
                                        delete message.timestamp;
                                    continue;
                                }
                            case 2: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.value = value;
                                    else
                                        delete message.value;
                                    continue;
                                }
                            }
                            reader.skipType(wireType, _depth, tag);
                            if (!reader.discardUnknown) {
                                $util.makeProp(message, "$unknowns", false);
                                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
                            }
                        }
                        if (_end !== $undefined)
                            throw $Error("missing end group");
                        return message;
                    };

                    /**
                     * Decodes a TimeseriesPoint message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesPoint & cafe.jeffrey.flamegraph.proto.TimeseriesPoint.$Shape} TimeseriesPoint
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    TimeseriesPoint.decodeDelimited = function(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a TimeseriesPoint message.
                     * @function verify
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    TimeseriesPoint.verify = function (message, _depth) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            return "max depth exceeded";
                        if (message.timestamp != null && $Object.hasOwnProperty.call(message, "timestamp"))
                            if (!$util.isInteger(message.timestamp) && !(message.timestamp && $util.isInteger(message.timestamp.low) && $util.isInteger(message.timestamp.high)))
                                return "timestamp: integer|Long expected";
                        if (message.value != null && $Object.hasOwnProperty.call(message, "value"))
                            if (!$util.isInteger(message.value) && !(message.value && $util.isInteger(message.value.low) && $util.isInteger(message.value.high)))
                                return "value: integer|Long expected";
                        return null;
                    };

                    /**
                     * Creates a TimeseriesPoint message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {cafe.jeffrey.flamegraph.proto.TimeseriesPoint} TimeseriesPoint
                     */
                    TimeseriesPoint.fromObject = function (object, _depth) {
                        if (object instanceof $root.cafe.jeffrey.flamegraph.proto.TimeseriesPoint)
                            return object;
                        if (!$util.isObject(object))
                            throw $TypeError(".cafe.jeffrey.flamegraph.proto.TimeseriesPoint: object expected");
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let message = new $root.cafe.jeffrey.flamegraph.proto.TimeseriesPoint();
                        if (object.timestamp != null)
                            if (typeof object.timestamp === "object" ? object.timestamp.low || object.timestamp.high : $Number(object.timestamp) !== 0)
                                if ($util.Long)
                                    message.timestamp = $util.Long.fromValue(object.timestamp, false);
                                else if (typeof object.timestamp === "string")
                                    message.timestamp = $parseInt(object.timestamp, 10);
                                else if (typeof object.timestamp === "number")
                                    message.timestamp = object.timestamp;
                                else if (typeof object.timestamp === "object")
                                    message.timestamp = new $util.LongBits(object.timestamp.low >>> 0, object.timestamp.high >>> 0).toNumber();
                        if (object.value != null)
                            if (typeof object.value === "object" ? object.value.low || object.value.high : $Number(object.value) !== 0)
                                if ($util.Long)
                                    message.value = $util.Long.fromValue(object.value, false);
                                else if (typeof object.value === "string")
                                    message.value = $parseInt(object.value, 10);
                                else if (typeof object.value === "number")
                                    message.value = object.value;
                                else if (typeof object.value === "object")
                                    message.value = new $util.LongBits(object.value.low >>> 0, object.value.high >>> 0).toNumber();
                        return message;
                    };

                    /**
                     * Creates a plain object from a TimeseriesPoint message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.TimeseriesPoint} message TimeseriesPoint
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    TimeseriesPoint.toObject = function (message, options, _depth) {
                        if (!options)
                            options = {};
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let object = {};
                        if (options.defaults) {
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.timestamp = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.timestamp = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.value = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.value = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                        }
                        if (message.timestamp != null && $Object.hasOwnProperty.call(message, "timestamp"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.timestamp = typeof message.timestamp === "number" ? $BigInt(message.timestamp) : $util.Long.fromBits(message.timestamp.low >>> 0, message.timestamp.high >>> 0, false).toBigInt();
                            else if (typeof message.timestamp === "number")
                                object.timestamp = options.longs === $String ? $String(message.timestamp) : message.timestamp;
                            else
                                object.timestamp = options.longs === $String ? $util.Long.prototype.toString.call(message.timestamp) : options.longs === $Number ? new $util.LongBits(message.timestamp.low >>> 0, message.timestamp.high >>> 0).toNumber() : message.timestamp;
                        if (message.value != null && $Object.hasOwnProperty.call(message, "value"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.value = typeof message.value === "number" ? $BigInt(message.value) : $util.Long.fromBits(message.value.low >>> 0, message.value.high >>> 0, false).toBigInt();
                            else if (typeof message.value === "number")
                                object.value = options.longs === $String ? $String(message.value) : message.value;
                            else
                                object.value = options.longs === $String ? $util.Long.prototype.toString.call(message.value) : options.longs === $Number ? new $util.LongBits(message.value.low >>> 0, message.value.high >>> 0).toNumber() : message.value;
                        return object;
                    };

                    /**
                     * Converts this TimeseriesPoint to JSON.
                     * @function toJSON
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    TimeseriesPoint.prototype.toJSON = function() {
                        return TimeseriesPoint.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the type url for TimeseriesPoint
                     * @function getTypeUrl
                     * @memberof cafe.jeffrey.flamegraph.proto.TimeseriesPoint
                     * @static
                     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns {string} The type url
                     */
                    TimeseriesPoint.getTypeUrl = function(prefix) {
                        if (prefix === $undefined)
                            prefix = "type.googleapis.com";
                        return prefix + "/cafe.jeffrey.flamegraph.proto.TimeseriesPoint";
                    };

                    return TimeseriesPoint;
                })();

                proto.Level = (function() {

                    /**
                     * Properties of a Level.
                     * @typedef {Object} cafe.jeffrey.flamegraph.proto.Level.$Properties
                     * @property {Array.<cafe.jeffrey.flamegraph.proto.Frame.$Properties>|null} [frames] Level frames
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */

                    /**
                     * Properties of a Level.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @interface ILevel
                     * @augments cafe.jeffrey.flamegraph.proto.Level.$Properties
                     * @deprecated Use cafe.jeffrey.flamegraph.proto.Level.$Properties instead.
                     */

                    /**
                     * Shape of a Level.
                     * @typedef {cafe.jeffrey.flamegraph.proto.Level.$Properties} cafe.jeffrey.flamegraph.proto.Level.$Shape
                     */

                    /**
                     * Constructs a new Level.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @classdesc Represents a Level.
                     * @constructor
                     * @param {cafe.jeffrey.flamegraph.proto.Level.$Properties=} [properties] Properties to set
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */
                    const Level = function (properties) {
                        this.frames = [];
                        if (properties)
                            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    };

                    /**
                     * Level frames.
                     * @member {Array.<cafe.jeffrey.flamegraph.proto.Frame.$Properties>} frames
                     * @memberof cafe.jeffrey.flamegraph.proto.Level
                     * @instance
                     */
                    Level.prototype.frames = $util.emptyArray;

                    /**
                     * Creates a new Level instance using the specified properties.
                     * @function create
                     * @memberof cafe.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.Level.$Properties=} [properties] Properties to set
                     * @returns {cafe.jeffrey.flamegraph.proto.Level} Level instance
                     * @type {{
                     *   (properties: cafe.jeffrey.flamegraph.proto.Level.$Shape): cafe.jeffrey.flamegraph.proto.Level & cafe.jeffrey.flamegraph.proto.Level.$Shape;
                     *   (properties?: cafe.jeffrey.flamegraph.proto.Level.$Properties): cafe.jeffrey.flamegraph.proto.Level;
                     * }}
                     */
                    Level.create = function(properties) {
                        return new Level(properties);
                    };

                    /**
                     * Encodes the specified Level message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.Level.verify|verify} messages.
                     * @function encode
                     * @memberof cafe.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.Level.$Properties} message Level message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    Level.encode = function (message, writer, _depth) {
                        if (!writer)
                            writer = $Writer.create();
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        if (message.frames != null && message.frames.length)
                            for (let i = 0; i < message.frames.length; ++i)
                                $root.cafe.jeffrey.flamegraph.proto.Frame.encode(message.frames[i], writer.uint32(/* id 1, wireType 2 =*/10).fork(), _depth + 1).ldelim();
                        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
                            for (let i = 0; i < message.$unknowns.length; ++i)
                                writer.raw(message.$unknowns[i]);
                        return writer;
                    };

                    /**
                     * Encodes the specified Level message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.Level.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.Level.$Properties} message Level message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    Level.encodeDelimited = function(message, writer) {
                        return this.encode(message, (writer || $Writer.create()).fork()).ldelim();
                    };

                    /**
                     * Decodes a Level message from the specified reader or buffer.
                     * @function decode
                     * @memberof cafe.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.Level & cafe.jeffrey.flamegraph.proto.Level.$Shape} Level
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    Level.decode = function (reader, length, _end, _depth, _target) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $Reader.recursionLimit)
                            throw $Error("max depth exceeded");
                        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.cafe.jeffrey.flamegraph.proto.Level();
                        while (reader.pos < end) {
                            let start = reader.pos;
                            let tag = reader.tag();
                            if (tag === _end) {
                                _end = $undefined;
                                break;
                            }
                            let wireType = tag & 7;
                            switch (tag >>>= 3) {
                            case 1: {
                                    if (wireType !== 2)
                                        break;
                                    if (!(message.frames && message.frames.length))
                                        message.frames = [];
                                    message.frames.push($root.cafe.jeffrey.flamegraph.proto.Frame.decode(reader, reader.uint32(), $undefined, _depth + 1));
                                    continue;
                                }
                            }
                            reader.skipType(wireType, _depth, tag);
                            if (!reader.discardUnknown) {
                                $util.makeProp(message, "$unknowns", false);
                                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
                            }
                        }
                        if (_end !== $undefined)
                            throw $Error("missing end group");
                        return message;
                    };

                    /**
                     * Decodes a Level message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.Level & cafe.jeffrey.flamegraph.proto.Level.$Shape} Level
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    Level.decodeDelimited = function(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a Level message.
                     * @function verify
                     * @memberof cafe.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    Level.verify = function (message, _depth) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            return "max depth exceeded";
                        if (message.frames != null && $Object.hasOwnProperty.call(message, "frames")) {
                            if (!$Array.isArray(message.frames))
                                return "frames: array expected";
                            for (let i = 0; i < message.frames.length; ++i) {
                                let error = $root.cafe.jeffrey.flamegraph.proto.Frame.verify(message.frames[i], _depth + 1);
                                if (error)
                                    return "frames." + error;
                            }
                        }
                        return null;
                    };

                    /**
                     * Creates a Level message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof cafe.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {cafe.jeffrey.flamegraph.proto.Level} Level
                     */
                    Level.fromObject = function (object, _depth) {
                        if (object instanceof $root.cafe.jeffrey.flamegraph.proto.Level)
                            return object;
                        if (!$util.isObject(object))
                            throw $TypeError(".cafe.jeffrey.flamegraph.proto.Level: object expected");
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let message = new $root.cafe.jeffrey.flamegraph.proto.Level();
                        if (object.frames) {
                            if (!$Array.isArray(object.frames))
                                throw $TypeError(".cafe.jeffrey.flamegraph.proto.Level.frames: array expected");
                            message.frames = $Array(object.frames.length);
                            for (let i = 0; i < object.frames.length; ++i) {
                                if (!$util.isObject(object.frames[i]))
                                    throw $TypeError(".cafe.jeffrey.flamegraph.proto.Level.frames: object expected");
                                message.frames[i] = $root.cafe.jeffrey.flamegraph.proto.Frame.fromObject(object.frames[i], _depth + 1);
                            }
                        }
                        return message;
                    };

                    /**
                     * Creates a plain object from a Level message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof cafe.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.Level} message Level
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    Level.toObject = function (message, options, _depth) {
                        if (!options)
                            options = {};
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let object = {};
                        if (options.arrays || options.defaults)
                            object.frames = [];
                        if (message.frames && message.frames.length) {
                            object.frames = $Array(message.frames.length);
                            for (let j = 0; j < message.frames.length; ++j)
                                object.frames[j] = $root.cafe.jeffrey.flamegraph.proto.Frame.toObject(message.frames[j], options, _depth + 1);
                        }
                        return object;
                    };

                    /**
                     * Converts this Level to JSON.
                     * @function toJSON
                     * @memberof cafe.jeffrey.flamegraph.proto.Level
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    Level.prototype.toJSON = function() {
                        return Level.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the type url for Level
                     * @function getTypeUrl
                     * @memberof cafe.jeffrey.flamegraph.proto.Level
                     * @static
                     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns {string} The type url
                     */
                    Level.getTypeUrl = function(prefix) {
                        if (prefix === $undefined)
                            prefix = "type.googleapis.com";
                        return prefix + "/cafe.jeffrey.flamegraph.proto.Level";
                    };

                    return Level;
                })();

                proto.Frame = (function() {

                    /**
                     * Properties of a Frame.
                     * @typedef {Object} cafe.jeffrey.flamegraph.proto.Frame.$Properties
                     * @property {number|Long|null} [leftSamples] Frame leftSamples
                     * @property {number|Long|null} [totalSamples] Frame totalSamples
                     * @property {number|null} [titleIndex] Frame titleIndex
                     * @property {cafe.jeffrey.flamegraph.proto.FrameType|null} [type] Frame type
                     * @property {number|Long|null} [leftWeight] Frame leftWeight
                     * @property {number|Long|null} [totalWeight] Frame totalWeight
                     * @property {number|Long|null} [selfSamples] Frame selfSamples
                     * @property {cafe.jeffrey.flamegraph.proto.FramePosition.$Properties|null} [position] Frame position
                     * @property {cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties|null} [sampleTypes] Frame sampleTypes
                     * @property {cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties|null} [diffDetails] Frame diffDetails
                     * @property {boolean|null} [beforeMarker] Frame beforeMarker
                     * @property {number|null} [prunedChildrenCount] Frame prunedChildrenCount
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */

                    /**
                     * Properties of a Frame.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @interface IFrame
                     * @augments cafe.jeffrey.flamegraph.proto.Frame.$Properties
                     * @deprecated Use cafe.jeffrey.flamegraph.proto.Frame.$Properties instead.
                     */

                    /**
                     * Shape of a Frame.
                     * @typedef {cafe.jeffrey.flamegraph.proto.Frame.$Properties} cafe.jeffrey.flamegraph.proto.Frame.$Shape
                     */

                    /**
                     * Constructs a new Frame.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @classdesc Represents a Frame.
                     * @constructor
                     * @param {cafe.jeffrey.flamegraph.proto.Frame.$Properties=} [properties] Properties to set
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */
                    const Frame = function (properties) {
                        if (properties)
                            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    };

                    /**
                     * Frame leftSamples.
                     * @member {number|Long} leftSamples
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.leftSamples = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Frame totalSamples.
                     * @member {number|Long} totalSamples
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.totalSamples = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Frame titleIndex.
                     * @member {number} titleIndex
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.titleIndex = 0;

                    /**
                     * Frame type.
                     * @member {cafe.jeffrey.flamegraph.proto.FrameType} type
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.type = 0;

                    /**
                     * Frame leftWeight.
                     * @member {number|Long} leftWeight
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.leftWeight = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Frame totalWeight.
                     * @member {number|Long} totalWeight
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.totalWeight = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Frame selfSamples.
                     * @member {number|Long} selfSamples
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.selfSamples = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Frame position.
                     * @member {cafe.jeffrey.flamegraph.proto.FramePosition.$Properties|null|undefined} position
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.position = null;

                    /**
                     * Frame sampleTypes.
                     * @member {cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties|null|undefined} sampleTypes
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.sampleTypes = null;

                    /**
                     * Frame diffDetails.
                     * @member {cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties|null|undefined} diffDetails
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.diffDetails = null;

                    /**
                     * Frame beforeMarker.
                     * @member {boolean} beforeMarker
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.beforeMarker = false;

                    /**
                     * Frame prunedChildrenCount.
                     * @member {number} prunedChildrenCount
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     */
                    Frame.prototype.prunedChildrenCount = 0;

                    /**
                     * Creates a new Frame instance using the specified properties.
                     * @function create
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.Frame.$Properties=} [properties] Properties to set
                     * @returns {cafe.jeffrey.flamegraph.proto.Frame} Frame instance
                     * @type {{
                     *   (properties: cafe.jeffrey.flamegraph.proto.Frame.$Shape): cafe.jeffrey.flamegraph.proto.Frame & cafe.jeffrey.flamegraph.proto.Frame.$Shape;
                     *   (properties?: cafe.jeffrey.flamegraph.proto.Frame.$Properties): cafe.jeffrey.flamegraph.proto.Frame;
                     * }}
                     */
                    Frame.create = function(properties) {
                        return new Frame(properties);
                    };

                    /**
                     * Encodes the specified Frame message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.Frame.verify|verify} messages.
                     * @function encode
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.Frame.$Properties} message Frame message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    Frame.encode = function (message, writer, _depth) {
                        if (!writer)
                            writer = $Writer.create();
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        if (message.leftSamples != null && $Object.hasOwnProperty.call(message, "leftSamples") && (typeof message.leftSamples === "object" ? message.leftSamples.low || message.leftSamples.high : message.leftSamples !== 0))
                            writer.uint32(/* id 1, wireType 0 =*/8).int64(message.leftSamples);
                        if (message.totalSamples != null && $Object.hasOwnProperty.call(message, "totalSamples") && (typeof message.totalSamples === "object" ? message.totalSamples.low || message.totalSamples.high : message.totalSamples !== 0))
                            writer.uint32(/* id 2, wireType 0 =*/16).int64(message.totalSamples);
                        if (message.titleIndex != null && $Object.hasOwnProperty.call(message, "titleIndex") && message.titleIndex !== 0)
                            writer.uint32(/* id 3, wireType 0 =*/24).int32(message.titleIndex);
                        if (message.type != null && $Object.hasOwnProperty.call(message, "type") && message.type !== 0)
                            writer.uint32(/* id 4, wireType 0 =*/32).int32(message.type);
                        if (message.leftWeight != null && $Object.hasOwnProperty.call(message, "leftWeight") && (typeof message.leftWeight === "object" ? message.leftWeight.low || message.leftWeight.high : message.leftWeight !== 0))
                            writer.uint32(/* id 5, wireType 0 =*/40).int64(message.leftWeight);
                        if (message.totalWeight != null && $Object.hasOwnProperty.call(message, "totalWeight") && (typeof message.totalWeight === "object" ? message.totalWeight.low || message.totalWeight.high : message.totalWeight !== 0))
                            writer.uint32(/* id 6, wireType 0 =*/48).int64(message.totalWeight);
                        if (message.selfSamples != null && $Object.hasOwnProperty.call(message, "selfSamples") && (typeof message.selfSamples === "object" ? message.selfSamples.low || message.selfSamples.high : message.selfSamples !== 0))
                            writer.uint32(/* id 7, wireType 0 =*/56).int64(message.selfSamples);
                        if (message.position != null && $Object.hasOwnProperty.call(message, "position"))
                            $root.cafe.jeffrey.flamegraph.proto.FramePosition.encode(message.position, writer.uint32(/* id 8, wireType 2 =*/66).fork(), _depth + 1).ldelim();
                        if (message.sampleTypes != null && $Object.hasOwnProperty.call(message, "sampleTypes"))
                            $root.cafe.jeffrey.flamegraph.proto.FrameSampleTypes.encode(message.sampleTypes, writer.uint32(/* id 9, wireType 2 =*/74).fork(), _depth + 1).ldelim();
                        if (message.diffDetails != null && $Object.hasOwnProperty.call(message, "diffDetails"))
                            $root.cafe.jeffrey.flamegraph.proto.DiffDetails.encode(message.diffDetails, writer.uint32(/* id 10, wireType 2 =*/82).fork(), _depth + 1).ldelim();
                        if (message.beforeMarker != null && $Object.hasOwnProperty.call(message, "beforeMarker") && message.beforeMarker !== false)
                            writer.uint32(/* id 11, wireType 0 =*/88).bool(message.beforeMarker);
                        if (message.prunedChildrenCount != null && $Object.hasOwnProperty.call(message, "prunedChildrenCount") && message.prunedChildrenCount !== 0)
                            writer.uint32(/* id 12, wireType 0 =*/96).int32(message.prunedChildrenCount);
                        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
                            for (let i = 0; i < message.$unknowns.length; ++i)
                                writer.raw(message.$unknowns[i]);
                        return writer;
                    };

                    /**
                     * Encodes the specified Frame message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.Frame.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.Frame.$Properties} message Frame message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    Frame.encodeDelimited = function(message, writer) {
                        return this.encode(message, (writer || $Writer.create()).fork()).ldelim();
                    };

                    /**
                     * Decodes a Frame message from the specified reader or buffer.
                     * @function decode
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.Frame & cafe.jeffrey.flamegraph.proto.Frame.$Shape} Frame
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    Frame.decode = function (reader, length, _end, _depth, _target) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $Reader.recursionLimit)
                            throw $Error("max depth exceeded");
                        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.cafe.jeffrey.flamegraph.proto.Frame(), value;
                        while (reader.pos < end) {
                            let start = reader.pos;
                            let tag = reader.tag();
                            if (tag === _end) {
                                _end = $undefined;
                                break;
                            }
                            let wireType = tag & 7;
                            switch (tag >>>= 3) {
                            case 1: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.leftSamples = value;
                                    else
                                        delete message.leftSamples;
                                    continue;
                                }
                            case 2: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.totalSamples = value;
                                    else
                                        delete message.totalSamples;
                                    continue;
                                }
                            case 3: {
                                    if (wireType !== 0)
                                        break;
                                    if (value = reader.int32())
                                        message.titleIndex = value;
                                    else
                                        delete message.titleIndex;
                                    continue;
                                }
                            case 4: {
                                    if (wireType !== 0)
                                        break;
                                    if (value = reader.int32())
                                        message.type = value;
                                    else
                                        delete message.type;
                                    continue;
                                }
                            case 5: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.leftWeight = value;
                                    else
                                        delete message.leftWeight;
                                    continue;
                                }
                            case 6: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.totalWeight = value;
                                    else
                                        delete message.totalWeight;
                                    continue;
                                }
                            case 7: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.selfSamples = value;
                                    else
                                        delete message.selfSamples;
                                    continue;
                                }
                            case 8: {
                                    if (wireType !== 2)
                                        break;
                                    message.position = $root.cafe.jeffrey.flamegraph.proto.FramePosition.decode(reader, reader.uint32(), $undefined, _depth + 1, message.position);
                                    continue;
                                }
                            case 9: {
                                    if (wireType !== 2)
                                        break;
                                    message.sampleTypes = $root.cafe.jeffrey.flamegraph.proto.FrameSampleTypes.decode(reader, reader.uint32(), $undefined, _depth + 1, message.sampleTypes);
                                    continue;
                                }
                            case 10: {
                                    if (wireType !== 2)
                                        break;
                                    message.diffDetails = $root.cafe.jeffrey.flamegraph.proto.DiffDetails.decode(reader, reader.uint32(), $undefined, _depth + 1, message.diffDetails);
                                    continue;
                                }
                            case 11: {
                                    if (wireType !== 0)
                                        break;
                                    if (value = reader.bool())
                                        message.beforeMarker = value;
                                    else
                                        delete message.beforeMarker;
                                    continue;
                                }
                            case 12: {
                                    if (wireType !== 0)
                                        break;
                                    if (value = reader.int32())
                                        message.prunedChildrenCount = value;
                                    else
                                        delete message.prunedChildrenCount;
                                    continue;
                                }
                            }
                            reader.skipType(wireType, _depth, tag);
                            if (!reader.discardUnknown) {
                                $util.makeProp(message, "$unknowns", false);
                                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
                            }
                        }
                        if (_end !== $undefined)
                            throw $Error("missing end group");
                        return message;
                    };

                    /**
                     * Decodes a Frame message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.Frame & cafe.jeffrey.flamegraph.proto.Frame.$Shape} Frame
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    Frame.decodeDelimited = function(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a Frame message.
                     * @function verify
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    Frame.verify = function (message, _depth) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            return "max depth exceeded";
                        if (message.leftSamples != null && $Object.hasOwnProperty.call(message, "leftSamples"))
                            if (!$util.isInteger(message.leftSamples) && !(message.leftSamples && $util.isInteger(message.leftSamples.low) && $util.isInteger(message.leftSamples.high)))
                                return "leftSamples: integer|Long expected";
                        if (message.totalSamples != null && $Object.hasOwnProperty.call(message, "totalSamples"))
                            if (!$util.isInteger(message.totalSamples) && !(message.totalSamples && $util.isInteger(message.totalSamples.low) && $util.isInteger(message.totalSamples.high)))
                                return "totalSamples: integer|Long expected";
                        if (message.titleIndex != null && $Object.hasOwnProperty.call(message, "titleIndex"))
                            if (!$util.isInteger(message.titleIndex))
                                return "titleIndex: integer expected";
                        if (message.type != null && $Object.hasOwnProperty.call(message, "type"))
                            if (typeof message.type !== "number" || (message.type | 0) !== message.type)
                                return "type: enum value expected";
                        if (message.leftWeight != null && $Object.hasOwnProperty.call(message, "leftWeight"))
                            if (!$util.isInteger(message.leftWeight) && !(message.leftWeight && $util.isInteger(message.leftWeight.low) && $util.isInteger(message.leftWeight.high)))
                                return "leftWeight: integer|Long expected";
                        if (message.totalWeight != null && $Object.hasOwnProperty.call(message, "totalWeight"))
                            if (!$util.isInteger(message.totalWeight) && !(message.totalWeight && $util.isInteger(message.totalWeight.low) && $util.isInteger(message.totalWeight.high)))
                                return "totalWeight: integer|Long expected";
                        if (message.selfSamples != null && $Object.hasOwnProperty.call(message, "selfSamples"))
                            if (!$util.isInteger(message.selfSamples) && !(message.selfSamples && $util.isInteger(message.selfSamples.low) && $util.isInteger(message.selfSamples.high)))
                                return "selfSamples: integer|Long expected";
                        if (message.position != null && $Object.hasOwnProperty.call(message, "position")) {
                            let error = $root.cafe.jeffrey.flamegraph.proto.FramePosition.verify(message.position, _depth + 1);
                            if (error)
                                return "position." + error;
                        }
                        if (message.sampleTypes != null && $Object.hasOwnProperty.call(message, "sampleTypes")) {
                            let error = $root.cafe.jeffrey.flamegraph.proto.FrameSampleTypes.verify(message.sampleTypes, _depth + 1);
                            if (error)
                                return "sampleTypes." + error;
                        }
                        if (message.diffDetails != null && $Object.hasOwnProperty.call(message, "diffDetails")) {
                            let error = $root.cafe.jeffrey.flamegraph.proto.DiffDetails.verify(message.diffDetails, _depth + 1);
                            if (error)
                                return "diffDetails." + error;
                        }
                        if (message.beforeMarker != null && $Object.hasOwnProperty.call(message, "beforeMarker"))
                            if (typeof message.beforeMarker !== "boolean")
                                return "beforeMarker: boolean expected";
                        if (message.prunedChildrenCount != null && $Object.hasOwnProperty.call(message, "prunedChildrenCount"))
                            if (!$util.isInteger(message.prunedChildrenCount))
                                return "prunedChildrenCount: integer expected";
                        return null;
                    };

                    /**
                     * Creates a Frame message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {cafe.jeffrey.flamegraph.proto.Frame} Frame
                     */
                    Frame.fromObject = function (object, _depth) {
                        if (object instanceof $root.cafe.jeffrey.flamegraph.proto.Frame)
                            return object;
                        if (!$util.isObject(object))
                            throw $TypeError(".cafe.jeffrey.flamegraph.proto.Frame: object expected");
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let message = new $root.cafe.jeffrey.flamegraph.proto.Frame();
                        if (object.leftSamples != null)
                            if (typeof object.leftSamples === "object" ? object.leftSamples.low || object.leftSamples.high : $Number(object.leftSamples) !== 0)
                                if ($util.Long)
                                    message.leftSamples = $util.Long.fromValue(object.leftSamples, false);
                                else if (typeof object.leftSamples === "string")
                                    message.leftSamples = $parseInt(object.leftSamples, 10);
                                else if (typeof object.leftSamples === "number")
                                    message.leftSamples = object.leftSamples;
                                else if (typeof object.leftSamples === "object")
                                    message.leftSamples = new $util.LongBits(object.leftSamples.low >>> 0, object.leftSamples.high >>> 0).toNumber();
                        if (object.totalSamples != null)
                            if (typeof object.totalSamples === "object" ? object.totalSamples.low || object.totalSamples.high : $Number(object.totalSamples) !== 0)
                                if ($util.Long)
                                    message.totalSamples = $util.Long.fromValue(object.totalSamples, false);
                                else if (typeof object.totalSamples === "string")
                                    message.totalSamples = $parseInt(object.totalSamples, 10);
                                else if (typeof object.totalSamples === "number")
                                    message.totalSamples = object.totalSamples;
                                else if (typeof object.totalSamples === "object")
                                    message.totalSamples = new $util.LongBits(object.totalSamples.low >>> 0, object.totalSamples.high >>> 0).toNumber();
                        if (object.titleIndex != null)
                            if ($Number(object.titleIndex) !== 0)
                                message.titleIndex = object.titleIndex | 0;
                        if (object.type !== 0 && (typeof object.type !== "string" || $root.cafe.jeffrey.flamegraph.proto.FrameType[object.type] !== 0))
                            switch (object.type) {
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
                            case "FRAME_TYPE_COLLAPSED_SYNTHETIC":
                            case 15:
                                message.type = 15;
                                break;
                            case "FRAME_TYPE_TRUNCATED_SYNTHETIC":
                            case 16:
                                message.type = 16;
                                break;
                            case "FRAME_TYPE_PYTHON":
                            case 17:
                                message.type = 17;
                                break;
                            case "FRAME_TYPE_JAVASCRIPT":
                            case 18:
                                message.type = 18;
                                break;
                            case "FRAME_TYPE_GO":
                            case 19:
                                message.type = 19;
                                break;
                            case "FRAME_TYPE_OTHER_RUNTIME":
                            case 20:
                                message.type = 20;
                                break;
                            default:
                                if (typeof object.type === "number" && (object.type | 0) === object.type)
                                    message.type = object.type;
                            }
                        if (object.leftWeight != null)
                            if (typeof object.leftWeight === "object" ? object.leftWeight.low || object.leftWeight.high : $Number(object.leftWeight) !== 0)
                                if ($util.Long)
                                    message.leftWeight = $util.Long.fromValue(object.leftWeight, false);
                                else if (typeof object.leftWeight === "string")
                                    message.leftWeight = $parseInt(object.leftWeight, 10);
                                else if (typeof object.leftWeight === "number")
                                    message.leftWeight = object.leftWeight;
                                else if (typeof object.leftWeight === "object")
                                    message.leftWeight = new $util.LongBits(object.leftWeight.low >>> 0, object.leftWeight.high >>> 0).toNumber();
                        if (object.totalWeight != null)
                            if (typeof object.totalWeight === "object" ? object.totalWeight.low || object.totalWeight.high : $Number(object.totalWeight) !== 0)
                                if ($util.Long)
                                    message.totalWeight = $util.Long.fromValue(object.totalWeight, false);
                                else if (typeof object.totalWeight === "string")
                                    message.totalWeight = $parseInt(object.totalWeight, 10);
                                else if (typeof object.totalWeight === "number")
                                    message.totalWeight = object.totalWeight;
                                else if (typeof object.totalWeight === "object")
                                    message.totalWeight = new $util.LongBits(object.totalWeight.low >>> 0, object.totalWeight.high >>> 0).toNumber();
                        if (object.selfSamples != null)
                            if (typeof object.selfSamples === "object" ? object.selfSamples.low || object.selfSamples.high : $Number(object.selfSamples) !== 0)
                                if ($util.Long)
                                    message.selfSamples = $util.Long.fromValue(object.selfSamples, false);
                                else if (typeof object.selfSamples === "string")
                                    message.selfSamples = $parseInt(object.selfSamples, 10);
                                else if (typeof object.selfSamples === "number")
                                    message.selfSamples = object.selfSamples;
                                else if (typeof object.selfSamples === "object")
                                    message.selfSamples = new $util.LongBits(object.selfSamples.low >>> 0, object.selfSamples.high >>> 0).toNumber();
                        if (object.position != null) {
                            if (!$util.isObject(object.position))
                                throw $TypeError(".cafe.jeffrey.flamegraph.proto.Frame.position: object expected");
                            message.position = $root.cafe.jeffrey.flamegraph.proto.FramePosition.fromObject(object.position, _depth + 1);
                        }
                        if (object.sampleTypes != null) {
                            if (!$util.isObject(object.sampleTypes))
                                throw $TypeError(".cafe.jeffrey.flamegraph.proto.Frame.sampleTypes: object expected");
                            message.sampleTypes = $root.cafe.jeffrey.flamegraph.proto.FrameSampleTypes.fromObject(object.sampleTypes, _depth + 1);
                        }
                        if (object.diffDetails != null) {
                            if (!$util.isObject(object.diffDetails))
                                throw $TypeError(".cafe.jeffrey.flamegraph.proto.Frame.diffDetails: object expected");
                            message.diffDetails = $root.cafe.jeffrey.flamegraph.proto.DiffDetails.fromObject(object.diffDetails, _depth + 1);
                        }
                        if (object.beforeMarker != null)
                            if (object.beforeMarker)
                                message.beforeMarker = $Boolean(object.beforeMarker);
                        if (object.prunedChildrenCount != null)
                            if ($Number(object.prunedChildrenCount) !== 0)
                                message.prunedChildrenCount = object.prunedChildrenCount | 0;
                        return message;
                    };

                    /**
                     * Creates a plain object from a Frame message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.Frame} message Frame
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    Frame.toObject = function (message, options, _depth) {
                        if (!options)
                            options = {};
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let object = {};
                        if (options.defaults) {
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.leftSamples = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.leftSamples = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.totalSamples = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.totalSamples = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                            object.titleIndex = 0;
                            object.type = options.enums === $String ? "FRAME_TYPE_UNKNOWN" : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.leftWeight = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.leftWeight = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.totalWeight = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.totalWeight = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.selfSamples = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.selfSamples = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                            object.position = null;
                            object.sampleTypes = null;
                            object.diffDetails = null;
                            object.beforeMarker = false;
                            object.prunedChildrenCount = 0;
                        }
                        if (message.leftSamples != null && $Object.hasOwnProperty.call(message, "leftSamples"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.leftSamples = typeof message.leftSamples === "number" ? $BigInt(message.leftSamples) : $util.Long.fromBits(message.leftSamples.low >>> 0, message.leftSamples.high >>> 0, false).toBigInt();
                            else if (typeof message.leftSamples === "number")
                                object.leftSamples = options.longs === $String ? $String(message.leftSamples) : message.leftSamples;
                            else
                                object.leftSamples = options.longs === $String ? $util.Long.prototype.toString.call(message.leftSamples) : options.longs === $Number ? new $util.LongBits(message.leftSamples.low >>> 0, message.leftSamples.high >>> 0).toNumber() : message.leftSamples;
                        if (message.totalSamples != null && $Object.hasOwnProperty.call(message, "totalSamples"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.totalSamples = typeof message.totalSamples === "number" ? $BigInt(message.totalSamples) : $util.Long.fromBits(message.totalSamples.low >>> 0, message.totalSamples.high >>> 0, false).toBigInt();
                            else if (typeof message.totalSamples === "number")
                                object.totalSamples = options.longs === $String ? $String(message.totalSamples) : message.totalSamples;
                            else
                                object.totalSamples = options.longs === $String ? $util.Long.prototype.toString.call(message.totalSamples) : options.longs === $Number ? new $util.LongBits(message.totalSamples.low >>> 0, message.totalSamples.high >>> 0).toNumber() : message.totalSamples;
                        if (message.titleIndex != null && $Object.hasOwnProperty.call(message, "titleIndex"))
                            object.titleIndex = message.titleIndex;
                        if (message.type != null && $Object.hasOwnProperty.call(message, "type"))
                            object.type = options.enums === $String ? $root.cafe.jeffrey.flamegraph.proto.FrameType[message.type] === $undefined ? message.type : $root.cafe.jeffrey.flamegraph.proto.FrameType[message.type] : message.type;
                        if (message.leftWeight != null && $Object.hasOwnProperty.call(message, "leftWeight"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.leftWeight = typeof message.leftWeight === "number" ? $BigInt(message.leftWeight) : $util.Long.fromBits(message.leftWeight.low >>> 0, message.leftWeight.high >>> 0, false).toBigInt();
                            else if (typeof message.leftWeight === "number")
                                object.leftWeight = options.longs === $String ? $String(message.leftWeight) : message.leftWeight;
                            else
                                object.leftWeight = options.longs === $String ? $util.Long.prototype.toString.call(message.leftWeight) : options.longs === $Number ? new $util.LongBits(message.leftWeight.low >>> 0, message.leftWeight.high >>> 0).toNumber() : message.leftWeight;
                        if (message.totalWeight != null && $Object.hasOwnProperty.call(message, "totalWeight"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.totalWeight = typeof message.totalWeight === "number" ? $BigInt(message.totalWeight) : $util.Long.fromBits(message.totalWeight.low >>> 0, message.totalWeight.high >>> 0, false).toBigInt();
                            else if (typeof message.totalWeight === "number")
                                object.totalWeight = options.longs === $String ? $String(message.totalWeight) : message.totalWeight;
                            else
                                object.totalWeight = options.longs === $String ? $util.Long.prototype.toString.call(message.totalWeight) : options.longs === $Number ? new $util.LongBits(message.totalWeight.low >>> 0, message.totalWeight.high >>> 0).toNumber() : message.totalWeight;
                        if (message.selfSamples != null && $Object.hasOwnProperty.call(message, "selfSamples"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.selfSamples = typeof message.selfSamples === "number" ? $BigInt(message.selfSamples) : $util.Long.fromBits(message.selfSamples.low >>> 0, message.selfSamples.high >>> 0, false).toBigInt();
                            else if (typeof message.selfSamples === "number")
                                object.selfSamples = options.longs === $String ? $String(message.selfSamples) : message.selfSamples;
                            else
                                object.selfSamples = options.longs === $String ? $util.Long.prototype.toString.call(message.selfSamples) : options.longs === $Number ? new $util.LongBits(message.selfSamples.low >>> 0, message.selfSamples.high >>> 0).toNumber() : message.selfSamples;
                        if (message.position != null && $Object.hasOwnProperty.call(message, "position"))
                            object.position = $root.cafe.jeffrey.flamegraph.proto.FramePosition.toObject(message.position, options, _depth + 1);
                        if (message.sampleTypes != null && $Object.hasOwnProperty.call(message, "sampleTypes"))
                            object.sampleTypes = $root.cafe.jeffrey.flamegraph.proto.FrameSampleTypes.toObject(message.sampleTypes, options, _depth + 1);
                        if (message.diffDetails != null && $Object.hasOwnProperty.call(message, "diffDetails"))
                            object.diffDetails = $root.cafe.jeffrey.flamegraph.proto.DiffDetails.toObject(message.diffDetails, options, _depth + 1);
                        if (message.beforeMarker != null && $Object.hasOwnProperty.call(message, "beforeMarker"))
                            object.beforeMarker = message.beforeMarker;
                        if (message.prunedChildrenCount != null && $Object.hasOwnProperty.call(message, "prunedChildrenCount"))
                            object.prunedChildrenCount = message.prunedChildrenCount;
                        return object;
                    };

                    /**
                     * Converts this Frame to JSON.
                     * @function toJSON
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    Frame.prototype.toJSON = function() {
                        return Frame.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the type url for Frame
                     * @function getTypeUrl
                     * @memberof cafe.jeffrey.flamegraph.proto.Frame
                     * @static
                     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns {string} The type url
                     */
                    Frame.getTypeUrl = function(prefix) {
                        if (prefix === $undefined)
                            prefix = "type.googleapis.com";
                        return prefix + "/cafe.jeffrey.flamegraph.proto.Frame";
                    };

                    return Frame;
                })();

                /**
                 * FrameType enum.
                 * @name cafe.jeffrey.flamegraph.proto.FrameType
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
                 * @property {number} FRAME_TYPE_COLLAPSED_SYNTHETIC=15 FRAME_TYPE_COLLAPSED_SYNTHETIC value
                 * @property {number} FRAME_TYPE_TRUNCATED_SYNTHETIC=16 FRAME_TYPE_TRUNCATED_SYNTHETIC value
                 * @property {number} FRAME_TYPE_PYTHON=17 FRAME_TYPE_PYTHON value
                 * @property {number} FRAME_TYPE_JAVASCRIPT=18 FRAME_TYPE_JAVASCRIPT value
                 * @property {number} FRAME_TYPE_GO=19 FRAME_TYPE_GO value
                 * @property {number} FRAME_TYPE_OTHER_RUNTIME=20 FRAME_TYPE_OTHER_RUNTIME value
                 */
                proto.FrameType = (function() {
                    const valuesById = $Object.create(null), values = $Object.create(valuesById);
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
                    values[valuesById[15] = "FRAME_TYPE_COLLAPSED_SYNTHETIC"] = 15;
                    values[valuesById[16] = "FRAME_TYPE_TRUNCATED_SYNTHETIC"] = 16;
                    values[valuesById[17] = "FRAME_TYPE_PYTHON"] = 17;
                    values[valuesById[18] = "FRAME_TYPE_JAVASCRIPT"] = 18;
                    values[valuesById[19] = "FRAME_TYPE_GO"] = 19;
                    values[valuesById[20] = "FRAME_TYPE_OTHER_RUNTIME"] = 20;
                    return values;
                })();

                proto.FramePosition = (function() {

                    /**
                     * Properties of a FramePosition.
                     * @typedef {Object} cafe.jeffrey.flamegraph.proto.FramePosition.$Properties
                     * @property {number|null} [bci] FramePosition bci
                     * @property {number|null} [line] FramePosition line
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */

                    /**
                     * Properties of a FramePosition.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @interface IFramePosition
                     * @augments cafe.jeffrey.flamegraph.proto.FramePosition.$Properties
                     * @deprecated Use cafe.jeffrey.flamegraph.proto.FramePosition.$Properties instead.
                     */

                    /**
                     * Shape of a FramePosition.
                     * @typedef {cafe.jeffrey.flamegraph.proto.FramePosition.$Properties} cafe.jeffrey.flamegraph.proto.FramePosition.$Shape
                     */

                    /**
                     * Constructs a new FramePosition.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @classdesc Represents a FramePosition.
                     * @constructor
                     * @param {cafe.jeffrey.flamegraph.proto.FramePosition.$Properties=} [properties] Properties to set
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */
                    const FramePosition = function (properties) {
                        if (properties)
                            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    };

                    /**
                     * FramePosition bci.
                     * @member {number} bci
                     * @memberof cafe.jeffrey.flamegraph.proto.FramePosition
                     * @instance
                     */
                    FramePosition.prototype.bci = 0;

                    /**
                     * FramePosition line.
                     * @member {number} line
                     * @memberof cafe.jeffrey.flamegraph.proto.FramePosition
                     * @instance
                     */
                    FramePosition.prototype.line = 0;

                    /**
                     * Creates a new FramePosition instance using the specified properties.
                     * @function create
                     * @memberof cafe.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.FramePosition.$Properties=} [properties] Properties to set
                     * @returns {cafe.jeffrey.flamegraph.proto.FramePosition} FramePosition instance
                     * @type {{
                     *   (properties: cafe.jeffrey.flamegraph.proto.FramePosition.$Shape): cafe.jeffrey.flamegraph.proto.FramePosition & cafe.jeffrey.flamegraph.proto.FramePosition.$Shape;
                     *   (properties?: cafe.jeffrey.flamegraph.proto.FramePosition.$Properties): cafe.jeffrey.flamegraph.proto.FramePosition;
                     * }}
                     */
                    FramePosition.create = function(properties) {
                        return new FramePosition(properties);
                    };

                    /**
                     * Encodes the specified FramePosition message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.FramePosition.verify|verify} messages.
                     * @function encode
                     * @memberof cafe.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.FramePosition.$Properties} message FramePosition message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    FramePosition.encode = function (message, writer, _depth) {
                        if (!writer)
                            writer = $Writer.create();
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        if (message.bci != null && $Object.hasOwnProperty.call(message, "bci") && message.bci !== 0)
                            writer.uint32(/* id 1, wireType 0 =*/8).int32(message.bci);
                        if (message.line != null && $Object.hasOwnProperty.call(message, "line") && message.line !== 0)
                            writer.uint32(/* id 2, wireType 0 =*/16).int32(message.line);
                        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
                            for (let i = 0; i < message.$unknowns.length; ++i)
                                writer.raw(message.$unknowns[i]);
                        return writer;
                    };

                    /**
                     * Encodes the specified FramePosition message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.FramePosition.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.FramePosition.$Properties} message FramePosition message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    FramePosition.encodeDelimited = function(message, writer) {
                        return this.encode(message, (writer || $Writer.create()).fork()).ldelim();
                    };

                    /**
                     * Decodes a FramePosition message from the specified reader or buffer.
                     * @function decode
                     * @memberof cafe.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.FramePosition & cafe.jeffrey.flamegraph.proto.FramePosition.$Shape} FramePosition
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    FramePosition.decode = function (reader, length, _end, _depth, _target) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $Reader.recursionLimit)
                            throw $Error("max depth exceeded");
                        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.cafe.jeffrey.flamegraph.proto.FramePosition(), value;
                        while (reader.pos < end) {
                            let start = reader.pos;
                            let tag = reader.tag();
                            if (tag === _end) {
                                _end = $undefined;
                                break;
                            }
                            let wireType = tag & 7;
                            switch (tag >>>= 3) {
                            case 1: {
                                    if (wireType !== 0)
                                        break;
                                    if (value = reader.int32())
                                        message.bci = value;
                                    else
                                        delete message.bci;
                                    continue;
                                }
                            case 2: {
                                    if (wireType !== 0)
                                        break;
                                    if (value = reader.int32())
                                        message.line = value;
                                    else
                                        delete message.line;
                                    continue;
                                }
                            }
                            reader.skipType(wireType, _depth, tag);
                            if (!reader.discardUnknown) {
                                $util.makeProp(message, "$unknowns", false);
                                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
                            }
                        }
                        if (_end !== $undefined)
                            throw $Error("missing end group");
                        return message;
                    };

                    /**
                     * Decodes a FramePosition message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.FramePosition & cafe.jeffrey.flamegraph.proto.FramePosition.$Shape} FramePosition
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    FramePosition.decodeDelimited = function(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a FramePosition message.
                     * @function verify
                     * @memberof cafe.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    FramePosition.verify = function (message, _depth) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            return "max depth exceeded";
                        if (message.bci != null && $Object.hasOwnProperty.call(message, "bci"))
                            if (!$util.isInteger(message.bci))
                                return "bci: integer expected";
                        if (message.line != null && $Object.hasOwnProperty.call(message, "line"))
                            if (!$util.isInteger(message.line))
                                return "line: integer expected";
                        return null;
                    };

                    /**
                     * Creates a FramePosition message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof cafe.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {cafe.jeffrey.flamegraph.proto.FramePosition} FramePosition
                     */
                    FramePosition.fromObject = function (object, _depth) {
                        if (object instanceof $root.cafe.jeffrey.flamegraph.proto.FramePosition)
                            return object;
                        if (!$util.isObject(object))
                            throw $TypeError(".cafe.jeffrey.flamegraph.proto.FramePosition: object expected");
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let message = new $root.cafe.jeffrey.flamegraph.proto.FramePosition();
                        if (object.bci != null)
                            if ($Number(object.bci) !== 0)
                                message.bci = object.bci | 0;
                        if (object.line != null)
                            if ($Number(object.line) !== 0)
                                message.line = object.line | 0;
                        return message;
                    };

                    /**
                     * Creates a plain object from a FramePosition message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof cafe.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.FramePosition} message FramePosition
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    FramePosition.toObject = function (message, options, _depth) {
                        if (!options)
                            options = {};
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let object = {};
                        if (options.defaults) {
                            object.bci = 0;
                            object.line = 0;
                        }
                        if (message.bci != null && $Object.hasOwnProperty.call(message, "bci"))
                            object.bci = message.bci;
                        if (message.line != null && $Object.hasOwnProperty.call(message, "line"))
                            object.line = message.line;
                        return object;
                    };

                    /**
                     * Converts this FramePosition to JSON.
                     * @function toJSON
                     * @memberof cafe.jeffrey.flamegraph.proto.FramePosition
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    FramePosition.prototype.toJSON = function() {
                        return FramePosition.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the type url for FramePosition
                     * @function getTypeUrl
                     * @memberof cafe.jeffrey.flamegraph.proto.FramePosition
                     * @static
                     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns {string} The type url
                     */
                    FramePosition.getTypeUrl = function(prefix) {
                        if (prefix === $undefined)
                            prefix = "type.googleapis.com";
                        return prefix + "/cafe.jeffrey.flamegraph.proto.FramePosition";
                    };

                    return FramePosition;
                })();

                proto.FrameSampleTypes = (function() {

                    /**
                     * Properties of a FrameSampleTypes.
                     * @typedef {Object} cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties
                     * @property {number|Long|null} [inlined] FrameSampleTypes inlined
                     * @property {number|Long|null} [c1] FrameSampleTypes c1
                     * @property {number|Long|null} [interpret] FrameSampleTypes interpret
                     * @property {number|Long|null} [jit] FrameSampleTypes jit
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */

                    /**
                     * Properties of a FrameSampleTypes.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @interface IFrameSampleTypes
                     * @augments cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties
                     * @deprecated Use cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties instead.
                     */

                    /**
                     * Shape of a FrameSampleTypes.
                     * @typedef {cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties} cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Shape
                     */

                    /**
                     * Constructs a new FrameSampleTypes.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @classdesc Represents a FrameSampleTypes.
                     * @constructor
                     * @param {cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties=} [properties] Properties to set
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */
                    const FrameSampleTypes = function (properties) {
                        if (properties)
                            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    };

                    /**
                     * FrameSampleTypes inlined.
                     * @member {number|Long} inlined
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @instance
                     */
                    FrameSampleTypes.prototype.inlined = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * FrameSampleTypes c1.
                     * @member {number|Long} c1
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @instance
                     */
                    FrameSampleTypes.prototype.c1 = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * FrameSampleTypes interpret.
                     * @member {number|Long} interpret
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @instance
                     */
                    FrameSampleTypes.prototype.interpret = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * FrameSampleTypes jit.
                     * @member {number|Long} jit
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @instance
                     */
                    FrameSampleTypes.prototype.jit = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Creates a new FrameSampleTypes instance using the specified properties.
                     * @function create
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties=} [properties] Properties to set
                     * @returns {cafe.jeffrey.flamegraph.proto.FrameSampleTypes} FrameSampleTypes instance
                     * @type {{
                     *   (properties: cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Shape): cafe.jeffrey.flamegraph.proto.FrameSampleTypes & cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Shape;
                     *   (properties?: cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties): cafe.jeffrey.flamegraph.proto.FrameSampleTypes;
                     * }}
                     */
                    FrameSampleTypes.create = function(properties) {
                        return new FrameSampleTypes(properties);
                    };

                    /**
                     * Encodes the specified FrameSampleTypes message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.FrameSampleTypes.verify|verify} messages.
                     * @function encode
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties} message FrameSampleTypes message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    FrameSampleTypes.encode = function (message, writer, _depth) {
                        if (!writer)
                            writer = $Writer.create();
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        if (message.inlined != null && $Object.hasOwnProperty.call(message, "inlined") && (typeof message.inlined === "object" ? message.inlined.low || message.inlined.high : message.inlined !== 0))
                            writer.uint32(/* id 1, wireType 0 =*/8).int64(message.inlined);
                        if (message.c1 != null && $Object.hasOwnProperty.call(message, "c1") && (typeof message.c1 === "object" ? message.c1.low || message.c1.high : message.c1 !== 0))
                            writer.uint32(/* id 2, wireType 0 =*/16).int64(message.c1);
                        if (message.interpret != null && $Object.hasOwnProperty.call(message, "interpret") && (typeof message.interpret === "object" ? message.interpret.low || message.interpret.high : message.interpret !== 0))
                            writer.uint32(/* id 3, wireType 0 =*/24).int64(message.interpret);
                        if (message.jit != null && $Object.hasOwnProperty.call(message, "jit") && (typeof message.jit === "object" ? message.jit.low || message.jit.high : message.jit !== 0))
                            writer.uint32(/* id 4, wireType 0 =*/32).int64(message.jit);
                        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
                            for (let i = 0; i < message.$unknowns.length; ++i)
                                writer.raw(message.$unknowns[i]);
                        return writer;
                    };

                    /**
                     * Encodes the specified FrameSampleTypes message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.FrameSampleTypes.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Properties} message FrameSampleTypes message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    FrameSampleTypes.encodeDelimited = function(message, writer) {
                        return this.encode(message, (writer || $Writer.create()).fork()).ldelim();
                    };

                    /**
                     * Decodes a FrameSampleTypes message from the specified reader or buffer.
                     * @function decode
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.FrameSampleTypes & cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Shape} FrameSampleTypes
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    FrameSampleTypes.decode = function (reader, length, _end, _depth, _target) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $Reader.recursionLimit)
                            throw $Error("max depth exceeded");
                        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.cafe.jeffrey.flamegraph.proto.FrameSampleTypes(), value;
                        while (reader.pos < end) {
                            let start = reader.pos;
                            let tag = reader.tag();
                            if (tag === _end) {
                                _end = $undefined;
                                break;
                            }
                            let wireType = tag & 7;
                            switch (tag >>>= 3) {
                            case 1: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.inlined = value;
                                    else
                                        delete message.inlined;
                                    continue;
                                }
                            case 2: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.c1 = value;
                                    else
                                        delete message.c1;
                                    continue;
                                }
                            case 3: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.interpret = value;
                                    else
                                        delete message.interpret;
                                    continue;
                                }
                            case 4: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.jit = value;
                                    else
                                        delete message.jit;
                                    continue;
                                }
                            }
                            reader.skipType(wireType, _depth, tag);
                            if (!reader.discardUnknown) {
                                $util.makeProp(message, "$unknowns", false);
                                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
                            }
                        }
                        if (_end !== $undefined)
                            throw $Error("missing end group");
                        return message;
                    };

                    /**
                     * Decodes a FrameSampleTypes message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.FrameSampleTypes & cafe.jeffrey.flamegraph.proto.FrameSampleTypes.$Shape} FrameSampleTypes
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    FrameSampleTypes.decodeDelimited = function(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a FrameSampleTypes message.
                     * @function verify
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    FrameSampleTypes.verify = function (message, _depth) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            return "max depth exceeded";
                        if (message.inlined != null && $Object.hasOwnProperty.call(message, "inlined"))
                            if (!$util.isInteger(message.inlined) && !(message.inlined && $util.isInteger(message.inlined.low) && $util.isInteger(message.inlined.high)))
                                return "inlined: integer|Long expected";
                        if (message.c1 != null && $Object.hasOwnProperty.call(message, "c1"))
                            if (!$util.isInteger(message.c1) && !(message.c1 && $util.isInteger(message.c1.low) && $util.isInteger(message.c1.high)))
                                return "c1: integer|Long expected";
                        if (message.interpret != null && $Object.hasOwnProperty.call(message, "interpret"))
                            if (!$util.isInteger(message.interpret) && !(message.interpret && $util.isInteger(message.interpret.low) && $util.isInteger(message.interpret.high)))
                                return "interpret: integer|Long expected";
                        if (message.jit != null && $Object.hasOwnProperty.call(message, "jit"))
                            if (!$util.isInteger(message.jit) && !(message.jit && $util.isInteger(message.jit.low) && $util.isInteger(message.jit.high)))
                                return "jit: integer|Long expected";
                        return null;
                    };

                    /**
                     * Creates a FrameSampleTypes message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {cafe.jeffrey.flamegraph.proto.FrameSampleTypes} FrameSampleTypes
                     */
                    FrameSampleTypes.fromObject = function (object, _depth) {
                        if (object instanceof $root.cafe.jeffrey.flamegraph.proto.FrameSampleTypes)
                            return object;
                        if (!$util.isObject(object))
                            throw $TypeError(".cafe.jeffrey.flamegraph.proto.FrameSampleTypes: object expected");
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let message = new $root.cafe.jeffrey.flamegraph.proto.FrameSampleTypes();
                        if (object.inlined != null)
                            if (typeof object.inlined === "object" ? object.inlined.low || object.inlined.high : $Number(object.inlined) !== 0)
                                if ($util.Long)
                                    message.inlined = $util.Long.fromValue(object.inlined, false);
                                else if (typeof object.inlined === "string")
                                    message.inlined = $parseInt(object.inlined, 10);
                                else if (typeof object.inlined === "number")
                                    message.inlined = object.inlined;
                                else if (typeof object.inlined === "object")
                                    message.inlined = new $util.LongBits(object.inlined.low >>> 0, object.inlined.high >>> 0).toNumber();
                        if (object.c1 != null)
                            if (typeof object.c1 === "object" ? object.c1.low || object.c1.high : $Number(object.c1) !== 0)
                                if ($util.Long)
                                    message.c1 = $util.Long.fromValue(object.c1, false);
                                else if (typeof object.c1 === "string")
                                    message.c1 = $parseInt(object.c1, 10);
                                else if (typeof object.c1 === "number")
                                    message.c1 = object.c1;
                                else if (typeof object.c1 === "object")
                                    message.c1 = new $util.LongBits(object.c1.low >>> 0, object.c1.high >>> 0).toNumber();
                        if (object.interpret != null)
                            if (typeof object.interpret === "object" ? object.interpret.low || object.interpret.high : $Number(object.interpret) !== 0)
                                if ($util.Long)
                                    message.interpret = $util.Long.fromValue(object.interpret, false);
                                else if (typeof object.interpret === "string")
                                    message.interpret = $parseInt(object.interpret, 10);
                                else if (typeof object.interpret === "number")
                                    message.interpret = object.interpret;
                                else if (typeof object.interpret === "object")
                                    message.interpret = new $util.LongBits(object.interpret.low >>> 0, object.interpret.high >>> 0).toNumber();
                        if (object.jit != null)
                            if (typeof object.jit === "object" ? object.jit.low || object.jit.high : $Number(object.jit) !== 0)
                                if ($util.Long)
                                    message.jit = $util.Long.fromValue(object.jit, false);
                                else if (typeof object.jit === "string")
                                    message.jit = $parseInt(object.jit, 10);
                                else if (typeof object.jit === "number")
                                    message.jit = object.jit;
                                else if (typeof object.jit === "object")
                                    message.jit = new $util.LongBits(object.jit.low >>> 0, object.jit.high >>> 0).toNumber();
                        return message;
                    };

                    /**
                     * Creates a plain object from a FrameSampleTypes message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.FrameSampleTypes} message FrameSampleTypes
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    FrameSampleTypes.toObject = function (message, options, _depth) {
                        if (!options)
                            options = {};
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let object = {};
                        if (options.defaults) {
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.inlined = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.inlined = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.c1 = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.c1 = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.interpret = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.interpret = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.jit = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.jit = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                        }
                        if (message.inlined != null && $Object.hasOwnProperty.call(message, "inlined"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.inlined = typeof message.inlined === "number" ? $BigInt(message.inlined) : $util.Long.fromBits(message.inlined.low >>> 0, message.inlined.high >>> 0, false).toBigInt();
                            else if (typeof message.inlined === "number")
                                object.inlined = options.longs === $String ? $String(message.inlined) : message.inlined;
                            else
                                object.inlined = options.longs === $String ? $util.Long.prototype.toString.call(message.inlined) : options.longs === $Number ? new $util.LongBits(message.inlined.low >>> 0, message.inlined.high >>> 0).toNumber() : message.inlined;
                        if (message.c1 != null && $Object.hasOwnProperty.call(message, "c1"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.c1 = typeof message.c1 === "number" ? $BigInt(message.c1) : $util.Long.fromBits(message.c1.low >>> 0, message.c1.high >>> 0, false).toBigInt();
                            else if (typeof message.c1 === "number")
                                object.c1 = options.longs === $String ? $String(message.c1) : message.c1;
                            else
                                object.c1 = options.longs === $String ? $util.Long.prototype.toString.call(message.c1) : options.longs === $Number ? new $util.LongBits(message.c1.low >>> 0, message.c1.high >>> 0).toNumber() : message.c1;
                        if (message.interpret != null && $Object.hasOwnProperty.call(message, "interpret"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.interpret = typeof message.interpret === "number" ? $BigInt(message.interpret) : $util.Long.fromBits(message.interpret.low >>> 0, message.interpret.high >>> 0, false).toBigInt();
                            else if (typeof message.interpret === "number")
                                object.interpret = options.longs === $String ? $String(message.interpret) : message.interpret;
                            else
                                object.interpret = options.longs === $String ? $util.Long.prototype.toString.call(message.interpret) : options.longs === $Number ? new $util.LongBits(message.interpret.low >>> 0, message.interpret.high >>> 0).toNumber() : message.interpret;
                        if (message.jit != null && $Object.hasOwnProperty.call(message, "jit"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.jit = typeof message.jit === "number" ? $BigInt(message.jit) : $util.Long.fromBits(message.jit.low >>> 0, message.jit.high >>> 0, false).toBigInt();
                            else if (typeof message.jit === "number")
                                object.jit = options.longs === $String ? $String(message.jit) : message.jit;
                            else
                                object.jit = options.longs === $String ? $util.Long.prototype.toString.call(message.jit) : options.longs === $Number ? new $util.LongBits(message.jit.low >>> 0, message.jit.high >>> 0).toNumber() : message.jit;
                        return object;
                    };

                    /**
                     * Converts this FrameSampleTypes to JSON.
                     * @function toJSON
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    FrameSampleTypes.prototype.toJSON = function() {
                        return FrameSampleTypes.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the type url for FrameSampleTypes
                     * @function getTypeUrl
                     * @memberof cafe.jeffrey.flamegraph.proto.FrameSampleTypes
                     * @static
                     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns {string} The type url
                     */
                    FrameSampleTypes.getTypeUrl = function(prefix) {
                        if (prefix === $undefined)
                            prefix = "type.googleapis.com";
                        return prefix + "/cafe.jeffrey.flamegraph.proto.FrameSampleTypes";
                    };

                    return FrameSampleTypes;
                })();

                proto.DiffDetails = (function() {

                    /**
                     * Properties of a DiffDetails.
                     * @typedef {Object} cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties
                     * @property {number|Long|null} [samples] DiffDetails samples
                     * @property {number|Long|null} [weight] DiffDetails weight
                     * @property {number|null} [percentSamples] DiffDetails percentSamples
                     * @property {number|null} [percentWeight] DiffDetails percentWeight
                     * @property {number|Long|null} [secondarySamples] DiffDetails secondarySamples
                     * @property {number|Long|null} [secondaryWeight] DiffDetails secondaryWeight
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */

                    /**
                     * Properties of a DiffDetails.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @interface IDiffDetails
                     * @augments cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties
                     * @deprecated Use cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties instead.
                     */

                    /**
                     * Shape of a DiffDetails.
                     * @typedef {cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties} cafe.jeffrey.flamegraph.proto.DiffDetails.$Shape
                     */

                    /**
                     * Constructs a new DiffDetails.
                     * @memberof cafe.jeffrey.flamegraph.proto
                     * @classdesc Represents a DiffDetails.
                     * @constructor
                     * @param {cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties=} [properties] Properties to set
                     * @property {Array.<Uint8Array>} [$unknowns] Unknown fields preserved while decoding when enabled
                     */
                    const DiffDetails = function (properties) {
                        if (properties)
                            for (let keys = $Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    };

                    /**
                     * DiffDetails samples.
                     * @member {number|Long} samples
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @instance
                     */
                    DiffDetails.prototype.samples = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * DiffDetails weight.
                     * @member {number|Long} weight
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @instance
                     */
                    DiffDetails.prototype.weight = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * DiffDetails percentSamples.
                     * @member {number} percentSamples
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @instance
                     */
                    DiffDetails.prototype.percentSamples = 0;

                    /**
                     * DiffDetails percentWeight.
                     * @member {number} percentWeight
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @instance
                     */
                    DiffDetails.prototype.percentWeight = 0;

                    /**
                     * DiffDetails secondarySamples.
                     * @member {number|Long} secondarySamples
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @instance
                     */
                    DiffDetails.prototype.secondarySamples = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * DiffDetails secondaryWeight.
                     * @member {number|Long} secondaryWeight
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @instance
                     */
                    DiffDetails.prototype.secondaryWeight = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Creates a new DiffDetails instance using the specified properties.
                     * @function create
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties=} [properties] Properties to set
                     * @returns {cafe.jeffrey.flamegraph.proto.DiffDetails} DiffDetails instance
                     * @type {{
                     *   (properties: cafe.jeffrey.flamegraph.proto.DiffDetails.$Shape): cafe.jeffrey.flamegraph.proto.DiffDetails & cafe.jeffrey.flamegraph.proto.DiffDetails.$Shape;
                     *   (properties?: cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties): cafe.jeffrey.flamegraph.proto.DiffDetails;
                     * }}
                     */
                    DiffDetails.create = function(properties) {
                        return new DiffDetails(properties);
                    };

                    /**
                     * Encodes the specified DiffDetails message. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.DiffDetails.verify|verify} messages.
                     * @function encode
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties} message DiffDetails message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    DiffDetails.encode = function (message, writer, _depth) {
                        if (!writer)
                            writer = $Writer.create();
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        if (message.samples != null && $Object.hasOwnProperty.call(message, "samples") && (typeof message.samples === "object" ? message.samples.low || message.samples.high : message.samples !== 0))
                            writer.uint32(/* id 1, wireType 0 =*/8).int64(message.samples);
                        if (message.weight != null && $Object.hasOwnProperty.call(message, "weight") && (typeof message.weight === "object" ? message.weight.low || message.weight.high : message.weight !== 0))
                            writer.uint32(/* id 2, wireType 0 =*/16).int64(message.weight);
                        if (message.percentSamples != null && $Object.hasOwnProperty.call(message, "percentSamples") && !$Object.is(message.percentSamples, 0))
                            writer.uint32(/* id 3, wireType 5 =*/29).float(message.percentSamples);
                        if (message.percentWeight != null && $Object.hasOwnProperty.call(message, "percentWeight") && !$Object.is(message.percentWeight, 0))
                            writer.uint32(/* id 4, wireType 5 =*/37).float(message.percentWeight);
                        if (message.secondarySamples != null && $Object.hasOwnProperty.call(message, "secondarySamples") && (typeof message.secondarySamples === "object" ? message.secondarySamples.low || message.secondarySamples.high : message.secondarySamples !== 0))
                            writer.uint32(/* id 5, wireType 0 =*/40).int64(message.secondarySamples);
                        if (message.secondaryWeight != null && $Object.hasOwnProperty.call(message, "secondaryWeight") && (typeof message.secondaryWeight === "object" ? message.secondaryWeight.low || message.secondaryWeight.high : message.secondaryWeight !== 0))
                            writer.uint32(/* id 6, wireType 0 =*/48).int64(message.secondaryWeight);
                        if (message.$unknowns != null && $Object.hasOwnProperty.call(message, "$unknowns"))
                            for (let i = 0; i < message.$unknowns.length; ++i)
                                writer.raw(message.$unknowns[i]);
                        return writer;
                    };

                    /**
                     * Encodes the specified DiffDetails message, length delimited. Does not implicitly {@link cafe.jeffrey.flamegraph.proto.DiffDetails.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.DiffDetails.$Properties} message DiffDetails message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    DiffDetails.encodeDelimited = function(message, writer) {
                        return this.encode(message, (writer || $Writer.create()).fork()).ldelim();
                    };

                    /**
                     * Decodes a DiffDetails message from the specified reader or buffer.
                     * @function decode
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {cafe.jeffrey.flamegraph.proto.DiffDetails & cafe.jeffrey.flamegraph.proto.DiffDetails.$Shape} DiffDetails
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    DiffDetails.decode = function (reader, length, _end, _depth, _target) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $Reader.recursionLimit)
                            throw $Error("max depth exceeded");
                        let end = length === $undefined ? reader.len : reader.pos + length, message = _target || new $root.cafe.jeffrey.flamegraph.proto.DiffDetails(), value;
                        while (reader.pos < end) {
                            let start = reader.pos;
                            let tag = reader.tag();
                            if (tag === _end) {
                                _end = $undefined;
                                break;
                            }
                            let wireType = tag & 7;
                            switch (tag >>>= 3) {
                            case 1: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.samples = value;
                                    else
                                        delete message.samples;
                                    continue;
                                }
                            case 2: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.weight = value;
                                    else
                                        delete message.weight;
                                    continue;
                                }
                            case 3: {
                                    if (wireType !== 5)
                                        break;
                                    if (!$Object.is(value = reader.float(), 0))
                                        message.percentSamples = value;
                                    else
                                        delete message.percentSamples;
                                    continue;
                                }
                            case 4: {
                                    if (wireType !== 5)
                                        break;
                                    if (!$Object.is(value = reader.float(), 0))
                                        message.percentWeight = value;
                                    else
                                        delete message.percentWeight;
                                    continue;
                                }
                            case 5: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.secondarySamples = value;
                                    else
                                        delete message.secondarySamples;
                                    continue;
                                }
                            case 6: {
                                    if (wireType !== 0)
                                        break;
                                    if (typeof (value = reader.int64()) === "object" ? value.low || value.high : value !== 0)
                                        message.secondaryWeight = value;
                                    else
                                        delete message.secondaryWeight;
                                    continue;
                                }
                            }
                            reader.skipType(wireType, _depth, tag);
                            if (!reader.discardUnknown) {
                                $util.makeProp(message, "$unknowns", false);
                                (message.$unknowns || (message.$unknowns = [])).push(reader.raw(start, reader.pos));
                            }
                        }
                        if (_end !== $undefined)
                            throw $Error("missing end group");
                        return message;
                    };

                    /**
                     * Decodes a DiffDetails message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {cafe.jeffrey.flamegraph.proto.DiffDetails & cafe.jeffrey.flamegraph.proto.DiffDetails.$Shape} DiffDetails
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    DiffDetails.decodeDelimited = function(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a DiffDetails message.
                     * @function verify
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    DiffDetails.verify = function (message, _depth) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            return "max depth exceeded";
                        if (message.samples != null && $Object.hasOwnProperty.call(message, "samples"))
                            if (!$util.isInteger(message.samples) && !(message.samples && $util.isInteger(message.samples.low) && $util.isInteger(message.samples.high)))
                                return "samples: integer|Long expected";
                        if (message.weight != null && $Object.hasOwnProperty.call(message, "weight"))
                            if (!$util.isInteger(message.weight) && !(message.weight && $util.isInteger(message.weight.low) && $util.isInteger(message.weight.high)))
                                return "weight: integer|Long expected";
                        if (message.percentSamples != null && $Object.hasOwnProperty.call(message, "percentSamples"))
                            if (typeof message.percentSamples !== "number")
                                return "percentSamples: number expected";
                        if (message.percentWeight != null && $Object.hasOwnProperty.call(message, "percentWeight"))
                            if (typeof message.percentWeight !== "number")
                                return "percentWeight: number expected";
                        if (message.secondarySamples != null && $Object.hasOwnProperty.call(message, "secondarySamples"))
                            if (!$util.isInteger(message.secondarySamples) && !(message.secondarySamples && $util.isInteger(message.secondarySamples.low) && $util.isInteger(message.secondarySamples.high)))
                                return "secondarySamples: integer|Long expected";
                        if (message.secondaryWeight != null && $Object.hasOwnProperty.call(message, "secondaryWeight"))
                            if (!$util.isInteger(message.secondaryWeight) && !(message.secondaryWeight && $util.isInteger(message.secondaryWeight.low) && $util.isInteger(message.secondaryWeight.high)))
                                return "secondaryWeight: integer|Long expected";
                        return null;
                    };

                    /**
                     * Creates a DiffDetails message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {cafe.jeffrey.flamegraph.proto.DiffDetails} DiffDetails
                     */
                    DiffDetails.fromObject = function (object, _depth) {
                        if (object instanceof $root.cafe.jeffrey.flamegraph.proto.DiffDetails)
                            return object;
                        if (!$util.isObject(object))
                            throw $TypeError(".cafe.jeffrey.flamegraph.proto.DiffDetails: object expected");
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let message = new $root.cafe.jeffrey.flamegraph.proto.DiffDetails();
                        if (object.samples != null)
                            if (typeof object.samples === "object" ? object.samples.low || object.samples.high : $Number(object.samples) !== 0)
                                if ($util.Long)
                                    message.samples = $util.Long.fromValue(object.samples, false);
                                else if (typeof object.samples === "string")
                                    message.samples = $parseInt(object.samples, 10);
                                else if (typeof object.samples === "number")
                                    message.samples = object.samples;
                                else if (typeof object.samples === "object")
                                    message.samples = new $util.LongBits(object.samples.low >>> 0, object.samples.high >>> 0).toNumber();
                        if (object.weight != null)
                            if (typeof object.weight === "object" ? object.weight.low || object.weight.high : $Number(object.weight) !== 0)
                                if ($util.Long)
                                    message.weight = $util.Long.fromValue(object.weight, false);
                                else if (typeof object.weight === "string")
                                    message.weight = $parseInt(object.weight, 10);
                                else if (typeof object.weight === "number")
                                    message.weight = object.weight;
                                else if (typeof object.weight === "object")
                                    message.weight = new $util.LongBits(object.weight.low >>> 0, object.weight.high >>> 0).toNumber();
                        if (object.percentSamples != null)
                            if (!$Object.is($Number(object.percentSamples), 0))
                                message.percentSamples = $Number(object.percentSamples);
                        if (object.percentWeight != null)
                            if (!$Object.is($Number(object.percentWeight), 0))
                                message.percentWeight = $Number(object.percentWeight);
                        if (object.secondarySamples != null)
                            if (typeof object.secondarySamples === "object" ? object.secondarySamples.low || object.secondarySamples.high : $Number(object.secondarySamples) !== 0)
                                if ($util.Long)
                                    message.secondarySamples = $util.Long.fromValue(object.secondarySamples, false);
                                else if (typeof object.secondarySamples === "string")
                                    message.secondarySamples = $parseInt(object.secondarySamples, 10);
                                else if (typeof object.secondarySamples === "number")
                                    message.secondarySamples = object.secondarySamples;
                                else if (typeof object.secondarySamples === "object")
                                    message.secondarySamples = new $util.LongBits(object.secondarySamples.low >>> 0, object.secondarySamples.high >>> 0).toNumber();
                        if (object.secondaryWeight != null)
                            if (typeof object.secondaryWeight === "object" ? object.secondaryWeight.low || object.secondaryWeight.high : $Number(object.secondaryWeight) !== 0)
                                if ($util.Long)
                                    message.secondaryWeight = $util.Long.fromValue(object.secondaryWeight, false);
                                else if (typeof object.secondaryWeight === "string")
                                    message.secondaryWeight = $parseInt(object.secondaryWeight, 10);
                                else if (typeof object.secondaryWeight === "number")
                                    message.secondaryWeight = object.secondaryWeight;
                                else if (typeof object.secondaryWeight === "object")
                                    message.secondaryWeight = new $util.LongBits(object.secondaryWeight.low >>> 0, object.secondaryWeight.high >>> 0).toNumber();
                        return message;
                    };

                    /**
                     * Creates a plain object from a DiffDetails message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {cafe.jeffrey.flamegraph.proto.DiffDetails} message DiffDetails
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    DiffDetails.toObject = function (message, options, _depth) {
                        if (!options)
                            options = {};
                        if (_depth === $undefined)
                            _depth = 0;
                        if (_depth > $util.recursionLimit)
                            throw $Error("max depth exceeded");
                        let object = {};
                        if (options.defaults) {
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.samples = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.samples = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.weight = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.weight = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                            object.percentSamples = 0;
                            object.percentWeight = 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.secondarySamples = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.secondarySamples = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.secondaryWeight = options.longs === $String ? long.toString() : options.longs === $Number ? long.toNumber() : typeof $BigInt !== "undefined" && options.longs === $BigInt ? long.toBigInt() : long;
                            } else
                                object.secondaryWeight = options.longs === $String ? "0" : typeof $BigInt !== "undefined" && options.longs === $BigInt ? $BigInt("0") : 0;
                        }
                        if (message.samples != null && $Object.hasOwnProperty.call(message, "samples"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.samples = typeof message.samples === "number" ? $BigInt(message.samples) : $util.Long.fromBits(message.samples.low >>> 0, message.samples.high >>> 0, false).toBigInt();
                            else if (typeof message.samples === "number")
                                object.samples = options.longs === $String ? $String(message.samples) : message.samples;
                            else
                                object.samples = options.longs === $String ? $util.Long.prototype.toString.call(message.samples) : options.longs === $Number ? new $util.LongBits(message.samples.low >>> 0, message.samples.high >>> 0).toNumber() : message.samples;
                        if (message.weight != null && $Object.hasOwnProperty.call(message, "weight"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.weight = typeof message.weight === "number" ? $BigInt(message.weight) : $util.Long.fromBits(message.weight.low >>> 0, message.weight.high >>> 0, false).toBigInt();
                            else if (typeof message.weight === "number")
                                object.weight = options.longs === $String ? $String(message.weight) : message.weight;
                            else
                                object.weight = options.longs === $String ? $util.Long.prototype.toString.call(message.weight) : options.longs === $Number ? new $util.LongBits(message.weight.low >>> 0, message.weight.high >>> 0).toNumber() : message.weight;
                        if (message.percentSamples != null && $Object.hasOwnProperty.call(message, "percentSamples"))
                            object.percentSamples = options.json && !$isFinite(message.percentSamples) ? $String(message.percentSamples) : message.percentSamples;
                        if (message.percentWeight != null && $Object.hasOwnProperty.call(message, "percentWeight"))
                            object.percentWeight = options.json && !$isFinite(message.percentWeight) ? $String(message.percentWeight) : message.percentWeight;
                        if (message.secondarySamples != null && $Object.hasOwnProperty.call(message, "secondarySamples"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.secondarySamples = typeof message.secondarySamples === "number" ? $BigInt(message.secondarySamples) : $util.Long.fromBits(message.secondarySamples.low >>> 0, message.secondarySamples.high >>> 0, false).toBigInt();
                            else if (typeof message.secondarySamples === "number")
                                object.secondarySamples = options.longs === $String ? $String(message.secondarySamples) : message.secondarySamples;
                            else
                                object.secondarySamples = options.longs === $String ? $util.Long.prototype.toString.call(message.secondarySamples) : options.longs === $Number ? new $util.LongBits(message.secondarySamples.low >>> 0, message.secondarySamples.high >>> 0).toNumber() : message.secondarySamples;
                        if (message.secondaryWeight != null && $Object.hasOwnProperty.call(message, "secondaryWeight"))
                            if (typeof $BigInt !== "undefined" && options.longs === $BigInt)
                                object.secondaryWeight = typeof message.secondaryWeight === "number" ? $BigInt(message.secondaryWeight) : $util.Long.fromBits(message.secondaryWeight.low >>> 0, message.secondaryWeight.high >>> 0, false).toBigInt();
                            else if (typeof message.secondaryWeight === "number")
                                object.secondaryWeight = options.longs === $String ? $String(message.secondaryWeight) : message.secondaryWeight;
                            else
                                object.secondaryWeight = options.longs === $String ? $util.Long.prototype.toString.call(message.secondaryWeight) : options.longs === $Number ? new $util.LongBits(message.secondaryWeight.low >>> 0, message.secondaryWeight.high >>> 0).toNumber() : message.secondaryWeight;
                        return object;
                    };

                    /**
                     * Converts this DiffDetails to JSON.
                     * @function toJSON
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    DiffDetails.prototype.toJSON = function() {
                        return DiffDetails.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the type url for DiffDetails
                     * @function getTypeUrl
                     * @memberof cafe.jeffrey.flamegraph.proto.DiffDetails
                     * @static
                     * @param {string} [prefix] Custom type url prefix, defaults to `"type.googleapis.com"`
                     * @returns {string} The type url
                     */
                    DiffDetails.getTypeUrl = function(prefix) {
                        if (prefix === $undefined)
                            prefix = "type.googleapis.com";
                        return prefix + "/cafe.jeffrey.flamegraph.proto.DiffDetails";
                    };

                    return DiffDetails;
                })();

                return proto;
            })();

            return flamegraph;
        })();

        return jeffrey;
    })();

    return cafe;
})();

export {
  $root as default
};
