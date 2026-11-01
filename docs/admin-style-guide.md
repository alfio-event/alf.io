# Admin frontend style guide

Rules for every screen of `frontend/admin` (Lit + Shoelace), new or migrated from the AngularJS admin.
Read it before you start, and use it again to review the result. Frontend paths are relative to
`frontend/admin/src`, the others to the repository root.

- [`branding-guidelines.md`](branding-guidelines.md) is the visual reference: color and spacing tokens, tables,
  cards and the index of the CSS classes in `styles.ts`. Open it when you style something this guide doesn't cover.
- Some older screens predate these rules. When a screen and this guide disagree, follow the guide.

## 1. Principles

The rules below apply these principles. When a case isn't covered, decide by them.

1. **Reuse first.** Look for a shared CSS block in `styles.ts` and for a helper in `components/`
   (see [Building blocks](#2-building-blocks)) before writing your own. Extend the shared block rather than
   copying its CSS into a component.
2. **One primary action.** A form or a dialog has one action that completes it, usually "Save": the only large,
   filled button at the bottom right. Every other action is quieter and sits next to what it applies to.
3. **Color carries meaning.** Blue (`primary`) marks what is selected, focused or clickable. Orange (`warning`)
   is Save. Green (`success`) creates something new. Red (`danger`) deletes or reports an error.
   Neutral grays do the rest: borders, backgrounds, secondary text.
4. **Calm layout.** One heading per level, an icon only on the section card header, one border around a group.
   Separate groups with spacing, a divider or a tinted panel.
5. **Works on a phone.** Check every screen at 400px wide: nothing overflows horizontally.
6. **Plain words.** Labels name things. Help texts and tooltips say what happens, in one sentence, without
   examples. Plurals agree with the number: "1 attendee has", "2 attendees have".

Background on 2 and on the placement of form actions:

- Luke Wroblewski, [Primary & Secondary Actions in Web Forms](https://www.lukew.com/ff/entry.asp?571)
- Nielsen Norman Group, [OK–Cancel or Cancel–OK?](https://www.nngroup.com/articles/ok-cancel-or-cancel-ok/)

## 2. Building blocks

| Need                                                          | Use                                                                            |
|---------------------------------------------------------------|--------------------------------------------------------------------------------|
| A titled block of a page or a form                            | `sectionCard()`, `components/section-card.ts`                                  |
| Data loaded by a `@lit/task` Task: spinner, error, refresh    | `taskContent()`, `components/task-content.ts`                                  |
| An empty list                                                 | `emptyState()`, `components/empty-state.ts`                                    |
| An input or select bound to a draft object                    | `formField()`, `selectField()`, `components/form-field.ts`                     |
| Validation of a form: validators, error display               | `TanStackFormController` + `components/form-validation.ts`, see [Validation](#validation) |
| A choice between options that need a short explanation       | `choiceCards()`, `components/choice-cards.ts`, see [Choices](#choices)         |
| An existing ticket category, with a Refresh button            | `ticketCategorySelect()`, `components/ticket-category-select.ts`               |
| The additional fields of an attendee                          | `attendeeFields()`, `components/attendee-fields.ts`                            |
| A file picked or dropped by the user                          | `<alfio-file-upload>`, `components/file-upload.ts`                             |
| A CSV import: template download, upload, file specifications  | `csvImport()`, `components/csv-import.ts`, see [CSV import](#9-csv-import)     |
| A date or a date interval                                     | `<alfio-format-date>`, see [Dates](#8-dates)                                   |
| Read-only "label: value" pairs                                | `detailList()`, `components/detail-list.ts`                                    |
| A status shown as a badge                                     | `statusBadge()`, `components/status-badge.ts`                                  |
| Search and pagination of a list                               | `ListSearchController`, `<alfio-pagination-bar>`                               |
| A yes/no confirmation                                         | `ConfirmationDialogService.requestConfirm()`, `service/confirmation-dialog.ts` |
| A notification after an action                               | `dispatchFeedback()`, `model/dom-events.ts`                                    |

Check `components/` for anything not listed here.

Each component adds the shared CSS blocks it uses to its `static styles`: `modernLayout` for section cards,
`form` for form controls, `dialog` for dialogs, `choiceCards` for choice cards, `attendeeFields` for the
attendees' additional fields, `csvImport` for CSV imports, `badges` for badges, `row` for grids. [`branding-guidelines.md`](branding-guidelines.md) lists which block defines each class.

Complete examples: `event/reservation-create/reservation-create.ts` (form with repeated groups and choices),
`event/check-in/add-attendees-dialog.ts` (dialog), `event/attendees-import/attendees-import.ts` (CSV import).

## 3. Page structure

- Wrap the page in `.container`, and start it with a `.page-title-row` (holding the `<h1>`) followed by a
  `<hr class="page-separator">`.
- Split the content in section cards with `sectionCard()`: header with icon, title, optional count badge and
  actions on the right; body below.
- Load the data with a `@lit/task` Task and render it with `taskContent()`.
- List pages: filter toolbar, tables and empty states are described in
  [`branding-guidelines.md`](branding-guidelines.md) (sections 2 to 4).

## 4. Forms

### Fields

- Shoelace controls (`sl-input`, `sl-select`, `sl-textarea`) render their own label from the `label` attribute.
  A separate `<label>` element would show it twice.
- Add `hoist` to every `sl-select` and `sl-dropdown` inside a section card, a dialog or any other container with
  `overflow: hidden`. Without it, the container clips the menu, which may then show a single option.
  `selectField()` already sets it.
- Stack fields with `.form-stack`, which spaces them with its gap. Put fields side by side with `.row` and
  `--alfio-row-cols` (from the `row` block).
- Repeated rows of fields (e.g. one row per attendee) use `class="row custom"` with
  `--alfio-custom-row-cols-layout`. The labels of the first row act as column headers; the following rows add
  `.label-hidden`, which keeps their labels for screen readers only.
- Explain a switch in its `help-text`.
- Messages: `sl-alert variant="danger"` for errors, `variant="primary"` for information, `variant="warning"`
  for a consequence the user must be aware of before saving.
- When the user may have created the options of a select in another tab (e.g. ticket categories), put a
  "Refresh" button next to the select, in an `.input-with-action` wrapper (from the `form` block).
  `ticketCategorySelect()` does this for categories.

### Validation

Every form validates its fields before saving: Save never sends a request with missing or wrong data. Shoelace's
`required` attribute alone does not stop the submit of a Lit form, and `form.reportValidity()` shows the browser's
tooltips instead of our messages: don't rely on either.

- Manage the values with `TanStackFormController` (`@tanstack/lit-form`), and register a `FormMountController` in the
  constructor (`new FormMountController(this, this.#form.api)`): lit-form doesn't mount the form, and its values are
  stale until a field is rendered.
- Render each validated control with `this.#form.field({ name, validators }, field => …)`, using
  `validatedInput()` and `validatedSelect()` from `components/form-validation.ts`: they add the `error` class (red
  border) and show the message as `help-text`, once the user has left the field or has tried to save (`fieldError()`).
- Register the same check as `onChange` and `onSubmit` validator with `validateWith()`, using the validators of
  `components/form-validation.ts` (`requiredValue`, `emailAddress`, `minLength`) when they fit. A validator returns
  the message to display, or `undefined`. A field that depends on another one lists it in `onChangeListenTo`
  (e.g. "Confirm password").
- Submit with `submitValidated(this.#form.api)`: it validates all the fields again, so that an error of a control
  that has been hidden in the meantime doesn't block the form, and calls the form's `onSubmit` only if they are valid.
  When it returns false, say so next to the Save button ("Please fill in the fields highlighted in red.") and call
  `focusFirstError()`.
- A field keeps its validators after its control has been hidden (e.g. by a choice or an "Edit" toggle): each
  validator checks first whether its control still applies.
- Show the errors returned by the server on their fields with `setServerError()`; the next submit removes them.
- In repeated groups, the field names contain the index (`tickets[0].attendees[1].firstName`): key the groups by
  index in `repeat()`, and add or remove them with `pushFieldValue()` / `removeFieldValue()`, which also move the
  state of the fields.

Examples: `event/reservation-create/reservation-create.ts` (repeated groups, choices),
`users/user-profile-edit.ts` (two forms, server errors), `event/promo-code/promo-code.ts` (dialog).

### Choices

Use `choiceCards()` when the user picks one of a few options and each option needs a short explanation, or when
the choice changes the fields that follow. Each option is a card with an icon, a title and a one-line description,
backed by a native radio, so keyboard navigation and screen readers work.

- The `label` is the question the user answers ("How do you want to add the attendees?"). It serves as the
  heading of the group.
- Pass the fields of the selected option as `panel`: they appear in a tinted panel right below the cards, which
  makes the inputs stand out and ties them to the choice.

```ts
choiceCards({
    name: `category-type-${tickets.key}`,
    label: 'Assign the attendees to',
    value: tickets.categoryType,
    options: CATEGORY_TYPE_CHOICES,
    panel: html`<!-- fields of the selected option -->`,
}, categoryType => this.updateTickets(tickets, { categoryType }));
```

For short values that need no explanation, such as a filter or "Person / Company", use a plain `sl-radio-group`.

### "Add another" buttons

A form that repeats a group of fields has a button that adds one more group. It creates nothing until the form is
saved, so it stays quiet and appears where the new group will appear, aligned to the left:

- **One more row of fields** (e.g. an attendee): a `variant="text"` button directly below the last row,
  aligned with the inputs.
  ```html
  <sl-button class="add-attendee" variant="text">
      <sl-icon slot="prefix" name="plus-circle"></sl-icon>
      Add attendee
  </sl-button>
  ```
- **One more section card**: a full-width, dashed `.section-card-add` button (from `modernLayout`) right after
  the last card. It looks like the empty card that the click fills in.
  ```html
  <sl-button class="section-card-add" variant="default" size="large">
      <sl-icon slot="prefix" name="plus-circle"></sl-icon>
      Add tickets for another category
  </sl-button>
  ```

Name the thing being added in the label. The bottom right of the form belongs to the primary action.

## 5. Buttons

Use `<sl-button>` for every button. When the table gives an icon, put it in `slot="prefix"`. Save, Cancel and
Close never have an icon: the label and the position in the footer are enough. Their labels are always exactly
"Save", "Cancel" and "Close", never variants such as "Save changes", "Save and continue" or "Discard changes".
While Save is saving, use its `loading` attribute.

| Action                                                      | Variant                        | Size    | Icon          | Where                                                   |
|-------------------------------------------------------------|--------------------------------|---------|---------------|---------------------------------------------------------|
| Save, or any action that completes the form                 | `warning`                      | `large` |               | footer, right                                           |
| Cancel, Close                                               | `default`                      | `large` |               | footer, left                                            |
| Add new: creates an entity (an event, a user, a promo code) | `success`                      | `large` | `plus-circle` | page header or list toolbar                             |
| Add another: one more group in a form                       | `text`, or `.section-card-add` |         | `plus-circle` | below the last group, see [Forms](#add-another-buttons) |
| Delete                                                      | `danger`                       |         | `trash`       |                                                         |
| Any other destructive confirmation (rotate a key, disable)  | `warning`                      | `large` |               | dialog footer, right                                    |
| Secondary action (bulk creation)                            | `primary`                      |         |               | list toolbar                                            |
| Action in a table row, a toolbar or a section card header   | as above                       | `small` |               | next to what it applies to                              |

Other icons: `pencil` edit, `clipboard` copy, `qr-code` QR code, `x-circle` clear (a field or a filter), `check2` confirm,
`arrow-clockwise` refresh.

Footer of a page form or of a dialog:

```html
<sl-divider></sl-divider>
<div class="row" style="--alfio-row-cols: 3">
    <sl-button variant="default" size="large">Cancel</sl-button>
    <div></div>
    <sl-button variant="warning" size="large" type="submit">Save</sl-button>
</div>
```

In a dialog, wrap it in `<div slot="footer">`. The grid comes from the `row` block: if the component has its own
Bootstrap-style `.row` rule, rename that one to `.bs-row`.

## 6. Dialogs

- Write dialogs in Lit, with `sl-dialog`. When the page that opens a dialog is a Lit component, the dialog is
  a Lit component too: replace any AngularJS dialog (`$uibModal`, `*-modal.html`) that wraps Lit content,
  together with its controller.
- Put the title in `label` and keep the header: its close (X) button lets the user close any dialog from the
  top right.
- Set the width with `class="responsive-dialog"` (from the `dialog` block) and `--alfio-dialog-max-width`
  (default `52rem`): the dialog then shrinks to fit a phone. `sl-dialog` has no `size` or `placement` attribute.
- A dialog with a form:
  - closes only from X and Cancel, so a stray click doesn't discard what the user typed: cancel
    `sl-request-close` when `event.detail.source === 'overlay'`;
  - starts empty every time it opens: render its content only while it is open, or reset its state in `show()`;
  - handles `sl-after-hide` only when `event.target === event.currentTarget`, because nested Shoelace components
    (selects, tooltips) fire their own.
- A page component shown in a dialog (e.g. `<alfio-reservation-create data-fast-creation>`) leaves out its page
  title, because the dialog's label replaces it. It keeps its own footer, because its Save button must stay
  inside its `<form>`.
- Ask yes/no questions with `ConfirmationDialogService.requestConfirm()`.

Example: `event/check-in/add-attendees-dialog.ts`.

## 7. Shoelace instead of Bootstrap

| AngularJS / Bootstrap                     | Lit / Shoelace                                                                                            |
|-------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `<button class="btn btn-success">`        | `<sl-button variant="success">`                                                                           |
| `<button class="btn btn-default">`        | `<sl-button variant="default">`                                                                           |
| `<button class="btn btn-primary btn-xs">` | `<sl-button variant="primary" size="small">`                                                              |
| `<button class="btn btn-link">`           | `<sl-button variant="text">`                                                                              |
| `<div class="alert alert-danger">`        | `<sl-alert variant="danger" open><sl-icon slot="icon" name="exclamation-triangle"></sl-icon>…</sl-alert>` |
| `<div class="alert alert-info">`          | `<sl-alert variant="primary" open><sl-icon slot="icon" name="info-circle"></sl-icon>…</sl-alert>`         |
| `<span class="label label-success">`      | `<sl-badge variant="success" pill>`, same for `warning` and `danger`                                      |
| `<span class="label label-info">`         | `<sl-badge variant="primary" pill>` (Shoelace has no `info` variant)                                      |
| `<i class="fa fa-…">`                     | `<sl-icon name="…">`                                                                                      |

- Icons are the Bootstrap Icons bundled with Shoelace: the names are the file names in
  `frontend/admin/node_modules/@shoelace-style/shoelace/dist/assets/icons/`. CSS that targeted `i` now targets `sl-icon`.
  Shoelace itself is loaded by `main.ts`, and Font Awesome helpers (`injectFontAwesome`) go away.
- Badges are always `pill`, and need the `badges` block.
- Once a component uses the Shoelace components, delete the Bootstrap emulation from its `styles`.

## 8. Dates

- Use `<alfio-format-date>` (`components/format-date.ts`) for all dates, instead of `<sl-format-date>` or
  formatting by hand (`toLocaleString`, `Intl.DateTimeFormat`, …).
- The display format is defined once, admin-wide, in `service/date-format.ts` (default: `Jun 30, 2026, 14:00`).
  Leave out month/day/hour options: `setDateFormatPreferences()` changes the format, and every date re-renders.
- Pass the backend value as it is (no `.replace(' ', 'T')`, no appended `Z`):
  - **Instants** (ISO-8601 with offset or `Z`, e.g. a `ZonedDateTime`) are converted to `time-zone`.
    Set it to the event time zone when an event is in scope; without it, the browser time zone is used.
  - **Wall-clock strings** (`yyyy-MM-dd HH:mm`, no offset) are already in the event time zone and are displayed
    unchanged.
- Import the component where it is used: `import '../components/format-date.ts';`.

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

## 9. CSV import

- Render the import with `csvImport()`: step 1 downloads a template, step 2 takes the filled-in file
  (`<alfio-file-upload>`), and a collapsed "File specifications" block describes the expected file for those who
  generate it with another system.
- The template has a header, with a column for each of the attendees' data to collect, and the file is read by
  column name: the user doesn't need to know the names of the fields.
- In the specifications (a `.bulk-specs` block), describe separator, quote and escape characters, the header in a
  `<pre>`, and the allowed values of the fields with options.
- Parse it with papaparse (`Papa.parse`): quoted fields can contain separators and newlines, so the file can't be
  split by lines.
- Show the parsed rows before saving (a table with a total, or editable rows), and say how many rows were skipped
  and why.
- Key the rows by index in `repeat()`, since their values may repeat.

## 10. Code

- No ternary operator (see `AGENTS.md`): in templates, use ``when(condition, () => html`…`, () => html`…`)`` from
  `lit/directives/when.js`; elsewhere, `if`/`else`.
- The E2E tests (`src/test/java/alfio/e2e`, mostly `AdminConsole.java`) find elements by id, class and `data-*`
  attributes. Before renaming one, search the tests for it and update them in the same change. Give the elements
  that tests interact with meaningful names, such as `#add-category` or `.choice-card[data-value="new"]`.

## 11. Migrating an AngularJS screen

The admin moves to Lit one screen at a time, while users keep working with it.

- Take a screenshot of the old screen before you start, and one of the new screen when you are done. Comparing
  them, by eye or with a comparison script such as `compare-ui.sh`, also shows the differences that come from this
  guide: those are expected. Fix the regressions (missing fields or content, clipped or misaligned elements), and
  list the intentional differences in your report.
- Remove what the new component replaces: the AngularJS template, controller, CSS and service methods, their
  `<script>`/`<link>` tags in `src/main/resources/alfio/web-templates/admin-index.ms`, and any AngularJS dialog
  wrapping Lit content (see [Dialogs](#6-dialogs)).
- The running server serves the legacy files from `build/resources/main`: run `./gradlew processResources` after
  changing them. It reads `admin-index.ms` once, so restart it after changing that file.
- Cover the migrated flow with an E2E test (see the E2E section of `CONTRIBUTING.md`).
