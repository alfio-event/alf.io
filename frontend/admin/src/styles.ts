import {css} from "lit";

export const pageHeader = css`
    .page-header {
        padding-bottom: 10px;
        margin: 44px 0 22px;
        border-bottom: 1px solid #eee;
    }

    .border-bottom {
        border-bottom: 1px solid #eee;
        margin-bottom: 15px;
    }
`;

export const textColors = css`
    .text-success {
        color: var(--sl-color-success-600);
    }

    .text-muted {
        color: var(--sl-color-gray-600);
    }

    .text-danger {
        color: var(--sl-color-danger-600);
    }
`;

export const cardBgColors = css`
    sl-card.bg-primary::part(header) {
        background: var(--sl-color-primary-700);
        color: var(--sl-color-neutral-0);
    }

    sl-card.bg-primary {
        --border-color: var(--sl-color-primary-700);
    }

    sl-card.bg-default::part(header) {
        color: var(--sl-color-neutral-950);
        background-color: var(--sl-color-gray-100);
        border-color: var(--sl-color-gray-200);
    }
`

export const badges = css`
    sl-badge::part(base) {
        border: unset;
    }
`;

export const textAlign = css`
    .text-center {
        text-align: center;
    }

    .text-start {
        text-align: start;
    }

    .text-end {
        text-align: end;
    }
`;

export const itemsList = css`
    .item {
        width: 100%;
        margin-bottom: 1rem;
    }
    .item [slot='header'] {
        display: flex;
        align-items: center;
        justify-content: space-between;
    }
    .item [slot='footer'] {
        display: flex;
        align-items: center;
        justify-content: end;
        gap: 1em;
    }

    .item [slot='footer'].multiple {
        justify-content: space-between;
    }

    .item [slot='footer'] > div.button-container {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 1em;
    }

    .item .body {
        display: grid;
        row-gap: 0.5rem;
    }

    .item .body .info-container {
        display: grid;
        row-gap: 0.5rem;
    }

    .item .body .info-container .info {
        display: grid;
        grid-template-columns: 0.5fr 1.3fr;
        grid-auto-rows: auto;
        column-gap: 3rem;
    }


    @media only screen and (min-width: 768px) {
        .item > .body {
            grid-template-columns: 1fr 1.3fr;
            grid-auto-rows: auto;
            column-gap: 3rem;
        }
    }
`;

export const listGroup = css`
    .list-group {
        list-style: none;
        margin: 0;
        padding: 0;
        width: 100%;
    }

    .list-group .list-group-item {
        border-top: var(--sl-panel-border-width) solid var(--sl-panel-border-color);
        padding: 0.5em 0 0.5em 0;
    }

    .list-group .list-group-item:first-child {
        border-top: 0;
    }
`;

export const row = css`

    :host {
        --alfio-row-cols: 2;
        --alfio-custom-row-cols-layout: repeat(var(--alfio-row-cols), 1fr);
        --alfio-column-gap: 3rem;
    }

    .row {
        display: grid;
        row-gap: 0.5rem;
    }

    @media only screen and (min-width: 768px) {
        .row {
            grid-template-columns: repeat(var(--alfio-row-cols), 1fr);
            grid-auto-rows: auto;
            column-gap: 3rem;
        }

        .row.custom {
            grid-template-columns: var(--alfio-custom-row-cols-layout);
            column-gap: var(--alfio-column-gap);
        }
    }
`;

export const dialog = css`
    :host {
        --sl-z-index-dialog: 1031; // bootstrap's navbar + 1
    }
`;

export const form = css`
    sl-input::part(form-control-label),
    sl-textarea::part(form-control-label),
    sl-select::part(form-control-label){
        font-weight: bold;
    }

    sl-input, sl-textarea, sl-select {
        margin-top: 15px;
    }

    sl-input.error, sl-textarea.error, sl-select.error {
        --sl-input-border-color: var(--sl-color-danger-600);
        --sl-input-border-color-hover: var(--sl-color-danger-500);
        --sl-input-border-color-focus: var(--sl-color-danger-600);
        --sl-input-focus-ring-color: var(--sl-color-danger-200);
    }

    .error-text {
        display: none;
    }

    sl-input.error .error-text, sl-textarea.error .error-text, sl-select.error .error-text {
        display: inline-block;
    }
`;


// imported minimal common css part from bootstrap
export const retroCompat = css`

    h1,h2,h3,h4,h5,h6,.h1,.h2,.h3,.h4,.h5,.h6 {
        font-family: inherit;
        font-weight: 500;
        line-height: 1.1;
        color: inherit
    }

    h1,.h1,h2,.h2,h3,.h3 {
        margin-top: 22px;
        margin-bottom: 11px
    }


    h4,.h4,h5,.h5,h6,.h6 {
        margin-top: 11px;
        margin-bottom: 11px
    }

    h1,.h1 { font-size: 41px }
    h2,.h2 { font-size: 34px }
    h3,.h3 { font-size: 28px }
    h4,.h4 { font-size: 20px }
    h5,.h5 { font-size: 16px }
    h6,.h6 { font-size: 14px }

    small,.small { font-size: 87% }

    a { color: #337ab7; text-decoration: none }
    a:hover,a:focus { color: #23527c; text-decoration: underline }
    a:focus { outline: 5px auto -webkit-focus-ring-color; outline-offset: -2px }

    .wMarginTop10px { margin-top: 10px; }

    .ms-1 {
        margin-left: var(--sl-spacing-2x-small);
    }
    .mt-1 {
        margin-top: var(--sl-spacing-x-small);
    }
    .mt-2 {
        margin-top: var(--sl-spacing-small);
    }
    .mt-3 {
        margin-top: var(--sl-spacing-medium);
    }

    .pt-1 {
        padding-top: var(--sl-spacing-2x-small);
    }
    .pt-1 {
        padding-top: var(--sl-spacing-x-small);
    }
    .pt-2 {
        padding-top: var(--sl-spacing-small);
    }
    .pt-3 {
        padding-top: var(--sl-spacing-medium);
    }

    sl-switch {
        padding-bottom: 0.5rem;
        min-height: 3.5rem;
    }

    sl-switch::part(base) {
        display: flex;
        justify-content: space-between;
        flex-direction: row-reverse;
        gap: 0.5rem;
    }

    sl-switch::part(label) {
        margin-inline-start: 0;
    }

`;
