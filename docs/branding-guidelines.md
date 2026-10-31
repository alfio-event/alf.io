# Admin Branding Guidelines

Established conventions for the admin frontend introduced during the Users / API Keys redesign. Use this guide when building or migrating new pages to ensure visual consistency across the application.

All styling relies exclusively on Shoelace CSS custom properties (`--sl-*`) and shared style blocks from `src/styles.ts`. No new color palettes or fonts.

## Shared style imports

Every component should import the style blocks it needs from `../styles.ts`. The canonical set for a full-page component is:

```ts
import {badges, base, dialog, form, modernLayout, modernTable, pageHeader, panelStyles, retroCompat, row, spacing, textColors} from '../styles.ts';
```

| Import | Purpose |
|---|---|
| `base` | Default font size, line height, antialiasing |
| `retroCompat` | Bootstrap compat: headings, `.container`, `.col`, `.text-right`, `.text-center`, `.mt-*`, `.hidden-xs` |
| `panelStyles` | `.panel`, `.list-group-item`, `.btn` compat, responsive `.col` grid |
| `spacing` | `.wMarginTop10px` |
| `pageHeader` | `.page-header`, `.border-bottom` |
| `form` | Label weight, error border colors, `.error-text` |
| `dialog` | Dialog z-index |
| `row` | `.row` CSS-grid with `--alfio-row-cols` |
| `badges` | `sl-badge::part(base) { border: unset; }` |
| `textColors` | `.text-success`, `.text-muted`, `.text-danger` |
| `modernLayout` | Page title, filter toolbar, section cards, featured card, dialogs, empty state |
| `modernTable` | Table, striped variant, headers, row hover, actions cell, username link |

## 1. Page layout

### Page title

Use the `page-title-row` + `page-separator` pattern (from `modernLayout`) instead of `page-header`:

```html
<div class="page-title-row">
    <h1>Page Title</h1>
</div>
<hr class="page-separator">
```

The `page-title-row` is a flex container that allows side-by-side elements (e.g. subtitle) while keeping the heading top-aligned. Reset heading margins so it sits consistently under the navbar.

### Container

Always wrap page content in `<div class="container">` (max-width 1170px, auto-centered).

## 2. Filter toolbar

When a page has filters and action buttons, use the `filter-toolbar` layout (from `modernLayout`): a single rounded bar with a light gray background, containing filter controls on the left and action buttons on the right.

```html
<div class="filter-toolbar">
    <div class="filter-left">
        <!-- sl-select filters with clear buttons -->
    </div>
    <div class="filter-right">
        <!-- action buttons: Add new, Bulk, Download -->
    </div>
</div>
```

Filter selects should use `class="filter-select"` (resets default `margin-top`, sets a flex-basis of 170px). Pair each filter with a small clear button using `name="x-circle"` and `class="filter-clear-btn"`.

When no filters apply, collapse to a right-aligned toolbar:

```html
<div class="filter-toolbar" style="justify-content: flex-end;">
    <div class="filter-right">
        <!-- action buttons only -->
    </div>
</div>
```

## 3. Section cards

Import `modernLayout` and use `section-card` to group sections (e.g. Enabled / Disabled lists). Each card has a header bar with icon + title + optional count badge, and a body area.

```html
<div class="section-card">
    <div class="section-header">
        <sl-icon name="check-circle" style="color: var(--sl-color-success-600)"></sl-icon>
        <h3>Section Title</h3>
        <sl-badge variant="success" pill>12</sl-badge>
    </div>
    <div class="section-body">
        <!-- table or other content -->
    </div>
</div>
```

### Icon semantics

| State | Icon | Color |
|---|---|---|
| Active / Enabled | `check-circle` | `--sl-color-success-600` |
| Disabled / Inactive | `eye-slash` | `--sl-color-gray-500` |

## 4. Tables

Import `modernTable` from `styles.ts`. All table classes are shadow-scoped to the component that imports this block.

### Headers

The shared `.table` class provides: uppercase, `letter-spacing: 0.025em`, `font-weight: 600`, muted color (`--sl-color-gray-700`), bold bottom border (`2px solid --sl-color-gray-300`).

### Rows

- Smooth hover transition to `--sl-color-primary-50`
- `vertical-align: middle` on cells
- Striped variant (`.table-striped`): odd rows use `--sl-color-gray-50`

### Role / organization cells

Wrap labels in `<sl-badge>` with `pill`:
- `variant="primary"` for roles
- `variant="default"` for organizations

### Actions column

Group buttons in a `flex` container (`class="actions-cell"`) with `gap: var(--sl-spacing-2x-small)` and `justify-content: flex-end`. Use short labels ("Edit", "Reset", "QR", "Disable", "Delete").

### Empty state

When no rows match filters, use the `empty-state` class (from `modernLayout`):

```html
<div class="empty-state">
    <sl-icon name="inbox"></sl-icon>
    <span>No items found</span>
</div>
```

The icon uses `--sl-icon-size: var(--sl-font-size-4x-large)` and `color: var(--sl-color-gray-300)`.

## 5. Specialized cards

Import `modernLayout` from `styles.ts`.

### API key / secret display

For displaying secrets or long identifiers, use `secret-display`:

```html
<div class="secret-display">the-key-value</div>
```

This renders with `font-family: var(--sl-font-mono)`, `font-size: var(--sl-font-size-small)`, a 44px min-height, and `word-break: break-all`.

Action buttons for secrets go below in a `flex` row (`class="api-key-actions"`) with `size="small"` buttons.

### Featured card (highlighted section)

For prominent cards (System API Key, call-to-action sections), use `featured-card`:

```html
<div class="featured-card">
    <h1><sl-icon name="key-fill"></sl-icon> Title</h1>
    <small class="text-muted">Description text.</small>
    <!-- content -->
</div>
```

## 6. Dialogs

Import `modernLayout` from `styles.ts` for credential card, confirmation dialog, and bulk specs styles.

### Form dialogs

- Use `<sl-dialog>` with `size="large"` and `placement="bottom"`
- Two-column layout for side-by-side fields via `row` (`--alfio-row-cols: 2`)
- Icon prefixes on inputs: `person` for usernames, `envelope` for email, `card-text` for descriptions
- Footer follows the established 3-column grid convention (see `admin-style-guide.md`)

### Credential display

Present credentials in a labeled grid instead of `<pre>`:

```html
<div class="credentials-card">
    <div class="cred-row">
        <span class="cred-label">Username</span>
        <span class="cred-value">john</span>
    </div>
    <div class="cred-row">
        <span class="cred-label">Password</span>
        <span class="cred-value">secret</span>
    </div>
</div>
```

QR images get a border, padding, and rounded corners:

```html
<div class="qr-wrapper">
    <img alt="QR code" src="...">
</div>
```

### Confirmation dialogs

- Use `label` attribute on `<sl-dialog>` for the title
- Wrap body in `class="confirm-dialog-body"`
- `<strong>` text for warnings uses `var(--sl-color-danger-600)` color

### Bulk import specs

Use `class="bulk-specs"` for styled specification blocks with nested `<pre>` elements.

## 7. Buttons

Button conventions are defined in `admin-style-guide.md`. This guide follows those conventions:

- **"Add new"** buttons: `variant="success"`, `size="large"`, icon `plus-circle`
- **"Save"** buttons: `variant="warning"` per `admin-style-guide.md`
- **"Cancel" / "Close"** buttons: `variant="default"`
- **"Delete"**: `variant="danger"`
- **Secondary actions** (Bulk creation, etc.): `variant="primary"`
- **Destructive confirmations** (Rotate, Disable): `variant="warning"`

Dialog and toolbar inline action buttons may use `size="small"`. Footer action buttons follow the style guide convention: `size="large"`.

Prefix icons on all action buttons: `plus-circle` for add, `pencil` for edit, `trash` for delete, `clipboard` for copy, `qr-code` for QR, `x-circle` for clear, `check2` for confirm.

## 8. Color palette (Shoelace tokens only)

Do not introduce raw hex colors. Use Shoelace design tokens:

- Backgrounds: `--sl-color-neutral-5`, `--sl-color-gray-50`
- Borders: `--sl-color-gray-200`, `--sl-color-gray-300`
- Text muted: `--sl-color-gray-500`, `--sl-color-gray-600`, `--sl-color-gray-700`
- Primary highlight: `--sl-color-primary-50` (hover), `--sl-color-primary-50` / `--sl-color-primary-200` (featured card)
- Primary text/links: `--sl-color-primary-600`, `--sl-color-primary-700`
- Success: `--sl-color-success-600`
- Danger: `--sl-color-danger-600`

## 9. Spacing

Use Shoelace spacing tokens exclusively (`--sl-spacing-2x-small` through `--sl-spacing-large`). Avoid pixel-based margins and padding in component CSS.

## 10. Monospace text

API keys, usernames in code context, and other technical identifiers use `font-family: var(--sl-font-mono)` at `font-size: var(--sl-font-size-small)`.

---

## Reference: CSS class index

Classes from `modernLayout` (import `modernLayout` from `styles.ts`):

| Class | Purpose |
|---|---|
| `page-title-row` | Flex header for `<h1>` + siblings |
| `page-separator` | Thin `<hr>` under title |
| `filter-toolbar` | Toolbar bar with filters + actions |
| `filter-left` / `filter-right` | Toolbar sub-columns |
| `filter-select` | Select with reset margin-top |
| `filter-clear-btn` | X button beside select |
| `section-card` | Card with header bar + body |
| `section-header` | Header with icon, title, badge |
| `section-body` | Content body wrapper |
| `empty-state` | Zero-results placeholder |
| `featured-card` | Prominent card with primary tint |
| `secret-display` | Monospaced secret/key field |
| `api-key-actions` | Flex action bar under secret |
| `credentials-card`, `cred-row` | Key-value credential display |
| `qr-wrapper` | Styled QR image container |
| `confirm-dialog-body` | Confirmation dialog text body |
| `bulk-specs` | Styled file specification block |

Classes from `modernTable` (import `modernTable` from `styles.ts`):

| Class | Purpose |
|---|---|
| `table` | Responsive data table |
| `table-striped` | Striped table variant |
| `table-responsive` | Horizontal scroll wrapper |
| `actions-cell` | Flex container for row buttons |
| `username-link` | Styled clickable username |

Classes from other shared blocks:

| Import | Class | Purpose |
|---|---|---|
| `retroCompat` | `.container` | Auto-centered max-width wrapper |
| `retroCompat` | `.text-center`, `.text-right` | Text alignment |
| `retroCompat` | `.mt-*`, `.ms-*` | Margin utilities |
| `retroCompat` | `.hidden-xs`, `.hidden-sm` | Responsive visibility |
| `panelStyles` | `.panel`, `.panel-heading`, `.panel-body` | Bootstrap panel compat |
| `row` | `.row` | CSS-grid with `--alfio-row-cols` |
| `badges` | `sl-badge::part(base)` | Remove badge border |
| `textColors` | `.text-muted`, `.text-success`, `.text-danger` | Semantic text color |