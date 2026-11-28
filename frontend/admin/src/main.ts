import '@shoelace-style/shoelace/dist/themes/light.css';
import './main.css';
import { adminConfig } from './admin-config';
import { initTranslations, loadTranslations } from './service/i18n';

/**
 * Entry point of the admin: loads the translations, then the application (app.ts).
 *
 * The application is imported only once the translations are ready, so that every module can call t(), also at
 * module level (e.g. the labels of a list of options). The page preloads both (admin-index.ms), so the browser
 * downloads them in parallel with this module.
 */
async function start(): Promise<void> {
    removeLanguageParameter();
    const config = adminConfig();
    try {
        const translations = await loadTranslations(config.translationsUrl);
        await initTranslations(config.language, translations);
    } catch (error) {
        console.error('Cannot load the translations of the admin', error);
        showLoadingError();
        return;
    }
    await import('./app');
}

/**
 * The server has stored the language chosen with the picker (?lang=de) in the session: the address doesn't need it
 * anymore, and a bookmark of the page must not force the language
 */
function removeLanguageParameter(): void {
    const url = new URL(window.location.href);
    if (url.searchParams.has('lang')) {
        url.searchParams.delete('lang');
        window.history.replaceState(window.history.state, '', url.toString());
    }
}

/**
 * Without the translations there are no texts to show: a minimal message, necessarily in English
 */
function showLoadingError(): void {
    const message = document.createElement('div');
    message.className = 'admin-loading-error';
    const text = document.createElement('p');
    text.textContent = 'Could not load the admin interface.';
    const retry = document.createElement('button');
    retry.type = 'button';
    retry.textContent = 'Retry';
    retry.addEventListener('click', () => window.location.reload());
    message.append(text, retry);
    document.body.append(message);
}

void start();
