import {Extension} from '@tiptap/core';
import {Plugin, PluginKey} from '@tiptap/pm/state';
import {Decoration, DecorationSet} from '@tiptap/pm/view';
import type {Node as ProseMirrorNode} from '@tiptap/pm/model';

/**
 * Mustache variables ("{{fullName}}") in <alfio-markdown-editor>, shown as chips.
 *
 * The variables stay text in the document, decorated: they can be bold or part of a link like any other text, and
 * the markdown round-trips unchanged (Tiptap's markdown support applies marks to text nodes only). Only plain
 * variables are decorated; sections ("{{#x}}…{{/x}}") and the other Mustache tags are left as they are.
 */

const VARIABLES = /\{\{\s*[A-Za-z_][\w.]*\s*\}\}/g;

export interface MustacheVariableDefinition {
    name: string;
    /** what the variable contains, shown in the editor's "Insert variable" menu */
    description: string;
}

/** the ranges of the variables in a text, [from, to) */
export function findVariables(text: string): { from: number, to: number }[] {
    return [...text.matchAll(VARIABLES)].map(match => ({from: match.index, to: match.index + match[0].length}));
}

const key = new PluginKey<DecorationSet>('alfioMustacheVariables');

function decorate(doc: ProseMirrorNode): DecorationSet {
    const decorations: Decoration[] = [];
    doc.descendants((node, position) => {
        if (node.isText && node.text != null) {
            for (const range of findVariables(node.text)) {
                decorations.push(Decoration.inline(position + range.from, position + range.to, {
                    class: 'mustache-variable',
                    title: 'Variable, replaced when the message is sent',
                }));
            }
        }
    });
    return DecorationSet.create(doc, decorations);
}

export const MustacheVariables = Extension.create({
    name: 'mustacheVariables',

    addProseMirrorPlugins() {
        return [new Plugin<DecorationSet>({
            key,
            state: {
                init: (_, state) => decorate(state.doc),
                apply: (tr, decorations) => {
                    if (tr.docChanged) {
                        return decorate(tr.doc);
                    }
                    return decorations;
                },
            },
            props: {
                decorations: state => key.getState(state),
            },
        })];
    },
});
