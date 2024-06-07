export type AlfioDialogClosed = CustomEvent<{ success: boolean }>;
declare global {
    interface GlobalEventHandlersEventMap {
        'alfio-dialog-closed': AlfioDialogClosed;
    }
}
