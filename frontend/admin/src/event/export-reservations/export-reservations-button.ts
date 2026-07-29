import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {panelStyles, dialog, form, row} from '../../styles.ts';

@customElement('alfio-export-reservations-button')
export class ExportReservationsButton extends LitElement {

    @state()
    isOwner: boolean = !!window.USER_IS_OWNER;

    @state()
    eventsCount: number | null = null;

    @state()
    loading: boolean = true;

    @state()
    error: boolean = false;

    @state()
    searchFrom: string = '';

    @state()
    searchTo: string = '';

    @state()
    dialogOpen: boolean = false;

    static readonly styles = [panelStyles, dialog, form, row, css`
        .export-reservations-button {
            margin-bottom: 30px;
        }

        .export-reservations-button .btn-block {
            width: 100%;
            color: #333;
            font-size: 16px;
            font-family: "Source Sans Pro", "Helvetica Neue", Helvetica, Arial, sans-serif;
            border-radius: 4px;
        }

        .export-reservations-button .btn-block sl-icon {
            margin-right: 0;
        }

        .button-row {
            display: flex;
            width: 100%;
        }

        .button-wrapper {
            margin-left: auto;
            width: calc(50% - 30px);
        }

        @media (min-width: 992px) {
            .button-wrapper {
                width: calc(25% - 30px);
            }
        }

        .action-row {
            display: flex;
            gap: 10px;
        }

        @media (min-width: 768px) {
            .action-row {
                flex-direction: row;
            }
        }

        @media (max-width: 767px) {
            .action-row {
                flex-direction: column;
            }
        }

        .dialog {
            --header-spacing: 15px;
            --body-spacing: 0;
            --footer-spacing: 15px;
        }

        .dialog::part(header-actions) {
            display: none;
        }

        .dialog::part(title) {
            font-family: "Source Sans Pro", "Helvetica Neue", Helvetica, Arial, sans-serif;
            font-size: 34px;
            font-weight: 500;
            line-height: 1.1;
            margin: 22px 0 11px;
        }

        .dialog::part(header) {
            box-sizing: border-box;
            border-bottom: 1px solid #e5e5e5;
        }

        .dialog::part(footer) {
            box-sizing: border-box;
            border-top: 1px solid #e5e5e5;
        }

        .dialog::part(panel) {
            position: absolute;
            top: 30px;
            left: 50%;
            transform: translateX(-50%);
            width: 895px;
            box-sizing: border-box;
            border-radius: 6px;
            box-shadow: 0 5px 15px rgba(0,0,0,.5);
            border: 1px solid rgba(0,0,0,.2);
        }

        .dialog::part(overlay) {
            background-color: rgba(0,0,0,.5);
        }

        .dialog-body {
            padding: 15px;
            font-family: "Source Sans Pro", "Helvetica Neue", Helvetica, Arial, sans-serif;
            font-size: 16px;
            line-height: 1.42857143;
            color: #333;
            -webkit-font-smoothing: antialiased;
        }

        .dialog-body .row {
            margin-left: -15px;
            margin-right: -15px;
            display: block !important;
        }

        .dialog-body .col-xs-12 {
            float: left;
            width: 100%;
            padding-left: 15px;
            padding-right: 15px;
        }

        .dialog-body .col-xs-12.col-lg-6 {
            width: 100%;
        }

        @media (min-width: 992px) {
            .dialog-body .col-xs-12.col-lg-6 {
                width: 50%;
            }
        }

        .dialog-body h4 {
            font-size: 20px;
            font-weight: 500;
            line-height: 1.1;
            margin: 11px 0;
        }

        .dialog-body hr {
            height: 0;
            margin: 22px 0;
            border: 0;
            border-top: 1px solid #eee;
        }

        .dialog-body .form-control-label {
            display: block;
            font-weight: bold;
            margin-bottom: 0;
        }

        .dialog-body .form-group {
            margin-bottom: 15px;
        }

        .dialog-body .text-row {
            margin-bottom: 0;
        }

        .dialog-body sl-input {
            display: block;
            width: 100%;
            margin-top: 8px;
            --sl-input-border-color: #ccc;
            --sl-input-font-size-medium: 0.875rem;
            --sl-input-height-medium: 2.125rem;
        }

        .dialog-body sl-input::part(input) {
            width: 100%;
            height: 100%;
            padding: 6px 12px;
            font-size: 14px;
            line-height: 1.42857143;
            color: #555;
            background-color: #fff;
            border: none;
            border-radius: 4px;
            box-sizing: border-box;
            outline: none;
        }

        .footer-btn {
            display: block;
            width: 100%;
            padding: 10px 16px;
            font-size: 20px;
            line-height: 1.3333333;
            border-radius: 6px;
            font-weight: normal;
            text-align: center;
            cursor: pointer;
            border: 1px solid transparent;
        }

        .close-btn {
            color: #333;
            background-color: #fff;
            border-color: #ccc;
        }

        .close-btn:hover {
            color: #333;
            background-color: #e9e9e9;
            border-color: #adadad;
        }

        .download-btn {
            color: #fff;
            background-color: #f0ad4e;
            border-color: #eea236;
        }

        .download-btn:hover {
            color: #fff;
            background-color: #ec971f;
            border-color: #d58512;
        }

        .footer-row {
            margin-left: -15px;
            margin-right: -15px;
        }

        .footer-col-md-4 {
            float: left;
            width: 33.33333%;
            padding-left: 15px;
            padding-right: 15px;
            position: relative;
            min-height: 1px;
        }

        .footer-col-md-4.footer-push-4 {
            position: relative;
            left: 33.33333%;
        }

        @media (max-width: 767px) {
            .footer-col-md-4 {
                width: 100%;
                float: none;
            }

            .footer-col-md-4.footer-push-4 {
                left: 0;
            }
        }
    `];

    render(): TemplateResult {
        return html`
            ${when(this.loading,
                () => html`<div class="spinner"><sl-spinner></sl-spinner></div>`,
                () => nothing)}

            ${when(this.error,
                () => html`<div class="alert alert-danger">Failed to load events</div>`,
                () => nothing)}

            ${when(!this.loading && !this.error && this.isOwner && this.eventsCount !== null && this.eventsCount > 0,
                () => html`
                    <div class="export-reservations-button">
                        <div class="button-row">
                            <div class="button-wrapper">
                                <button type="button" class="btn btn-block" @click=${() => this.openDialog()}>
                                    <sl-icon name="download"></sl-icon> Export Reservations
                                </button>
                            </div>
                        </div>
                    </div>
                `,
                () => nothing)}

              <sl-dialog label="Export Reservations" class="dialog" ?open=${this.dialogOpen} @sl-request-close=${(e: CustomEvent<any>) => this.handleRequestClose(e)} @sl-after-hide=${() => this.dialogOpen = false} style="--width: 895px;">
                 <div class="dialog-body">
                    <div class="row">
                          <div class="col-xs-12">
                              <div class="text-row">
                                  Reservations can be exported for a given period of time.<br>
                                  The export will produce an "Excel" file with one sheet per Event
                              </div>
                          </div>
                      </div>
                     <hr>
                     <h4>Reservation date</h4>
                    <div class="row">
                          <div class="col-xs-12 col-lg-6">
                              <div class="form-group">
                                  <label for="reservation-date-from" class="form-control-label">From</label>
                                  <sl-input type="date"
                                            id="reservation-date-from"
                                            .value=${this.searchFrom}
                                            @sl-input=${(e: Event) => this.onDateChange('from', e)}>
                                  </sl-input>
                              </div>
                          </div>
                          <div class="col-xs-12 col-lg-6">
                              <div class="form-group">
                                  <label for="reservation-date-to" class="form-control-label">To</label>
                                  <sl-input type="date"
                                            id="reservation-date-to"
                                            .value=${this.searchTo}
                                            @sl-input=${(e: Event) => this.onDateChange('to', e)}>
                                  </sl-input>
                              </div>
                          </div>
                      </div>
                 </div>
                 <span slot="footer" style="display: block;">
                     <div class="footer-row clearfix">
                         <div class="footer-col-md-4">
                             <button type="button" class="footer-btn close-btn" style="margin-bottom: 10px" @click=${() => this.closeDialog()}>Close</button>
                         </div>
                         <div class="footer-col-md-4 footer-push-4">
                             ${when(this.isReady(),
                                 () => html`
                                     <button type="button" class="footer-btn download-btn" style="margin-bottom: 10px" @click=${() => this.download()}>Download</button>
                                 `,
                                () => nothing)}
                         </div>
                     </div>
                 </span>
             </sl-dialog>
        `;
    }

    connectedCallback(): void {
        super.connectedCallback();
        this.loadEventsCount();
    }

    private async loadEventsCount(): Promise<void> {
        this.loading = true;
        try {
            const response = await fetch('/admin/api/events-count', {credentials: 'include'});
            if (!response.ok) {
                throw new Error(`events-count request failed with status ${response.status}`);
            }
            this.eventsCount = (await response.json()) as number;
        } catch (e) {
            console.error('Failed to load events count', e);
            this.error = true;
        } finally {
            this.loading = false;
        }
    }

    private onDateChange(field: 'from' | 'to', e: Event): void {
        const target = e.target as unknown as { value: string };
        const value = target.value;
        if (field === 'from') {
            this.searchFrom = value;
        } else {
            this.searchTo = value;
        }
    }

    private isReady(): boolean {
        if (!this.searchFrom || !this.searchTo) {
            return false;
        }
        return this.searchTo > this.searchFrom;
    }

    private download(): void {
        if (!this.isReady()) {
            return;
        }
        const url = `/admin/api/export/reservations?from=${encodeURIComponent(this.searchFrom)}&to=${encodeURIComponent(this.searchTo)}`;
        window.open(url, '_blank', 'noopener,noreferrer');
    }

    openDialog(): void {
        this.searchFrom = '';
        this.searchTo = '';
        this.dialogOpen = true;
    }

     closeDialog(): void {
         this.dialogOpen = false;
     }

     private handleRequestClose(e: CustomEvent): void {
         const source = (e as CustomEvent<{ source: string }>).detail?.source;
         if (source === 'overlay') {
             e.preventDefault();
         }
     }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-export-reservations-button': ExportReservationsButton;
    }
}
