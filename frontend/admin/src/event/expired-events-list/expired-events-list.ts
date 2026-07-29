import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {fetchJson, formatDate, injectFontAwesome, renderEventActions} from '../../service/helpers.ts';
import {EventStatistic} from '../../model/event.ts';
import {panelStyles, spacing} from '../../styles.ts';

@customElement('alfio-expired-events-list')
export class ExpiredEventsList extends LitElement {

    @state()
    loaded: boolean = false;

    @state()
    events: EventStatistic[] = [];

    @state()
    loading: boolean = false;

    @state()
    error: boolean = false;

    static readonly styles = [panelStyles, spacing, css`
        :host {
            --event-list-info-width: 66.66666667%;
            --event-list-actions-width: 33.33333333%;
        }
`];

    render() {
        return html`
            <div class="panel panel-default">
                <div class="panel-heading">
                    <h4 class="panel-title">Past events</h4>
                </div>

                ${when(!this.loaded,
                    () => html`
                        <div class="panel-body">
                            <button class="btn btn-warning" @click=${() => this.loadEvents()}>Load expired events</button>
                        </div>
                    `,
                    () => nothing)}

                ${when(this.loaded && !this.loading && this.error,
                    () => html`
                        <div class="panel-body">
                            <div class="alert alert-danger">
                                <span><i class="fa fa-exclamation-triangle"></i> failed to load expired events.</span>
                            </div>
                        </div>
                    `,
                    () => nothing)}

                ${when(this.loaded && !this.loading && !this.error && this.events.length === 0,
                    () => html`
                        <div class="panel-body">
                            <div class="alert alert-info">
                                <span><i class="fa fa-info-circle"></i> no past events have been found.</span>
                            </div>
                        </div>
                    `,
                    () => nothing)}

                ${when(this.loaded && !this.loading && !this.error && this.events.length > 0,
                    () => html`
                        <ul class="list-group">
                            ${repeat(this.events, (ev) => ev.id, (ev) => {
                                return html`
                                    <li class="list-group-item">
                                        <div class="row">
                                            <div class="col col-info">
                                                <div class="list-group-item-heading event-title">
                                                    <h4>
                                                        <a href="#/events/${ev.shortName}/detail">${ev.displayName}</a>
                                                    </h4>
                                                </div>
                                                <div class="list-group-item-text">
                                                    ${formatDate(ev.formattedBegin)} / ${formatDate(ev.formattedEnd)}
                                                </div>
                                            </div>
                                            <div class="col col-actions text-right wMarginTop10px">
                                                ${this.renderActions(ev)}
                                            </div>
                                        </div>
                                    </li>
                                `;
                            })}
                        </ul>
                    `,
                    () => nothing)}

                ${when(this.loading,
                    () => html`
                        <div class="panel-body">
                            <div class="loading-spinner">
                                <sl-spinner></sl-spinner>
                            </div>
                        </div>
                    `,
                    () => nothing)}
            </div>
        `;
    }

    firstUpdated(): void {
        injectFontAwesome(this.renderRoot as ShadowRoot);
    }

    private async loadEvents(): Promise<void> {
        this.loaded = true;
        this.loading = true;
        try {
            this.events = await fetchJson<EventStatistic[]>('/admin/api/expired-events');
        } catch (e) {
            console.error('Failed to load expired events', e);
            this.error = true;
        } finally {
            this.loading = false;
        }
    }

    private renderActions(ev: EventStatistic): TemplateResult {
        return renderEventActions(ev);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-expired-events-list': ExpiredEventsList;
    }
}