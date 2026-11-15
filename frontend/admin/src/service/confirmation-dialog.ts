import {escapeHtml} from "./helpers.ts";

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
                        <sl-button variant="default" id="close" size="large">Cancel</sl-button>
                        <div></div>
                        <sl-button variant="${variant}" size="large" id="confirm">Confirm</sl-button>
                    </div>
                </div>
              </sl-dialog>
            `;
            const dialog = div.querySelector('sl-dialog')!;

            document.body.appendChild(div);

            const close = (r: boolean) => {
                dialog.hide().then(() => {
                    document.body.removeChild(div);
                    resolve(r);
                });
            }
            customElements.whenDefined('sl-dialog').then(async () => {
                await dialog.show();
                div.querySelectorAll('sl-button')?.forEach(e => e.addEventListener('click', () => {
                    close(e.id === 'confirm')
                }));
                dialog.addEventListener('sl-hide', (e) => {
                    if (['close-button', 'keyboard', 'overlay'].includes(e.detail.source)) {
                        resolve(false);
                    }
                });
            });
        })
    }


}
