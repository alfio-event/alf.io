import {javascriptLanguage} from '@codemirror/lang-javascript';
import type {SyntaxNode} from '@lezer/common';

// the id of the sample script returned by /admin/api/extensions/sample
const SAMPLE_ID = 'myExtensionIdentifier';

/**
 * The value of the "id" string returned by getScriptMetadata(), and its position (between the quotes)
 */
export interface MetadataId {
    value: string;
    from: number;
    to: number;
}

/**
 * Finds the "id" of the object returned by getScriptMetadata(). Null when the script doesn't declare it as a string literal.
 */
export function findMetadataId(script: string): MetadataId | null {
    const tree = javascriptLanguage.parser.parse(script);
    const metadataFunction = tree.topNode.getChildren('FunctionDeclaration')
        .find((f) => nodeText(f.getChild('VariableDefinition'), script) === 'getScriptMetadata');
    const returned = metadataFunction?.getChild('Block')?.getChild('ReturnStatement')?.getChild('ObjectExpression');
    if (returned == null) {
        return null;
    }
    const idProperty = returned.getChildren('Property')
        .find((p) => nodeText(p.getChild('PropertyDefinition'), script) === 'id');
    const idString = idProperty?.getChild('String');
    // a String node without a closing quote is still being typed
    if (idString == null || idString.to - idString.from < 2) {
        return null;
    }
    const from = idString.from + 1;
    const to = idString.to - 1;
    return { value: script.slice(from, to), from, to };
}

/**
 * Makes the "id" of the metadata follow the extension name, unless the user chose a different id:
 * the id is updated only while it is the sample one, empty, or equal to the name it was copied from.
 * Returns the script unchanged when the id doesn't follow the name.
 *
 * @param previousName the name the id was last copied from
 */
export function syncMetadataId(script: string, previousName: string, name: string): string {
    const id = findMetadataId(script);
    if (id === null || id.value === name) {
        return script;
    }
    if (id.value !== SAMPLE_ID && id.value !== '' && id.value !== previousName) {
        return script;
    }
    return script.slice(0, id.from) + name + script.slice(id.to);
}

function nodeText(node: SyntaxNode | null, script: string): string | null {
    if (node === null) {
        return null;
    }
    return script.slice(node.from, node.to);
}
