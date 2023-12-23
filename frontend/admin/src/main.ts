// see 
// https://github.com/shoelace-style/rollup-example/blob/master/src/index.js
// +
// https://shoelace.style/getting-started/installation?id=bundling#bundling
import './main.css';
import '@shoelace-style/shoelace/dist/themes/light.css';
import { setBasePath } from '@shoelace-style/shoelace/dist/utilities/base-path.js';

// imported components
import '@shoelace-style/shoelace/dist/components/card/card.js';
import '@shoelace-style/shoelace/dist/components/button/button.js';
import '@shoelace-style/shoelace/dist/components/icon/icon.js';
import '@shoelace-style/shoelace/dist/components/alert/alert';

// see https://vitejs.dev/guide/env-and-mode
const baseUrl = import.meta.env.MODE ? 'http://localhost:5173/' : '';
setBasePath(`${baseUrl}shoelace/`);

export { NewElement } from './new-element';