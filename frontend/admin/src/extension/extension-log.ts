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
import {humanize, readRouteParams, replaceRouteParams, toPageNumber} from '../service/helpers.ts';
import {base, badges, modernLayout, modernTable, retroCompat, textColors} from '../styles.ts';
import {AlfioPageChange} from '../components/pagination-bar.ts';
import {ListSearchController} from '../components/list-search.ts';
import {taskContent} from '../components/task-content.ts';
import {emptyState} from '../components/empty-state.ts';
import {renderScope} from './extension-scope.ts';
import '../components/pagination-bar.ts';
import '../components/format-date.ts';

const ITEMS_PER_PAGE = 50;
const LOG_TYPES: ExtensionLogType[] = ['SUCCESS', 'INFO', 'WARNING', 'ERROR'];

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
        css`
            :host {
                display: block;
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
                    <h1>Extension logs</h1>
                </div>
                <hr class="page-separator"/>
                <div class="filter-toolbar">
                    <div class="filter-left">
                        ${this.searchController.render('logs')}
                    </div>
                    <div class="filter-right">
                        <sl-select class="filter-select" label="Type" placeholder="All types" clearable
                                   .value=${this.type ?? ''}
                                   @sl-change=${(e: Event) => this.onTypeChange(String((e.target as SlSelect).value))}>
                            ${repeat(LOG_TYPES, (type) => type, (type) => html`
                                <sl-option value=${type}>${humanize(type)}</sl-option>
                            `)}
                        </sl-select>
                    </div>
                </div>
                ${taskContent(this.loadTask, this.lastData, 'Failed to load the logs. Please try again.',
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
                        ${emptyState('No log entries found')}
                    </section>
                `,
                () => html`
                    ${this.renderTable(data)}
                    <alfio-pagination-bar
                        .page=${this.page}
                        .total=${logs.right}
                        page-size=${ITEMS_PER_PAGE}
                        item-label="log entries"
                        @alfio-page-change=${this.onPageChange}
                    ></alfio-pagination-bar>
                `)}
        `;
    }

    private renderTable(data: LogData): TemplateResult {
        return html`
            <div class="table-responsive">
                <table class="table table-striped">
                    <thead>
                        <tr>
                            <th>Path</th>
                            <th>Name</th>
                            <th>Type</th>
                            <th>Description</th>
                            <th>Date</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(data.logs.left, (log) => log.id, (log) => html`
                            <tr>
                                <td>${renderScope(log.effectivePath, data.organizations, data.events)}</td>
                                <td>${log.name}</td>
                                <td>${this.logBadge(log.type)}</td>
                                <td class="description">${log.description}</td>
                                <td>
                                    <alfio-format-date date=${log.timestamp}></alfio-format-date>
                                </td>
                            </tr>
                        `)}
                    </tbody>
                </table>
            </div>
        `;
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
        return html`<sl-badge variant=${variant} pill>${humanize(type)}</sl-badge>`;
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