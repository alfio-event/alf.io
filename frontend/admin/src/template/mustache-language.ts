import {LanguageSupport, StreamLanguage} from '@codemirror/language';
import type {StringStream} from '@codemirror/language';

/**
 * Minimal Mustache support for the template editor: the {{...}} (and {{{...}}}) tags are highlighted,
 * as are HTML tags, since the e-mail and PDF templates are mostly HTML.
 * Token names are the standard CodeMirror ones, so the default highlight style colors them.
 */
export const mustacheLanguage = new LanguageSupport(StreamLanguage.define({
    name: 'mustache',
    startState: () => null,
    token(stream: StringStream): string | null {
        if (stream.match('{{{', false)) {
            stream.next(); stream.next(); stream.next();
            consumeUntil(stream, '}}}');
            return 'keyword';
        }
        if (stream.match('{{', false)) {
            stream.next(); stream.next();
            const comment = stream.match('!', false);
            consumeUntil(stream, '}}');
            if (comment) {
                return 'comment';
            }
            return 'keyword';
        }
        if (stream.match('<', false)) {
            stream.next();
            consumeUntil(stream, '>');
            return 'tagName';
        }
        stream.next();
        return null;
    },
}));

function consumeUntil(stream: StringStream, delimiter: string): void {
    while (!stream.eol()) {
        if (stream.match(delimiter, false)) {
            for (let i = 0; i < delimiter.length; i++) {
                stream.next();
            }
            return;
        }
        stream.next();
    }
}
