import {escapeHtml} from "./helpers.ts";
import { t } from './i18n';

export class ConfirmationDialogService {
    public static requestConfirm(title: string,
                                 message: string,
                                 variant: 'success' | 'danger' | 'warning' | 'primary'  = 'success'): Promise<boolean> {
        return new Promise((resolve) => {
            const div = document.createElement('div');
            div.innerHTML = `
              <sl-dialog label="${escapeHtml(title).replaceAll('"', '&quot;')}" style="--width: min(32rem, calc(100vw - 2 * var(--sl-spacing-large)));">
                <div>${escapeHtml(message)}</div>
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--sl-spacing-small);">
                        <sl-button variant="default" id="close" size="large">${escapeHtml(t('admin.common.cancel'))}</sl-button>
                        <div></div>
                        <sl-button variant="${variant}" size="large" id="confirm">${escapeHtml(t('admin.common.confirm'))}</sl-button>
                    </div>
                </div>
              </sl-dialog>
            `;
            const dialog = div.querySelector('sl-dialog')!;

            document.body.appendChild(div);

            // the answer given by the buttons; closing the dialog in any other way (X, Escape, overlay) means "no"
            let answer = false;
            // the listeners are attached before the dialog opens, so that a click during the opening animation counts
            div.querySelectorAll('sl-button').forEach(e => e.addEventListener('click', () => {
                answer = e.id === 'confirm';
                void dialog.hide();
            }));
            dialog.addEventListener('sl-after-hide', (e) => {
                // nested Shoelace components fire their own sl-after-hide
                if (e.target !== dialog) {
                    return;
                }
                div.remove();
                resolve(answer);
            });
            void customElements.whenDefined('sl-dialog').then(() => dialog.show());
        })
    }


}
