# Admin frontend style guide

Rules for every screen of `frontend/admin` (Lit + Shoelace).
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
5. **Works on a phone and a tablet.** Check every screen at 400px, 768px and 1024px wide: nothing overflows
   horizontally, and the content, not the controls, fills the first screen (see
   [Phones and tablets](#phones-and-tablets)).
6. **Plain words.** Labels name things. Help texts and tooltips say what happens, in one sentence, without
   examples. Plurals agree with the number: "1 attendee has", "2 attendees have".

Background on 2 and on the placement of form actions:

- Luke Wroblewski, [Primary & Secondary Actions in Web Forms](https://www.lukew.com/ff/entry.asp?571)
- Nielsen Norman Group, [OK–Cancel or Cancel–OK?](https://www.nngroup.com/articles/ok-cancel-or-cancel-ok/)

## 2. Building blocks

| Need                                                          | Use                                                                            |
|---------------------------------------------------------------|--------------------------------------------------------------------------------|
| A titled block of a page or a form                            | `sectionCard()`, `components/section-card.ts`                                  |
| The section index of a long creation form (sticky nav on desktop, "Jump to section" select on mobile) | `<alfio-section-nav>`, `components/section-nav.ts`, with the `creationLayout` styles |
| Data loaded by a `@lit/task` Task: spinner, error, refresh    | `taskContent()`, `components/task-content.ts`                                  |
| An empty list                                                 | `emptyState()`, `components/empty-state.ts`                                    |
| The organization whose items a list shows                     | `organizationFilter()`, `components/organization-filter.ts`                    |
| An input or select bound to a draft object                    | `formField()`, `selectField()`, `components/form-field.ts`                     |
| A localized text, one block per language (markdown-capable)   | `<alfio-multi-language-text>`, `components/multi-language-text.ts`, see [Multi-language text](#multi-language-text) |
| A WYSIWYG markdown field                                      | `<alfio-markdown-editor>`, `components/markdown-editor.ts` — usually via `alfio-multi-language-text` with `markdown` |
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
| A link to another site (documentation, a provider dashboard)  | `externalLink()`, `components/external-link.ts`: new tab, with an icon         |
| The steps to set up an integration (where to find API keys)   | `setupGuide()`, `components/setup-guide.ts`: collapsed below the one-sentence card description |

Check `components/` for anything not listed here.

Each component adds the shared CSS blocks it uses to its `static styles`: `modernLayout` for section cards,
`form` for form controls, `dialog` for dialogs, `choiceCards` for choice cards, `attendeeFields` for the
attendees' additional fields, `csvImport` for CSV imports, `badges` for badges, `row` for grids. [`branding-guidelines.md`](branding-guidelines.md) lists which block defines each class.

Complete examples: `event/reservation-create/reservation-create.ts` (form with repeated groups and choices),
`event/event-create/event-create.ts` (creation form with multi-language text, image upload and dialogs),
`event/check-in/add-attendees-dialog.ts` (dialog), `event/attendees-import/attendees-import.ts` (CSV import).

## 3. Page structure

- Wrap the page in `.container`, and start it with a `.page-title-row` (holding the `<h1>`) followed by a
  `<hr class="page-separator">`. An optional one-sentence description goes below the separator, as
  `<p class="page-description text-muted">`.
- Split the content in section cards with `sectionCard()`: header with icon, title, optional count badge and
  actions on the right; body below.
- Load the data with a `@lit/task` Task and render it with `taskContent()`.
- List pages: a `.filter-toolbar` (filters left, actions right, "Add new" last), then the list in a `sectionCard()`
  with a count badge. Filter toolbar, tables and empty states are described in
  [`branding-guidelines.md`](branding-guidelines.md) (sections 2 to 4). Use `<alfio-pagination-bar>` for pages,
  not hand-made Previous/Next buttons.

### Page width and alignment

The navbar's background spans the whole window; its content (logo, links, account menu) lines up with the widest
content, the workspace of an event (`--alfio-workspace-width`), so on a large monitor it doesn't drift to the
edges. The content below it is always **centered**. Center the container, never its text: titles, forms and tables
stay left-aligned inside it. The content comes in two widths, defined once as tokens in `main.css` (`:root`):

- **A page**: `.container` (from `modernLayout`), at most `--alfio-page-width` (1170px), centered. The footer is
  centered on the same axis.
- **The workspace of an event**: `<alfio-event-layout>` centers the event menu and the page as one unit, at most
  `--alfio-workspace-width` (menu + `--alfio-workspace-gap` + page width). Its page's `.container` fills the
  column next to the menu, which at full width is exactly `--alfio-page-width`.

A page that changes width is fine while it stays centered: it grows on both sides around the same axis. Don't
left-align a page, don't push it away from the event menu with a margin, and don't hard-code a width: use the
tokens. A narrower page (e.g. a single short form) may set a smaller `max-width` on its `.container`; it stays
centered.

### Phones and tablets

A phone shows one column; a tablet in portrait shows the event menu above the page, in landscape beside it.
The shared blocks already adapt the page: below `700px` the title, its margins and the padding of the section
cards shrink (`modernLayout`). What each page decides:

- **List tables become stacked rows** below `640px`. Add `table-stacked` to the table and `data-label` (the column
  title) to each cell: every row becomes a block of "label  value" lines. Leave out `data-label` on the cell that
  identifies the row (it becomes the row's title) and on the actions. A small cell such as a status icon takes
  `stacked-inline`, which puts it in front of the next cell. A table with `sortableHeader()` also takes
  `table-sortable`: its headers become a row of sort buttons. Header controls other than sorting (e.g. "Select
  all") are hidden in a stacked table: keep such a table horizontal.
- Keep horizontal scrolling for tables whose columns are meant to be compared (statistics, the order summary,
  the preview of an import, the translations editor).
- **Secondary columns** take `hide-small` (`reservationTable`): they go when the table is narrow, below `700px`
  of window or `940px` of table (e.g. next to the event menu on a tablet), and come back, as lines, in a
  stacked row. Keep the identifier, the main value (customer, amount) and the actions visible.
- **A toolbar without filters** is `filter-toolbar actions-only`: just its buttons on the right, without the gray
  band, which would take a row of the first screen for nothing.
- **One copy of each action** on the screen: don't repeat in the page an action that the event menu or the
  navbar already shows at that width.

### Creation pages with dependent choices

Use `event/event-create/event-create.ts` as the reference for long creation forms, including subscription
creation. The improvement is the order of decisions and the visibility of their consequences, as well as the
Shoelace styling. Prefer one adaptive page that experienced organizers can scan and edit freely. Page length
alone is not a reason to introduce a wizard, numbered steps, completion percentages or mandatory Next/Back actions.

#### Put prerequisites before dependent fields

- Start with a full-width setup card containing the organizer. Explain that it determines available payment
  methods and subscriptions. When several organizers are available, reveal the remaining form after selection.
  When only one is available, select it automatically and keep its name visible.
- Put each subsequent controlling choice immediately before the fields it affects. Do not collect every
  choice in the setup card: free/paid admission belongs with pricing; currency precedes amounts and payment methods.
- For an event, the order is **name → format → location → timezone → dates**. Location can determine timezone,
  and timezone gives meaning to the dates. Online events omit the physical location but retain an editable
  timezone. Preserve a manually chosen timezone when resolving a location.
- Use `choiceCards()` for choices that change the following fields, with those fields in its tinted `panel`.
  Keep the panel within the group instead of nesting another bordered section card inside it.

#### Group the form and provide section navigation

The event page groups its content as follows:

| Group | Contents, in order |
|-------|--------------------|
| Event setup | Organizer |
| Event details | Name, format and conditional location, timezone, dates |
| Tickets and payments | Capacity, free/paid choice, conditional currency/pricing/taxes/payment methods, categories |
| Event page | Languages and description, public links, required logo |

- Below setup, use a narrow sticky section index beside the form on desktop. The event reference uses a
  `12rem` index and a `minmax(0, 1fr)` content column, separated by `--sl-spacing-x-large`.
- Use an `<h2>` for each navigable group and `sectionCard()` for its titled blocks. Separate groups with
  whitespace; use the existing Shoelace colors, typography, spacing and borders.
- Section links scroll within the form, move focus to the destination and highlight the current section
  with primary blue and `aria-current="location"`. They must not change the admin's hash route. Account for
  the fixed admin header in both sticky positioning and `scroll-margin-top`.
- At the event reference's `900px` breakpoint, switch to a single column and replace the index with a compact
  `sl-select` labelled "Jump to section". Stack fields and choice cards as needed. Verify at `400px`, including
  long URLs, category actions and the footer; Cancel and Save must have space between them.
- The layout and navigation classes live in the `creationLayout` block of `styles.ts`, and the navigation
  behavior in `<alfio-section-nav>` (`components/section-nav.ts`): render it as a child of `.creation-layout`,
  beside the `.creation-content` column, passing the sections of the form (`{id, label}`), each rendered as
  `<section class="form-section" id="…">` with an `<h2>`.

#### Keep important content visible

- Required fields remain expanded. **The event logo is required**: show its upload control in a section card,
  label it required and block Save without an uploaded or copied logo. Do not put it in an optional artwork accordion.
- Keep website, terms and privacy URLs visible together. Label the privacy URL "recommended" and explain its
  purpose; being technically optional does not make it suitable for hiding.
- Reserve `sl-details` for genuinely optional, secondary content. Verify the existing requirements before
  classifying any field as optional. Continue using the shared multilingual text and image-upload components.

#### Keep actions quiet and preserve entered work

- Keep copying from an existing item as a quiet header action when that feature exists. Put Reset in a
  secondary "More" menu and confirm before discarding entered information.
- The footer contains Cancel on the left and Save on the right, following [Buttons](#5-buttons), with
  validation feedback near Save. Preserve the existing requirements for saving; a layout redesign does not
  introduce a separate incomplete-draft workflow.
- Changing an earlier choice preserves compatible values. Untouched defaults can follow the new settings;
  explicit customizations must not be overwritten. Show conflicts beside the affected fields or categories,
  and confirm changes only when they would discard entered information.
- Recheck organizer- and currency-dependent selections, show unavailable selections so the user can resolve
  them, and ignore stale responses from an earlier selection. Reveal and focus invalid fields when saving.

For **subscription creation**, apply this structure to its existing fields: organizer setup first, then
subscription details and the choices governing validity/availability, pricing and payment settings, and
public-facing content. Determine the exact order from the subscription's dependencies and save contract;
do not add event-specific venue or ticket-category fields. Preserve its required fields and use the same
section navigation, choice panels, visible public links, upload treatment and footer conventions.

### Detail pages: edit in place

A detail page (e.g. the event detail) shows the item as section cards, and each part is edited where it is shown:

- Each editable card has a small "Edit" button in its header (`sectionCard({ actions })`). It opens a dialog with
  the fields of that card only, with Cancel and Save. Don't link to a separate edit page, and don't mix the two
  approaches on the same page.
- Reuse the creation form for the dialog instead of duplicating its fields: the event form renders a single part
  with `<alfio-event-create data-event-name="…" data-section="details|description|seats">` and fires
  `alfio-event-section-saved` / `alfio-event-section-cancelled`. Create the form at every opening, so that it
  starts from the saved item, and reload the page after Save.
- Operations on a list of the page that change many cards at once (e.g. rearranging the categories) open a dialog
  too: the page behind stays as it is, and Cancel discards the change.

### Event pages: the event menu

Every page of an event (`/admin/events/{eventName}/…`) is shown next to `<alfio-event-sidebar>`
(`event/event-sidebar/event-sidebar.ts`), which `<alfio-event-layout>` (`shell/event-layout.ts`) renders. It shows
the event name and status, "Create reservation", the Actions and Download menus, and the links to the pages grouped
by task (Sales, Attendees, Setup), with the current page highlighted. Below `992px` the links become a "Go to" select
above the page, with the actions on one row ("Create reservation", Actions, and Download, icon only on a phone).
The menu and the page are centered together, as one workspace (see
[Page width and alignment](#page-width-and-alignment)).

- Below `992px` the menu, above the page, already offers "Create reservation": a page that repeats it in its own
  toolbar (e.g. the reservations) hides its copy there, so that the action appears once.

- A new event page gets its route in `EVENT_ROUTES` (`admin-routes.ts`, see [Routing](#11-routing)) and its link
  in `navGroups()`: its path below `/admin/events/{eventName}/` and the routes on which it is the current page.
  Don't add a second navigation to the page itself.
- The menu reads the event from the route, and reloads it on `EVENT_CHANGED_EVENT`. A page that changes what the
  menu shows (e.g. the status) calls `dispatchEventChanged(eventName, this)` (`service/event.ts`).
- Its column is sticky: a dialog rendered inside it would stay under the page. The menu's own dialogs are appended
  to `document.body`.

### Ticket categories: words to use

Describe what the organizer gets, not how tickets are allocated:

| Concept (code) | Say | Don't say |
|---|---|---|
| Category with `bounded = false` (the default) | nothing; "Available (shared with other categories)" in a ticket bar | dynamic, grows dynamically |
| Category with `bounded = true` | "Limit the number of tickets", "Up to 20 tickets" | fixed, fixed allocation, bounded |
| Tickets of the limited categories, not sold yet | "Kept for categories with a limit" | reserved (a reservation is an order) |
| Seats of the event outside every category (`notAllocatedTickets`) | "Not on sale" | not assigned, not allocated |
| Category with `tokenGenerationRequested` / `accessRestricted` | "Unlisted" | hidden, restricted, access code category |
| Its per-ticket codes | "Invitation codes" | access codes (those are the shared codes of the promo codes page) |
| Moving leftover tickets to the shared capacity | "Put back on sale" | assign to the dynamic categories |

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
- A secret (API key, password, private key) is write-only: the server never returns its value, only whether one
  is saved. Its field is empty, says "Saved. Type a new value to replace it" when a value is saved, and offers
  "Remove the saved value" in its help text. A value set on the level above is never shown either: the placeholder
  only says that one applies ("Same as the organization (a value is set)"). `settingControl()` does both.
- Messages: `sl-alert variant="danger"` for errors, `variant="primary"` for information, `variant="warning"`
  for a consequence the user must be aware of before saving.
- When the user may have created the options of a select in another tab (e.g. ticket categories), put a
  "Refresh" button next to the select, in an `.input-with-action` wrapper (from the `form` block).
  `ticketCategorySelect()` does this for categories.

### Multi-language text

- Render any localized text (a title, a description, …) with `<alfio-multi-language-text>`
  (`components/multi-language-text.ts`): one block per selected language, an "Add translation" menu below
  the last block, and — with `markdown` — the WYSIWYG `<alfio-markdown-editor>` with an inline
  Edit/Preview toggle. Don't render the per-language textareas or editors by hand.
- Bind it to the form like any other field: the `value` property is the map (locale → text), the `change`
  event carries the new map (`MultiLanguageTextChange`), and `error` shows the field error below the blocks.
  With `card` off, it renders as a bare field group, e.g. inside a dialog or another section card.
- `languages` are all the languages the instance supports; `contextLanguages` are the ones of the purchase
  context when there is one — they define the default language picked for a new text.
- When several localized fields are on the same page or dialog, listen to `locales-change` and apply the same
  languages to the other fields, keeping their current texts (see `subscriptions/subscription-edit.ts` and
  `event/event-create/event-create.ts` + `category-dialog.ts`).

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

A yes/no setting that a level can leave to the level above (system → organization → event) has three choices.
Show them as an `sl-select`, not as a switch (a switch reads as on/off): "Same as the organization (Yes)" / "Yes" /
"No". The first option names the value that applies when the level doesn't set it. `settingControl()`
(`configuration/setting-control.ts`) renders it.

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
| Other action in a list toolbar, next to Add new             | as above                       | `large` |               | list toolbar, before Add new                            |
| Action in a table row or a section card header              | as above                       | `small` |               | next to what it applies to                              |

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

- Write dialogs in Lit, with `sl-dialog`.
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

| Bootstrap                                 | Lit / Shoelace                                                                                            |
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
- In an `sl-tab-group`, mark the initial tab and its panel `active` (e.g. `?active=${index === 0}` on both):
  Shoelace activates the first tab only when the group scrolls into view, so below the fold every panel
  stays hidden until then.

## 8. Dates

- Use `<alfio-format-date>` (`components/format-date.ts`) for all dates, instead of `<sl-format-date>` or
  formatting by hand (`toLocaleString`, `Intl.DateTimeFormat`, …).
- The display format is defined once, admin-wide, in `service/date-format.ts` (default: `Jun 30, 2026, 14:00`).
  Dates and numbers follow the language of the admin: the `locale` key of its translation file (`de-DE`).
  Leave out month/day/hour options: `setDateFormatPreferences()` changes the format, and every date re-renders.
- Pass the backend value as it is (no `.replace(' ', 'T')`, no appended `Z`):
  - **Instants** (ISO-8601 with offset or `Z`, e.g. a `ZonedDateTime`) are converted to `time-zone`.
    Set it to the event time zone when an event is in scope; without it, the browser time zone is used.
  - **Wall-clock strings** (`yyyy-MM-dd HH:mm`, no offset) are already in the event time zone and are displayed
    unchanged.
- Import the component where it is used: `import '../components/format-date.ts';`.
- Where an element can't be used (e.g. the labels of a chart), call `formatDate()` from `service/date-format.ts`.
  The `day-month` style drops the year (`Jun 30`) for compact labels. Re-render on `DATE_FORMAT_CHANGED_EVENT`,
  as `<alfio-format-date>` does.

```html
<!-- date + time (default) -->
<alfio-format-date date=${log.timestamp}></alfio-format-date>
<!-- instant shown in the event time zone, with a fallback when the value is empty -->
<alfio-format-date .date=${payment.transactionTimestamp} time-zone=${event.timeZone}>—</alfio-format-date>
<!-- interval -->
<alfio-format-date date=${ev.formattedBegin} end=${ev.formattedEnd}></alfio-format-date>
<!-- date only / time only / day and month only -->
<alfio-format-date date=${value} format="date"></alfio-format-date>
<alfio-format-date date=${value} format="day-month"></alfio-format-date>
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

## 11. Routing

The admin is a single page application below `/admin` (`shell/admin-app.ts`), routed by `admin-router.ts`, the only
module that uses `@lit-labs/router`.

- A page gets its route in `admin-routes.ts`: `ADMIN_ROUTES` for the top-level pages, `EVENT_ROUTES` and
  `SUBSCRIPTION_ROUTES` for the pages of an event or subscription, which their layout (`shell/event-layout.ts`,
  `shell/subscription-layout.ts`) renders after loading the event or subscription. Add the route to the tests in
  `admin-routes.test.ts`, and give it a `title` for the browser tab.
- The page receives the route params as `data-*` attributes. A route renders the same template whatever its params
  and query: Lit keeps the element when only those change, so the page keeps its state.
- Links: `href=${adminUrl('/events/x/detail')}`; the router handles the click. Navigate from code with
  `navigate('/events/x/detail')`, or `navigateToUrl(url)` for a URL built with `adminUrl()`. Never set
  `window.location`: a full reload loses the state of the shell. The only exception is the language menu of the
  navbar: the texts are loaded once, before the first render, so a new language needs a reload (`?lang=`, see
  [Translations](#12-translations)).
- A link to something that is not an admin page (an export, a public page) opens in a new tab or has `download`;
  if it hasn't, the router lets the browser load it.
- Filters and pagination live in the query: read it with `readRouteParams()`, update it with `replaceRouteParams()`
  (`service/helpers.ts`). A component that depends on the route listens to `ROUTE_CHANGED_EVENT` and reads
  `adminRoute()`.
- A page with unsaved changes asks before the user leaves: it listens to `BEFORE_LEAVE_EVENT` and adds a
  `Promise<boolean>` to `detail.confirmations` (see `configuration/settings-form.ts`).
- Call the API with the helpers of `service/helpers.ts` (`fetchJson`, `postJson`, … or `adminFetch` for a custom
  request): they report an expired session (the shell reloads the page, which leads to the login) and a refused
  action (the shell explains it).
- The shell and the layouts render in light DOM, so that the pages stay reachable from the document (E2E tests);
  their styles are in `main.css`. Pages keep their shadow DOM.
- Cover a new page with an E2E test (see the E2E section of `CONTRIBUTING.md`).

## 12. Translations

Every text a user can see comes from the translation files, through `t()` and the other functions of
`service/i18n.ts`. Only the logs (`console.*`) stay in English.

- The texts are in `src/main/resources/alfio/i18n/public.properties`, the same file as the public frontend, with keys
  that start with `admin.`. Add only the English text: the other languages (`public_de.properties`, …) are
  translated on POEditor, and a key missing there shows the English text.
- The server chooses the language: the one picked in the user menu (`?lang=de`, kept in the session), otherwise
  the browser's, otherwise English. The page loads the texts once, before the first render, so `t()` also works in
  constants at module level.
- Keys: `admin.<area>.<screen>.<element>`, e.g. `admin.group.csv.empty`. Before adding a key, look for the same
  English text: `admin.common.*` has the shared ones (`admin.common.save`, `admin.common.cancel`, …).
- Write keys as literal strings, so that `i18n-keys.test.ts` checks that they exist. To choose among several keys,
  keep them in a map of `i18nKey()` values, never build them (`'admin.status.' + status`):

  ```ts
  const LOG_TYPE_LABELS: Record<ExtensionLogType, string> = {
      SUCCESS: i18nKey('admin.extension-log.type.success'),
      ERROR: i18nKey('admin.extension-log.type.error'),
  };
  html`<sl-badge>${t(LOG_TYPE_LABELS[type])}</sl-badge>`
  ```

- Values go in placeholders, `{0}`, `{1}`, …: `t('admin.group.deleted', name)` for `Group {0} successfully deleted`.
  Keep the sentence whole, so that translators can move the values: never join pieces of text.
- Markup goes in the values, never in the text: ``tHtml('admin.extensions.delete.text', html`<b>${name}</b>`)`` for
  `The extension {0} will be removed.` No `unsafeHTML`.
- Plurals: one key per form, `_one` and `_other` (`admin.group.csv.imported_one`, `…_other`), where `{0}` is the
  count. Call `t('admin.group.csv.imported', { count: n })`, or `tHtmlPlural()` with markup. The count can be
  followed by more values: `{ count: n, 1: file.name }` for `{1}`.
- The server reads every text with Java's `MessageFormat`: write an apostrophe twice (`it''s`), and don't write
  braces in a text: pass them as a value. `FormattersTest` checks every file.
- Numbers: `formatNumber()`, not `toLocaleString()` or `Intl.NumberFormat('en-US')`. Dates: see [Dates](#8-dates).
- Errors: show a validation error code of the server with `translateErrorCode(code, fallback)`. The admin API
  translates the errors of a `Result` itself (`AdminErrorTranslationAdvice`): give a new error an `admin.error.*` key
  (`ErrorCode.translatable()`).
- Keep in their original form the texts that aren't English words: brand names, CSV column names, sample data,
  the Italian e-invoicing terms.
- The E2E tests run Chrome in English and look for the English texts: when you change one, search the tests for it.
