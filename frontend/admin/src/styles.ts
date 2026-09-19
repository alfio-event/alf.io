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

export const spacing = css`
    .wMarginTop10px { margin-top: 10px; }

    .ms-1 {
        margin-left: var(--sl-spacing-2x-small);
    }
    .ms-2 {
        margin-left: var(--sl-spacing-x-small);
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

    .p-1 {
        padding: var(--sl-spacing-2x-small);
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

export const panelStyles = css`
    :host {
        display: block;
        font-family: "Helvetica Neue", Helvetica, Arial, sans-serif;
        font-size: 14px;
        line-height: 1.42857143;
        -webkit-font-smoothing: antialiased;
    }

    *, *::before, *::after {
        box-sizing: border-box;
    }

    .panel {
        position: relative;
        display: block;
        margin-bottom: 20px;
        background-color: #fff;
        border: 1px solid #ddd;
        border-radius: 4px;
        box-shadow: 0 1px 1px rgba(0, 0, 0, 0.05);
    }

    .panel-heading {
        padding: 10px 15px;
        border-bottom: 1px solid transparent;
        border-top-left-radius: 3px;
        border-top-right-radius: 3px;
        background-color: #f5f5f5;
        overflow: hidden;
    }

    h4, .h4 {
        font-size: 20px;
        font-weight: 500;
        line-height: 1.1;
    }

    .panel-title {
        margin-top: 0;
        margin-bottom: 0;
        color: inherit;
    }

    .panel-body {
        padding: 15px;
    }

    .list-group {
        list-style: none;
        margin: 0;
        padding: 0;
        margin-bottom: 0;
        width: 100%;
    }

    .list-group-item {
        position: relative;
        display: block;
        padding: 10px 15px;
        margin-bottom: -1px;
        background-color: #fff;
        border: 1px solid #ddd;
        overflow: hidden;
    }

    .list-group-item:first-child {
        border-top-left-radius: 0;
        border-top-right-radius: 0;
    }

    .list-group-item:last-child {
        margin-bottom: 0;
        border-bottom-right-radius: 3px;
        border-bottom-left-radius: 3px;
    }

    .list-group-item-heading {
        margin-top: 0;
        margin-bottom: 5px;
    }

    .list-group-item-text {
        margin-bottom: 0;
        font-size: 14px;
        line-height: 1.42857143;
        color: #555;
    }

    .row {
        margin-left: -15px;
        margin-right: -15px;
    }

    .row::after {
        content: "";
        display: table;
        clear: both;
    }

    .clearfix::after {
        content: "";
        display: table;
        clear: both;
    }

    .col {
        float: left;
        min-height: 1px;
        padding-left: 15px;
        padding-right: 15px;
        position: relative;
        box-sizing: border-box;
    }

    .hidden-xs { display: none !important; }
    .hidden-sm { display: none !important; }

    @media only screen and (min-width: 768px) {
        .hidden-xs { display: block !important; }
        .hidden-sm { display: none !important; }
        .col-actions .btn {
            display: inline-block !important;
        }
        .text-right .btn {
            display: inline-block !important;
        }
    }

    @media only screen and (min-width: 992px) {
        .hidden-sm { display: block !important; }
        .text-right .btn {
            display: inline-block !important;
        }
    }

    @media only screen and (max-width: 767px) {
        .col {
            width: 100%;
        }
        .text-right .btn.hidden-xs {
            display: none !important;
        }
    }

    @media only screen and (min-width: 768px) and (max-width: 991px) {
        .text-right .btn.hidden-sm {
            display: none !important;
        }
        .col-actions .btn.hidden-sm {
            display: none !important;
        }
    }

    .text-right {
        text-align: right;
    }

    .text-center {
        text-align: center;
    }

    .text-muted {
        color: #777;
    }

    .pull-right {
        float: right !important;
    }

    .pull-left {
        float: left !important;
    }

    .btn {
        display: inline-block;
        margin-bottom: 0;
        font-weight: normal;
        text-align: center;
        white-space: nowrap;
        vertical-align: middle;
        cursor: pointer;
        background-image: none;
        border: 1px solid transparent;
        padding: 6px 12px;
        font-size: 14px;
        line-height: 1.42857143;
        border-radius: 3px;
        user-select: none;
        text-decoration: none;
    }

    .btn:focus, .btn:hover {
        text-decoration: none;
    }

    .btn-xs {
        padding: 1px 5px;
        font-size: 14px;
        line-height: 1.5;
        border-radius: 3px;
    }

    .btn-xs sl-icon, .btn sl-icon {
        font-size: 14px;
        margin-right: 4px;
    }

    .btn-primary {
        color: #fff;
        background-color: #337ab7;
        border-color: #2e6da4;
    }

    .btn-primary:hover {
        background-color: #286090;
        border-color: #204d74;
        color: #fff;
    }

    .btn-warning {
        color: #fff;
        background-color: #f0ad4e;
        border-color: #eea236;
    }

    .btn-warning:hover {
        background-color: #ec971f;
        border-color: #d58512;
        color: #fff;
    }

    .btn-success {
        color: #fff;
        background-color: #5cb85c;
        border-color: #4cae4c;
    }

    .btn-success:hover {
        background-color: #449d44;
        border-color: #398439;
        color: #fff;
    }

    .label {
        display: inline;
        padding: 0.2em 0.6em 0.3em;
        font-size: 75%;
        font-weight: 700;
        line-height: 1;
        color: #fff;
        text-align: center;
        white-space: nowrap;
        vertical-align: baseline;
        border-radius: 0.25em;
        margin-right: 5px;
    }

    .label-danger {
        background-color: #d9534f;
    }

    .label-warning {
        background-color: #f0ad4e;
    }

    .badge {
        display: inline-block;
        min-width: 10px;
        padding: 3px 7px;
        font-size: 12px;
        font-weight: 700;
        line-height: 1;
        color: #fff;
        text-align: center;
        white-space: nowrap;
        vertical-align: middle;
        background-color: #777;
        border-radius: 10px;
    }

    .img-responsive {
        display: block;
        max-width: 64px;
        height: auto;
        width: auto;
    }

    .alert {
        padding: 15px;
        margin-bottom: 20px;
        border: 1px solid transparent;
        border-radius: 4px;
    }

    .alert-info {
        color: #31708f;
        background-color: #d9edf7;
        border-color: #bce8f1;
    }

    .alert-danger {
        color: #a94442;
        background-color: #f2dede;
        border-color: #ebccd1;
    }

    .loading-spinner {
        display: flex;
        justify-content: center;
        padding: 2rem;
    }

    .col-info {
        width: var(--event-list-info-width, 66.66666667%);
    }

    .col-actions {
        width: var(--event-list-actions-width, 33.33333333%);
    }

    .col-image {
        width: var(--event-list-image-width, 8.33333333%);
        display: none;
    }

    @media only screen and (min-width: 768px) {
        .col-image { display: block; }
        .col-info { display: block; }
        .col-actions { display: block; }
    }

    @media only screen and (max-width: 767px) {
        .col-info, .col-actions, .col-image {
            width: 100%;
        }
    }

    .event-title a {
        color: #337ab7;
        text-decoration: none;
    }

    .event-title a:hover {
        color: #23527c;
        text-decoration: underline;
    }
`;
