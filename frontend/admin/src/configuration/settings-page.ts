import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import type {SlSelect, SlTabShowEvent} from '@shoelace-style/shoelace';
import {EventBasicInfo, SettingsLevel} from '../model/configuration.ts';
import {Organization} from '../model/organization.ts';
import {OrganizationService} from '../service/organization.ts';
import {SettingsService} from '../service/settings.ts';
import {taskContent} from '../components/task-content.ts';
import {base, listTabs, modernLayout, purchaseContextListPage, textColors} from '../styles.ts';
import './settings-form.ts';
import './translations-editor.ts';
import '../template/template-list.ts';
import '../event/promo-code/promo-code.ts';

import { adminConfig } from '../admin-config';
import { navigate, adminUrl } from '../admin-router';
import { t, tHtml } from '../service/i18n';
interface PageData {
    level: SettingsLevel;
    // the levels the user can choose, empty on the pages of an event (the event menu is the navigation there)
    organizations: Organization[];
    events: EventBasicInfo[];
}

type TabName = 'settings' | 'translations' | 'promo-codes' | 'templates';

/**
 * The configuration of the system, of an organization or of an event: the settings, the customized
 * translations and, for the system and the organizations, the templates (and the promo codes of an organization).
 *
 * - no attribute: the system
 * - data-organization-id: an organization
 * - data-event-id: an event, chosen in the level select of this page
 * - data-event-name: an event, as one of the pages of the event (next to its menu, without the level select)
 */
@customElement('alfio-settings-page')
export class SettingsPage extends LitElement {

    @property({type: Number, attribute: 'data-organization-id'}) organizationId?: number;
    @property({type: Number, attribute: 'data-event-id'}) eventId?: number;
    @property({type: String, attribute: 'data-event-name'}) eventName = '';

    // the tabs shown at least once: the content of a tab is loaded only when it is shown
    @state() private visitedTabs = new Set<TabName>(['settings']);
    @state() private lastData: PageData | null = null;

    private readonly loadTask = new Task(this, {
        task: async ([organizationId, eventId, eventName]): Promise<PageData> => {
            if (eventName !== '') {
                // one of the pages of the event: the event menu is the navigation
                const event = (await SettingsService.loadEvents()).find((e) => e.shortName === eventName);
                if (event == null) {
                    throw new Error('event not found');
                }
                return {level: eventLevel(event), organizations: [], events: []};
            }
            const [organizations, events] = await Promise.all([
                OrganizationService.loadAll(),
                SettingsService.loadEvents(),
            ]);
            let level: SettingsLevel = {type: 'SYSTEM'};
            let selectedEvent: EventBasicInfo | undefined = undefined;
            if (eventId != null) {
                selectedEvent = events.find((e) => e.id === eventId);
                if (selectedEvent == null) {
                    throw new Error('event not found');
                }
                level = eventLevel(selectedEvent);
            } else if (organizationId != null) {
                const organization = organizations.find((o) => o.id === organizationId);
                if (organization == null) {
                    throw new Error('organization not found');
                }
                level = {type: 'ORGANIZATION', organizationId: organization.id, organizationName: organization.name};
            }
            // the expired events are left out, unless one of them is the one shown
            const selectableEvents = events.filter((e) => !e.expired || e === selectedEvent);
            return {level, organizations: [...organizations], events: selectableEvents};
        },
        args: () => [this.organizationId, this.eventId, this.eventName] as const,
        onComplete: (data) => {
            // only the administrators can change the settings of the system
            if (data.level.type === 'SYSTEM' && !adminConfig().isAdmin && data.organizations.length > 0) {
                navigate(`/configuration/organization/${data.organizations[0].id}`, { replace: true });
                return;
            }
            if (this.lastData?.level.type !== data.level.type) {
                this.visitedTabs = new Set(['settings']);
            }
            this.lastData = data;
        },
    });

    static readonly styles = [
        base,
        textColors,
        modernLayout,
        purchaseContextListPage,
        listTabs,
        css`
            :host {
                display: block;
            }
            .level-select {
                flex: 0 1 24rem;
                margin-top: 0;
            }
            .level-option-event {
                padding-inline-start: var(--sl-spacing-large);
            }
            .page-title-row h1 i {
                font-style: italic;
            }
            .list-tabs {
                margin-top: var(--sl-spacing-medium);
            }
            /* the section index of the settings sticks to the window, not to the tab body */
            .list-tabs::part(body) {
                overflow: visible;
            }
            sl-tab-panel::part(base) {
                padding-top: var(--sl-spacing-large);
            }
        `,
    ];

    render(): TemplateResult {
        return taskContent(this.loadTask, this.lastData, t('admin.settings-page.load-failed'),
            (data) => this.renderContent(data));
    }

    private renderContent(data: PageData): TemplateResult {
        const level = data.level;
        const tabs = this.tabs(level);
        return html`
            <div class="container">
                <div class="page-title-row ${this.titleClass(level)}">
                    <h1>${this.pageTitle(level)}</h1>
                </div>
                <hr class="page-separator" />
                <p class="page-description text-muted">${this.description(level)}</p>
                ${when(!this.eventPage(), () => this.renderLevelSelect(data))}
                <sl-tab-group class="list-tabs" @sl-tab-show=${this.onTabShow}>
                    ${repeat(tabs, (tab) => tab.name, (tab, index) => html`
                        <sl-tab slot="nav" panel=${tab.name} id="tab-${tab.name}" ?active=${index === 0}>${tab.label}</sl-tab>
                    `)}
                    ${repeat(tabs, (tab) => tab.name, (tab, index) => html`
                        <sl-tab-panel name=${tab.name} ?active=${index === 0}>
                            ${when(this.visitedTabs.has(tab.name), () => this.renderTab(tab.name, level))}
                        </sl-tab-panel>
                    `)}
                </sl-tab-group>
            </div>
        `;
    }

    private renderLevelSelect(data: PageData): TemplateResult {
        const level = data.level;
        let value = 'system';
        if (level.type === 'ORGANIZATION') {
            value = `organization-${level.organizationId}`;
        } else if (level.type === 'EVENT') {
            value = `event-${level.eventId}`;
        }
        return html`
            <div class="filter-toolbar">
                <sl-select id="settings-level" class="level-select" label=${t('admin.settings-page.level')} hoist .value=${value}
                           @sl-change=${(e: Event) => this.goToLevel(data, String((e.target as SlSelect).value))}>
                    ${when(adminConfig().isAdmin, () => html`
                        <sl-option value="system">
                            <sl-icon slot="prefix" name="globe"></sl-icon>
                            ${t('admin.menu.system')}
                        </sl-option>
                    `)}
                    ${repeat(data.organizations, (organization) => organization.id, (organization) => html`
                        <sl-option value="organization-${organization.id}">
                            <sl-icon slot="prefix" name="building"></sl-icon>
                            ${organization.name}
                        </sl-option>
                        ${repeat(data.events.filter((event) => event.organizationId === organization.id), (event) => event.id, (event) => html`
                            <sl-option class="level-option-event" value="event-${event.id}">
                                <sl-icon slot="prefix" name="calendar-event"></sl-icon>
                                ${event.displayName}
                            </sl-option>
                        `)}
                    `)}
                </sl-select>
            </div>
        `;
    }

    private renderTab(tab: TabName, level: SettingsLevel): TemplateResult {
        if (tab === 'translations') {
            return html`<alfio-translations-editor .level=${level}></alfio-translations-editor>`;
        }
        if (tab === 'promo-codes' && level.type === 'ORGANIZATION') {
            return html`<alfio-promo-code data-organization-id=${level.organizationId}></alfio-promo-code>`;
        }
        if (tab === 'templates') {
            if (level.type === 'ORGANIZATION') {
                return html`<alfio-template-list data-organization-id=${level.organizationId}></alfio-template-list>`;
            }
            return html`<alfio-template-list data-system></alfio-template-list>`;
        }
        let cancelUrl = '';
        if (level.type === 'EVENT' && this.eventPage()) {
            cancelUrl = adminUrl(`/events/${level.eventName}/detail`);
        }
        return html`<alfio-settings-form .level=${level} data-cancel-url=${cancelUrl}></alfio-settings-form>`;
    }

    private tabs(level: SettingsLevel): { name: TabName; label: string }[] {
        const tabs: { name: TabName; label: string }[] = [
            {name: 'settings', label: t('admin.settings-page.tab.settings')},
            {name: 'translations', label: t('admin.settings-page.tab.translations')},
        ];
        if (level.type === 'ORGANIZATION') {
            tabs.push({name: 'promo-codes', label: t('admin.page-title.promo-codes')});
        }
        if (level.type !== 'EVENT') {
            tabs.push({name: 'templates', label: t('admin.page-title.templates')});
        }
        return tabs;
    }

    private pageTitle(level: SettingsLevel): TemplateResult {
        if (level.type === 'ORGANIZATION') {
            return html`${t('admin.settings-page.title', level.organizationName)}`;
        }
        if (level.type === 'EVENT') {
            return html`${tHtml('admin.settings-page.title', html`<i>${level.eventDisplayName}</i>`)}`;
        }
        return html`${t('admin.settings-page.title.system')}`;
    }

    private description(level: SettingsLevel): string {
        if (level.type === 'ORGANIZATION') {
            return t('admin.settings-page.description.organization');
        }
        if (level.type === 'EVENT') {
            return t('admin.settings-page.description.event');
        }
        return t('admin.settings-page.description.system');
    }

    private eventPage(): boolean {
        return this.eventName !== '';
    }

    private titleClass(level: SettingsLevel): string {
        if (level.type === 'EVENT' && this.eventPage()) {
            return 'secondary';
        }
        return '';
    }

    private onTabShow = (event: SlTabShowEvent): void => {
        const name = event.detail.name as TabName;
        if (!this.visitedTabs.has(name)) {
            this.visitedTabs = new Set([...this.visitedTabs, name]);
        }
    };

    private goToLevel(data: PageData, value: string): void {
        if (value === 'system') {
            navigate('/configuration/system');
            return;
        }
        if (value.startsWith('organization-')) {
            navigate(`/configuration/organization/${value.substring('organization-'.length)}`);
            return;
        }
        const eventId = Number(value.substring('event-'.length));
        const event = data.events.find((e) => e.id === eventId);
        if (event != null) {
            navigate(`/configuration/organization/${event.organizationId}/event/${event.id}`);
        }
    }
}

function eventLevel(event: EventBasicInfo): SettingsLevel {
    return {
        type: 'EVENT',
        organizationId: event.organizationId,
        eventId: event.id,
        eventName: event.shortName,
        eventDisplayName: event.displayName,
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-settings-page': SettingsPage;
    }
}
