// see
// https://github.com/shoelace-style/rollup-example/blob/master/src/index.js
// +
// https://shoelace.style/getting-started/installation?id=bundling#bundling
import '@shoelace-style/shoelace/dist/themes/light.css';
import './main.css';
import { setBasePath } from '@shoelace-style/shoelace/dist/utilities/base-path.js';
import "@shoelace-style/shoelace/dist/components/format-date/format-date.js";
import "@shoelace-style/shoelace/dist/components/format-number/format-number.js";
import "@shoelace-style/shoelace/dist/components/divider/divider.js";
import "@shoelace-style/shoelace/dist/components/dialog/dialog.js";
import "@shoelace-style/shoelace/dist/components/icon-button/icon-button.js";
import "@shoelace-style/shoelace/dist/components/tab-group/tab-group.js";
import "@shoelace-style/shoelace/dist/components/tab/tab.js";
import "@shoelace-style/shoelace/dist/components/tab-panel/tab-panel.js";
import '@shoelace-style/shoelace/dist/components/card/card.js';
import '@shoelace-style/shoelace/dist/components/button/button.js';
import '@shoelace-style/shoelace/dist/components/icon/icon.js';
import '@shoelace-style/shoelace/dist/components/alert/alert';
import '@shoelace-style/shoelace/dist/components/input/input';
import '@shoelace-style/shoelace/dist/components/textarea/textarea';
import '@shoelace-style/shoelace/dist/components/select/select';
import '@shoelace-style/shoelace/dist/components/option/option';
import '@shoelace-style/shoelace/dist/components/qr-code/qr-code.js';
import '@shoelace-style/shoelace/dist/components/badge/badge.js';
import '@shoelace-style/shoelace/dist/components/tooltip/tooltip.js';
import '@shoelace-style/shoelace/dist/components/radio/radio.js';
import '@shoelace-style/shoelace/dist/components/radio-group/radio-group.js';
import '@shoelace-style/shoelace/dist/components/checkbox/checkbox.js';
import '@shoelace-style/shoelace/dist/components/button-group/button-group.js';
import '@shoelace-style/shoelace/dist/components/menu/menu.js';
import '@shoelace-style/shoelace/dist/components/menu-item/menu-item.js';
import '@shoelace-style/shoelace/dist/components/dropdown/dropdown.js';
import '@shoelace-style/shoelace/dist/components/spinner/spinner.js';
import '@shoelace-style/shoelace/dist/components/drawer/drawer.js';
import '@shoelace-style/shoelace/dist/components/details/details.js';
import '@shoelace-style/shoelace/dist/components/switch/switch.js';



// see https://vitejs.dev/guide/env-and-mode
if (import.meta.env.MODE === 'development') {
    const baseUrl = import.meta.env.MODE ? 'http://localhost:5173/' : '';
    setBasePath(`${baseUrl}shoelace/`);
} else if (import.meta.env.MODE === 'production') {
    setBasePath(`${document.getElementById('lit-js')?.getAttribute('src')?.replace(/assets.*/g, '')}shoelace/`);
}
export { NewElement } from './new-element';
export { ProjectBanner } from './project-banner/project-banner';
export { AdditionalItemList } from './event/additional-item-list/additional-item-list';
export { DisplayCommonMarkPreview } from './display-common-mark-preview/display-common-mark-preview';
export { AdditionalItemEdit } from './event/additional-item-edit/additional-item-edit';
export { FeedbackVisualizer } from './feedback-visualizer/feedback-visualizer';
export { AdditionalFieldList } from './purchase-context/additional-field/additional-field-list.ts';
export { AdditionalFieldStatistics } from './purchase-context/additional-field/additional-field-statistics.ts';
export { AdditionalFieldEdit } from './purchase-context/additional-field/additional-field-edit.ts';
export { OfflinePaymentConfigBlock } from './configuration/offline-payment-config-block';
export { OfflinePaymentDialog } from './configuration/offline-payment-dialog';
export { ConfirmationDialog } from './configuration/confirmation-dialog';
export { CustomOfflinePaymentEventSelector } from './event/custom-offline-payment-event-selector/custom-offline-payment-event-selector';
export { CustomOfflinePaymentSelector } from './event/custom-offline-payment-event-selector/custom-offline-payment-selector';
export { CustomOfflinePaymentDeniedMethodsSelector } from './event/custom-offline-payment-event-selector/custom-offline-payment-denied-methods-selector';
