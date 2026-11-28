import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import {Task} from '@lit/task';
import type {SlCheckbox, SlInput, SlSelect} from '@shoelace-style/shoelace';
import {ConfigurationModification, ConfigurationSetting, SettingsByCategory} from '../../model/configuration.ts';
import {Group, LinkedGroup, LinkedGroupMatchType, LinkedGroupType} from '../../model/group.ts';
import {ConfigurationService} from '../../service/configuration.ts';
import {GroupService} from '../../service/group.ts';
import {PAYMENT_PROXY_DESCRIPTIONS} from '../../service/payment-proxy.ts';
import {sectionCard} from '../../components/section-card.ts';
import {taskContent} from '../../components/task-content.ts';
import {form, modernLayout, row, textColors} from '../../styles.ts';
import '../custom-offline-payment-event-selector/custom-offline-payment-denied-methods-selector.ts';
import type {CustomOfflinePaymentDeniedMethodsSelector} from '../custom-offline-payment-event-selector/custom-offline-payment-denied-methods-selector.ts';
import { adminUrl } from '../../admin-router';
import { t, tHtml, i18nKey } from '../../service/i18n';

/**
 * The category whose options are edited
 */
export interface CategoryOptionsTarget {
    eventId: number;
    eventName: string;
    organizationId: number;
    categoryId: number;
    // the payment methods of the event: the category can exclude some of them
    allowedPaymentProxies: string[];
}

interface LoadedOptions {
    settings: SettingWithCategory[];
    groups: readonly Group[];
    link: LinkedGroup | null;
}

interface SettingWithCategory {
    // the setting category (e.g. GENERAL), needed to save it
    category: string;
    setting: ConfigurationSetting;
}

const PAYMENT_METHODS_DENY_LIST = 'PAYMENT_METHODS_BLACKLIST';
const MAX_TICKETS_PER_RESERVATION = 'MAX_AMOUNT_OF_TICKETS_BY_RESERVATION';

const GROUP_TYPES: { value: LinkedGroupType; label: string }[] = [
    {value: 'ONCE_PER_VALUE', label: t('admin.category-options.group-type.once')},
    {value: 'LIMITED_QUANTITY', label: t('admin.category-options.group-type.limited')},
    {value: 'UNLIMITED', label: t('admin.category-options.group-type.unlimited')},
];

const GROUP_MATCH_TYPES: { value: LinkedGroupMatchType; label: string }[] = [
    {value: 'FULL', label: t('admin.category-options.match-type.full')},
    {value: 'EMAIL_DOMAIN', label: t('admin.category-options.match-type.domain')},
];

/** "Same as the event", with the value set at a level above the category */
const INHERITED_LABELS: Record<string, string> = {
    SYSTEM: i18nKey('admin.category-options.inherited.system'),
    ORGANIZATION: i18nKey('admin.category-options.inherited.organization'),
    PURCHASE_CONTEXT: i18nKey('admin.category-options.inherited.event'),
};

/**
 * The options of a ticket category, edited in the "Options" tab of the category dialog: the settings that can
 * be changed for a single category (an empty value means "same as the event"), the payment methods it doesn't
 * accept and the group whose members only can buy it.
 *
 * Nothing is saved until the dialog calls validate() and save().
 */
@customElement('alfio-category-options')
export class CategoryOptions extends LitElement {

    @property({attribute: false}) target!: CategoryOptionsTarget;

    // the values set for the category, by key: '' = same as the event
    @state() private values: Record<string, string> = {};
    @state() private link: LinkedGroup | null = null;
    @state() private errors: Record<string, string> = {};

    private readonly loadTask = new Task(this, {
        task: async ([target]): Promise<LoadedOptions> => {
            const [settingsByCategory, groups, link] = await Promise.all([
                ConfigurationService.loadCategorySettings(target.eventId, target.categoryId),
                GroupService.loadGroups(target.organizationId),
                GroupService.loadCategoryLink(target.eventName, target.categoryId),
            ]);
            const settings = flattenSettings(settingsByCategory);
            const values: Record<string, string> = {};
            for (const {setting} of settings) {
                values[setting.key] = categoryValue(setting);
            }
            this.values = values;
            this.link = copyLink(link);
            this.errors = {};
            return {settings, groups: groups.filter((group) => group.active), link};
        },
        args: () => [this.target] as const,
    });

    static readonly styles = [
        form,
        modernLayout,
        row,
        textColors,
        css`
            :host {
                display: grid;
                gap: var(--sl-spacing-medium);
            }
            .section-card {
                margin-bottom: 0;
            }
            .payment-methods {
                display: grid;
                gap: var(--sl-spacing-x-small);
            }
            .payment-methods sl-checkbox::part(label) {
                color: var(--sl-color-neutral-700);
            }
        `,
    ];

    render(): TemplateResult {
        return taskContent(this.loadTask, this.loadTask.value ?? null,
            t('admin.category-options.load-failed'),
            (data) => this.renderContent(data));
    }

    private renderContent(data: LoadedOptions): TemplateResult {
        const settings = data.settings.filter(({setting}) => setting.key !== PAYMENT_METHODS_DENY_LIST);
        const denyList = data.settings.find(({setting}) => setting.key === PAYMENT_METHODS_DENY_LIST);
        return html`
            ${when(settings.length > 0, () => sectionCard({icon: 'sliders', title: t('admin.event-menu.group.sales')}, html`
                <p class="text-muted">${t('admin.category-options.sales.help')}</p>
                <div class="form-stack">
                    ${repeat(settings, ({setting}) => setting.key, ({setting}) => this.renderSetting(setting))}
                </div>
            `), () => nothing)}
            ${when(denyList != null, () => this.renderPaymentMethods())}
            ${this.renderGroup(data)}
        `;
    }

    private renderSetting(setting: ConfigurationSetting): TemplateResult {
        const value = this.values[setting.key] ?? '';
        const error = this.errors[setting.key];
        if (setting.componentType === 'BOOLEAN') {
            return html`
                <sl-select id=${setting.key} label=${setting.description} hoist .value=${value}
                           @sl-change=${(e: Event) => this.setValue(setting.key, String((e.target as SlSelect).value))}>
                    <sl-option value="">${inheritedLabel(setting)}</sl-option>
                    <sl-option value="true">${t('admin.common.yes')}</sl-option>
                    <sl-option value="false">${t('admin.common.no')}</sl-option>
                </sl-select>
            `;
        }
        let helpText = inheritedLabel(setting);
        if (error != null) {
            helpText = error;
        }
        return html`
            <sl-input id=${setting.key} label=${setting.description} placeholder=${t('admin.category-options.same-as-event')}
                      class=${classMap({error: error != null})} help-text=${helpText}
                      .value=${value}
                      @sl-input=${(e: Event) => this.setValue(setting.key, (e.target as SlInput).value)}></sl-input>
        `;
    }

    private renderPaymentMethods(): TemplateResult | typeof nothing {
        const target = this.target;
        const denied = this.deniedMethods();
        // the methods of the event, and the ones excluded before they were removed from the event
        const methods = Object.keys(PAYMENT_PROXY_DESCRIPTIONS)
            .filter((method) => method !== 'CUSTOM_OFFLINE')
            .filter((method) => target.allowedPaymentProxies.includes(method) || denied.includes(method));
        const customOffline = target.allowedPaymentProxies.includes('CUSTOM_OFFLINE');
        if (methods.length === 0 && !customOffline) {
            return nothing;
        }
        return sectionCard({icon: 'credit-card', title: t('admin.event-detail.payment-methods')}, html`
            <p class="text-muted">${t('admin.category-options.payment-methods.help')}</p>
            <div class="payment-methods">
                ${repeat(methods, (method) => method, (method) => html`
                    <sl-checkbox class="denied-payment-method" value=${method} ?checked=${denied.includes(method)}
                                 @sl-change=${(e: Event) => this.toggleDenied(method, (e.target as SlCheckbox).checked)}>
                        ${when(denied.includes(method),
                            () => html`<del>${PAYMENT_PROXY_DESCRIPTIONS[method]}</del>`,
                            () => html`${PAYMENT_PROXY_DESCRIPTIONS[method]}`)}
                    </sl-checkbox>
                `)}
                ${when(customOffline, () => html`
                    <span class="text-muted">${PAYMENT_PROXY_DESCRIPTIONS['CUSTOM_OFFLINE']}</span>
                    <custom-offline-payment-denied-methods-selector organization=${target.organizationId}
                                                                    event=${target.eventId}
                                                                    category=${target.categoryId}></custom-offline-payment-denied-methods-selector>
                `, () => nothing)}
            </div>
        `);
    }

    private renderGroup(data: LoadedOptions): TemplateResult {
        const link = this.link;
        let groupValue = '';
        if (link != null) {
            groupValue = String(link.groupId);
        }
        return sectionCard({icon: 'people', title: t('admin.category-options.group')}, html`
            ${when(data.groups.length === 0, () => html`
                <p class="text-muted">
                    ${tHtml('admin.category-options.group.none', html`<a href=${adminUrl(`/group/${this.target.organizationId}/all`)}>${t('admin.category-options.group.create-one')}</a>`)}
                </p>
            `, () => html`
                <div class="form-stack">
                    <sl-select id="group" label=${t('admin.category-options.group.who')} hoist .value=${groupValue}
                               @sl-change=${(e: Event) => this.selectGroup(String((e.target as SlSelect).value))}>
                        <sl-option value="">${t('admin.category-options.group.anyone')}</sl-option>
                        ${repeat(data.groups, (group) => group.id, (group) => html`
                            <sl-option value=${String(group.id)}>${t('admin.category-options.group.members-of', group.name)}</sl-option>
                        `)}
                    </sl-select>
                    ${when(link != null, () => this.renderLinkOptions(link!), () => nothing)}
                </div>
            `)}
        `);
    }

    private renderLinkOptions(link: LinkedGroup): TemplateResult {
        const error = this.errors.maxAllocation;
        let maxAllocation = '';
        if (link.maxAllocation != null) {
            maxAllocation = String(link.maxAllocation);
        }
        return html`
            <div class="row" style="--alfio-row-cols: 2">
                <sl-select id="matchType" label=${t('admin.category-options.group.match-by')} hoist .value=${link.matchType}
                           @sl-change=${(e: Event) => this.updateLink({matchType: (e.target as SlSelect).value as LinkedGroupMatchType})}>
                    ${repeat(GROUP_MATCH_TYPES, (type) => type.value, (type) => html`<sl-option value=${type.value}>${type.label}</sl-option>`)}
                </sl-select>
                <sl-select id="allocationType" label=${t('admin.category-options.group.tickets-per-member')} hoist .value=${link.type}
                           @sl-change=${(e: Event) => this.updateLink({type: (e.target as SlSelect).value as LinkedGroupType})}>
                    ${repeat(GROUP_TYPES, (type) => type.value, (type) => html`<sl-option value=${type.value}>${type.label}</sl-option>`)}
                </sl-select>
            </div>
            ${when(link.type === 'LIMITED_QUANTITY', () => html`
                <sl-input id="maxAllocation" label=${t('admin.category-options.group.max-tickets-per-member')} type="number" min="1" step="1" required
                          class=${classMap({error: error != null})} help-text=${error ?? ''}
                          .value=${maxAllocation}
                          @sl-input=${(e: Event) => this.setMaxAllocation((e.target as SlInput).value)}></sl-input>
            `, () => nothing)}
        `;
    }

    // ---------- editing ----------

    private setValue(key: string, value: string): void {
        this.values = {...this.values, [key]: value};
        this.clearError(key);
    }

    private clearError(key: string): void {
        if (this.errors[key] == null) {
            return;
        }
        const errors = {...this.errors};
        delete errors[key];
        this.errors = errors;
    }

    private deniedMethods(): string[] {
        return splitList(this.values[PAYMENT_METHODS_DENY_LIST] ?? '');
    }

    private toggleDenied(method: string, denied: boolean): void {
        const methods = this.deniedMethods().filter((m) => m !== method);
        if (denied) {
            methods.push(method);
        }
        this.setValue(PAYMENT_METHODS_DENY_LIST, methods.join(','));
    }

    private selectGroup(groupId: string): void {
        this.clearError('maxAllocation');
        if (groupId === '') {
            this.link = null;
            return;
        }
        const saved = this.loadTask.value?.link ?? null;
        // the same group as the saved link keeps its settings
        if (saved != null && String(saved.groupId) === groupId) {
            this.link = copyLink(saved);
            return;
        }
        this.link = {
            groupId: Number(groupId),
            eventId: this.target.eventId,
            ticketCategoryId: this.target.categoryId,
            matchType: 'FULL',
            type: 'ONCE_PER_VALUE',
            maxAllocation: null,
        };
    }

    private updateLink(changes: Partial<LinkedGroup>): void {
        if (this.link == null) {
            return;
        }
        this.link = {...this.link, ...changes};
        this.clearError('maxAllocation');
    }

    private setMaxAllocation(value: string): void {
        let maxAllocation: number | null = null;
        if (value.trim() !== '') {
            maxAllocation = Number(value);
        }
        this.updateLink({maxAllocation});
    }

    // ---------- validation and save ----------

    /**
     * Checks the values: the errors are displayed on their fields
     *
     * @return true if the options can be saved (also when they haven't been loaded)
     */
    public validate(): boolean {
        const errors: Record<string, string> = {};
        const maxTickets = (this.values[MAX_TICKETS_PER_RESERVATION] ?? '').trim();
        if (maxTickets !== '' && !isPositiveInteger(maxTickets)) {
            errors[MAX_TICKETS_PER_RESERVATION] = t('admin.validation.positive-integer-or-empty');
        }
        const link = this.link;
        if (link != null && link.type === 'LIMITED_QUANTITY' && !isPositiveInteger(String(link.maxAllocation ?? ''))) {
            errors.maxAllocation = t('admin.validation.positive-integer');
        }
        this.errors = errors;
        return Object.keys(errors).length === 0;
    }

    /**
     * Saves what has been changed. Call validate() first
     */
    public async save(): Promise<void> {
        const data = this.loadTask.value;
        if (data == null) {
            return;
        }
        const target = this.target;
        const changed: Record<string, ConfigurationModification[]> = {};
        for (const {category, setting} of data.settings) {
            const original = categoryValue(setting);
            const value = (this.values[setting.key] ?? '').trim();
            if (value === original) {
                continue;
            }
            if (value === '') {
                // back to the value of the event
                await ConfigurationService.deleteCategorySetting(target.eventId, target.categoryId, setting.key);
                continue;
            }
            let id = -1;
            if (setting.configurationPathLevel === 'TICKET_CATEGORY') {
                id = setting.id;
            }
            changed[category] = [...(changed[category] ?? []), {id, key: setting.key, value}];
        }
        if (Object.keys(changed).length > 0) {
            await ConfigurationService.updateCategorySettings(target.eventId, target.categoryId, changed);
        }
        await this.renderRoot.querySelector<CustomOfflinePaymentDeniedMethodsSelector>('custom-offline-payment-denied-methods-selector')?.save();
        await this.saveLink(data.link);
    }

    private async saveLink(saved: LinkedGroup | null): Promise<void> {
        const link = this.link;
        if (sameLink(saved, link)) {
            return;
        }
        if (link == null) {
            if (saved?.id != null) {
                await GroupService.unlink(this.target.organizationId, this.target.eventId, saved.id, this.target.categoryId);
            }
            return;
        }
        let maxAllocation: number | null = null;
        if (link.type === 'LIMITED_QUANTITY') {
            maxAllocation = link.maxAllocation;
        }
        await GroupService.link({...link, maxAllocation});
    }
}

function flattenSettings(settingsByCategory: SettingsByCategory): SettingWithCategory[] {
    const result: SettingWithCategory[] = [];
    for (const [category, settings] of Object.entries(settingsByCategory)) {
        for (const setting of settings) {
            if (setting.internal !== true) {
                result.push({category, setting});
            }
        }
    }
    // switches first, then the values to type, as the other configuration pages
    return result.sort((a, b) => sortWeight(a.setting) - sortWeight(b.setting));
}

function sortWeight(setting: ConfigurationSetting): number {
    if (setting.componentType === 'BOOLEAN') {
        return 0;
    }
    return 10;
}

/**
 * The value set for the category, '' if the category uses the value of the event (or of a level above)
 */
function categoryValue(setting: ConfigurationSetting): string {
    if (setting.configurationPathLevel === 'TICKET_CATEGORY' && setting.value != null) {
        return setting.value;
    }
    return '';
}

/**
 * "Same as the event", with the value that applies when it is set at a level above the category
 */
function inheritedLabel(setting: ConfigurationSetting): string {
    if (setting.configurationPathLevel === 'TICKET_CATEGORY' || setting.value == null || setting.value === '') {
        return t('admin.category-options.same-as-event');
    }
    let value = setting.value;
    if (setting.componentType === 'BOOLEAN') {
        value = t('admin.common.no');
        if (setting.value === 'true') {
            value = t('admin.common.yes');
        }
    }
    return t(INHERITED_LABELS[setting.configurationPathLevel] ?? INHERITED_LABELS.PURCHASE_CONTEXT, value);
}

function splitList(value: string): string[] {
    return value.split(',').map((item) => item.trim()).filter((item) => item !== '');
}

function isPositiveInteger(value: string): boolean {
    const number = Number(value.trim());
    return value.trim() !== '' && Number.isInteger(number) && number >= 1;
}

function copyLink(link: LinkedGroup | null): LinkedGroup | null {
    if (link == null) {
        return null;
    }
    return {...link};
}

function sameLink(a: LinkedGroup | null, b: LinkedGroup | null): boolean {
    if (a == null || b == null) {
        return a == null && b == null;
    }
    return a.groupId === b.groupId && a.type === b.type && a.matchType === b.matchType
        && (a.maxAllocation ?? null) === (b.maxAllocation ?? null);
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-category-options': CategoryOptions;
    }
}
