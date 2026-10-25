import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import type {SlSelect} from '@shoelace-style/shoelace';
import {AlfioEvent} from '../../model/event.ts';
import {CheckInStatus, TicketAndCheckInResult} from '../../model/check-in.ts';
import {EventService} from '../../service/event.ts';
import {CheckInService} from '../../service/check-in.ts';
import {humanize} from '../../service/helpers.ts';
import {formatAmount, localizedTitle} from '../../service/reservation-format.ts';
import {badges, base, detailList, modernLayout, purchaseContextListPage, retroCompat, row} from '../../styles.ts';
import {sectionCard} from '../../components/section-card.ts';
import {taskContent} from '../../components/task-content.ts';

const CAPTURE_INTERVAL_MS = 250;
// sent by the worker when the frame doesn't contain a valid QR code
const DECODE_ERROR = 'error decoding QR Code';
// after "next scan", the code just processed is ignored until it has been out of sight for this long
const SAME_CODE_GRACE_MS = 2000;

type BadgeVariant = 'success' | 'warning' | 'danger';

// badge displayed next to the attendee name. Any other status is an error
const STATUS_BADGES: Partial<Record<CheckInStatus, { variant: BadgeVariant, label: string }>> = {
    SUCCESS: { variant: 'success', label: 'Checked in' },
    OK_READY_TO_BE_CHECKED_IN: { variant: 'success', label: 'Ready to check in' },
    MUST_PAY: { variant: 'warning', label: 'Payment due' },
    ALREADY_CHECK_IN: { variant: 'warning', label: 'Already checked in' },
};

interface ScanResult {
    code: string;
    loading: boolean;
    data?: TicketAndCheckInResult;
}

/**
 * Checks in attendees by scanning their QR code with the device camera.
 * Frames are decoded by the jsqrcode web worker served with the legacy admin.
 */
@customElement('alfio-check-in-scan')
export class CheckInScan extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @state() private scanning = false;
    @state() private cameras: MediaDeviceInfo[] = [];
    @state() private selectedCamera = '';
    @state() private scanned: ScanResult | null = null;
    @state() private actionInProgress = false;
    @state() private cameraError: string | null = null;
    @query('video') private video?: HTMLVideoElement;
    @query('canvas') private canvas?: HTMLCanvasElement;

    private readonly canReadCamera = navigator.mediaDevices?.getUserMedia != null;
    private stream: MediaStream | null = null;
    private worker: Worker | null = null;
    private captureTimer: ReturnType<typeof setTimeout> | undefined;
    private decoding = false;
    private ignoredCode: { code: string, seenAt: number } | null = null;

    private readonly loadEventTask = new Task(this, {
        task: async ([eventName]): Promise<AlfioEvent> => (await EventService.load(eventName)).event,
        args: () => [this.eventName] as const,
    });

    static readonly styles = [
        base,
        retroCompat,
        modernLayout,
        purchaseContextListPage,
        detailList,
        badges,
        row,
        css`
            :host {
                --alfio-row-cols: 3;
            }
            .scan-container {
                display: grid;
                gap: var(--sl-spacing-medium);
            }
            .scan-container .section-card,
            .scan-container .featured-card {
                margin-bottom: 0;
            }
            .overheating-warning {
                display: none;
            }
            video {
                display: block;
                width: 100%;
                max-height: 70vh;
                background: var(--sl-color-neutral-900);
                border-radius: var(--sl-input-border-radius-medium);
            }
            canvas {
                display: none;
            }
            .camera-select {
                min-width: 12rem;
            }
            .scanned-ticket {
                display: grid;
                gap: var(--sl-spacing-medium);
                --alfio-section-body-padding: var(--sl-spacing-medium);
            }
            .scan-actions sl-button {
                width: 100%;
            }
            @media (max-width: 767px) {
                .overheating-warning {
                    display: block;
                }
            }
        `,
    ];

    disconnectedCallback(): void {
        super.disconnectedCallback();
        this.stopScanning();
        this.worker?.terminate();
        this.worker = null;
    }

    render(): TemplateResult {
        return taskContent(this.loadEventTask, this.loadEventTask.value ?? null, 'Failed to load the event. Please try again.',
            (event) => this.renderContent(event));
    }

    private renderContent(event: AlfioEvent): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>Scan tickets for <i>${localizedTitle(event)}</i></h1>
                </div>
                <hr class="page-separator" />
                <p class="page-description text-muted">Check in attendees by scanning the QR code on their ticket with the camera of this device.</p>
                <div class="scan-container">
                    <sl-alert class="overheating-warning" variant="warning" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        Please note that HTML5 check-in functionality could cause overheating on some mobile devices. Use it at your own risk.<br>
                        If you have an Android smartphone/tablet, please use the
                        <a href="https://play.google.com/store/apps/details?id=alfio.scan" target="_blank" rel="noopener">official application</a> instead.
                    </sl-alert>
                    ${when(this.canReadCamera,
                        () => this.renderScanner(event),
                        () => this.renderError('Your browser does not support the camera API (navigator.mediaDevices).'))}
                </div>
            </div>
        `;
    }

    private renderScanner(event: AlfioEvent): TemplateResult {
        return html`
            ${when(this.cameraError != null, () => this.renderError(this.cameraError!))}
            ${when(!this.scanning && this.scanned == null, () => html`
                <div class="featured-card">
                    <h1><sl-icon name="qr-code-scan"></sl-icon> Ready to scan</h1>
                    <small class="text-muted">The browser will ask for permission to use the camera.</small>
                    <sl-button id="scan-button" variant="primary" size="large" @click=${this.startScanning}>
                        <sl-icon slot="prefix" name="camera-video"></sl-icon>
                        Start scanning
                    </sl-button>
                </div>
            `)}
            <div ?hidden=${!this.scanning}>
                ${sectionCard({ icon: 'camera-video', title: 'Point the camera at the QR code', actions: this.renderCameraActions() }, html`
                    <video muted autoplay playsinline></video>
                `)}
            </div>
            <canvas></canvas>
            ${when(this.scanned != null, () => this.renderScanned(event, this.scanned!))}
        `;
    }

    private renderCameraActions(): TemplateResult {
        return html`
            ${when(this.cameras.length > 1, () => html`
                <sl-select class="camera-select" size="small" aria-label="Camera" .value=${this.selectedCamera} @sl-change=${this.onCameraChange}>
                    ${this.cameras.map((camera, index) => html`
                        <sl-option value=${camera.deviceId}>${camera.label || `Camera ${index + 1}`}</sl-option>
                    `)}
                </sl-select>
            `)}
            <sl-button id="stop-button" variant="default" size="small" @click=${this.stopScanning}>
                <sl-icon slot="prefix" name="stop-circle"></sl-icon>
                Stop
            </sl-button>
        `;
    }

    private renderScanned(event: AlfioEvent, scanned: ScanResult): TemplateResult {
        if (scanned.loading || scanned.data == null) {
            return sectionCard({ icon: 'hourglass-split', title: 'Checking ticket' }, html`
                <div class="loading"><sl-spinner></sl-spinner></div>
            `);
        }
        const { ticket, result } = scanned.data;
        const badge = STATUS_BADGES[result.status] ?? { variant: 'danger', label: humanize(result.status) };
        return sectionCard({
            icon: 'person-badge',
            title: ticket?.fullName || 'Ticket',
            badge: html`<sl-badge class="scan-status" variant=${badge.variant} pill>${badge.label}</sl-badge>`,
        }, html`
            <div class="scanned-ticket">
                ${this.renderResult(scanned.data)}
                ${when(ticket != null, () => html`
                    <dl class="detail-list">
                        <dt>Email</dt><dd>${ticket!.email ?? ''}</dd>
                        ${when(ticket!.categoryName, () => html`<dt>Category</dt><dd>${ticket!.categoryName}</dd>`)}
                        ${when(ticket!.userLanguage, () => html`<dt>Language</dt><dd>${ticket!.userLanguage}</dd>`)}
                    </dl>
                `)}
                ${this.renderScannedActions(event, scanned)}
            </div>
        `);
    }

    /**
     * Same layout as the dialog footers: secondary action on the left, main action on the right
     */
    private renderScannedActions(event: AlfioEvent, scanned: ScanResult): TemplateResult {
        const status = scanned.data!.result.status;
        const confirm = status === 'OK_READY_TO_BE_CHECKED_IN'
            ? html`
                <sl-button id="confirm-check-in-button" variant="success" size="large" ?loading=${this.actionInProgress}
                           @click=${() => this.checkIn(event, scanned.code)}>
                    <sl-icon slot="prefix" name="check2"></sl-icon>
                    Confirm check-in
                </sl-button>`
            : status === 'MUST_PAY'
                ? html`
                    <sl-button id="confirm-payment-button" variant="success" size="large" ?loading=${this.actionInProgress}
                               @click=${() => this.confirmPayment(event, scanned.code)}>
                        <sl-icon slot="prefix" name="cash-coin"></sl-icon>
                        Confirm payment
                    </sl-button>`
                : null;
        const nextScan = html`
            <sl-button id="next-scan-button" variant="primary" size="large" ?disabled=${this.actionInProgress} @click=${this.nextScan}>
                <sl-icon slot="prefix" name="qr-code-scan"></sl-icon>
                Next scan
            </sl-button>`;
        return html`
            <div class="row scan-actions">
                <sl-button variant="default" size="large" ?disabled=${this.actionInProgress} @click=${this.stopScanning}>
                    <sl-icon slot="prefix" name="stop-circle"></sl-icon>
                    Stop scanning
                </sl-button>
                ${confirm != null ? nextScan : html`<div></div>`}
                ${confirm ?? nextScan}
            </div>
        `;
    }

    private renderResult({ result }: TicketAndCheckInResult): TemplateResult {
        switch (result.status) {
            case 'SUCCESS':
                return this.renderAlert('success', 'check2-circle', 'Check in has been completed!');
            case 'OK_READY_TO_BE_CHECKED_IN':
                return this.renderAlert('success', 'check2-circle', 'Ticket is ok, ready to be checked in.');
            case 'MUST_PAY': {
                const amount = formatAmount(result.dueAmount, result.currency);
                return this.renderAlert('warning', 'cash-coin', `Ticket is ok, but customer must still pay the ticket${amount ? ` (${amount})` : ''}.`);
            }
            case 'ALREADY_CHECK_IN':
                return this.renderAlert('warning', 'exclamation-triangle', result.message || 'Ticket already checked in.');
            default:
                return this.renderError(result.message || result.status);
        }
    }

    private renderError(message: string): TemplateResult {
        return this.renderAlert('danger', 'exclamation-octagon', message);
    }

    private renderAlert(variant: 'success' | 'warning' | 'danger', icon: string, message: string): TemplateResult {
        return html`
            <sl-alert class="scan-result" variant=${variant} open>
                <sl-icon slot="icon" name=${icon}></sl-icon>
                ${message}
            </sl-alert>
        `;
    }

    private startScanning = async (): Promise<void> => {
        this.cameraError = null;
        this.scanned = null;
        this.scanning = true;
        await this.updateComplete;
        if (this.stream?.active && this.video != null) {
            // the camera is still open: resume decoding
            await this.video.play();
            this.scheduleCapture();
        } else {
            await this.openCamera(this.selectedCamera);
        }
    };

    private stopScanning = (): void => {
        clearTimeout(this.captureTimer);
        this.stream?.getTracks().forEach(track => track.stop());
        this.stream = null;
        this.decoding = false;
        this.scanning = false;
        this.scanned = null;
        this.ignoredCode = null;
    };

    private nextScan = (): Promise<void> => {
        // the ticket just processed is probably still in front of the camera
        this.ignoredCode = this.scanned != null ? { code: this.scanned.code, seenAt: Date.now() } : null;
        return this.startScanning();
    };

    private onCameraChange = (event: Event): void => {
        this.selectedCamera = String((event.target as SlSelect).value);
        void this.openCamera(this.selectedCamera);
    };

    private async openCamera(deviceId: string): Promise<void> {
        clearTimeout(this.captureTimer);
        this.stream?.getTracks().forEach(track => track.stop());
        try {
            this.stream = await navigator.mediaDevices.getUserMedia({
                video: deviceId ? { deviceId: { exact: deviceId } } : { facingMode: 'environment' },
            });
            // labels are available only after the user has granted access to the camera
            const devices = await navigator.mediaDevices.enumerateDevices();
            this.cameras = devices.filter(device => device.kind === 'videoinput');
            this.selectedCamera = this.stream.getVideoTracks()[0]?.getSettings().deviceId ?? deviceId;
            if (!this.scanning || this.video == null) {
                // scanning has been stopped in the meantime
                this.stream.getTracks().forEach(track => track.stop());
                return;
            }
            this.video.srcObject = this.stream;
            await this.video.play();
            this.scheduleCapture();
        } catch (e) {
            console.error('error while loading camera', e);
            this.cameraError = 'Error while loading the camera. Please make sure that the page has access to it.';
            this.stopScanning();
        }
    }

    private scheduleCapture(): void {
        clearTimeout(this.captureTimer);
        this.captureTimer = setTimeout(() => this.captureFrame(), CAPTURE_INTERVAL_MS);
    }

    private captureFrame(): void {
        if (!this.scanning || this.scanned != null) {
            return;
        }
        const video = this.video;
        const canvas = this.canvas;
        if (!this.decoding && video != null && canvas != null && video.videoWidth > 0) {
            canvas.width = video.videoWidth;
            canvas.height = video.videoHeight;
            const context = canvas.getContext('2d', { willReadFrequently: true })!;
            context.drawImage(video, 0, 0);
            this.decoding = true;
            this.getWorker().postMessage(context.getImageData(0, 0, canvas.width, canvas.height));
        }
        this.scheduleCapture();
    }

    private getWorker(): Worker {
        if (this.worker == null) {
            this.worker = new Worker(decodeWorkerUrl());
            this.worker.addEventListener('message', (message: MessageEvent<string>) => {
                this.decoding = false;
                if (message.data !== DECODE_ERROR && this.scanning && this.scanned == null && !this.isIgnored(message.data)) {
                    void this.onCodeScanned(message.data);
                }
            });
        }
        return this.worker;
    }

    private isIgnored(code: string): boolean {
        const now = Date.now();
        if (this.ignoredCode?.code === code && now - this.ignoredCode.seenAt < SAME_CODE_GRACE_MS) {
            this.ignoredCode.seenAt = now;
            return true;
        }
        return false;
    }

    private async onCodeScanned(code: string): Promise<void> {
        const event = this.loadEventTask.value!;
        // the camera stays open, so that the next scan starts immediately
        clearTimeout(this.captureTimer);
        this.ignoredCode = null;
        this.scanning = false;
        this.scanned = { code, loading: true };
        await this.runAction(code, () => CheckInService.getTicket(event.id, code));
    }

    private checkIn(event: AlfioEvent, code: string): Promise<void> {
        return this.runAction(code, () => CheckInService.checkIn(event.id, code));
    }

    private confirmPayment(event: AlfioEvent, code: string): Promise<void> {
        return this.runAction(code, () => CheckInService.confirmPayment(event.id, code));
    }

    private async runAction(code: string, action: () => Promise<TicketAndCheckInResult>): Promise<void> {
        this.actionInProgress = true;
        try {
            const data = await action();
            this.scanned = { code, loading: false, data };
        } catch (e) {
            console.error(e);
            this.scanned = { code, loading: false, data: { ticket: null, result: { status: 'ERROR', message: 'Unexpected error. Please try again.' } } };
        } finally {
            this.actionInProgress = false;
        }
    }
}

/**
 * The worker is served next to the jsqrcode library, already loaded by the admin page
 */
function decodeWorkerUrl(): string {
    const library = document.querySelector<HTMLScriptElement>('script[src*="/jsqrcode/jsqrcode.min.js"]');
    if (library != null) {
        return library.src.replace(/jsqrcode\.min\.js.*$/, 'decode-worker.js');
    }
    return `${window.ALFIO_CONTEXT_PATH ?? ''}/resources/js/jsqrcode/decode-worker.js`;
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-check-in-scan': CheckInScan;
    }
}
