import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import Papa from 'papaparse';
import {AlfioEvent, TicketCategory} from '../../model/event.ts';
import {
    PromoCodeDiscount,
    PromoCodeType,
    DiscountType,
    DateTimeModification,
    UsageDetailEvent
} from '../../model/promo-code.ts';
import {PromoCodeService} from '../../service/promo-code.ts';
import {ConfirmationDialogService} from '../../service/confirmation-dialog.ts';
import {
    badges,
    dialog,
    form,
    modernLayout,
    modernTable,
    retroCompat,
    row,
    spacing,
    textColors
} from '../../styles.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {EventService} from '../../service/event.ts';
import {fetchJson} from '../../service/helpers.ts';
import type {SlDialog, SlInput, SlSelect, SlSwitch, SlTextarea} from '@shoelace-style/shoelace';

interface PromoCodeWithUsage extends PromoCodeDiscount {
    useCount: number | undefined;
}

interface LoadData {
    promoCodeDescription: string;
    promocodes: PromoCodeWithUsage[];
    accesscodes: PromoCodeWithUsage[];
    restrictedCategories: TicketCategory[];
    validCategories: TicketCategory[];
    ticketCategoriesById: Record<number, TicketCategory>;
    currencies: AvailableCurrency[];
    event: AlfioEvent | null;
    forEvent: boolean;
    organizationId: number;
}

interface EditingState {
    code: PromoCodeDiscount;
    data: LoadData;
}

interface AvailableCurrency {
    code: string;
    name: string;
}

@customElement('alfio-promo-code')
export class PromoCode extends LitElement {

    @property({type: String, attribute: 'data-event-name'})
    eventName?: string;

    @property({type: Number, attribute: 'data-organization-id'})
    organizationId?: number;

    @property({type: Boolean, attribute: 'data-for-event'})
    forEvent?: boolean;

    @query('sl-dialog#code-dialog')
    codeDialog!: SlDialog;

    @query('sl-dialog#usage-details-dialog')
    usageDetailsDialog!: SlDialog;

    @state()
    private validationErrors: Record<string, string> = {};

    @state()
    private usageData: UsageDetailEvent[] = [];

    @state()
    private saving = false;

    @state()
    private editingState: EditingState | null = null;

    @state()
    private pendingCodeType: PromoCodeType = 'DISCOUNT';

    @state()
    private selectedDiscountType: DiscountType = 'PERCENTAGE';

    @state()
    private allCategories = true;

    @state()
    private selectedCategories: string[] = [];

    @state()
    private filters = {promo: {search: '', category: ''}, access: {search: '', category: ''}};

    private loadDataTask = new Task(this,
        async ([_eventName, _orgId, _forEvent]) => {
            void _eventName;
            void _orgId;
            void _forEvent;

            const forEvent = !!this.forEvent && !!this.eventName;
            let ev: AlfioEvent | null = null;
            if (forEvent && this.eventName) {
                try {
                    const result = await EventService.load(this.eventName);
                    ev = result.event;
                } catch {
                    // fall through
                }
            }
            const orgId = this.organizationId ?? (ev?.organizationId ?? 0);

            let promoCodeDescription = 'Promo';
            if (forEvent && ev) {
                try {
                    const val = await PromoCodeService.loadSingleConfig(ev.shortName, 'USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL');
                    promoCodeDescription = (val === 'true') ? 'Partner' : 'Promo';
                } catch {
                    // ignore, keep default
                }
            }

            const allCodes = forEvent
                ? await PromoCodeService.listByEvent(ev!.id)
                : await PromoCodeService.listByOrganization(orgId);

            const promocodes = allCodes
                .filter(pc => pc.codeType === 'DISCOUNT' || pc.codeType === 'DYNAMIC') as PromoCodeWithUsage[];

            const accesscodes = allCodes
                .filter(pc => pc.codeType === 'ACCESS') as PromoCodeWithUsage[];

            const discountOrAccess = allCodes.filter(pc => pc.codeType === 'DISCOUNT' || pc.codeType === 'ACCESS');

            await Promise.allSettled(discountOrAccess.map(async (pc) => {
                (pc as PromoCodeWithUsage).useCount = await PromoCodeService.countUse(pc.id);
            }));

            const ticketCategoriesById: Record<number, TicketCategory> = {};
            const restrictedCategories: TicketCategory[] = [];
            const validCategories: TicketCategory[] = [];

            if (forEvent && ev) {
                for (const tc of ev.ticketCategories) {
                    ticketCategoriesById[tc.id] = tc;
                    if ((tc as any).accessRestricted && !(tc as any).expired) {
                        restrictedCategories.push(tc);
                    }
                    if (!(tc as any).expired && !(tc as any).accessRestricted) {
                        validCategories.push(tc);
                    }
                }
            }

            let currencies: AvailableCurrency[] = [];
            if (!forEvent) {
                const currencyResult = await fetchJson<AvailableCurrency[]>('/admin/api/utils/currencies');
                currencies = Array.isArray(currencyResult) ? currencyResult : [];
            }

            return {
                promoCodeDescription,
                promocodes,
                accesscodes,
                restrictedCategories,
                validCategories,
                ticketCategoriesById,
                currencies,
                event: ev ?? null,
                forEvent,
                organizationId: orgId,
            };
        },
        () => [this.eventName ?? '', this.organizationId ?? 0, this.forEvent ?? false]
    );

    static readonly styles = [
        retroCompat,
        textColors,
        form,
        dialog,
        badges,
        modernTable,
        modernLayout,
        row,
        spacing,
        css`
            .promo-code-name {
                font-family: var(--sl-font-mono);
                font-weight: 600;
            }

            .code-section > .section-header {
                flex-wrap: wrap;
            }

            .code-section > .section-header h3 {
                font-size: var(--sl-font-size-small);
                font-weight: 600;
                text-transform: uppercase;
            }
            .category-badge::part(base) {
                background: var(--sl-color-gray-100);
                color: var(--sl-color-gray-700);
            }

            .code-section > .section-header > sl-icon {
                color: var(--sl-color-primary-600);
                background: var(--sl-color-primary-100);
                padding: var(--sl-spacing-2x-small);
                border-radius: var(--sl-border-radius-small);
            }

            .section-description {
                margin-inline-start: auto;
            }

            .code-section > .section-body {
                padding: var(--sl-spacing-medium);
            }

            .code-section .filter-toolbar, .code-section .filter-left, .code-section .filter-right {
                align-items: center;
            }

            .code-section .filter-select, .code-search {
                flex: 0 1 14rem;
                min-width: 0;
                margin-top: 0;
            }

            .code-status {
                display: inline-flex;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
                font-weight: 600;
                white-space: nowrap;
            }

            .code-usage {
                min-width: 5rem;
                white-space: nowrap;
            }

            .code-usage sl-progress-bar {
                --height: var(--sl-spacing-2x-small);
                margin-top: var(--sl-spacing-2x-small);
            }

            .code-contact {
                display: block;
                overflow-wrap: anywhere;
            }

            .code-section .actions-cell {
                flex-wrap: nowrap;
            }

            .code-section sl-icon-button.danger {
                color: var(--sl-color-danger-600);
            }


            .code-section sl-format-date {
                white-space: nowrap;
            }

            .promo-dialog-title {
                display: flex;
                align-items: flex-start;
                gap: var(--sl-spacing-small);
            }

            #code-dialog {
                --width: min(52rem, calc(100vw - (2 * var(--sl-spacing-large))));
            }

            .promo-dialog-title sl-icon,
            .dialog-section-header sl-icon {
                color: var(--sl-color-primary-600);
            }

            .promo-dialog-title sl-icon {
                margin-top: var(--sl-spacing-2x-small);
                font-size: var(--sl-font-size-x-large);
            }

            .promo-dialog-title strong,
            .promo-dialog-title small {
                display: block;
            }

            .promo-dialog-title small {
                margin-top: var(--sl-spacing-2x-small);
                color: var(--sl-color-gray-500);
                font-size: var(--sl-font-size-small);
                font-weight: normal;
            }

            .promo-dialog-form {
                display: grid;
                gap: var(--sl-spacing-medium);
            }

            .promo-dialog-form .section-card {
                margin-bottom: 0;
            }

            .promo-dialog-form .section-body {
                padding: var(--sl-spacing-medium);
            }

            .dialog-section-header {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-small);
                padding: var(--sl-spacing-small) var(--sl-spacing-medium);
                background: var(--sl-color-gray-50);
                border-bottom: 1px solid var(--sl-color-gray-200);
                color: var(--sl-color-gray-700);
                font-size: var(--sl-font-size-small);
                font-weight: 600;
                letter-spacing: 0.025em;
                text-transform: uppercase;
            }

            .dialog-section-header sl-badge {
                margin-inline-start: auto;
                letter-spacing: normal;
                text-transform: none;
            }

            .promo-dialog-form sl-input,
            .promo-dialog-form sl-select,
            .promo-dialog-form sl-textarea {
                margin-top: 0;
            }

        `
    ];

    render() {
        return this.loadDataTask.render({
            initial: () => html`<sl-spinner></sl-spinner>`,
            error: () => html`<sl-alert open variant="danger"><sl-icon name="exclamation-triangle" slot="icon"></sl-icon>Failed to load promo codes.</sl-alert>`,
            complete: (data) => this.renderContent(data),
        });
    }

    private renderContent(data: LoadData): TemplateResult {
        return html`
            ${when(
                !data.event?.freeOfCharge,
                () => this.renderSection(data, data.promocodes, false)
            )}
            ${when(
                data.forEvent && data.restrictedCategories.length > 0 && !data.event?.freeOfCharge,
                () => this.renderSection(data, data.accesscodes, true)
            )}
            ${when(
                data.forEvent && data.event?.freeOfCharge,
                () => html`
                    <sl-alert open variant="warning" class="first-element">
                        <sl-icon name="exclamation-triangle" slot="icon"></sl-icon>
                        <strong>Your event cannot have ${data.promoCodeDescription} or Access Codes</strong>
                        <p>Free events do not support promo or access codes.</p>
                    </sl-alert>
                `
            )}

            ${this.renderCodeDialog(data)}
            ${this.renderUsageDetailsDialog()}
        `;
    }

    private renderSection(data: LoadData, codes: PromoCodeWithUsage[], isAccess: boolean): TemplateResult {
        const title = isAccess ? 'Access codes' : `${data.promoCodeDescription} codes`;
        const icon = isAccess ? 'unlock' : 'percent';
        const description = isAccess
            ? 'Access codes are special codes that give access to hidden categories. By entering an Access Code, an attendee can register one or more tickets, depending on the configuration.'
            : `Manage/Handle the ${data.promoCodeDescription} codes.`;
        const codeType: PromoCodeType = isAccess ? 'ACCESS' : 'DISCOUNT';

        const filterKey = isAccess ? 'access' : 'promo';
        const filter = this.filters[filterKey];
        const filteredCodes = codes.filter(code => code.promoCode.toLowerCase().includes(filter.search.trim().toLowerCase())
            && (!filter.category || (isAccess ? code.hiddenCategoryId === Number(filter.category)
                : !code.categories?.length || code.categories.includes(Number(filter.category)))));
        const setFilter = (field: 'search' | 'category', value: string) => {
            this.filters = {...this.filters, [filterKey]: {...filter, [field]: value}};
        };
        return html`
            <div class="section-card code-section ${isAccess ? '' : 'first-element'}">
                <div class="section-header">
                    <sl-icon name=${icon}></sl-icon><h3>${title}</h3>
                    <sl-badge variant="primary" pill>${filteredCodes.length === codes.length ? codes.length : `${filteredCodes.length} / ${codes.length}`}</sl-badge>
                    <span class="text-muted section-description">${description}</span>
                </div>
                <div class="section-body">
                    <div class="filter-toolbar">
                        <div class="filter-left">
                            <sl-input class="code-search" size="small" placeholder="Search code" aria-label="Search ${title}"
                                .value=${filter.search} @sl-input=${(e: Event) => setFilter('search', (e.target as SlInput).value)}>
                                <sl-icon name="search" slot="prefix"></sl-icon>
                            </sl-input>
                            ${when(data.forEvent, () => html`
                                <sl-select class="filter-select" size="small" placeholder="All categories" aria-label="Filter ${title} by category"
                                    .value=${filter.category} @sl-change=${(e: Event) => setFilter('category', (e.target as SlSelect).value as string)}>
                                    ${repeat(Object.values(data.ticketCategoriesById).filter(cat => !isAccess || cat.accessRestricted), cat => cat.id,
                                        cat => html`<sl-option value=${String(cat.id)}>${cat.name}</sl-option>`)}
                                </sl-select>
                            `)}
                            <sl-icon-button class="filter-clear-btn" name="x-circle" label="Clear ${title} filters"
                                @click=${() => {this.filters = {...this.filters, [filterKey]: {search: '', category: ''}};}}></sl-icon-button>
                        </div>
                        <div class="filter-right">
                            <sl-button size="small" ?disabled=${filteredCodes.length === 0} @click=${() => this.downloadCodes(filteredCodes, data, isAccess)}>
                                <sl-icon name="download" slot="prefix"></sl-icon>Download
                            </sl-button>
                            <sl-button variant="success" size="large" @click=${() => this.openCodeDialog(data, codeType)}>
                                <sl-icon name="plus-circle" slot="prefix"></sl-icon>Add ${isAccess ? 'Access' : data.promoCodeDescription} Code
                            </sl-button>
                        </div>
                    </div>
                    ${this.renderTable(data, filteredCodes, isAccess)}
                </div>
            </div>
        `;
    }

    private downloadCodes(codes: PromoCodeWithUsage[], data: LoadData, isAccess: boolean): void {
        const csv = Papa.unparse(codes.map(code => ({
            Code: code.promoCode, Status: this.codeStatus(code).label,
            Usage: code.useCount ?? '', 'Max usage': code.maxUsage ?? '',
            Start: code.formattedStart, End: code.formattedEnd, 'Time zone': data.event?.timeZone ?? 'UTC',
            ...(!isAccess ? {Amount: code.formattedDiscountAmount ?? code.discountAmount, 'Discount type': code.discountType, Currency: code.currencyCode} : {}),
            Categories: (isAccess ? [code.hiddenCategoryId] : code.categories ?? []).filter(id => id != null)
                .map(id => data.ticketCategoriesById[id!]?.name ?? id).join(', '),
            Description: code.description, Contact: code.emailReference,
        })), {escapeFormulae: true});
        const url = URL.createObjectURL(new Blob([csv], {type: 'text/csv;charset=utf-8;'}));
        const link = document.createElement('a');
        link.href = url;
        link.download = `${isAccess ? 'access' : 'promo'}-codes.csv`;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }

    private codeStatus(code: PromoCodeWithUsage) {
        if (code.expired) return {label: 'Expired', icon: 'clock-history', color: 'text-muted'};
        if (code.maxUsage != null && code.useCount != null && code.useCount >= code.maxUsage) {
            return {label: 'Used up', icon: 'check-circle', color: 'text-muted'};
        }
        return code.currentlyValid
            ? {label: 'Active', icon: 'check-circle', color: 'text-success'}
            : {label: 'Scheduled', icon: 'clock', color: 'text-muted'};
    }

    private renderTable(data: LoadData, codes: PromoCodeWithUsage[], isAccess: boolean): TemplateResult {
        if (codes.length === 0) {
            return html`
                <div class="empty-state">
                    <sl-icon name="inbox"></sl-icon>
                    <p>No codes found. Adjust the filters or add a new code.</p>
                </div>
            `;
        }

        return html`
            <div class="table-responsive">
                <table class="table table-striped">
                    <thead>
                        <tr>
                            <th>Code</th>
                            <th>Status</th>
                            <th>Usage</th>
                            <th>Start</th>
                            <th>End</th>
                            ${when(!isAccess, () => html`<th>Amount</th>`)}
                            ${when(data.forEvent, () => html`<th>Categories</th>`)}
                            <th>Description</th>
                            <th style="text-align:right">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(codes, (code) => code.id, (code) => {
                            const hasUsage = (code.useCount ?? 0) > 0;
                            const status = this.codeStatus(code);
                            return html`
                                <tr class=${code.expired ? 'text-muted' : ''}>
                                    <td>
                                        <span class="promo-code-name">
                                            ${hasUsage
                                                ? html`<a href="#" @click=${(e: Event) => { e.preventDefault(); this.showUsageDetails(code, data); }}>${code.promoCode}</a>`
                                                : code.promoCode}
                                        </span>
                                        ${when(!code.expired && code.codeType === 'DYNAMIC', () => html`<span class="text-muted"> (auto)</span>`)}
                                    </td>
                                    <td><span class="code-status ${status.color}"><sl-icon name=${status.icon}></sl-icon>${status.label}</span></td>
                                    <td class="code-usage">
                                        <strong>${code.useCount ?? '–'}</strong><span class="text-muted"> / ${code.maxUsage ?? '∞'}</span>
                                        ${when(code.maxUsage != null && code.maxUsage > 0 && code.useCount != null, () => html`
                                            <sl-progress-bar value=${Math.min(100, 100 * code.useCount! / code.maxUsage!)}
                                                label="${code.promoCode}: ${code.useCount} of ${code.maxUsage} uses"></sl-progress-bar>
                                        `)}
                                    </td>
                                    <td>
                                        <sl-format-date
                                            time-zone=${data.event?.timeZone ?? 'UTC'}
                                            date=${data.event ? code.formattedStart.replace(' ', 'T') : code.utcStart}
                                            month="short" day="2-digit" year="numeric" hour="2-digit" minute="2-digit" hour-format="24"
                                        ></sl-format-date>
                                    </td>
                                    <td>
                                        <sl-format-date
                                            time-zone=${data.event?.timeZone ?? 'UTC'}
                                            date=${data.event ? code.formattedEnd.replace(' ', 'T') : code.utcEnd}
                                            month="short" day="2-digit" year="numeric" hour="2-digit" minute="2-digit" hour-format="24"
                                        ></sl-format-date>
                                    </td>
                                    ${when(!isAccess, () => this.renderDiscountAmount(code))}
                                    ${when(data.forEvent, () => this.renderCategories(code, data))}
                                    <td>${code.description || '—'}<small class="text-muted code-contact">${code.emailReference}</small></td>
                                    <td>
                                        <div class="actions-cell">
                                            <sl-icon-button name="pencil" label="Edit ${code.promoCode}" @click=${() => this.openCodeDialog(data, code.codeType, code)}></sl-icon-button>
                                            ${when(!code.expired, () => html`
                                                <sl-icon-button name="eye-slash" label="Disable ${code.promoCode}" @click=${() => this.disableCode(code, data)}></sl-icon-button>
                                            `)}
                                            ${when(code.useCount === 0, () => html`
                                                <sl-icon-button class="danger" name="trash" label="Delete ${code.promoCode}" @click=${() => this.deleteCode(code, data)}></sl-icon-button>
                                            `)}
                                        </div>
                                    </td>
                                </tr>
                            `;
                        })}
                    </tbody>
                </table>
            </div>
        `;
    }

    private renderDiscountAmount(code: PromoCodeDiscount): TemplateResult {
        if (code.discountType === 'PERCENTAGE') {
            return html`<td><strong>-${code.discountAmount}%</strong></td>`;
        }
        const currency = code.currencyCode ?? '';
        return html`
            <td>
                -${code.formattedDiscountAmount ?? code.discountAmount}
                ${currency ? currency : ''}
                ${when(code.discountType === 'FIXED_AMOUNT', () => html`<div class="text-muted">per ticket</div>`)}
            </td>
        `;
    }

    private renderCategories(code: PromoCodeDiscount, data: LoadData): TemplateResult {
        if (code.codeType === 'ACCESS' && code.hiddenCategoryId) {
            const cat = data.ticketCategoriesById[code.hiddenCategoryId];
            return html`<td><sl-badge variant="neutral" class="category-badge" pill>${cat ? cat.name : code.hiddenCategoryId}</sl-badge></td>`;
        }

        if (code.categories && code.categories.length > 0) {
            return html`
                <td>
                    ${repeat(code.categories, (id) => id, (id) => {
                        const cat = data.ticketCategoriesById[id];
                        return html`<sl-badge variant="neutral" class="category-badge" pill>${cat ? cat.name : id}</sl-badge>`;
                    })}
                </td>
            `;
        }

        if (code.codeType !== 'ACCESS') {
            return html`<td>All categories</td>`;
        }

        return html`<td></td>`;
    }

    // ── Add / edit code dialog ──

    private async openCodeDialog(data: LoadData, codeType: PromoCodeType, code?: PromoCodeDiscount): Promise<void> {
        if (this.saving) return;
        this.validationErrors = {};
        this.editingState = code ? {code, data} : null;
        this.pendingCodeType = codeType;
        this.selectedDiscountType = code?.discountType ?? 'PERCENTAGE';
        this.selectedCategories = (code?.categories ?? []).map(String);
        this.allCategories = this.selectedCategories.length === 0;
        await this.updateComplete;

        const form = this.codeDialog.querySelector('form')!;
        const localDate = (date: Date) => new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
        const values: Record<string, string> = {
            promoCode: code?.promoCode ?? '',
            start: code?.formattedStart.replace(' ', 'T') ?? localDate(new Date()),
            end: code?.formattedEnd.replace(' ', 'T') ?? (data.event?.formattedBegin.replace(' ', 'T') ?? localDate(new Date(Date.now() + 86400000))),
            maxUsage: code?.maxUsage == null ? '' : String(code.maxUsage),
            discountType: code?.discountType ?? 'PERCENTAGE',
            discountAmount: code ? String(code.formattedDiscountAmount ?? code.discountAmount) : '',
            currencyCode: code?.currencyCode ?? 'USD',
            description: code?.description ?? '',
            emailReference: code?.emailReference ?? '',
            hiddenCategoryId: code?.hiddenCategoryId == null ? '' : String(code.hiddenCategoryId),
        };
        form.reset();
        const allCategoriesSwitch = form.querySelector<SlSwitch>('sl-switch');
        if (allCategoriesSwitch) allCategoriesSwitch.checked = this.allCategories;
        form.querySelectorAll<SlInput | SlSelect | SlTextarea>('sl-input, sl-select, sl-textarea')
            .forEach(control => { control.value = control.name === 'categories' ? this.selectedCategories : values[control.name] ?? ''; });
        await this.codeDialog.show();
    }

    private renderCodeDialog(data: LoadData): TemplateResult {
        const isAccess = this.pendingCodeType === 'ACCESS';
        const isDiscount = this.pendingCodeType === 'DISCOUNT';
        const editing = this.editingState != null;
        const title = `${editing ? 'Edit' : 'Insert new'} ${isAccess ? 'Access' : data.promoCodeDescription} Code`;
        const save = () => this.saveCode(data);

        return html`
            <sl-dialog id="code-dialog" label=${title} size="large" placement="bottom"
                @sl-request-close=${(e: CustomEvent) => { if (this.saving) e.preventDefault(); }}>
                <div slot="label" class="promo-dialog-title">
                    <sl-icon name=${isAccess ? 'unlock' : 'ticket-perforated'}></sl-icon>
                    <div>
                        <strong>${title}</strong>
                        <small>${editing ? 'Update validity, usage limits, and details. Code and discount cannot be changed.' : 'Fields marked * are required.'}</small>
                    </div>
                </div>
                <form class="promo-dialog-form" @submit=${(e: Event) => { e.preventDefault(); save(); }}>
                    <div class="section-card">
                        <div class="dialog-section-header"><sl-icon name="tag"></sl-icon>Code</div>
                        <div class="section-body row" style="--alfio-row-cols: 2">
                            <sl-input name="promoCode" label="Code name" ?readonly=${editing} helper-text="Min 7 characters. Letters, numbers, and -:_@!$*,;" required minlength="7" pattern="[A-Za-z0-9_\\-:@!$*,;]*" clearable @sl-input=${(e: Event) => {
                                const input = e.target as SlInput;
                                input.value = input.value.toUpperCase();
                            }}>
                                <sl-icon name="tag" slot="prefix"></sl-icon>
                            </sl-input>
                            <sl-input name="maxUsage" type="number" label="Limit usage to" helper-text="Leave empty for unlimited uses" min="0">
                                <sl-icon name="hash" slot="prefix"></sl-icon>
                            </sl-input>
                        </div>
                    </div>

                    <div class="section-card">
                        <div class="dialog-section-header">
                            <sl-icon name="calendar-event"></sl-icon>Validity
                            ${when(data.forEvent && data.event, () => html`<sl-badge variant="primary" pill>${data.event!.timeZone}</sl-badge>`)}
                        </div>
                        <div class="section-body row" style="--alfio-row-cols: 2">
                            <sl-input type="datetime-local" name="start" label="Valid from" required></sl-input>
                            <sl-input type="datetime-local" name="end" label="Valid until" required></sl-input>
                        </div>
                    </div>

                    ${when(isDiscount, () => html`
                        <div class="section-card">
                            <div class="dialog-section-header"><sl-icon name="percent"></sl-icon>Discount</div>
                            <div class="section-body row" style="--alfio-row-cols: ${data.forEvent ? 2 : 3}">
                                <sl-select name="discountType" label="Discount type" ?disabled=${editing} value="PERCENTAGE" required
                                    @sl-change=${(e: Event) => { this.selectedDiscountType = (e.target as SlSelect).value as DiscountType; }}>
                                    <sl-option value="PERCENTAGE">Percentage</sl-option>
                                    <sl-option value="FIXED_AMOUNT_RESERVATION">Fixed amount, per reservation</sl-option>
                                    <sl-option value="FIXED_AMOUNT">Fixed amount, per ticket</sl-option>
                                </sl-select>
                                <sl-input name="discountAmount" ?readonly=${editing} type="number" label="Discount amount" min="0" required>
                                    ${when(this.selectedDiscountType === 'PERCENTAGE', () => html`<sl-icon name="percent" slot="suffix"></sl-icon>`)}
                                </sl-input>
                                ${when(!data.forEvent, () => html`
                                    <sl-select name="currencyCode" label="Currency" ?disabled=${editing} value="USD">
                                        ${repeat(data.currencies, (c) => c.code, (c) => html`<sl-option value=${c.code}>${c.name}</sl-option>`)}
                                    </sl-select>
                                `)}
                            </div>
                        </div>
                    `)}

                    ${when(data.forEvent && (isDiscount || isAccess), () => html`
                        <div class="section-card">
                            <div class="dialog-section-header"><sl-icon name=${isAccess ? 'unlock' : 'collection'}></sl-icon>Ticket categories</div>
                            <div class="section-body">
                                ${when(!isAccess, () => html`
                                    <sl-switch help-text="Or select specific categories" class="block mt-2 mb-2"
                                        .checked=${this.allCategories}
                                        @sl-change=${(e: Event) => {
                                            this.allCategories = (e.target as SlSwitch).checked;
                                            if (this.allCategories) this.selectedCategories = [];
                                        }}>Apply to all ticket categories</sl-switch>
                                `)}
                                ${when(isAccess || !this.allCategories, () => html`
                                    <sl-select name=${isAccess ? 'hiddenCategoryId' : 'categories'}
                                        label=${isAccess ? 'Select Category' : 'Select Categories'}
                                        placeholder=${isAccess ? 'Select a category' : 'Select categories'}
                                        ?multiple=${!isAccess} ?required=${!isAccess || !editing} ?disabled=${isAccess && editing}
                                        .value=${isAccess ? String(this.editingState?.code.hiddenCategoryId ?? '') : this.selectedCategories}
                                        @sl-change=${(e: Event) => {
                                            if (!isAccess) this.selectedCategories = (e.target as SlSelect).value as string[];
                                        }}>
                                        ${repeat(isAccess
                                            ? [...new Set([...data.restrictedCategories.map(cat => cat.id), ...(this.editingState?.code.hiddenCategoryId != null ? [this.editingState.code.hiddenCategoryId] : [])])]
                                            : [...new Set([...data.validCategories.map(cat => cat.id), ...(this.editingState?.code.categories ?? [])])], id => id,
                                            id => html`<sl-option value=${String(id)}>${data.ticketCategoriesById[id]?.name ?? id}</sl-option>`)}
                                    </sl-select>
                                `)}
                            </div>
                        </div>
                    `)}

                    <div class="section-card">
                        <div class="dialog-section-header"><sl-icon name="card-text"></sl-icon>Details</div>
                        <div class="section-body row" style="--alfio-row-cols: 2">
                            <sl-textarea name="description" label="Description" maxlength="1024" rows="2" resize="auto"></sl-textarea>
                            <sl-input name="emailReference" type="email" label="Contact" maxlength="256">
                                <sl-icon name="envelope" slot="prefix"></sl-icon>
                            </sl-input>
                        </div>
                    </div>

                    ${when(Object.keys(this.validationErrors).length > 0, () => html`
                        <sl-alert open variant="danger">
                            <sl-icon name="exclamation-triangle" slot="icon"></sl-icon>
                            ${repeat(Object.entries(this.validationErrors), ([field]) => field,
                                ([, message]) => html`<div>${message}</div>`)}
                        </sl-alert>
                    `)}
                </form>

                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.codeDialog.hide()} ?disabled=${this.saving}>Cancel</sl-button>
                        <div></div>
                        <sl-button variant="warning" size="large" @click=${() => save()} ?disabled=${this.saving}>
                            ${when(this.saving, () => html`<sl-spinner slot="prefix"></sl-spinner>`)}
                            ${when(!this.saving, () => html`<sl-icon name="check2" slot="prefix"></sl-icon>`)}
                            Save
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private async saveCode(data: LoadData): Promise<void> {
        if (this.saving) return;
        const code = this.editingState?.code;
        data = this.editingState?.data ?? data;
        const form = this.codeDialog.querySelector('form')!;
        if (!form.reportValidity()) return;
        const value = (name: string): string => {
            const control = form.querySelector<SlInput | SlSelect | SlTextarea>(`[name="${name}"]`);
            return typeof control?.value === 'string' ? control.value : '';
        };
        const selectedCategories = this.allCategories ? [] : this.selectedCategories.map(Number);
        const maxUsage = value('maxUsage');
        const common = {
            start: this.parseDateTime(value('start')),
            end: this.parseDateTime(value('end')),
            maxUsage: maxUsage ? Number.parseInt(maxUsage, 10) : null,
            description: value('description'),
            emailReference: value('emailReference'),
        };
        const promoCode = value('promoCode').toUpperCase();
        const discountType = (value('discountType') || 'PERCENTAGE') as DiscountType;
        const currencyCode = data.event?.currency ?? value('currencyCode');
        if (!code) {
            if (promoCode.length < 7 || !/^[A-Za-z0-9_\-:@!$*,;]*$/.test(promoCode)) {
                this.validationErrors = {promoCode: 'Use at least 7 characters: letters, numbers, and -:_@!$*,;'};
                return;
            }
            if (!data.forEvent && discountType.startsWith('FIXED_AMOUNT') && !currencyCode) {
                this.validationErrors = {currencyCode: 'Currency is required for fixed-amount discounts.'};
                return;
            }
        }
        this.saving = true;
        this.validationErrors = {};
        const failureMessage = code ? 'Failed to update code' : 'Failed to create promo code';
        try {
            const response = code
                ? await PromoCodeService.update(code.id, {
                    ...common,
                    categories: data.forEvent && code.codeType === 'DISCOUNT'
                        ? selectedCategories
                        : code.categories,
                    hiddenCategoryId: code.hiddenCategoryId,
                    eventId: code.eventId,
                    organizationId: code.organizationId!,
                })
                : await PromoCodeService.add({
                    ...common,
                    promoCode,
                    discountAmount: value('discountAmount') ? Number(value('discountAmount')) : null,
                    discountType,
                    categories: data.forEvent ? selectedCategories : [],
                    codeType: this.pendingCodeType,
                    hiddenCategoryId: this.pendingCodeType === 'ACCESS' ? Number(value('hiddenCategoryId')) : null,
                    currencyCode,
                    eventId: data.event?.id ?? null,
                    organizationId: data.forEvent ? null : data.organizationId,
                });
            if (response.ok) {
                await this.codeDialog.hide();
                this.loadDataTask.run();
                dispatchFeedback({type: 'success', message: code ? 'Code updated successfully' : 'Promo code created successfully'}, this);
            } else {
                const body = await response.json();
                if (body.validationErrors) {
                    this.validationErrors = this.extractValidationErrors(body.validationErrors);
                } else {
                    dispatchFeedback({type: 'danger', message: failureMessage}, this);
                }
            }
        } catch {
            dispatchFeedback({type: 'danger', message: failureMessage}, this);
        } finally {
            this.saving = false;
        }
    }

    // ── Usage details dialog ──

    private async showUsageDetails(code: PromoCodeDiscount, data: LoadData): Promise<void> {
        try {
            this.usageData = await PromoCodeService.getUsageDetails(code.id, data.event?.shortName);
            if (this.usageData.length === 0) {
                dispatchFeedback({type: 'warning', message: 'No reservations found for this code.'}, this);
                return;
            }
            this.usageDetailsDialog.show();
        } catch {
            dispatchFeedback({type: 'danger', message: 'Failed to load usage details.'}, this);
        }
    }

    private renderUsageDetailsDialog(): TemplateResult {
        return html`
            <sl-dialog id="usage-details-dialog" label="Usage details" class="usage-details-dialog" size="large" placement="bottom">
                ${repeat(this.usageData, (detail) => detail.event.shortName, (detail) => html`
                    <h4>${detail.event.displayName}</h4>
                    <div class="table-responsive">
                        <table class="table table-striped">
                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>Customer</th>
                                    <th>Payment</th>
                                    <th>Amount</th>
                                    <th>Confirmation</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${repeat(detail.reservations, (r) => r.id, (reservation) => html`
                                    <tr>
                                        <td><a href="/admin/#/events/${detail.event.shortName}/reservations/${reservation.id}" target="_blank">${reservation.id}</a></td>
                                        <td>${reservation.firstName} ${reservation.lastName} &lt;${reservation.email}&gt;</td>
                                        <td>${reservation.paymentType}</td>
                                        <td>
                                            ${when(reservation.paymentType !== 'NONE', () => html`${reservation.currency} ${reservation.formattedAmount}`)}
                                        </td>
                                        <td>
                                            <sl-format-date
                                                time-zone="UTC"
                                                date=${this.asUtcDateTime(reservation.confirmationTimestamp)}
                                                month="short" day="2-digit" year="numeric" hour="2-digit" minute="2-digit" hour-format="24"
                                            ></sl-format-date>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="4">
                                            <table class="table">
                                                <thead>
                                                    <tr>
                                                        <th style="font-weight:normal">Ticket ID</th>
                                                        <th style="font-weight:normal">Attendee</th>
                                                        <th style="font-weight:normal">Type</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    ${repeat(reservation.tickets, (t) => t.id, (ticket) => html`
                                                        <tr>
                                                            <td>${ticket.id.substring(0, 8).toUpperCase()}</td>
                                                            <td>${ticket.firstName} ${ticket.lastName}</td>
                                                            <td>${ticket.type}</td>
                                                        </tr>
                                                    `)}
                                                </tbody>
                                            </table>
                                        </td>
                                    </tr>
                                `)}
                            </tbody>
                        </table>
                    </div>
                `)}

                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <div></div>
                        <div></div>
                        <sl-button variant="default" size="large" @click=${() => this.usageDetailsDialog.hide()}>Close</sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    // ── Actions ──

    private async deleteCode(code: PromoCodeDiscount, data: LoadData): Promise<void> {
        const confirmed = await ConfirmationDialogService.requestConfirm(
            `Delete ${data.promoCodeDescription} code`,
            `Do you really want to delete the code "${code.promoCode}"? This action cannot be undone.`,
            'danger'
        );

        if (!confirmed) return;

        try {
            const response = await PromoCodeService.remove(code.id);
            if (response.ok) {
                this.loadDataTask.run();
                dispatchFeedback({type: 'success', message: 'Code deleted successfully'}, this);
            } else {
                dispatchFeedback({type: 'danger', message: 'Failed to delete code'}, this);
            }
        } catch {
            dispatchFeedback({type: 'danger', message: 'Failed to delete code'}, this);
        }
    }

    private async disableCode(code: PromoCodeDiscount, data: LoadData): Promise<void> {
        const confirmed = await ConfirmationDialogService.requestConfirm(
            `Disable ${data.promoCodeDescription} code`,
            `Do you want to disable the code "${code.promoCode}"?`,
            'warning'
        );

        if (!confirmed) return;

        try {
            const response = await PromoCodeService.disable(code.id);
            if (response.ok) {
                this.loadDataTask.run();
                dispatchFeedback({type: 'success', message: 'Code disabled successfully'}, this);
            } else {
                dispatchFeedback({type: 'danger', message: 'Failed to disable code'}, this);
            }
        } catch {
            dispatchFeedback({type: 'danger', message: 'Failed to disable code'}, this);
        }
    }

    // ── Helpers ──

    private parseDateTime(isoString: string): DateTimeModification {
        const date = isoString.substring(0, 10);
        const time = isoString.substring(11, 16);
        return {date, time};
    }

    private asUtcDateTime(dateTime: string): string {
        const normalized = dateTime.replace(' ', 'T');
        return /(?:Z|[+-]\d{2}:?\d{2})$/.test(normalized) ? normalized : `${normalized}Z`;
    }

    private extractValidationErrors(errors: Array<{fieldName: string, message: string}>): Record<string, string> {
        const result: Record<string, string> = {};
        for (const err of errors) {
            result[err.fieldName] = err.message;
        }
        return result;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-promo-code': PromoCode;
    }
}
