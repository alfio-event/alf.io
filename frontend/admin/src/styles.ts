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
