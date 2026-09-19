import {customElement} from "lit/decorators.js";
import {html, LitElement, nothing, TemplateResult} from "lit";
import {AlfioFeedbackEvent} from "../model/dom-events.ts";
import {escapeHtml} from "../service/helpers.ts";

@customElement('alfio-feedback-visualizer')
export class FeedbackVisualizer extends LitElement {

    private readonly listener: EventListener = (e) => {
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
        return html`${nothing}`;
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
