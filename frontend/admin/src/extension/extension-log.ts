import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {SlSelect} from '@shoelace-style/shoelace';
import {ExtensionEvent, ExtensionLog, ExtensionLogType} from '../model/extension.ts';
import type {Organization} from '../model/organization.ts';
import {PageAndContent} from '../model/reservation.ts';
import {ExtensionService} from '../service/extension.ts';
import {readRouteParams, replaceRouteParams, toPageNumber} from '../service/helpers.ts';
import {base, badges, modernLayout, modernTable, reservationTable, retroCompat, textColors} from '../styles.ts';
import {AlfioPageChange} from '../components/pagination-bar.ts';
import {ListSearchController} from '../components/list-search.ts';
import {taskContent} from '../components/task-content.ts';
import {emptyState} from '../components/empty-state.ts';
import {renderScope} from './extension-scope.ts';
import '../components/pagination-bar.ts';
import '../components/format-date.ts';
import { t, i18nKey } from '../service/i18n';

const ITEMS_PER_PAGE = 50;
const LOG_TYPES: ExtensionLogType[] = ['SUCCESS', 'INFO', 'WARNING', 'ERROR'];

const LOG_TYPE_LABELS: Record<ExtensionLogType, string> = {
    SUCCESS: i18nKey('admin.extension-log.type.success'),
    INFO: i18nKey('admin.extension-log.type.info'),
    WARNING: i18nKey('admin.extension-log.type.warning'),
    ERROR: i18nKey('admin.extension-log.type.error'),
};

interface LogData {
    logs: PageAndContent<ExtensionLog[]>;
    organizations: ReadonlyArray<Organization>;
    events: ReadonlyArray<ExtensionEvent>;
}

/**
 * Log of the executions of the extension scripts, with name and type filters and pagination.
 * The page hosted by the "extension.log" route of the AngularJS admin.
 */
@customElement('alfio-extension-log')
export class ExtensionLogPage extends LitElement {

    @state() private page = 1;
    @state() private search = '';
    @state() private type: ExtensionLogType | null = null;
    @state() private lastData: LogData | null = null;

    private readonly searchController = new ListSearchController(this, (search) => {
        this.search = search;
        this.page = 1;
        this.syncLocation();
    });

    private readonly loadTask = new Task(this, {
        task: async ([page, search, type]): Promise<LogData> => {
            const [logs, options] = await Promise.all([
                ExtensionService.loadLogs(page, search, type),
                ExtensionService.loadScopeOptions(),
            ]);
            return { logs, organizations: options.organizations, events: options.events };
        },
        args: () => [this.page, this.search, this.type] as const,
        onComplete: (data) => {
            this.lastData = data;
        },
    });

    static readonly styles = [
        base,
        retroCompat,
        badges,
        textColors,
        modernLayout,
        modernTable,
        reservationTable,
        css`
            :host {
                display: block;
            }
            .page-title-row {
                flex-wrap: wrap;
            }
            .filter-toolbar,
            .filter-left {
                align-items: center;
            }
            /* the search field takes the rest of the toolbar */
            .type-filter {
                flex: 0 0 11rem;
            }
            .scope {
                font-size: var(--sl-font-size-small);
            }
            .script-name {
                font-family: var(--sl-font-mono);
                font-size: var(--sl-font-size-small);
                white-space: nowrap;
            }
            .description {
                overflow-wrap: anywhere;
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        const params = readRouteParams();
        this.search = params.get('search') ?? '';
        this.searchController.init(this.search);
        this.page = toPageNumber(params.get('page'));
        const type = params.get('type');
        if (type != null && LOG_TYPES.includes(type as ExtensionLogType)) {
            this.type = type as ExtensionLogType;
        }
    }

    render(): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>${t('admin.extension-log.title')}</h1>
                </div>
                <hr class="page-separator"/>
                <p class="page-description text-muted">${t('admin.extension-log.description')}</p>
                <div class="filter-toolbar">
                    <div class="filter-left">
                        ${this.searchController.render(t('admin.extension-log.items'))}
                        <sl-select class="filter-select type-filter label-hidden" label=${t('admin.extension-log.filter')}
                                   placeholder=${t('admin.extension-log.all-types')} clearable hoist
                                   .value=${this.type ?? ''}
                                   @sl-change=${(e: Event) => this.onTypeChange(String((e.target as SlSelect).value))}>
                            ${repeat(LOG_TYPES, (type) => type, (type) => html`
                                <sl-option value=${type}>${t(LOG_TYPE_LABELS[type])}</sl-option>
                            `)}
                        </sl-select>
                    </div>
                </div>
                ${taskContent(this.loadTask, this.lastData, t('admin.extension-log.load-failed'),
                    (data) => this.renderContent(data))}
            </div>
        `;
    }

    private renderContent(data: LogData): TemplateResult {
        const logs = data.logs;
        return html`
            ${when(logs.right === 0,
                () => html`
                    <section class="section-card">
                        ${emptyState(this.emptyMessage())}
                    </section>
                `,
                () => html`
                    ${this.renderTable(data)}
                    <alfio-pagination-bar
                        .page=${this.page}
                        .total=${logs.right}
                        page-size=${ITEMS_PER_PAGE}
                        item-label=${t('admin.extension-log.entries')}
                        @alfio-page-change=${this.onPageChange}
                    ></alfio-pagination-bar>
                `)}
        `;
    }

    private renderTable(data: LogData): TemplateResult {
        return html`
            <div class="table-responsive">
                <table class="table table-striped table-stacked">
                    <thead>
                        <tr>
                            <th class="hide-small">${t('admin.extensions.path')}</th>
                            <th>${t('admin.common.name')}</th>
                            <th>${t('admin.common.type')}</th>
                            <th>${t('admin.common.description')}</th>
                            <th>${t('admin.common.date')}</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(data.logs.left, (log) => log.id, (log) => html`
                            <tr>
                                <td class="scope hide-small" data-label=${t('admin.extensions.path')}>${renderScope(log.effectivePath, data.organizations, data.events)}</td>
                                <td class="script-name">${log.name}</td>
                                <td data-label=${t('admin.common.type')}>${this.logBadge(log.type)}</td>
                                <td class="description" data-label=${t('admin.common.description')}>${log.description}</td>
                                <td class="timestamp" data-label=${t('admin.common.date')}>
                                    <alfio-format-date date=${log.timestamp}></alfio-format-date>
                                </td>
                            </tr>
                        `)}
                    </tbody>
                </table>
            </div>
        `;
    }

    private emptyMessage(): string {
        if (this.search !== '' || this.type != null) {
            return t('admin.extension-log.none-matching');
        }
        return t('admin.extension-log.none');
    }

    private logBadge(type: ExtensionLogType): TemplateResult {
        let variant = 'danger';
        if (type === 'SUCCESS') {
            variant = 'success';
        } else if (type === 'INFO') {
            variant = 'primary';
        } else if (type === 'WARNING') {
            variant = 'warning';
        }
        return html`<sl-badge variant=${variant} pill>${t(LOG_TYPE_LABELS[type])}</sl-badge>`;
    }

    private onTypeChange(value: string): void {
        if (value === '') {
            this.type = null;
        } else {
            this.type = value as ExtensionLogType;
        }
        this.page = 1;
        this.syncLocation();
    }

    private onPageChange = (event: AlfioPageChange): void => {
        this.page = event.detail.page;
        this.syncLocation();
    };

    private syncLocation(): void {
        const params = new URLSearchParams({page: String(this.page)});
        if (this.search !== '') {
            params.set('search', this.search);
        }
        if (this.type != null) {
            params.set('type', this.type);
        }
        replaceRouteParams(params);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-extension-log': ExtensionLogPage;
    }
}