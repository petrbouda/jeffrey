/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import {FilterMatchMode, FilterOperator} from "primevue/api";

export default class FilterUtils {

    static DATE_DEFAULT = {
        operator: FilterOperator.AND,
        constraints: [{value: null, matchMode: FilterMatchMode.EQUALS}]
    }

    static NUMERIC_DEFAULT = {
        operator: FilterOperator.AND,
        constraints: [{value: null, matchMode: FilterMatchMode.EQUALS}]
    }

    static STRING_DEFAULT = {
        operator: FilterOperator.AND,
        constraints: [{value: null, matchMode: FilterMatchMode.CONTAINS}]
    }

    // ---------------------------------------------------------------
    // [
    //     {
    //         "field": "startTime",
    //         "header": "Start Time",
    //         "type": "jdk.jfr.Timestamp",
    //         "description": null
    //     }, ...
    // ]
    // ---------------------------------------------------------------
    //  {
    //      'startTime.value': {operator: FilterOperator.AND, constraints: [{value: null, matchMode: FilterMatchMode.EQUALS}]},
    //      name: {value: null, matchMode: FilterMatchMode.CONTAINS},
    //      baseAddress: {operator: FilterOperator.AND, constraints: [{value: null, matchMode: FilterMatchMode.EQUALS}]},
    //      topAddress: {value: null, matchMode: FilterMatchMode.CONTAINS},
    //  }
    // ---------------------------------------------------------------
    // jdk.jfr.Percentage
    // jdk.jfr.Timespan
    // jdk.jfr.Timestamp
    // jdk.jfr.Frequency
    // jdk.jfr.BooleanFlag
    // jdk.jfr.MemoryAddress
    // jdk.jfr.DataAmount
    // jdk.jfr.Unsigned -> "byte", "short", "int", "long"
    // jdk.jfr.snippets.Temperature
    // ---------------------------------------------------------------
    // Columns DataTypes
    // {
    //   text: [
    //     ot.STARTS_WITH,
    //     ot.CONTAINS,
    //     ot.NOT_CONTAINS,
    //     ot.ENDS_WITH,
    //     ot.EQUALS,
    //     ot.NOT_EQUALS
    //   ],
    //       numeric: [
    //   ot.EQUALS,
    //   ot.NOT_EQUALS,
    //   ot.LESS_THAN,
    //   ot.LESS_THAN_OR_EQUAL_TO,
    //   ot.GREATER_THAN,
    //   ot.GREATER_THAN_OR_EQUAL_TO
    // ],
    //     date: [
    //   ot.DATE_IS,
    //   ot.DATE_IS_NOT,
    //   ot.DATE_BEFORE,
    //   ot.DATE_AFTER
    // ]
    //      boolean: ??
    // }
    static createFilters(columns) {
        const result = {}
        columns.forEach((col) => {
            if (col.type === "jdk.jfr.Timestamp") {
                result[col.field] = FilterUtils.NUMERIC_DEFAULT
            } else if (col.type === "jdk.jfr.Unsigned"
                || col.type === "jdk.jfr.DataAmount"
                || col.type === "jdk.jfr.MemoryAddress"
                || col.type === "jdk.jfr.Frequency"
                || col.type === "jdk.jfr.Timespan"
                || col.type === "jdk.jfr.Percentage") {

                result[col.field] = FilterUtils.NUMERIC_DEFAULT
            } else {
                result[col.field] = FilterUtils.STRING_DEFAULT
            }
        })
        return result
    }
}
