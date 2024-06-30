import {customElement} from "lit/decorators.js";
import {html, LitElement, TemplateResult} from "lit";
import {msg, localized} from '@lit/localize';
import {AlfioFeedbackEvent} from "../model/dom-events.ts";
import {escapeHtml} from "../service/helpers.ts";

@customElement('alfio-feedback-visualizer')
@localized()
export class FeedbackVisualizer extends LitElement {

    private listener: EventListener = (e) => {
        const detail = (e as CustomEvent<AlfioFeedbackEvent>).detail;
        const alert = Object.assign(document.createElement('sl-alert'), {
            variant: detail.type,
            closable: true,
            duration: 3000,
            innerHTML: `
                <sl-icon name="${this.getIcon(detail)}" slot="icon"></sl-icon>
                ${escapeHtml(detail.message)}
            `
        });

        document.body.append(alert);
        return alert.toast();
    };

    private getIcon(detail: AlfioFeedbackEvent): string {
        switch(detail.type) {
            case "success":
                return 'check2-circle';
            case "warning":
                return 'exclamation-triangle';
            case 'danger':
                return 'exclamation-octagon';
            default:
                return 'info-circle';
        }
    }

    protected render(): TemplateResult {
        return html`
            <sl-alert variant="danger" duration="3000" closable>
                <sl-icon slot="icon" name="exclamation-octagon"></sl-icon>
                <strong>${msg(`Your account has been deleted`)}</strong><br />
                ${msg(`We're very sorry to see you go!`)}
            </sl-alert>
        `;
    }

    connectedCallback() {
        super.connectedCallback();
        window.addEventListener("alfio-feedback", this.listener);
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        window.removeEventListener("alfio-feedback", this.listener);
    }
}
