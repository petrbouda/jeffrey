# Add a Data Table

Add a data table using the project's single canonical table design — the `components/table/DataTable.vue` family. Do NOT hand-roll `<div class="table-responsive"><table>`.

## Usage

```
/data-table
```

## Overview

Every data table is built from four components in `components/table/`:

| Component | Role |
|---|---|
| `DataTable` | Card wrapper; renders `.table-responsive` + `<table class="table table-sm table-hover mb-0">`. Slots: `#toolbar`, default (`<thead>`/`<tbody>`), `#footer`. Optional prop `tableClass`. |
| `TableToolbar` | Header bar. `v-model` = search text, prop `searchPlaceholder`, prop `showSearch` (default true). Slots: default (left side), `#filters` (right side). |
| `SortableTableHeader` | Sortable `<th>`. Props `column`, `label`, `sortColumn`, `sortDirection` (`asc|desc`), `align` (`start|end`), `width?`. Emits `sort`. |
| `TableShowMore` | Pagination footer. Props `shown`, `matchCount`, `total`, `expanded`, `pageSize`. Emits `toggle`. |

When there is no data, render `<EmptyState>` as a sibling instead of an empty `DataTable`.

## Workflow

### Imports

```ts
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import EmptyState from '@/components/EmptyState.vue';
import Badge from '@/components/Badge.vue';
```

### Template skeleton

```vue
<EmptyState
  v-if="rows.length === 0"
  icon="bi-inbox"
  title="No rows"
  description="Nothing matches the current filter."
/>
<DataTable v-else>
  <template #toolbar>
    <TableToolbar v-model="query" search-placeholder="Filter rows…">
      <!-- left side: label / context -->
      <span class="toolbar-info">Rows</span>
      <template #filters>
        <Badge key-label="Total" :value="matchCount" variant="secondary" size="s" borderless />
      </template>
    </TableToolbar>
  </template>

  <thead>
    <tr>
      <SortableTableHeader
        column="name"
        label="Name"
        :sort-column="sortColumn"
        :sort-direction="sortDirection"
        @sort="onSort"
      />
      <SortableTableHeader
        column="count"
        label="Count"
        align="end"
        :sort-column="sortColumn"
        :sort-direction="sortDirection"
        @sort="onSort"
      />
    </tr>
  </thead>

  <tbody>
    <tr v-for="row in visible" :key="row.id">
      <td>{{ row.name }}</td>
      <td class="text-end">{{ FormattingService.formatNumber(row.count) }}</td>
    </tr>
  </tbody>

  <template #footer>
    <TableShowMore
      :shown="visible.length"
      :match-count="matchCount"
      :total="rows.length"
      :expanded="expanded"
      :page-size="pageSize"
      @toggle="expanded = !expanded"
    />
  </template>
</DataTable>
```

### Conventions

- Plain (non-sortable) columns are just `<th>`; right-align numeric columns/cells with `class="text-end"`.
- Sorting: keep `sortColumn` + `sortDirection` refs; `onSort(col)` toggles direction when the same column is clicked, otherwise switches column.
- Pagination: keep `expanded` + `pageSize`; `visible` is the filtered list sliced to `pageSize` unless `expanded`. `TableShowMore` only renders when `matchCount > pageSize`.
- Format every value through `FormattingService` (numbers, bytes, durations, percentages, timestamps) — never `new Date()` or raw `toFixed`.
- Use `Badge.vue` for badges; design tokens for any colors/shadows/radii.
- Do NOT reintroduce the old pattern: a bare `<div class="table-responsive"><table class="table …">` — `DataTable` already provides that markup inside a styled card.

## Reference Examples

- `jeffrey-microscope/pages-microscope/src/views/profiles/detail/ProfileThreadDumps.vue`
- `jeffrey-microscope/pages-microscope/src/views/profiles/detail/ProfileSecurity.vue`
- Components: `jeffrey-microscope/pages-microscope/src/components/table/`

## Verification

`cd jeffrey-microscope/pages-microscope && npm run build`
