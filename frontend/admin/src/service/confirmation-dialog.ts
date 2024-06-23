import {escapeHtml} from "./helpers.ts";

export class ConfirmationDialogService {
    public static requestConfirm(title: string,
                                 message: string,
                                 variant: 'success' | 'danger' | 'warning' | 'primary'  = 'success'): Promise<boolean> {
        return new Promise((resolve) => {
            const div = document.createElement('div');
            div.innerHTML = `
              <sl-dialog label="${title}">
                <div class="text-center">${escapeHtml(message)}</div>
                <div slot="footer" style="display: flex; justify-content: space-between;">
                    <sl-button variant="default" id="close" style="width: 30%;" size="large">Close</sl-button>
                    <sl-button variant=${variant} size="large"  style="width: 30%;" id="confirm">Confirm</sl-button>
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
