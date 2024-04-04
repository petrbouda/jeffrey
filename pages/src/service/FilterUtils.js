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
