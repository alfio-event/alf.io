import { LitElement, html } from 'lit';
import { customElement } from 'lit/decorators.js'

@customElement('new-element')
export class NewElement extends LitElement {

    render() {
        return html`<h1>Hello world</h1>
        <sl-card>
          <sl-rating></sl-rating>
          <sl-button>plop</sl-button>
          <sl-alert open>
            <sl-icon slot="icon" name="info-circle"></sl-icon>
            This is a standard alert. You can customize its content and even the icon.
          </sl-alert>
        </sl-card>`;
    }
}

declare global {
    interface HTMLElementTagNameMap {
      'new-element': NewElement
    }
  }
  