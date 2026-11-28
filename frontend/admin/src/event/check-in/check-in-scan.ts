import {css, html, LitElement, nothing, TemplateResult} from 'lit';
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
import {canScanQrCodes, createQrDetector, QrDetector} from '../../service/qr-scanning.ts';
import { t, tHtml, i18nKey } from '../../service/i18n';

const CAPTURE_INTERVAL_MS = 250;
// after "next scan", the code just processed is ignored until it has been out of sight for this long
const SAME_CODE_GRACE_MS = 2000;

type BadgeVariant = 'success' | 'warning' | 'danger';

// badge displayed next to the attendee name. Any other status is an error
const STATUS_BADGES: Partial<Record<CheckInStatus, { variant: BadgeVariant, label: string }>> = {
    SUCCESS: { variant: 'success', label: t('admin.allocation.checked-in') },
    OK_READY_TO_BE_CHECKED_IN: { variant: 'success', label: t('admin.check-in-scan.status.ready') },
    MUST_PAY: { variant: 'warning', label: t('admin.check-in-scan.status.must-pay') },
    ALREADY_CHECK_IN: { variant: 'warning', label: t('admin.check-in-scan.status.already-checked-in') },
};

// the label of the other statuses, all errors
const ERROR_LABELS: Partial<Record<CheckInStatus, string>> = {
    EVENT_NOT_FOUND: i18nKey('admin.check-in-scan.status.event-not-found'),
    TICKET_NOT_FOUND: i18nKey('admin.check-in-scan.status.ticket-not-found'),
    EMPTY_TICKET_CODE: i18nKey('admin.check-in-scan.status.empty-code'),
    INVALID_TICKET_CODE: i18nKey('admin.check-in-scan.status.invalid-code'),
    INVALID_TICKET_STATE: i18nKey('admin.check-in-scan.status.invalid-state'),
    INVALID_TICKET_CATEGORY_CHECK_IN_DATE: i18nKey('admin.check-in-scan.status.invalid-date'),
    BADGE_SCAN_ALREADY_DONE: i18nKey('admin.check-in-scan.status.badge-already-scanned'),
    OK_READY_FOR_BADGE_SCAN: i18nKey('admin.check-in-scan.status.ready-for-badge'),
    BADGE_SCAN_SUCCESS: i18nKey('admin.check-in-scan.status.badge-scanned'),
    ERROR: i18nKey('admin.check-in-scan.status.error'),
};

function statusLabel(status: CheckInStatus): string {
    const key = ERROR_LABELS[status];
    if (key == null) {
        return humanize(status);
    }
    return t(key);
}

interface ScanResult {
    code: string;
    loading: boolean;
    data?: TicketAndCheckInResult;
}

/**
 * Checks in attendees by scanning their QR code with the device camera.
 * Frames are decoded by the browser (BarcodeDetector): where it's missing, the page points to the scan apps.
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

    private stream: MediaStream | null = null;
    private detector: QrDetector | null = null;
    private captureTimer: ReturnType<typeof setTimeout> | undefined;
    private decoding = false;
    private ignoredCode: { code: string, seenAt: number } | null = null;

    private readonly loadEventTask = new Task(this, {
        task: async ([eventName]): Promise<AlfioEvent> => (await EventService.load(eventName)).event,
        args: () => [this.eventName] as const,
    });

    // asked each time the page opens
    private readonly scanSupportTask = new Task(this, {
        task: () => canScanQrCodes(),
        args: () => [] as const,
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
    }

    render(): TemplateResult {
        return taskContent(this.loadEventTask, this.loadEventTask.value ?? null, t('admin.load-error.event-retry'),
            (event) => this.renderContent(event));
    }

    private renderContent(event: AlfioEvent): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>${tHtml('admin.check-in-scan.title', html`<i>${localizedTitle(event)}</i>`)}</h1>
                </div>
                <hr class="page-separator" />
                <p class="page-description text-muted">${t('admin.check-in-scan.description')}</p>
                <div class="scan-container">
                    <sl-alert class="overheating-warning" variant="warning" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${t('admin.check-in-scan.overheating')}<br>
                        ${tHtml('admin.check-in-scan.android', html`<a href="https://play.google.com/store/apps/details?id=alfio.scan" target="_blank" rel="noopener">${t('admin.check-in-scan.official-application')}</a>`)}
                    </sl-alert>
                    ${this.renderScannerIfSupported(event)}
                </div>
            </div>
        `;
    }

    private renderScannerIfSupported(event: AlfioEvent): TemplateResult | typeof nothing {
        const supported = this.scanSupportTask.value;
        if (supported == null) {
            return nothing;
        }
        if (!supported) {
            return html`
                <sl-alert class="scan-not-supported" variant="primary" open>
                    <sl-icon slot="icon" name="info-circle"></sl-icon>
                    ${tHtml('admin.check-in-scan.not-supported',
                        html`<a href="https://play.google.com/store/apps/details?id=alfio.scan" target="_blank" rel="noopener">${t('admin.check-in.android-app')}</a>`,
                        html`<a href="https://itunes.apple.com/us/app/alf-io-scan/id1425492093" target="_blank" rel="noopener">${t('admin.check-in.ios-app')}</a>`)}
                </sl-alert>
            `;
        }
        return this.renderScanner(event);
    }

    private renderScanner(event: AlfioEvent): TemplateResult {
        return html`
            ${when(this.cameraError != null, () => this.renderError(this.cameraError!))}
            ${when(!this.scanning && this.scanned == null, () => html`
                <div class="featured-card">
                    <h2><sl-icon name="qr-code-scan"></sl-icon> ${t('admin.check-in-scan.ready')}</h2>
                    <small class="text-muted">${t('admin.check-in-scan.permission')}</small>
                    <sl-button id="scan-button" variant="primary" size="large" @click=${this.startScanning}>
                        <sl-icon slot="prefix" name="camera-video"></sl-icon>
                        ${t('admin.check-in-scan.start')}
                    </sl-button>
                </div>
            `)}
            <div ?hidden=${!this.scanning}>
                ${sectionCard({ icon: 'camera-video', title: t('admin.check-in-scan.point-camera'), actions: this.renderCameraActions() }, html`
                    <video muted autoplay playsinline></video>
                `)}
            </div>
            ${when(this.scanned != null, () => this.renderScanned(event, this.scanned!))}
        `;
    }

    private renderCameraActions(): TemplateResult {
        return html`
            ${when(this.cameras.length > 1, () => html`
                <sl-select class="camera-select label-hidden" size="small" label=${t('admin.check-in-scan.camera')} hoist .value=${this.selectedCamera} @sl-change=${this.onCameraChange}>
                    ${this.cameras.map((camera, index) => html`
                        <sl-option value=${camera.deviceId}>${camera.label || t('admin.check-in-scan.camera-number', index + 1)}</sl-option>
                    `)}
                </sl-select>
            `)}
            <sl-button id="stop-button" variant="default" size="small" @click=${this.stopScanning}>
                <sl-icon slot="prefix" name="stop-circle"></sl-icon>
                ${t('admin.check-in-scan.stop')}
            </sl-button>
        `;
    }

    private renderScanned(event: AlfioEvent, scanned: ScanResult): TemplateResult {
        if (scanned.loading || scanned.data == null) {
            return sectionCard({ icon: 'hourglass-split', title: t('admin.check-in-scan.checking') }, html`
                <div class="loading"><sl-spinner></sl-spinner></div>
            `);
        }
        const { ticket, result } = scanned.data;
        const badge = STATUS_BADGES[result.status] ?? { variant: 'danger', label: statusLabel(result.status) };
        return sectionCard({
            icon: 'person-badge',
            title: ticket?.fullName || t('admin.common.ticket'),
            badge: html`<sl-badge class="scan-status" variant=${badge.variant} pill>${badge.label}</sl-badge>`,
        }, html`
            <div class="scanned-ticket">
                ${this.renderResult(scanned.data)}
                ${when(ticket != null, () => html`
                    <dl class="detail-list">
                        <dt>${t('admin.common.email-short')}</dt><dd>${ticket!.email ?? ''}</dd>
                        ${when(ticket!.categoryName, () => html`<dt>${t('admin.category-select.label')}</dt><dd>${ticket!.categoryName}</dd>`)}
                        ${when(ticket!.userLanguage, () => html`<dt>${t('admin.reservation-create.language')}</dt><dd>${ticket!.userLanguage}</dd>`)}
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
        const confirm = this.renderConfirmAction(event, scanned);
        const nextScan = html`
            <sl-button id="next-scan-button" variant="primary" size="large" ?disabled=${this.actionInProgress} @click=${this.nextScan}>
                <sl-icon slot="prefix" name="qr-code-scan"></sl-icon>
                ${t('admin.check-in-scan.next')}
            </sl-button>`;
        return html`
            <div class="row scan-actions">
                <sl-button variant="default" size="large" ?disabled=${this.actionInProgress} @click=${this.stopScanning}>
                    <sl-icon slot="prefix" name="stop-circle"></sl-icon>
                    ${t('admin.check-in-scan.stop-scanning')}
                </sl-button>
                ${when(confirm != null, () => html`${nextScan}${confirm}`, () => html`<div></div>${nextScan}`)}
            </div>
        `;
    }

    // the action that completes the scan, if the status of the ticket requires one
    private renderConfirmAction(event: AlfioEvent, scanned: ScanResult): TemplateResult | null {
        const status = scanned.data!.result.status;
        if (status === 'OK_READY_TO_BE_CHECKED_IN') {
            return html`
                <sl-button id="confirm-check-in-button" variant="warning" size="large" ?loading=${this.actionInProgress}
                           @click=${() => this.checkIn(event, scanned.code)}>
                    <sl-icon slot="prefix" name="check2"></sl-icon>
                    ${t('admin.check-in-scan.confirm-check-in')}
                </sl-button>`;
        }
        if (status === 'MUST_PAY') {
            return html`
                <sl-button id="confirm-payment-button" variant="warning" size="large" ?loading=${this.actionInProgress}
                           @click=${() => this.confirmPayment(event, scanned.code)}>
                    <sl-icon slot="prefix" name="cash-coin"></sl-icon>
                    ${t('admin.check-in-scan.confirm-payment')}
                </sl-button>`;
        }
        return null;
    }

    private renderResult({ result }: TicketAndCheckInResult): TemplateResult {
        switch (result.status) {
            case 'SUCCESS':
                return this.renderAlert('success', 'check2-circle', t('admin.check-in-scan.result.success'));
            case 'OK_READY_TO_BE_CHECKED_IN':
                return this.renderAlert('success', 'check2-circle', t('admin.check-in-scan.result.ready'));
            case 'MUST_PAY': {
                const amount = formatAmount(result.dueAmount, result.currency);
                let message = t('admin.check-in-scan.result.must-pay');
                if (amount) {
                    message = t('admin.check-in-scan.result.must-pay-amount', amount);
                }
                return this.renderAlert('warning', 'cash-coin', message);
            }
            case 'ALREADY_CHECK_IN':
                return this.renderAlert('warning', 'exclamation-triangle', result.message || t('admin.check-in-scan.result.already-checked-in'));
            default:
                return this.renderError(result.message || statusLabel(result.status));
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
        this.ignoredCode = null;
        if (this.scanned != null) {
            this.ignoredCode = { code: this.scanned.code, seenAt: Date.now() };
        }
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
            this.stream = await navigator.mediaDevices.getUserMedia({ video: videoConstraints(deviceId) });
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
            this.cameraError = t('admin.check-in-scan.camera-error');
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
        if (!this.decoding && video != null && video.videoWidth > 0) {
            this.decoding = true;
            void this.decode(video);
        }
        this.scheduleCapture();
    }

    private async decode(video: HTMLVideoElement): Promise<void> {
        try {
            if (this.detector == null) {
                this.detector = createQrDetector();
            }
            const codes = await this.detector.detect(video);
            const code = codes.find(c => c.rawValue !== '')?.rawValue;
            if (code != null && this.scanning && this.scanned == null && !this.isIgnored(code)) {
                void this.onCodeScanned(code);
            }
        } catch (e) {
            // a frame that can't be read (e.g. the video is not ready yet): try with the next one
            console.debug('error while decoding the frame', e);
        } finally {
            this.decoding = false;
        }
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
            this.scanned = { code, loading: false, data: { ticket: null, result: { status: 'ERROR', message: t('admin.check-in-scan.unexpected-error') } } };
        } finally {
            this.actionInProgress = false;
        }
    }
}

// the selected camera, or the one facing the attendee by default
function videoConstraints(deviceId: string): MediaTrackConstraints {
    if (deviceId) {
        return { deviceId: { exact: deviceId } };
    }
    return { facingMode: 'environment' };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-check-in-scan': CheckInScan;
    }
}
