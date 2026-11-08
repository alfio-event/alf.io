import {postJson, readAsDataUrl, responseErrorMessage} from './helpers.ts';

/**
 * An image ready for upload: rasterized and possibly resized, as the AngularJS create-event
 * flow (ImageTransformService) does before calling /admin/api/file/upload?resizeImage=true.
 */
export interface TransformedImage {
    // full data URL, for the preview
    dataUrl: string;
    // base64 payload sent to the backend
    base64: string;
    // mime type sent to the backend (image/png for converted SVGs)
    type: string;
    // file name sent to the backend (a converted SVG gets ".png" appended)
    name: string;
}

// longest side of the uploaded image, as in the AngularJS implementation
const MAX_DIMENSION = 450;

function drawImage(image: CanvasImageSource & { width: number, height: number }): HTMLCanvasElement {
    const cnv = document.createElement('canvas');
    cnv.width = image.width;
    cnv.height = image.height;
    const canvasCtx = cnv.getContext('2d');
    if (canvasCtx == null) {
        throw new Error('Canvas 2D context is not available');
    }
    canvasCtx.drawImage(image, 0, 0, image.width, image.height);
    return cnv;
}

function loadImage(src: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => resolve(img);
        img.onerror = () => reject(new Error('The image could not be loaded'));
        img.src = src;
    });
}

/**
 * SVGs are converted to PNG: Firefox does not render an SVG drawn on a canvas unless the
 * size is set explicitly inside the SVG itself, so the width/height are patched into the
 * markup before rasterizing. Same approach as the AngularJS ImageTransformService.
 */
async function transformSvg(file: File): Promise<TransformedImage> {
    const svgRoot = new DOMParser()
        .parseFromString(await file.text(), 'text/xml')
        .getElementsByTagName('svg')[0];
    if (svgRoot == null) {
        throw new Error('The SVG file is not valid');
    }
    const img = new Image();
    if (svgRoot.hasAttribute('height')) {
        img.height = Number(svgRoot.getAttribute('height'));
        img.width = Number(svgRoot.getAttribute('width'));
    } else {
        img.height = 500;
    }
    await loadImageInto(img, await readAsDataUrl(file));
    // set the size explicitly _inside_ the svg (FF limitation, see https://stackoverflow.com/a/61195034)
    svgRoot.setAttribute('width', `${img.width}px`);
    svgRoot.setAttribute('height', `${img.height}px`);
    const serializedSvg = new XMLSerializer().serializeToString(svgRoot);
    const rasterized = await loadImage(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(serializedSvg)}`);
    const dataUrl = drawImage(rasterized).toDataURL('image/png');
    return {
        dataUrl,
        base64: dataUrl.substring(dataUrl.indexOf('base64,') + 7),
        type: 'image/png',
        name: `${file.name}.png`,
    };
}

function loadImageInto(img: HTMLImageElement, src: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
        img.onload = () => resolve(img);
        img.onerror = () => reject(new Error('The image could not be loaded'));
        img.src = src;
    });
}

async function transformRaster(file: File): Promise<TransformedImage> {
    const img = await loadImage(await readAsDataUrl(file));
    let width = img.width;
    let height = img.height;
    let resize = false;
    // source: https://stackoverflow.com/a/24015367
    if (width > height && width > MAX_DIMENSION) {
        height *= MAX_DIMENSION / width;
        width = MAX_DIMENSION;
        resize = true;
    } else if (height > MAX_DIMENSION) {
        width *= MAX_DIMENSION / height;
        height = MAX_DIMENSION;
        resize = true;
    }
    const source = resize
        ? await createImageBitmap(img, {resizeWidth: width, resizeHeight: height, resizeQuality: 'high'})
        : img;
    const dataUrl = drawImage(source).toDataURL(file.type);
    return {
        dataUrl,
        base64: dataUrl.substring(dataUrl.indexOf('base64,') + 7),
        type: file.type,
        name: file.name,
    };
}

/**
 * Converts the picked file as the create-event flow does: SVGs become PNG, images larger
 * than 450px on the longest side are resized down.
 */
export function transformImageFile(file: File): Promise<TransformedImage> {
    if (file.type === 'image/svg+xml') {
        return transformSvg(file);
    }
    return transformRaster(file);
}

/**
 * Uploads a transformed image, resolves with the file blob id
 */
export async function uploadImage(image: TransformedImage): Promise<string> {
    const response = await postJson('/admin/api/file/upload?resizeImage=true', {
        file: image.base64,
        type: image.type,
        name: image.name,
    });
    if (!response.ok) {
        throw new Error(await responseErrorMessage(response, 'Failed to upload the image'));
    }
    // the backend returns the file digest as a JSON string
    return await response.json();
}
