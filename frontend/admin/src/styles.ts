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
`



export const row = css`

    :host {
        --alfio-row-cols: 2
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

`;