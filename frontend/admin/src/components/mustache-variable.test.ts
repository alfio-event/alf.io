import { describe, expect, it } from 'vitest';
import { findVariables } from './mustache-variable';

describe('findVariables', () => {
    it('finds the variables of a text', () => {
        expect(findVariables('Hello {{fullName}}, see {{ ticket.publicUuid }}')).toEqual([
            { from: 6, to: 18 },
            { from: 24, to: 47 },
        ]);
    });

    it('leaves the other Mustache tags alone', () => {
        expect(findVariables('{{#walletEnabled}}x{{/walletEnabled}}')).toEqual([]);
        expect(findVariables('{{^empty}} {{! comment }} {{> partial}}')).toEqual([]);
        // the inner "{{unescaped}}" of a triple mustache is a variable for the editor too: same chip
        expect(findVariables('{{{unescaped}}}')).toEqual([{ from: 1, to: 14 }]);
    });

    it('ignores text that is not a variable', () => {
        expect(findVariables('{{unclosed and {single} and {{}}')).toEqual([]);
    });
});
