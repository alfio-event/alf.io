import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import {Task} from '@lit/task';
import {TanStackFormController} from '@tanstack/lit-form';
import Papa from 'papaparse';
import {AlfioEvent, TicketCategory} from '../../model/event.ts';
import {
    PromoCodeDiscount,
    PromoCodeType,
    DiscountType,
    UsageDetailEvent
} from '../../model/promo-code.ts';
import {DateTimeModification} from '../../model/purchase-context.ts';
import {PromoCodeService} from '../../service/promo-code.ts';
import {UtilService} from '../../service/util.ts';
import {ConfirmationDialogService} from '../../service/confirmation-dialog.ts';
import {
    badges,
    dialog,
    form,
    modernLayout,
    modernTable,
    purchaseContextListPage,
    retroCompat,
    row,
    sortableTable,
    spacing,
    textColors
} from '../../styles.ts';
import {nextSort, sortableHeader, sortItems, SortState, SortValue} from '../../service/table-sort.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {EventService} from '../../service/event.ts';
import {downloadCsv, fetchJson} from '../../service/helpers.ts';
import {localizedTitle} from '../../service/reservation-format.ts';
import '../../components/format-date.ts';
import '../../components/pagination-bar.ts';
import type {AlfioPageChange} from '../../components/pagination-bar.ts';
import type {SlDialog, SlInput, SlRequestCloseEvent, SlSelect, SlSwitch, SlTextarea} from '@shoelace-style/shoelace';
import {emptyState} from '../../components/empty-state.ts';
import {taskContent} from '../../components/task-content.ts';
import {
    BoundField,
    emailAddress,
    fieldError,
    focusFirstError,
    FormMountController,
    requiredValue,
    setServerError,
    submitValidated,
    validatedInput,
    ValidatedInputOptions,
    validatedSelect,
    validateWith,
} from '../../components/form-validation.ts';

const PROMO_CODE_PATTERN = /^[A-Za-z0-9_\-:@!$*,;]*$/;

/**
 * The values of the code dialog
 */
interface CodeFormValues {
    promoCode: string;
    // empty for unlimited uses
    maxUsage: string;
    // datetime-local values
    start: string;
    end: string;
    discountType: DiscountType;
    discountAmount: string;
    currencyCode: string;
    // the categories a discount applies to, if not all of them
    categories: string[];
    // the category unlocked by an access code
    hiddenCategoryId: string;
    description: string;
    emailReference: string;
}

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

type SortKey = 'code' | 'status' | 'usage' | 'start' | 'end' | 'amount' | 'description';
interface TableState extends SortState<SortKey> {
    page: number;
}

function emptyCodeForm(): CodeFormValues {
    return {
        promoCode: '',
        maxUsage: '',
        start: '',
        end: '',
        discountType: 'PERCENTAGE',
        discountAmount: '',
        currencyCode: 'USD',
        categories: [],
        hiddenCategoryId: '',
        description: '',
        emailReference: '',
    };
}

function validateMaxUsage(value: unknown): string | undefined {
    const text = String(value ?? '').trim();
    if (text === '') {
        return undefined;
    }
    const maxUsage = Number(text);
    if (!Number.isInteger(maxUsage) || maxUsage < 1) {
        return 'Please enter a number, at least 1, or leave it empty for unlimited uses';
    }
    return undefined;
}

function optionalEmail(value: unknown): string | undefined {
    if (String(value ?? '').trim() === '') {
        return undefined;
    }
    return emailAddress(value);
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

    // errors returned by the server that don't belong to a field of the dialog
    @state()
    private validationErrors: Record<string, string> = {};

    readonly #codeForm = new TanStackFormController(this, {
        defaultValues: emptyCodeForm(),
        onSubmit: async ({value}) => {
            await this.saveCode(value);
        },
    });

    constructor() {
        super();
        new FormMountController(this, this.#codeForm.api);
    }

    @state()
    private usageData: UsageDetailEvent[] = [];

    @state()
    private usageCode = '';

    @state()
    private usageTimeZone = 'UTC';

    @state()
    private saving = false;

    @state()
    private editingState: EditingState | null = null;

    @state()
    private pendingCodeType: PromoCodeType = 'DISCOUNT';

    @state()
    private allCategories = true;

    @state()
    private filters = {promo: {search: '', category: ''}, access: {search: '', category: ''}};

    @state()
    private tableState: Record<'promo' | 'access', TableState> = {
        promo: {page: 1, sortKey: 'code', sortDirection: 'asc'},
        access: {page: 1, sortKey: 'code', sortDirection: 'asc'},
    };

    /**
     * False while AngularJS has not interpolated the attributes yet (e.g. data-organization-id="{{...}}",
     * which Lit reads as NaN): the next run will have the real values.
     */
    private get ready(): boolean {
        if (this.eventName != null && this.eventName.includes('{{')) {
            return false;
        }
        if (this.organizationId != null && Number.isNaN(this.organizationId)) {
            return false;
        }
        return true;
    }

    private loadDataTask = new Task(this,
        async ([_eventName, _orgId, _forEvent]) => {
            void _eventName;
            void _orgId;
            void _forEvent;

            if (!this.ready) {
                // AngularJS has not interpolated the attributes yet: the next run will have them
                return new Promise<never>(() => {});
            }

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
                    if (val === 'true') {
                        promoCodeDescription = 'Partner';
                    }
                } catch {
                    // ignore, keep default
                }
            }

            let allCodes: PromoCodeDiscount[];
            if (forEvent) {
                allCodes = await PromoCodeService.listByEvent(ev!.id);
            } else {
                allCodes = await PromoCodeService.listByOrganization(orgId);
            }

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
                if (Array.isArray(currencyResult)) {
                    currencies = currencyResult;
                }
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
        purchaseContextListPage,
        row,
        spacing,
        sortableTable,
        css`
            .promo-code-name {
                font-family: var(--sl-font-mono);
                font-weight: 600;
            }

            .code-section {
                margin-bottom: var(--sl-spacing-x-large);
                --alfio-section-body-padding: var(--sl-spacing-medium);
            }

            .section-heading {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-small);
                min-width: 0;
                order: 1;
            }

            .category-badge::part(base) {
                background: var(--sl-color-gray-100);
                color: var(--sl-color-gray-700);
            }

            .section-description {
                flex-basis: 100%;
                font-size: var(--sl-font-size-small);
                line-height: var(--sl-line-height-normal);
                order: 3;
            }

            .section-description::part(base) {
                border: 0;
                background: transparent;
                padding: 0;
            }

            .section-description::part(summary) {
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
            }

            .section-description::part(content) {
                padding: var(--sl-spacing-2x-small) 0 0;
                color: var(--sl-color-gray-600);
            }

            .code-section .header-toolbar {
                margin-inline-start: auto;
                margin-block: calc(-1 * var(--sl-spacing-2x-small));
                padding: 0;
                border: 0;
                background: transparent;
                order: 2;
            }

            .code-section .header-toolbar .filter-left {
                flex: 0 1 auto;
                flex-wrap: nowrap;
                white-space: nowrap;
            }

            .code-section .header-toolbar {
                flex-wrap: nowrap;
            }

            .code-section .empty-state sl-icon {
                font-size: var(--sl-font-size-3x-large);
            }

            .code-section .filter-toolbar, .code-section .filter-left, .code-section .filter-right {
                align-items: center;
            }

            .code-section .filter-select, .code-search {
                min-width: 0;
                margin-top: 0;
            }

            .code-section .filter-select {
                flex: 0 1 20rem;
            }

            .code-section .code-search {
                flex: 0 1 16rem;
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

            @media (max-width: 767px) {
                .code-section .header-toolbar,
                .code-section .header-toolbar .filter-left {
                    flex-wrap: wrap;
                }

                .code-section .filter-select,
                .code-section .code-search {
                    flex: 1 1 14rem;
                }
            }

            .code-section alfio-format-date {
                white-space: nowrap;
            }

            .code-section .table-responsive {
                max-height: 65vh;
                overflow: auto;
            }

            .code-section .table {
                border-collapse: separate;
                border-spacing: 0;
            }

            .code-section .table thead {
                position: relative;
                z-index: 3;
            }

            .code-section .table thead th {
                position: sticky;
                top: 0;
                z-index: 4;
                background-color: var(--sl-color-gray-50) !important;
                background-clip: padding-box;
                box-shadow: 0 2px 0 var(--sl-color-gray-300);
            }

            #usage-details-dialog {
                --alfio-dialog-max-width: 75rem;
            }

        `
    ];

    render() {
        return taskContent(this.loadDataTask, this.loadDataTask.value ?? null, 'Failed to load promo codes.',
            data => this.renderContent(data));
    }

    private renderContent(data: LoadData): TemplateResult {
        const event = data.event;
        if (data.forEvent && event != null) {
            // page of the event detail
            return html`
                <div class="container">
                    <div class="page-title-row secondary">
                        <h1>${data.promoCodeDescription} codes for <i>${localizedTitle(event)}</i></h1>
                    </div>
                    <hr class="page-separator" />
                    ${this.renderCodes(data)}
                </div>
            `;
        }
        // embedded in the organization configuration
        return this.renderCodes(data);
    }

    private renderCodes(data: LoadData): TemplateResult {
        return html`
            ${when(
                !data.event?.freeOfCharge,
                () => html`
                    <div class="filter-toolbar" style="justify-content: flex-end;">
                        <div class="filter-right">${this.renderAddCodeButton(data)}</div>
                    </div>
                    ${this.renderSection(data, data.promocodes, false)}
                `
            )}
            ${when(
                this.shouldRenderAccessSection(data),
                () => this.renderSection(data, data.accesscodes, true)
            )}
            ${when(
                data.forEvent && data.event?.freeOfCharge,
                () => html`
                    <sl-alert open variant="warning">
                        <sl-icon name="exclamation-triangle" slot="icon"></sl-icon>
                        <strong>Your event cannot have ${data.promoCodeDescription.toLowerCase()} or access codes</strong>
                        <p>Free events do not support promo or access codes.</p>
                    </sl-alert>
                `
            )}

            ${this.renderCodeDialog(data)}
            ${this.renderUsageDetailsDialog()}
        `;
    }

    private renderSection(data: LoadData, codes: PromoCodeWithUsage[], isAccess: boolean): TemplateResult {
        let title = `${data.promoCodeDescription} codes`;
        let icon = 'percent';
        let description = `${data.promoCodeDescription} codes give a discount on the tickets of a reservation.`;
        let filterKey: 'promo' | 'access' = 'promo';
        if (isAccess) {
            title = 'Access codes';
            icon = 'unlock';
            description = 'Access codes unlock unlisted categories: an attendee who enters one can buy tickets of that category.';
            filterKey = 'access';
        }

        const filter = this.filters[filterKey];
        const searchTerm = filter.search.trim().toLowerCase();
        const matchedCodes = codes.filter(code => `${code.promoCode} ${code.description ?? ''}`.toLowerCase().includes(searchTerm)
            && this.matchesCategory(code, filter.category, isAccess));
        let countBadge = `${matchedCodes.length} / ${codes.length}`;
        if (matchedCodes.length === codes.length) {
            countBadge = String(codes.length);
        }
        const state = this.tableState[filterKey];
        const sortedCodes = sortItems(matchedCodes, state, (code, key) => this.sortValue(code, key));
        const pageCount = Math.max(1, Math.ceil(sortedCodes.length / 25));
        const page = Math.min(state.page, pageCount);
        if (page !== state.page) {
            queueMicrotask(() => this.setTablePage(filterKey, page));
        }
        const setFilter = (field: 'search' | 'category', value: string) => {
            this.filters = {...this.filters, [filterKey]: {...filter, [field]: value}};
            this.setTablePage(filterKey, 1);
        };
        const setSort = (sortKey: SortKey) => {
            this.tableState = {...this.tableState, [filterKey]: {page: 1, ...nextSort(state, sortKey)}};
        };
        return html`
            <div class="section-card code-section">
                <div class="section-header">
                    <div class="section-heading">
                        <sl-icon name=${icon}></sl-icon><h3>${title}</h3>
                        <sl-badge variant="primary" pill>${countBadge}</sl-badge>
                    </div>
                    <sl-details class="text-muted section-description">
                        <span slot="summary">About ${title.toLowerCase()}</span>${description}
                    </sl-details>
                    <div class="filter-toolbar header-toolbar">
                        <div class="filter-left">
                            <sl-input class="code-search" size="small" clearable placeholder="Search code" aria-label="Search ${title}"
                                .value=${filter.search}
                                @sl-input=${(e: Event) => setFilter('search', (e.target as SlInput).value)}
                                @sl-clear=${() => setFilter('search', '')}>
                                <sl-icon name="search" slot="prefix"></sl-icon>
                            </sl-input>
                            ${when(data.forEvent, () => html`
                                <sl-select class="filter-select" size="small" clearable hoist placeholder="All categories" aria-label="Filter ${title} by category"
                                    .value=${filter.category}
                                    @sl-change=${(e: Event) => setFilter('category', (e.target as SlSelect).value as string)}
                                    @sl-clear=${() => setFilter('category', '')}>
                                    ${repeat(Object.values(data.ticketCategoriesById).filter(cat => !isAccess || cat.accessRestricted), cat => cat.id,
                                        cat => html`<sl-option value=${String(cat.id)}>${cat.name}</sl-option>`)}
                                </sl-select>
                            `)}
                        </div>
                        <div class="filter-right">
                            <sl-button size="small" ?disabled=${matchedCodes.length === 0} @click=${() => this.downloadCodes(matchedCodes, data, isAccess)}>
                                <sl-icon name="download" slot="prefix"></sl-icon>Download
                            </sl-button>
                        </div>
                    </div>
                </div>
                <div class="section-body">
                    ${this.renderTable(data, sortedCodes.slice((page - 1) * 25, page * 25), isAccess, setSort, state)}
                    ${when(sortedCodes.length > 0, () => html`
                        <alfio-pagination-bar page=${page} total=${sortedCodes.length} page-size="25" item-label="codes"
                                              @alfio-page-change=${(e: AlfioPageChange) => this.setTablePage(filterKey, e.detail.page)}></alfio-pagination-bar>
                    `)}
                </div>
            </div>
        `;
    }

    private matchesCategory(code: PromoCodeWithUsage, category: string, isAccess: boolean): boolean {
        if (!category) {
            return true;
        }
        if (isAccess) {
            return code.hiddenCategoryId === Number(category);
        }
        // a discount without categories applies to all of them
        return !code.categories?.length || code.categories.includes(Number(category));
    }

    private setTablePage(filterKey: 'promo' | 'access', page: number): void {
        this.tableState = {...this.tableState, [filterKey]: {...this.tableState[filterKey], page}};
    }

    private sortValue(code: PromoCodeWithUsage, sortKey: SortKey): SortValue {
        switch (sortKey) {
            case 'code': return code.promoCode;
            case 'status': return this.codeStatus(code).label;
            case 'usage': return code.useCount ?? -1;
            case 'start': return code.formattedStart;
            case 'end': return code.formattedEnd;
            case 'amount': return code.discountAmount;
            case 'description': return code.description ?? '';
        }
    }

    private canAddAccessCode(data: LoadData): boolean {
        return data.forEvent && data.restrictedCategories.length > 0 && !data.event?.freeOfCharge;
    }

    private shouldRenderAccessSection(data: LoadData): boolean {
        return data.forEvent && !data.event?.freeOfCharge
            && (data.accesscodes.length > 0 || this.canAddAccessCode(data));
    }

    private renderAddCodeButton(data: LoadData): TemplateResult {
        if (!this.canAddAccessCode(data)) {
            return html`
                <sl-button variant="success" size="large" @click=${() => this.openCodeDialog(data, 'DISCOUNT')}>
                    <sl-icon name="plus-circle" slot="prefix"></sl-icon>Add ${data.promoCodeDescription.toLowerCase()} code
                </sl-button>
            `;
        }
        return html`
            <sl-dropdown hoist placement="bottom-end">
                <sl-button slot="trigger" variant="success" size="large" caret>
                    <sl-icon name="plus-circle" slot="prefix"></sl-icon>Add code
                </sl-button>
                <sl-menu @sl-select=${(e: CustomEvent) => this.openCodeDialog(data, e.detail.item.value as PromoCodeType)}>
                    <sl-menu-item value="DISCOUNT"><sl-icon name="percent" slot="prefix"></sl-icon>${data.promoCodeDescription} code</sl-menu-item>
                    <sl-menu-item value="ACCESS"><sl-icon name="unlock" slot="prefix"></sl-icon>Access code</sl-menu-item>
                </sl-menu>
            </sl-dropdown>
        `;
    }

    private downloadCodes(codes: PromoCodeWithUsage[], data: LoadData, isAccess: boolean): void {
        const csv = Papa.unparse(codes.map(code => {
            let discount = {};
            let categoryIds: Array<number | null | undefined> = code.categories ?? [];
            if (isAccess) {
                categoryIds = [code.hiddenCategoryId];
            } else {
                discount = {Amount: code.formattedDiscountAmount ?? code.discountAmount, 'Discount type': code.discountType, Currency: code.currencyCode};
            }
            return {
                Code: code.promoCode, Status: this.codeStatus(code).label,
                Usage: code.useCount ?? '', 'Max usage': code.maxUsage ?? '',
                Start: code.formattedStart, End: code.formattedEnd, 'Time zone': data.event?.timeZone ?? 'UTC',
                ...discount,
                Categories: categoryIds.filter(id => id != null)
                    .map(id => data.ticketCategoriesById[id!]?.name ?? id).join(', '),
                Description: code.description, Contact: code.emailReference,
            };
        }), {escapeFormulae: true});
        let fileName = 'promo-codes.csv';
        if (isAccess) {
            fileName = 'access-codes.csv';
        }
        downloadCsv(fileName, csv);
    }

    private codeStatus(code: PromoCodeWithUsage) {
        if (code.expired) return {label: 'Expired', icon: 'clock-history', color: 'text-muted'};
        if (code.maxUsage != null && code.useCount != null && code.useCount >= code.maxUsage) {
            return {label: 'Used up', icon: 'check-circle', color: 'text-muted'};
        }
        if (code.currentlyValid) {
            return {label: 'Active', icon: 'check-circle', color: 'text-success'};
        }
        return {label: 'Scheduled', icon: 'clock', color: 'text-muted'};
    }

    // with an event, the dates are shown in its time zone; otherwise, the UTC instant
    private codeDate(code: PromoCodeDiscount, data: LoadData, which: 'start' | 'end'): string {
        if (data.event) {
            if (which === 'start') {
                return code.formattedStart;
            }
            return code.formattedEnd;
        }
        if (which === 'start') {
            return code.utcStart;
        }
        return code.utcEnd;
    }

    private categoryName(id: number, data: LoadData): string | number {
        return data.ticketCategoriesById[id]?.name ?? id;
    }

    private renderTable(data: LoadData, codes: PromoCodeWithUsage[], isAccess: boolean,
                        setSort: (key: SortKey) => void, state: TableState): TemplateResult {
        if (codes.length === 0) {
            return html`
                ${emptyState('No codes found. Adjust the filters or add a new code.')}
            `;
        }

        return html`
            <div class="table-responsive">
                <table class="table table-striped">
                    <thead>
                        <tr>
                            ${sortableHeader('Code', 'code', state, setSort)}
                            ${sortableHeader('Status', 'status', state, setSort)}
                            ${sortableHeader('Usage', 'usage', state, setSort)}
                            ${sortableHeader('Start', 'start', state, setSort)}
                            ${sortableHeader('End', 'end', state, setSort)}
                            ${when(!isAccess, () => sortableHeader('Amount', 'amount', state, setSort))}
                            ${when(data.forEvent, () => html`<th>Categories</th>`)}
                            ${sortableHeader('Description', 'description', state, setSort)}
                            <th style="text-align:right">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(codes, (code) => code.id, (code) => {
                            const hasUsage = (code.useCount ?? 0) > 0;
                            const status = this.codeStatus(code);
                            return html`
                                <tr class=${classMap({'text-muted': code.expired})}>
                                    <td>
                                        <span class="promo-code-name">
                                            ${when(hasUsage,
                                                () => html`<a href="#" @click=${(e: Event) => { e.preventDefault(); this.showUsageDetails(code, data); }}>${code.promoCode}</a>`,
                                                () => code.promoCode)}
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
                                        <alfio-format-date
                                            time-zone=${data.event?.timeZone ?? 'UTC'}
                                            date=${this.codeDate(code, data, 'start')}
                                        ></alfio-format-date>
                                    </td>
                                    <td>
                                        <alfio-format-date
                                            time-zone=${data.event?.timeZone ?? 'UTC'}
                                            date=${this.codeDate(code, data, 'end')}
                                        ></alfio-format-date>
                                    </td>
                                    ${when(!isAccess, () => this.renderDiscountAmount(code))}
                                    ${when(data.forEvent, () => this.renderCategories(code, data))}
                                    <td>${code.description || '—'}<small class="text-muted code-contact">${code.emailReference}</small></td>
                                    <td>
                                        <div class="actions-cell">
                                            ${when(data.event, () => html`
                                                <sl-icon-button name="link-45deg" label="Copy link for ${code.promoCode}"
                                                    @click=${() => UtilService.copyValueToClipboard(
                                                        () => `${window.BASE_URL.replace(/\/+$/, '')}/e/${encodeURIComponent(data.event!.shortName)}/c/${encodeURIComponent(code.promoCode)}`,
                                                        'Code link', this
                                                    )}></sl-icon-button>
                                            `)}
                                            <sl-icon-button name="pencil" label="Edit ${code.promoCode}" @click=${() => this.openCodeDialog(data, code.codeType, code)}></sl-icon-button>
                                            ${when(!code.expired || code.useCount === 0, () => html`
                                                <sl-dropdown hoist placement="bottom-end">
                                                    <sl-icon-button slot="trigger" name="three-dots-vertical" label="More actions for ${code.promoCode}"></sl-icon-button>
                                                    <sl-menu @sl-select=${(e: CustomEvent) => {
                                                        if (e.detail.item.value === 'disable') this.disableCode(code, data);
                                                        if (e.detail.item.value === 'delete') this.deleteCode(code, data);
                                                    }}>
                                                        ${when(!code.expired, () => html`
                                                            <sl-menu-item value="disable"><sl-icon name="eye-slash" slot="prefix"></sl-icon>Disable</sl-menu-item>
                                                        `)}
                                                        ${when(code.useCount === 0, () => html`
                                                            <sl-menu-item value="delete" class="danger"><sl-icon name="trash" slot="prefix"></sl-icon>Delete</sl-menu-item>
                                                        `)}
                                                    </sl-menu>
                                                </sl-dropdown>
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
                ${currency}
                ${when(code.discountType === 'FIXED_AMOUNT', () => html`<div class="text-muted">per ticket</div>`)}
            </td>
        `;
    }

    private renderCategories(code: PromoCodeDiscount, data: LoadData): TemplateResult {
        if (code.codeType === 'ACCESS' && code.hiddenCategoryId) {
            return html`<td><sl-badge variant="neutral" class="category-badge" pill>${this.categoryName(code.hiddenCategoryId, data)}</sl-badge></td>`;
        }

        if (code.categories && code.categories.length > 0) {
            return html`
                <td>
                    ${repeat(code.categories, (id) => id, (id) => html`
                        <sl-badge variant="neutral" class="category-badge" pill>${this.categoryName(id, data)}</sl-badge>
                    `)}
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
        this.editingState = null;
        if (code) {
            this.editingState = {code, data};
        }
        this.pendingCodeType = codeType;
        const categories = (code?.categories ?? []).map(String);
        this.allCategories = categories.length === 0;
        const localDate = (date: Date) => new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
        let maxUsage = '';
        if (code?.maxUsage != null) {
            maxUsage = String(code.maxUsage);
        }
        let discountAmount = '';
        if (code) {
            discountAmount = String(code.formattedDiscountAmount ?? code.discountAmount);
        }
        let hiddenCategoryId = '';
        if (code?.hiddenCategoryId != null) {
            hiddenCategoryId = String(code.hiddenCategoryId);
        }
        this.#codeForm.api.reset({
            promoCode: code?.promoCode ?? '',
            maxUsage,
            start: code?.formattedStart.replace(' ', 'T') ?? localDate(new Date()),
            end: code?.formattedEnd.replace(' ', 'T') ?? (data.event?.formattedBegin.replace(' ', 'T') ?? localDate(new Date(Date.now() + 86400000))),
            discountType: code?.discountType ?? 'PERCENTAGE',
            discountAmount,
            currencyCode: code?.currencyCode ?? 'USD',
            categories,
            hiddenCategoryId,
            description: code?.description ?? '',
            emailReference: code?.emailReference ?? '',
        });
        await this.updateComplete;
        await this.codeDialog.show();
    }

    private renderCodeDialog(data: LoadData): TemplateResult {
        const isAccess = this.pendingCodeType === 'ACCESS';
        const isDiscount = this.pendingCodeType === 'DISCOUNT';
        const editing = this.editingState != null;
        let kind = data.promoCodeDescription.toLowerCase();
        let icon = 'ticket-perforated';
        if (isAccess) {
            kind = 'access';
            icon = 'unlock';
        }
        let title = `New ${kind} code`;
        let subtitle = 'Fields marked * are required.';
        if (editing) {
            title = `Edit ${kind} code`;
            subtitle = 'Update validity, usage limits, and details. Code and discount cannot be changed.';
        }
        const save = () => this.submitCode();
        const submitted = this.#codeForm.api.state.submissionAttempts > 0;
        const input = (field: BoundField<string>, options: ValidatedInputOptions) => validatedInput(field, submitted, options);
        return html`
            <sl-dialog id="code-dialog" class="responsive-dialog" label=${title}
                @sl-request-close=${(e: SlRequestCloseEvent) => this.onCodeDialogRequestClose(e)}>
                <div slot="label" class="dialog-title">
                    <sl-icon name=${icon}></sl-icon>
                    <div>
                        <strong>${title}</strong>
                        <small>${subtitle}</small>
                    </div>
                </div>
                <form class="dialog-form form-stack" novalidate @submit=${(e: Event) => { e.preventDefault(); void save(); }}>
                    <div class="section-card">
                        <div class="dialog-section-header"><sl-icon name="tag"></sl-icon>Code</div>
                        <div class="section-body row" style="--alfio-row-cols: 2">
                            ${this.#codeForm.field({ name: 'promoCode', validators: validateWith(value => this.validatePromoCode(value)) }, field => input({
                                state: field.state,
                                handleChange: (value: string) => field.handleChange(value.toUpperCase()),
                                handleBlur: () => field.handleBlur(),
                            }, { name: 'promoCode', label: 'Code name', prefixIcon: 'tag', readonly: editing, helpText: 'Min 7 characters. Letters, numbers, and -:_@!$*,;' }))}
                            ${this.#codeForm.field({ name: 'maxUsage', validators: validateWith(value => validateMaxUsage(value)) },
                                field => input(field, { name: 'maxUsage', label: 'Limit usage to', type: 'number', prefixIcon: 'hash', optional: true, helpText: 'Leave empty for unlimited uses' }))}
                        </div>
                    </div>

                    <div class="section-card">
                        <div class="dialog-section-header">
                            <sl-icon name="calendar-event"></sl-icon>Validity
                            ${when(data.forEvent && data.event, () => html`<sl-badge variant="primary" pill>${data.event!.timeZone}</sl-badge>`)}
                        </div>
                        <div class="section-body row" style="--alfio-row-cols: 2">
                            ${this.#codeForm.field({ name: 'start', validators: validateWith(value => requiredValue(value)) },
                                field => input(field, { name: 'start', label: 'Valid from', type: 'datetime-local' }))}
                            ${this.#codeForm.field({
                                name: 'end',
                                // checked again when the start changes
                                validators: { ...validateWith(value => this.validateEnd(value)), onChangeListenTo: ['start'] },
                            }, field => input(field, { name: 'end', label: 'Valid until', type: 'datetime-local' }))}
                        </div>
                    </div>

                    ${when(isDiscount, () => html`
                        <div class="section-card">
                            <div class="dialog-section-header"><sl-icon name="percent"></sl-icon>Discount</div>
                            <div class="section-body row" style="--alfio-row-cols: ${this.discountColumns(data)}">
                                ${this.#codeForm.field({ name: 'discountType' }, field => html`
                                    <sl-select name="discountType" label="Discount type" ?disabled=${editing} required hoist
                                        .value=${field.state.value}
                                        @sl-change=${(e: Event) => field.handleChange((e.target as SlSelect).value as DiscountType)}>
                                        <sl-option value="PERCENTAGE">Percentage</sl-option>
                                        <sl-option value="FIXED_AMOUNT_RESERVATION">Fixed amount, per reservation</sl-option>
                                        <sl-option value="FIXED_AMOUNT">Fixed amount, per ticket</sl-option>
                                    </sl-select>
                                `)}
                                ${this.#codeForm.field({
                                    name: 'discountAmount',
                                    validators: { ...validateWith(value => this.validateDiscountAmount(value)), onChangeListenTo: ['discountType'] },
                                }, field => input(field, {
                                    name: 'discountAmount',
                                    label: 'Discount amount',
                                    type: 'number',
                                    readonly: editing,
                                    suffixIcon: this.percentageSuffix(),
                                }))}
                                ${when(!data.forEvent, () => this.#codeForm.field({
                                    name: 'currencyCode',
                                    validators: { ...validateWith(value => this.validateCurrency(value, data)), onChangeListenTo: ['discountType'] },
                                }, field => html`
                                    ${when(editing,
                                        () => html`<sl-input name="currencyCode" label="Currency" readonly .value=${field.state.value}></sl-input>`,
                                        () => validatedSelect(field, submitted, { className: 'currency-code', label: 'Currency' }, html`
                                            ${repeat(data.currencies, (c) => c.code, (c) => html`<sl-option value=${c.code}>${c.name}</sl-option>`)}
                                        `))}
                                `))}
                            </div>
                        </div>
                    `)}

                    ${when(data.forEvent && (isDiscount || isAccess), () => html`
                        <div class="section-card">
                            <div class="dialog-section-header"><sl-icon name=${this.categoriesIcon(isAccess)}></sl-icon>Ticket categories</div>
                            <div class="section-body">
                                ${when(!isAccess, () => html`
                                    <sl-switch help-text="Turn it off to select the categories the discount applies to." class="block mt-2 mb-2"
                                        .checked=${this.allCategories}
                                        @sl-change=${(e: Event) => {
                                            this.allCategories = (e.target as SlSwitch).checked;
                                            if (this.allCategories) {
                                                this.#codeForm.api.setFieldValue('categories', [], { dontUpdateMeta: true });
                                            }
                                        }}>Apply to all ticket categories</sl-switch>
                                `)}
                                ${when(isAccess, () => this.renderHiddenCategory(data, editing, submitted))}
                                ${when(isDiscount && !this.allCategories, () => this.renderDiscountCategories(data, submitted))}
                            </div>
                        </div>
                    `)}

                    <div class="section-card">
                        <div class="dialog-section-header"><sl-icon name="card-text"></sl-icon>Details</div>
                        <div class="section-body row" style="--alfio-row-cols: 2">
                            ${this.#codeForm.field({ name: 'description' }, field => html`
                                <sl-textarea name="description" label="Description" maxlength="1024" rows="2" resize="auto"
                                             .value=${field.state.value}
                                             @sl-input=${(e: Event) => field.handleChange((e.target as SlTextarea).value)}></sl-textarea>
                            `)}
                            ${this.#codeForm.field({ name: 'emailReference', validators: validateWith(value => optionalEmail(value)) },
                                field => input(field, { name: 'emailReference', label: 'Contact', type: 'email', prefixIcon: 'envelope', maxlength: 256, optional: true }))}
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
                        <sl-button variant="warning" size="large" @click=${() => save()} ?loading=${this.saving} ?disabled=${this.saving}>
                            Save
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    // a click outside the dialog would discard what the user typed: only the close button and Cancel close it
    private onCodeDialogRequestClose(event: SlRequestCloseEvent): void {
        if (this.saving || event.detail.source === 'overlay') {
            event.preventDefault();
        }
    }

    private renderHiddenCategory(data: LoadData, editing: boolean, submitted: boolean): TemplateResult {
        const hiddenCategoryId = this.editingState?.code.hiddenCategoryId;
        let categoryIds = data.restrictedCategories.map(cat => cat.id);
        if (hiddenCategoryId != null && !categoryIds.includes(hiddenCategoryId)) {
            categoryIds = [...categoryIds, hiddenCategoryId];
        }
        return html`${this.#codeForm.field({ name: 'hiddenCategoryId', validators: validateWith(value => this.validateHiddenCategory(value)) }, field => html`
            ${when(editing,
                () => html`
                    <sl-input name="hiddenCategoryId" label="Category" readonly
                              .value=${data.ticketCategoriesById[Number(field.state.value)]?.name ?? field.state.value}></sl-input>
                `,
                () => validatedSelect(field, submitted, { className: 'hidden-category', label: 'Select Category', placeholder: 'Select a category' }, html`
                    ${repeat(categoryIds, id => id, id => html`<sl-option value=${String(id)}>${data.ticketCategoriesById[id]?.name ?? id}</sl-option>`)}
                `))}
        `)}`;
    }

    private renderDiscountCategories(data: LoadData, submitted: boolean): TemplateResult {
        const categoryIds = [...new Set([...data.validCategories.map(cat => cat.id), ...(this.editingState?.code.categories ?? [])])];
        return html`${this.#codeForm.field({ name: 'categories', validators: validateWith(value => this.validateCategories(value)) }, field => {
            const error = fieldError(field.state.meta, submitted);
            return html`
                <sl-select name="categories" label="Categories" placeholder="Select categories" multiple required hoist
                           class=${classMap({ error: error != null })} help-text=${error ?? ''}
                           .value=${field.state.value}
                           @sl-change=${(e: Event) => field.handleChange([...(e.target as SlSelect).value as string[]])}
                           @sl-blur=${() => field.handleBlur()}>
                    ${repeat(categoryIds, id => id, id => html`<sl-option value=${String(id)}>${data.ticketCategoriesById[id]?.name ?? id}</sl-option>`)}
                </sl-select>
            `;
        })}`;
    }

    // the currency is chosen only for an organization's codes
    private discountColumns(data: LoadData): number {
        if (data.forEvent) {
            return 2;
        }
        return 3;
    }

    private categoriesIcon(isAccess: boolean): string {
        if (isAccess) {
            return 'unlock';
        }
        return 'collection';
    }

    private percentageSuffix(): string | undefined {
        if (this.#codeForm.api.state.values.discountType === 'PERCENTAGE') {
            return 'percent';
        }
        return undefined;
    }

    // the code, the discount and the access category can't be changed once saved
    private get creating(): boolean {
        return this.editingState == null;
    }

    private validatePromoCode(value: unknown): string | undefined {
        if (!this.creating) {
            return undefined;
        }
        const code = String(value ?? '').trim();
        if (code.length < 7 || !PROMO_CODE_PATTERN.test(code)) {
            return 'Use at least 7 characters: letters, numbers, and -:_@!$*,;';
        }
        return undefined;
    }

    private validateEnd(value: unknown): string | undefined {
        const missing = requiredValue(value);
        if (missing != null) {
            return missing;
        }
        const start = this.#codeForm.api.state.values.start;
        // datetime-local values (yyyy-MM-ddTHH:mm) sort as strings
        if (start !== '' && String(value) <= start) {
            return 'Please choose a date after "Valid from"';
        }
        return undefined;
    }

    private validateDiscountAmount(value: unknown): string | undefined {
        if (!this.creating || this.pendingCodeType !== 'DISCOUNT') {
            return undefined;
        }
        const amount = Number(value);
        if (String(value ?? '').trim() === '' || Number.isNaN(amount) || amount < 0) {
            return 'Please enter an amount, 0 or more';
        }
        if (this.#codeForm.api.state.values.discountType === 'PERCENTAGE' && amount > 100) {
            return 'A percentage cannot be more than 100';
        }
        return undefined;
    }

    private validateCurrency(value: unknown, data: LoadData): string | undefined {
        const fixedAmount = this.#codeForm.api.state.values.discountType.startsWith('FIXED_AMOUNT');
        if (!this.creating || data.forEvent || this.pendingCodeType !== 'DISCOUNT' || !fixedAmount) {
            return undefined;
        }
        if (requiredValue(value) != null) {
            return 'Please select the currency of the discount';
        }
        return undefined;
    }

    private validateCategories(value: unknown): string | undefined {
        if (this.pendingCodeType !== 'DISCOUNT' || this.allCategories) {
            return undefined;
        }
        if (!Array.isArray(value) || value.length === 0) {
            return 'Please select at least one category';
        }
        return undefined;
    }

    private validateHiddenCategory(value: unknown): string | undefined {
        if (!this.creating || this.pendingCodeType !== 'ACCESS') {
            return undefined;
        }
        if (requiredValue(value) != null) {
            return 'Please select the category that the code unlocks';
        }
        return undefined;
    }

    private async submitCode(): Promise<void> {
        if (this.saving) return;
        this.validationErrors = {};
        if (!await submitValidated(this.#codeForm.api)) {
            await this.updateComplete;
            focusFirstError(this.codeDialog);
        }
    }

    /**
     * Called by the code form once all the fields are valid
     */
    private async saveCode(values: CodeFormValues): Promise<void> {
        const code = this.editingState?.code;
        const data = this.editingState?.data ?? this.loadDataTask.value;
        if (data == null) return;
        let selectedCategories: number[] = [];
        if (!this.allCategories) {
            selectedCategories = values.categories.map(Number);
        }
        let maxUsage: number | null = null;
        if (values.maxUsage.trim() !== '') {
            maxUsage = Number.parseInt(values.maxUsage, 10);
        }
        const common = {
            start: this.parseDateTime(values.start),
            end: this.parseDateTime(values.end),
            maxUsage,
            description: values.description,
            emailReference: values.emailReference.trim(),
        };
        const currencyCode = data.event?.currency ?? values.currencyCode;
        this.saving = true;
        let failureMessage = 'Failed to create promo code';
        if (code) {
            failureMessage = 'Failed to update code';
        }
        try {
            let response: Response;
            if (code) {
                let categories = code.categories;
                if (data.forEvent && code.codeType === 'DISCOUNT') {
                    categories = selectedCategories;
                }
                response = await PromoCodeService.update(code.id, {
                    ...common,
                    categories,
                    hiddenCategoryId: code.hiddenCategoryId,
                    eventId: code.eventId,
                    organizationId: code.organizationId!,
                });
            } else {
                let discountAmount: number | null = null;
                if (values.discountAmount.trim() !== '') {
                    discountAmount = Number(values.discountAmount);
                }
                let hiddenCategoryId: number | null = null;
                if (this.pendingCodeType === 'ACCESS') {
                    hiddenCategoryId = Number(values.hiddenCategoryId);
                }
                let categories: number[] = [];
                if (data.forEvent) {
                    categories = selectedCategories;
                }
                let organizationId: number | null = data.organizationId;
                if (data.forEvent) {
                    organizationId = null;
                }
                response = await PromoCodeService.add({
                    ...common,
                    promoCode: values.promoCode.trim().toUpperCase(),
                    discountAmount,
                    discountType: values.discountType,
                    categories,
                    codeType: this.pendingCodeType,
                    hiddenCategoryId,
                    currencyCode,
                    eventId: data.event?.id ?? null,
                    organizationId,
                });
            }
            if (response.ok) {
                await this.codeDialog.hide();
                this.loadDataTask.run();
                let message = 'Promo code created successfully';
                if (code) {
                    message = 'Code updated successfully';
                }
                dispatchFeedback({type: 'success', message}, this);
            } else {
                const body = await response.json();
                if (body.validationErrors) {
                    this.showServerErrors(this.extractValidationErrors(body.validationErrors));
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

    /**
     * The errors of a field are displayed on it, the others above the buttons
     */
    private showServerErrors(errors: Record<string, string>): void {
        const fields = Object.keys(this.#codeForm.api.fieldInfo);
        const others: Record<string, string> = {};
        Object.entries(errors).forEach(([field, message]) => {
            if (fields.includes(field)) {
                setServerError(this.#codeForm.api, field, message);
            } else {
                others[field] = message;
            }
        });
        this.validationErrors = others;
    }

    private async showUsageDetails(code: PromoCodeDiscount, data: LoadData): Promise<void> {
        try {
            this.usageData = await PromoCodeService.getUsageDetails(code.id, data.event?.shortName);
            if (this.usageData.length === 0) {
                dispatchFeedback({type: 'warning', message: 'No reservations found for this code.'}, this);
                return;
            }
            this.usageCode = code.promoCode;
            this.usageTimeZone = data.event?.timeZone ?? 'UTC';
            await this.updateComplete;
            this.usageDetailsDialog.show();
        } catch {
            dispatchFeedback({type: 'danger', message: 'Failed to load usage details.'}, this);
        }
    }

    private renderUsageDetailsDialog(): TemplateResult {
        return html`
            <sl-dialog id="usage-details-dialog" label="Usage details" class="usage-details-dialog responsive-dialog">
                <div slot="label" class="dialog-title">
                    <sl-icon name="ticket-perforated"></sl-icon>
                    <div>
                        <strong>Usage details - ${this.usageCode}</strong>
                        <small>Reservations and attendees using this code. Times shown in ${this.usageTimeZone} time zone.</small>
                    </div>
                </div>
                ${repeat(this.usageData, (detail) => detail.event.shortName, (detail) => html`
                    <section class="section-card">
                        <div class="dialog-section-header">
                            <sl-icon name="calendar-event"></sl-icon>${detail.event.displayName}
                            <sl-badge variant="primary" pill>${this.reservationCount(detail.reservations.length)}</sl-badge>
                        </div>
                        <div class="section-body table-responsive">
                        <table class="table table-striped" aria-label="Reservations for ${detail.event.displayName}">
                            <thead>
                                <tr>
                                    <th scope="col">Reservation</th>
                                    <th>Customer</th>
                                    <th>Payment</th>
                                    <th>Amount</th>
                                    <th>Confirmation</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${repeat(detail.reservations, (r) => r.id, (reservation) => html`
                                    <tr>
                                        <td><a href="/admin/#/events/${encodeURIComponent(detail.event.shortName)}/reservation/${encodeURIComponent(reservation.id)}" target="_blank" rel="noopener" class="username-link" title=${reservation.id} aria-label="Open reservation ${reservation.id}">${reservation.id.substring(0, 8).toUpperCase()}</a></td>
                                        <td>${reservation.firstName} ${reservation.lastName}<small class="text-muted code-contact">${reservation.email}</small></td>
                                        <td>${reservation.paymentType}</td>
                                        <td>
                                            ${when(reservation.paymentType !== 'NONE' && reservation.finalPriceCts != null && reservation.currency,
                                                () => html`<sl-format-number type="currency" currency=${reservation.currency} value=${reservation.finalPriceCts! / 100}></sl-format-number>`,
                                                () => html`—`)}
                                        </td>
                                        <td>
                                            ${when(reservation.confirmationTimestamp, () => html`
                                                <alfio-format-date
                                                    time-zone=${this.usageTimeZone}
                                                    date=${reservation.confirmationTimestamp}
                                                ></alfio-format-date>
                                            `, () => html`<span class="text-muted">Not confirmed</span>`)}
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="5">
                                            <sl-details summary="Tickets (${reservation.tickets.length})">
                                            <table class="table" aria-label="Tickets for reservation ${reservation.id}">
                                                <thead>
                                                    <tr>
                                                        <th scope="col">Ticket ID</th>
                                                        <th scope="col">Attendee</th>
                                                        <th scope="col">Type</th>
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
                                            </sl-details>
                                        </td>
                                    </tr>
                                `)}
                            </tbody>
                        </table>
                        </div>
                    </section>
                `)}

                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
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

    private reservationCount(count: number): string {
        if (count === 1) {
            return '1 reservation';
        }
        return `${count} reservations`;
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
