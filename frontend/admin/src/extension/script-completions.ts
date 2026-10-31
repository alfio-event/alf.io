import {
    Completion,
    CompletionContext,
    CompletionResult,
    CompletionSource,
    completeFromList,
    ifNotIn,
} from '@codemirror/autocomplete';
import {LanguageSupport, syntaxTree} from '@codemirror/language';
import {javascriptLanguage, localCompletionSource, snippets} from '@codemirror/lang-javascript';
import {EditorState} from '@codemirror/state';
import type {SyntaxNode} from '@lezer/common';
import {EXTENSION_EVENTS, ExtensionEventDispatch} from 'virtual:extension-events';

// The syntax accepted by the backend is verified by ScriptSyntaxSupportTest (Rhino 1.9.2, ES6 language level):
// keep these lists in sync with it.

// snippets of constructs rejected by the backend validator (JSNodeVisitor: while, do...while)
// or not supported by Rhino (classes, ES modules). The remaining ones (for, for...of with let, if, try, function) are supported
const UNSUPPORTED_SNIPPETS = new Set(['do', 'while', 'class', 'import']);

// the keywords proposed by @codemirror/lang-javascript, all supported, without those of classes and ES modules
// (export, extends, static, super), not supported by Rhino
const KEYWORDS: Completion[] = ('break case const continue default delete false finally in instanceof let new return '
    + 'switch this throw true typeof var yield').split(' ').map((label) => ({label, type: 'keyword'}));

// where @codemirror/lang-javascript doesn't propose keywords and snippets
const NO_KEYWORDS_IN = ['TemplateString', 'String', 'RegExp', 'LineComment', 'BlockComment', 'VariableDefinition',
    'Label', 'PropertyDefinition', 'PropertyName', 'PrivatePropertyDefinition', 'PrivatePropertyName', '.', '?.'];

/**
 * JavaScript support for the extension scripts, replacing javascript() from @codemirror/lang-javascript:
 * - keywords and snippets limited to what the backend accepts
 * - completion of the event names inside the "events" array returned by getScriptMetadata(), where nothing else is proposed.
 *   The names come from the backend enum ExtensionEvent, read at build time. Only the events that reach the script
 *   are proposed: the backend runs a script for an event only if its "async" flag matches how the event is dispatched.
 */
export const extensionScriptLanguage = new LanguageSupport(javascriptLanguage, [
    javascriptLanguage.data.of({
        autocomplete: outsideEventsArray(ifNotIn(NO_KEYWORDS_IN, completeFromList([
            ...snippets.filter((s) => !UNSUPPORTED_SNIPPETS.has(s.label)),
            ...KEYWORDS,
        ]))),
    }),
    javascriptLanguage.data.of({autocomplete: outsideEventsArray(localCompletionSource)}),
    javascriptLanguage.data.of({autocomplete: completeExtensionEvent}),
]);

/**
 * Disables a completion source in the "events" array, where only event names are valid
 */
function outsideEventsArray(source: CompletionSource): CompletionSource {
    return (context) => {
        let node: SyntaxNode | null = syntaxTree(context.state).resolveInner(context.pos, -1);
        while (node !== null && node.name !== 'ArrayExpression') {
            node = node.parent;
        }
        if (isEventsArray(node, context.state)) {
            return null;
        }
        return source(context);
    };
}

function completeExtensionEvent(context: CompletionContext): CompletionResult | null {
    const node = syntaxTree(context.state).resolveInner(context.pos, -1);

    // inside a string of the array: 'RESERV|'
    if (node.name === 'String' && isEventsArray(node.parent, context.state)) {
        const quoteEnd = node.from + 1;
        return {
            from: quoteEnd,
            options: eventOptions(node.parent!, context.state, (name) => name),
            validFor: /^[A-Z0-9_]*$/,
        };
    }

    // a bare word in the array: RESERV| becomes 'RESERVATION_CONFIRMED'
    if (node.name === 'VariableName' && isEventsArray(node.parent, context.state)) {
        return {
            from: node.from,
            options: eventOptions(node.parent!, context.state, (name) => `'${name}'`),
            validFor: /^[A-Z0-9_]*$/,
        };
    }

    // Ctrl+Space in the empty space of the array
    if (context.explicit && node.name === 'ArrayExpression' && isEventsArray(node, context.state)) {
        return {
            from: context.pos,
            options: eventOptions(node, context.state, (name) => `'${name}'`),
        };
    }
    return null;
}

/**
 * true if the node is the value of an "events" property: { events: [ ... ] }
 */
function isEventsArray(node: SyntaxNode | null, state: EditorState): boolean {
    if (node == null || node.name !== 'ArrayExpression' || node.parent?.name !== 'Property') {
        return false;
    }
    const key = node.parent.getChild('PropertyDefinition');
    return key != null && state.sliceDoc(key.from, key.to) === 'events';
}

/**
 * The events not yet listed in the array that can be handled by the script
 */
function eventOptions(array: SyntaxNode, state: EditorState, toApply: (name: string) => string): Completion[] {
    const listed = new Set(array.getChildren('String')
        .map((s) => state.sliceDoc(s.from, s.to).replace(/^['"]|['"]$/g, '')));
    const async = asyncFlag(array, state);
    return EXTENSION_EVENTS
        .filter((event) => !listed.has(event.name))
        .filter((event) => async === null || reachesScript(event.dispatch, async))
        .map((event) => ({label: event.name, apply: toApply(event.name), type: 'enum', detail: DISPATCH_LABELS[event.dispatch]}));
}

const DISPATCH_LABELS: Record<ExtensionEventDispatch, string> = {
    SYNC: 'sync',
    ASYNC: 'async',
    ALL: 'sync / async',
};

function reachesScript(dispatch: ExtensionEventDispatch, async: boolean): boolean {
    if (dispatch === 'ALL') {
        return true;
    }
    if (async) {
        return dispatch === 'ASYNC';
    }
    return dispatch === 'SYNC';
}

/**
 * The "async" flag declared next to the events array: false when missing, like the backend does,
 * null when it is not a literal (e.g. a variable) and cannot be read
 */
function asyncFlag(array: SyntaxNode, state: EditorState): boolean | null {
    const metadata = array.parent?.parent;
    if (metadata == null || metadata.name !== 'ObjectExpression') {
        return null;
    }
    const property = metadata.getChildren('Property').find((p) => {
        const key = p.getChild('PropertyDefinition');
        return key != null && state.sliceDoc(key.from, key.to) === 'async';
    });
    if (property === undefined) {
        return false;
    }
    const value = property.getChild('BooleanLiteral');
    if (value === null) {
        return null;
    }
    return state.sliceDoc(value.from, value.to) === 'true';
}
