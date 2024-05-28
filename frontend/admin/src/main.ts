// see
// https://github.com/shoelace-style/rollup-example/blob/master/src/index.js
// +
// https://shoelace.style/getting-started/installation?id=bundling#bundling
import './main.css';
import '@shoelace-style/shoelace/dist/themes/light.css';
import { setBasePath } from '@shoelace-style/shoelace/dist/utilities/base-path.js';
import "@shoelace-style/shoelace/dist/components/format-date/format-date.js";
import "@shoelace-style/shoelace/dist/components/format-number/format-number.js";
import "@shoelace-style/shoelace/dist/components/divider/divider.js";
import "@shoelace-style/shoelace/dist/components/dialog/dialog.js";
import "@shoelace-style/shoelace/dist/components/button/button.js";

// imported components
import '@shoelace-style/shoelace/dist/components/card/card.js';
import '@shoelace-style/shoelace/dist/components/button/button.js';
import '@shoelace-style/shoelace/dist/components/icon/icon.js';
import '@shoelace-style/shoelace/dist/components/alert/alert';



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
