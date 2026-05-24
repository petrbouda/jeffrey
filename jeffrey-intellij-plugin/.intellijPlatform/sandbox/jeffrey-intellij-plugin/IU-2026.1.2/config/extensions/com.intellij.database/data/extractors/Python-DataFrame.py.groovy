/*
 * Available context bindings:
 *   COLUMNS     List<DataColumn>
 *   ROWS        Iterable<DataRow>
 *   OUT         { append() }
 *   FORMATTER   { format(row, col); formatValue(Object, col); getTypeName(Object, col); isStringLiteral(Object, col); }
 *   TRANSPOSED  Boolean
 * plus ALL_COLUMNS, TABLE, DIALECT
 *
 * where:
 *   DataRow     { rowNumber(); first(); last(); data(): List<Object>; value(column): Object }
 *   DataColumn  { columnNumber(), name() }
 */


def escapePyString(String s) {
  if (s == null) return ""
  s.replace("\\", "\\\\").replace("'", "\\'")
}

def isNumericLiteral(String s) {
  s != null && s.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")
}

def toPythonLiteral(Object value, col) {
  if (value == null) return "None"
  if (value instanceof Boolean) return value ? "True" : "False"

  def formatted = FORMATTER.formatValue(value, col)
  if (formatted == null) return "None"

  if (!FORMATTER.isStringLiteral(value, col) && isNumericLiteral(formatted)) {
    return formatted
  }

  "'" + escapePyString(formatted) + "'"
}

def joinComma(List<String> items) {
  items.join(", ")
}

def columnLiterals = COLUMNS.collect { [] as List<String> }

ROWS.each { row ->
  COLUMNS.eachWithIndex { col, idx ->
    columnLiterals[idx] << toPythonLiteral(row.value(col), col)
  }
}

def columnsPython = COLUMNS
  .withIndex()
  .collect { col, idx ->
    "'${escapePyString(col.name())}': [${joinComma(columnLiterals[idx])}]"
  }

OUT.append(
  "import pandas as pd\n\n" +
  "data = {${joinComma(columnsPython)}}\n" +
  "df = pd.DataFrame(data)\n"
)