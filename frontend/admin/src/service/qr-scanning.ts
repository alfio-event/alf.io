/**
 * QR codes read with the browser's own BarcodeDetector (Shape Detection API). Not every browser has it (desktop
 * Firefox and Safari don't): the check-in pages ask canScanQrCodes() each time they open, and point to the scan apps
 * otherwise.
 */

interface DetectedBarcode {
    rawValue: string;
}

export interface QrDetector {
    detect(source: HTMLVideoElement): Promise<DetectedBarcode[]>;
}

interface BarcodeDetectorConstructor {
    new(options: { formats: string[] }): QrDetector;
    getSupportedFormats(): Promise<string[]>;
}

const QR_CODE = 'qr_code';

function barcodeDetector(): BarcodeDetectorConstructor | null {
    const detector = (window as unknown as { BarcodeDetector?: BarcodeDetectorConstructor }).BarcodeDetector;
    return detector ?? null;
}

/** whether this browser can read QR codes from the camera */
export async function canScanQrCodes(): Promise<boolean> {
    const detector = barcodeDetector();
    if (detector == null || navigator.mediaDevices?.getUserMedia == null) {
        return false;
    }
    try {
        return (await detector.getSupportedFormats()).includes(QR_CODE);
    } catch {
        return false;
    }
}

/** call only when canScanQrCodes() is true */
export function createQrDetector(): QrDetector {
    const detector = barcodeDetector()!;
    return new detector({ formats: [QR_CODE] });
}
