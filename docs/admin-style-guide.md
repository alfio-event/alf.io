# Admin Frontend Style Guide

## Important:

when working on an admin frontend component, always check if the style class has been already defined in `styles.ts`.
If so, use it as it is.  
Limit code duplication and custom styling to the bare minimum.
We don't want code duplication to rise significantly after a modification. Enforce DRY pattern.

check `./branding-guidelines.md` for more details about the new style we're going to introduce

## Dialog footers

- Always wrap footer buttons in `<div slot="footer">` with `<sl-divider>` above.
- Use the 3-column grid row for button placement:
  ```html
  <div slot="footer">
      <sl-divider></sl-divider>
      <div class="row" style="--alfio-row-cols: 3">
          <sl-button variant="default" size="large" ...>Cancel</sl-button>
          <div></div>
          <sl-button variant="success" size="large" ...>Save</sl-button>
      </div>
  </div>
  ```
- Import `row` from `src/styles.ts` and add it to `static styles`. This provides
  the `--alfio-row-cols` grid.
- If a component already has a local `.row` CSS rule (Bootstrap-style
  float/margin clearfix), rename it to `.bs-row` to avoid conflicts.
- Use `variant="default"` for Cancel/Close, `variant="success"` for Save,
  `variant="warning"` for destructive actions (e.g. Confirm rotation).
- Buttons are always `size="large"`.
- An empty `<div></div>` spacer separates the buttons.

## Form fields

- Shoelace controls (`sl-input`, `sl-select`, `sl-textarea`) already render their
  own label via the `label` attribute. Do **not** add a separate `<label>` HTML
  element — it causes the label to appear twice.
- Use `sl-alert variant="danger"` for validation errors and `sl-alert variant="primary"`
  for informational messages.
- Icons: use `sl-icon` with Shoelace icon names (Bootstrap Icons subset). Remove
  any `injectFontAwesome` helper.
- Add `hoist` to every `sl-select` and `sl-dropdown` inside a `section-card`, a dialog
  or any other container with `overflow: hidden`. Without it the menu is clipped by the
  container and may show a single option:
  ```html
  <sl-select label="Scope" hoist>...</sl-select>
  ```
  `selectField()` from `components/form-field.ts` already sets it.

## Date formatting

- Use `<alfio-format-date>` (`src/components/format-date.ts`) for all dates. Do **not** use
  `<sl-format-date>` or format dates by hand (`toLocaleString`, `Intl.DateTimeFormat`, …).
- The display format is defined once, admin-wide, in `src/service/date-format.ts`
  (default: `Jun 30, 2026, 14:00`). Do not pass month/day/hour options to the component;
  change the format with `setDateFormatPreferences()` and every date re-renders.
- Pass the backend value as it is: no `.replace(' ', 'T')`, no appended `Z`.
  - **Instants** (ISO-8601 with offset or `Z`, e.g. a `ZonedDateTime`) are converted to `time-zone`.
    Set it to the `Event` time zone when an event is in scope; without it, the browser time zone is used.
  - **Wall-clock strings** (`yyyy-MM-dd HH:mm`, no offset) are already expressed in the event
    time zone and are displayed unchanged.
- Usage:
  ```html
  <!-- date + time (default) -->
  <alfio-format-date date=${log.timestamp}></alfio-format-date>
  <!-- instant shown in the event time zone, with a fallback when the value is empty -->
  <alfio-format-date .date=${payment.transactionTimestamp} time-zone=${event.timeZone}>—</alfio-format-date>
  <!-- interval -->
  <alfio-format-date date=${ev.formattedBegin} end=${ev.formattedEnd}></alfio-format-date>
  <!-- date only / time only -->
  <alfio-format-date date=${value} format="date"></alfio-format-date>
  ```
- Import the component where it is used: `import '../components/format-date.ts';`.

## Buttons

- Replace ALL `<button class="btn">` with the equivalent `<sl-button>`  
- "Add new" buttons MUST always use `variant="success"`, and having size "large". Icon inside must be plus-circle
- "Save" buttons MUST always use `variant="warning"`
- "Cancel/Close" buttons MUST always use `variant="default"` 

## Icons

- Replace `<i class="fa fa-...">` with Shoelace `<sl-icon name="...">`.
- Icon names map to Bootstrap Icons bundled with `@shoelace-style/shoelace` 2.20.1;
  available names can be found under
  `node_modules/@shoelace-style/shoelace/dist/assets/icons/`.
- Shoelace CSS is already loaded via `main.ts`.
- Update CSS selectors that targeted `i` (e.g. `.btn-xs i`) to target `sl-icon`.
- Icons inside `<sl-button>` should use `slot="prefix"`, not bare children.

## Shoelace-native components

- Always prefer Shoelace native components over Bootstrap-emulated CSS classes.
- Use `<sl-button>` instead of `<button class="btn ...">`, and `<sl-alert>` instead
  of `<div class="alert ...">`.
- Remove any Bootstrap-style CSS emulation from the component's `styles` block
  when switching.
- Reference patterns:
  - `<sl-button variant="success">` instead of `<button class="btn btn-success">`
  - `<sl-button variant="default">` instead of `<button class="btn btn-default">`
  - `<sl-button variant="primary" size="small">` instead of `<button class="btn btn-primary btn-xs">`
  - `<sl-button variant="text">` instead of `<button class="btn btn-link">`
  - `<sl-alert variant="danger"><sl-icon slot="icon" name="exclamation-triangle"></sl-icon>Message</sl-alert>` instead of `<div class="alert alert-danger">Message</div>`
  - `<sl-alert variant="primary"><sl-icon slot="icon" name="info-circle"></sl-icon>Message</sl-alert>` instead of `<div class="alert alert-info">Message</div>`

## Badges

- Replace `<span class="label label-*">` with `<sl-badge>`.
- Variants: `variant="success"` for `label-success`, `variant="primary"` for `label-info` (shoelace has no `info` variant), `variant="warning"` for `label-warning`, `variant="danger"` for `label-danger`.
- Always add `pill` for rounded appearance (established convention across the codebase).
- Import `badges` from `src/styles.ts` and add it to the component's `static styles` (provides `sl-badge::part(base) { border: unset; }`).
- Reference patterns:
  - `<sl-badge variant="primary" pill>Text</sl-badge>` instead of `<span class="label label-info">Text</span>`
  - `<sl-badge variant="danger" pill>Text</sl-badge>` instead of `<span class="label label-danger">Text</span>`
  - `<sl-badge variant="warning" pill>Text</sl-badge>` instead of `<span class="label label-warning">Text</span>`
  - `<sl-badge variant="success" pill>Text</sl-badge>` instead of `<span class="label label-success">Text</span>`

## CSV bulk import

- Use a native `<input type="file" accept="text/csv">` with `FileReader` to read
  the file as text.
- Parse CSV with a streaming parser that tracks quote state — do **not** split by
  lines first, because quoted fields can contain newlines.
- Only the first column of each row is used as the description.
- Display parsed results in a `table.table-striped` with a Total footer row.
- Use `repeat(items, (_, i) => i, renderFn)` to avoid duplicate key issues when
  descriptions are repeated.
- CSV specs to display: no header, comma separator, double-quote quote char,
  backslash escape char.


## Style imports

Components typically import from `../styles.ts`:
`pageHeader`, `retroCompat`, `panelStyles`, `spacing`, `form`, `dialog`, `row`,
`textColors`, `cardBgColors`, `badges`, `itemsList`.

The `retroCompat` style provides Bootstrap-like classes: `.row` (Bootstrap
style), `.col`, `.col-xs-*`, `.table-striped`, `.text-muted`, `.wMarginTop10px`,
`.container`, etc.
