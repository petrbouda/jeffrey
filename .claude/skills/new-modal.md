# Add a Modal

Add a modal dialog using the project's single canonical modal component, `GenericModal`. Never build a custom overlay.

## Usage

```
/new-modal
```

## Overview

All dialogs use `components/GenericModal.vue` with `v-model:show`. Pick the `size` by content; large editors use the **wide near-fullscreen** `events-modal-dialog` pattern so the modal has equal gutters on all sides, the body is the single scroll container, and the footer stays pinned.

## GenericModal API

- **Props**: `modalId` (unique string), `title`, `icon` (e.g. `"bi bi-shield-check"`), `size` (`sm | md | lg | xl | fullscreen`, default `fullscreen`), `modalDialogClass`, `showFooter` (default `true`).
- **v-model**: `v-model:show` (boolean).
- **Slots**: default = body; `#header` (replaces title + close button); `#title` (replaces title text, icon kept); `#footer` (replaces default Close button).

## Size decision guide

| Size | When |
|---|---|
| `md` | Simple forms, a single input, confirmations |
| `lg` | Radio/list pickers, single-column content |
| `xl` | Rich content, two-column forms, data tables |
| **wide** (`events-modal-dialog`) | Large editors that should fill the screen with even gutters |

## Workflow

### Standard modal (md / lg / xl)

```vue
<GenericModal
  v-model:show="showDialog"
  modal-id="myThingModal"
  title="Edit thing"
  icon="bi bi-pencil"
  size="md"
  modal-dialog-class="modal-dialog-centered"
>
  <!-- body -->
  <form id="myThingForm" @submit.prevent="save"> … </form>

  <template #footer>
    <button class="btn btn-secondary" @click="showDialog = false">Cancel</button>
    <button type="submit" form="myThingForm" class="btn btn-primary" :disabled="saving">Save</button>
  </template>
</GenericModal>
```

Drive it with simple refs: `const showDialog = ref(false)` and open/close by setting it.

### Wide near-fullscreen modal (large editors)

Add `events-modal-dialog modal-dialog-centered` plus your own dialog class, then override the width in scoped CSS so left/right gutters equal Bootstrap's `1.75rem` top/bottom margin:

```vue
<GenericModal
  v-model:show="showEditor"
  modal-id="myEditorModal"
  :title="editorTitle"
  icon="bi bi-shield-check"
  size="xl"
  modal-dialog-class="my-editor-dialog events-modal-dialog modal-dialog-centered"
>
  <!-- body scrolls as one unit -->
  <template #footer>
    <button class="btn btn-secondary" @click="showEditor = false">Cancel</button>
    <button class="btn btn-primary" :disabled="saving">Save</button>
  </template>
</GenericModal>
```

```css
/* scoped — give the dialog even gutters all around */
:deep(.modal-dialog.my-editor-dialog) {
  max-width: none;
  width: calc(100vw - 3.5rem);
}
```

Notes:
- `events-modal-dialog` is a global class in `assets/styles.scss`; it sets dialog height to `calc(100vh - 3.5rem)`, makes `.modal-content` a flex column, and makes `.modal-body` the single scroll container (footer stays pinned). Do not also add `modal-dialog-scrollable` — they conflict.
- The `:deep(.modal-dialog.<name>)` selector is required because the dialog is rendered by the child `GenericModal`; the doubled class beats Bootstrap's `.modal-xl` width.

### Conventions

- Footer buttons: `.btn .btn-secondary` (Cancel) + `.btn .btn-primary` (primary action); destructive uses `.btn .btn-danger`.
- Disable the primary button while saving (`:disabled="saving"`).
- Use `Badge.vue` for badges, design tokens for any colors/shadows/radii.

## Reference Examples

- **Wide two-column editor**: `jeffrey-microscope/pages-microscope/src/views/global/GuardiansView.vue`
- **md form**: `jeffrey-microscope/pages-microscope/src/components/EditNameModal.vue`
- **xl data modal**: `jeffrey-microscope/pages-microscope/src/components/gc/GCEventDetailsModal.vue`
- **Global wide-modal CSS**: `.events-modal-dialog` in `jeffrey-microscope/pages-microscope/src/assets/styles.scss`

## Verification

`cd jeffrey-microscope/pages-microscope && npm run build`
