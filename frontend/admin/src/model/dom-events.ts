import {LitElement} from "lit";

export type AlfioDialogClosed = CustomEvent<{ success: boolean }>;
export type AlfioFeedback = CustomEvent<AlfioFeedbackEvent>;

export interface AlfioFeedbackEvent {
    type: 'neutral' | 'success' | 'warning' | 'danger';
    message: string;
}

export function dispatchFeedback(payload: AlfioFeedbackEvent, src: LitElement): void {
    src.dispatchEvent(new CustomEvent<AlfioFeedbackEvent>('alfio-feedback', {
        detail: payload,
        bubbles: true,
        composed: true
    }));
}

declare global {
    interface GlobalEventHandlersEventMap {
        'alfio-dialog-closed': AlfioDialogClosed;
        'alfio-feedback': AlfioFeedback;
    }
}
