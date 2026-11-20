import {html, TemplateResult} from 'lit';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {checkJsonSetting, JsonSchema} from './json-setting-check.ts';

/**
 * The layout of the badges printed by Alf.io-PI (LABEL_LAYOUT), as read by the server
 * (CheckInApiController.LabelLayout)
 */
interface LabelLayout {
    qrCode?: {
        additionalInfo?: string[];
        infoSeparator?: string;
    };
    content?: {
        firstRow?: string;
        secondRow?: string;
        thirdRow?: string[];
        additionalRows?: string[];
        checkbox?: boolean;
    };
    general?: {
        printPartialID?: boolean;
    };
    mediaName?: string;
}

// what the server reads: keep in sync with CheckInApiController.LabelLayout
const LABEL_LAYOUT_SCHEMA: Record<string, JsonSchema> = {
    qrCode: {object: {additionalInfo: {list: 'text'}, infoSeparator: 'text'}},
    content: {
        object: {
            firstRow: 'text', secondRow: 'text', thirdRow: {list: 'text'}, additionalRows: {list: 'text'}, checkbox: 'yes/no',
        },
    },
    general: {object: {printPartialID: 'yes/no'}},
    mediaName: 'text',
};

/**
 * Checks a badge layout against what the server reads, see checkJsonSetting()
 *
 * @return the message to display, or undefined if the layout is valid
 */
export function validLabelLayout(value: unknown): string | undefined {
    return checkJsonSetting(value, LABEL_LAYOUT_SCHEMA);
}

export const DEFAULT_LABEL_LAYOUT = JSON.stringify({
    qrCode: {
        // the ticket UUID is always included
        additionalInfo: [],
        infoSeparator: '::',
    },
    content: {
        firstRow: 'firstName',
        secondRow: 'lastName',
        additionalRows: [],
        checkbox: false,
    },
    general: {
        printPartialID: true,
    },
}, null, 2);

/**
 * A preview of a badge printed with the given layout, filled in with sample data. An invalid layout shows the
 * default one: the field itself reports the error.
 */
export function labelLayoutPreview(value: string): TemplateResult {
    const layout = parseLayout(value);
    const additionalRows = layout.content?.additionalRows ?? [];
    let otherRows = additionalRows;
    if (otherRows.length === 0) {
        otherRows = layout.content?.thirdRow ?? [];
    }
    const qrCode = (layout.qrCode?.additionalInfo ?? []).join(layout.qrCode?.infoSeparator ?? '::');
    return html`
        <figure class="label-preview" aria-label="Badge preview">
            <figcaption class="example-label">Preview</figcaption>
            <div class="label-sample">
                <div class="label-text">
                    <strong class="label-first-row">${sampleValue(layout.content?.firstRow, 'firstName')}</strong>
                    <span class="label-second-row">${sampleValue(layout.content?.secondRow, 'lastName')}</span>
                    ${repeat(otherRows, (row, index) => `${index}-${row}`, (row) => html`<span class="label-other-row">Sample ${row}</span>`)}
                </div>
                <div class="label-code">
                    <sl-qr-code value=${qrCode || 'ticket'} size="72" error-correction="Q"></sl-qr-code>
                    ${when(layout.general?.printPartialID === true, () => html`<span class="label-partial-id">12345</span>`)}
                </div>
            </div>
        </figure>
    `;
}

function parseLayout(value: string): LabelLayout {
    try {
        const parsed = JSON.parse(value);
        if (parsed != null && typeof parsed === 'object') {
            return parsed as LabelLayout;
        }
    } catch {
        // invalid: the default layout is shown
    }
    return JSON.parse(DEFAULT_LABEL_LAYOUT) as LabelLayout;
}

function sampleValue(key: string | undefined, defaultKey: string): string {
    const field = key || defaultKey;
    if (field === 'firstName') {
        return 'Ada';
    }
    if (field === 'lastName') {
        return 'Lovelace';
    }
    return `Sample ${field}`;
}
