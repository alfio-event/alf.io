import {LanguageSupport, StreamLanguage} from '@codemirror/language';
import type {Completion, CompletionContext, CompletionResult} from '@codemirror/autocomplete';
import type {EditorView} from '@codemirror/view';
import type {MustacheVariableDefinition} from './mustache-variable.ts';

/**
 * Plain text with Mustache tags, for <alfio-code-editor>: the tags ("{{fullName}}", "{{#x}}…{{/x}}") are highlighted,
 * and typing "{{" proposes the variables.
 */
const mustache = StreamLanguage.define<null>({
    name: 'mustache',
    startState: () => null,
    token(stream) {
        if (stream.match(/^\{\{\{?[#^/!>&]?\s*[\w.]*\s*\}?\}\}/)) {
            return 'keyword';
        }
        // the rest of the text, up to the next tag
        if (stream.match(/^[^{]+/) == null) {
            stream.next();
        }
        return null;
    },
});

export function mustacheLanguage(variables: MustacheVariableDefinition[]): LanguageSupport {
    return new LanguageSupport(mustache, [mustache.data.of({autocomplete: variableCompletions(variables)})]);
}

/** completes the name of a variable after "{{" */
export function variableCompletions(variables: MustacheVariableDefinition[]) {
    return (context: CompletionContext): CompletionResult | null => {
        const before = context.matchBefore(/\{\{\s*[\w.]*/);
        if (before == null) {
            return null;
        }
        const nameStart = before.from + before.text.search(/[\w.]*$/);
        return {
            from: nameStart,
            options: variables.map(v => ({label: v.name, detail: v.description, apply: applyVariable, type: 'variable'})),
            validFor: /^[\w.]*$/,
        };
    };
}

/** inserts the name, and closes the tag unless the closing braces are already there (e.g. auto-closed brackets) */
function applyVariable(view: EditorView, completion: Completion, from: number, to: number): void {
    const closed = view.state.sliceDoc(to, to + 2) === '}}';
    let insert = completion.label;
    if (!closed) {
        insert = `${insert}}}`;
    }
    view.dispatch({
        changes: {from, to, insert},
        // after the closing braces
        selection: {anchor: from + completion.label.length + 2},
    });
}
