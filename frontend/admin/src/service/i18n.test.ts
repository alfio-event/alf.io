import { beforeEach, describe, expect, it } from 'vitest';
import { currentFormattingLocale, initTranslations, t, tHtml, tHtmlPlural, translateErrorCode, validLocale } from './i18n';

describe('t', () => {
    beforeEach(async () => {
        await initTranslations('en', {
            'locale': 'en-US',
            'admin.common.save': 'Save',
            'admin.event.sold': '{{0}} of {{1}} sold',
            'admin.event.tickets_one': 'One ticket',
            'admin.event.tickets_other': '{{0}} tickets',
            'admin.text.with-markup': 'Use <b>{{0}}</b>',
            'error.shortname': 'Event name is not valid',
        });
    });

    it('returns the text of a key', () => {
        expect(t('admin.common.save')).toBe('Save');
    });

    it('fills the positional placeholders', () => {
        expect(t('admin.event.sold', 3, 10)).toBe('3 of 10 sold');
    });

    it('picks the plural form from the count', () => {
        expect(t('admin.event.tickets', { count: 1 })).toBe('One ticket');
        expect(t('admin.event.tickets', { count: 4 })).toBe('4 tickets');
        expect(t('admin.event.tickets', { count: 1234 })).toBe('1,234 tickets');
    });

    it('does not escape the values: Lit does it when rendering', () => {
        expect(t('admin.text.with-markup', 'a & b')).toBe('Use <b>a & b</b>');
    });

    it('returns the key of a missing text, so that the mistake is visible', () => {
        expect(t('admin.does.not.exist')).toBe('admin.does.not.exist');
    });

    it('puts markup in the placeholders, without parsing the translation as HTML', () => {
        const link = { markup: 'a link' };
        expect(tHtml('admin.text.with-markup', link)).toEqual(['Use <b>', link, '</b>']);
        expect(tHtml('admin.common.save')).toEqual(['Save']);
    });

    it('puts markup in a text with plurals', () => {
        const count = { markup: '<strong>4</strong>' };
        expect(tHtmlPlural('admin.event.tickets', 4, count)).toEqual(['', count, ' tickets']);
        expect(tHtmlPlural('admin.event.tickets', 1, count)).toEqual(['One ticket']);
    });

    it('translates the error codes sent by the server', () => {
        expect(translateErrorCode('error.shortname', 'fallback')).toBe('Event name is not valid');
        expect(translateErrorCode('not_found', 'fallback')).toBe('fallback');
        expect(translateErrorCode(undefined, 'fallback')).toBe('fallback');
    });
});

describe('plural rules', () => {
    it('follows the rules of the language', async () => {
        await initTranslations('pl', {
            'locale': 'pl-PL',
            'admin.event.tickets_one': '{{0}} bilet',
            'admin.event.tickets_few': '{{0}} bilety',
            'admin.event.tickets_many': '{{0}} biletów',
        });
        expect(t('admin.event.tickets', { count: 1 })).toBe('1 bilet');
        expect(t('admin.event.tickets', { count: 3 })).toBe('3 bilety');
        expect(t('admin.event.tickets', { count: 5 })).toBe('5 biletów');
    });
});

describe('formatting locale', () => {
    it('comes from the "locale" key', async () => {
        await initTranslations('de', { 'locale': 'de-DE' });
        expect(currentFormattingLocale()).toBe('de-DE');
    });

    it('falls back to the language when the key is not a valid tag', () => {
        expect(validLocale('de_DE', 'de')).toBe('de');
        expect(validLocale(undefined, 'fr')).toBe('fr');
        expect(validLocale(' it-IT ', 'it')).toBe('it-IT');
    });
});
