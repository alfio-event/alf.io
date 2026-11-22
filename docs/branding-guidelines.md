# Admin Branding Guidelines

Visual reference for the admin frontend: layout patterns, tables, cards, color and spacing tokens, and the index of the CSS classes in `src/styles.ts`. The rules for forms, buttons and dialogs are in [`admin-style-guide.md`](admin-style-guide.md), which takes precedence over this document.

All styling relies exclusively on Shoelace CSS custom properties (`--sl-*`) and shared style blocks from `src/styles.ts`. No new color palettes or fonts.

## Shared style imports

Every component should import the style blocks it needs from `../styles.ts`. The canonical set for a full-page component is:

```ts
import {badges, base, dialog, form, modernLayout, modernTable, retroCompat, row, spacing, textColors} from '../styles.ts';
```

| Import | Purpose |
|---|---|
| `base` | Default font size, line height, antialiasing |
| `retroCompat` | Bootstrap compat: headings, `.container`, `.col`, `.text-right`, `.text-center`, `.mt-*`, `.hidden-xs` |
| `spacing` | `.wMarginTop10px` |
| `form` | Label weight, error border colors, `.error-text`, `.input-with-action` |
| `dialog` | Dialog z-index, `.responsive-dialog` width, dialog titles and sections, loading placeholder |
| `row` | `.row` CSS-grid with `--alfio-row-cols` |
| `badges` | `sl-badge::part(base) { border: unset; }` |
| `textColors` | `.text-success`, `.text-muted`, `.text-danger` |
| `modernLayout` | Page title, filter toolbar, section cards, "add another" card button, form stack, featured card, dialogs, empty state |
| `choiceCards` | Choice cards and the panel of the selected choice, see `choiceCards()` |
| `attendeeFields` | Grid of the additional fields of an attendee, see `attendeeFields()` |
| `csvImport` | Steps of a CSV import and its file specifications, see `csvImport()` |
| `modernTable` | Table, striped variant, headers, row hover, actions cell, username link |

## 1. Page layout

### Page title

Use the `page-title-row` + `page-separator` pattern (from `modernLayout`):

```html
<div class="page-title-row">
    <h1>Page title</h1>
</div>
<hr class="page-separator">
<p class="page-description text-muted">One sentence that says what the page is for.</p>
```

The `page-title-row` is a flex container that keeps the heading top-aligned, with the things that belong to the title beside it (a status badge, an Edit button). The optional description goes below the separator, never inside the title row. Pages of an event or a subscription use `page-title-row secondary` ("Reservations for *Event*").

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

The action buttons on the right share the size of "Add new" (`large`), so that the bar has one height. Filter controls on the left, and a Refresh button next to them, stay small. When a list belongs to an organization, put `organizationFilter()` (`components/organization-filter.ts`) on the left: it renders nothing for users of a single organization.

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

Import `modernLayout` and use `section-card` to group sections (e.g. Enabled / Disabled lists). Each card has a header bar with icon + title + optional count badge, and a body area. Render it with `sectionCard()` (`components/section-card.ts`), which produces this markup:

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

- Structure, width, close button and footer: see the Dialogs section of `admin-style-guide.md`
- Two-column layout for side-by-side fields via `row` (`--alfio-row-cols: 2`)
- Icon prefixes on inputs: `person` for usernames, `envelope` for email, `card-text` for descriptions

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

Variants, sizes, icons and placement of buttons are defined in the Buttons section of `admin-style-guide.md`.

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

## 11. Browser support

The admin supports the same browsers as the public frontend (`frontend/public/.browserslistrc`), down to Safari 14.1: organizers often use old laptops. The E2E suite runs on Safari 14.1.

- JavaScript syntax is transpiled and missing APIs (e.g. `structuredClone`, `Array.prototype.at`) are polyfilled by the build (`frontend/admin/vite.config.js`).
- CSS inside Lit's `` css`...` `` templates is **not** processed. Don't use features newer than Safari 14.1 there without a fallback, e.g. `:has()` (15.4), `color-mix()` (16.2), CSS nesting (16.5), container queries (16), `@layer` (15.4). Prefer a class set from the template's state, or wrap the newer feature in `@supports`.

---

## Reference: CSS class index

Classes from `modernLayout` (import `modernLayout` from `styles.ts`):

| Class | Purpose |
|---|---|
| `page-title-row` | Flex header for `<h1>` + siblings |
| `page-separator` | Thin `<hr>` under title |
| `page-description` | One-sentence description below the separator |
| `filter-toolbar` | Toolbar bar with filters + actions |
| `filter-left` / `filter-right` | Toolbar sub-columns |
| `filter-select` | Select with reset margin-top |
| `filter-clear-btn` | X button beside select |
| `section-card` | Card with header bar + body |
| `section-header` | Header with icon, title, badge |
| `section-body` | Content body wrapper |
| `section-card-add` | Dashed, full-width button that adds one more section card |
| `form-stack` | Vertical stack of form controls, spaced by the gap |
| `label-hidden` | Keeps the label of a control for screen readers only |
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
| `row` | `.row` | CSS-grid with `--alfio-row-cols` |
| `creationLayout` | `.creation-layout`, `.creation-content` | Two-column layout of a long creation form: section index + content |
| `creationLayout` | `.section-nav`, `.mobile-section-nav` | Sticky section index (desktop) / "Jump to section" select (mobile), rendered by `<alfio-section-nav>` |
| `creationLayout` | `.form-section` | One navigable group of a creation form, introduced by an `h2` |
| `form` | `.input-with-action` | Control with a button beside it (e.g. select + Refresh) |
| `choiceCards` | `.choice-cards`, `.choice-card`, `.choice-panel` | Rendered by `choiceCards()` |
| `attendeeFields` | `.attendee-fields` | Rendered by `attendeeFields()` |
| `csvImport` | `.import-steps`, `.import-step`, `.step-number`, `.file-specs` | Rendered by `csvImport()` |
| `dialog` | `.responsive-dialog` | Dialog width, with `--alfio-dialog-max-width` |
| `badges` | `sl-badge::part(base)` | Remove badge border |
| `textColors` | `.text-muted`, `.text-success`, `.text-danger` | Semantic text color |