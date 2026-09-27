const assert = require('node:assert/strict');
const {test} = require('node:test');
const {validateCoach} = require('../../scripts/validate-coach.cjs');

function schema(count = 1, description = 'Read records') {
    return {openapi: '3.1.0', paths: Object.fromEntries(Array.from({length: count}, (_, i) => [`/records/${i}`, {get: {operationId: `read${i}`, description}}]))};
}
const instructions = (length = 20) => `# Coach\n\n\`\`\`text\n${'a'.repeat(length)}\n\`\`\`\n`;
const validate = (value, length = 20) => validateCoach(JSON.stringify(value), instructions(length));

test('accepts all exact limits and counts instructions only', () => {
    assert.deepEqual(validate(schema(30, 'a'.repeat(300)), 8000), {errors: [], operationCount: 30, instructionLength: 8000});
});
test('reports every exceeded limit with actionable locations', () => {
    const errors = validate(schema(31, 'a'.repeat(301)), 8001).errors.join('\n');
    assert.match(errors, /GET \/records\/0 \(read0\).*301.*300/);
    assert.match(errors, /31 operations; maximum 30/);
    assert.match(errors, /coach-gpt.md: instructions have 8001.*8000/);
});
test('rejects malformed YAML and duplicate mapping keys', () => {
    for (const text of ['', 'paths: [', 'paths: {}\npaths: {}']) {
        assert.match(validateCoach(text, instructions()).errors.join('\n'), /coach-action.openapi.yaml/);
    }
});
test('rejects duplicate and missing operation IDs', () => {
    const value = schema(3);
    value.paths['/records/1'].get.operationId = 'read0';
    delete value.paths['/records/2'].get.operationId;
    const errors = validate(value).errors.join('\n');
    assert.match(errors, /duplicate operationId read0/);
    assert.match(errors, /GET \/records\/2: operationId is required/);
});
test('resolves nested references, escaped JSON pointers, and recursive schemas', () => {
    const value = schema();
    value.components = {schemas: {'a/b~c': {type: 'object', properties: {next: {$ref: '#/components/schemas/a~1b~0c'}}}}};
    value.paths['/records/0'].get.responses = {200: {schema: {$ref: '#/components/schemas/a~1b~0c'}}};
    assert.deepEqual(validate(value).errors, []);
    value.components.schemas['a/b~c'].properties.next.$ref = '#/components/schemas/missing';
    assert.match(validate(value).errors.join('\n'), /unresolved reference #\/components\/schemas\/missing/);
});
test('rejects external references and missing paths', () => {
    const value = schema();
    value.paths['/records/0'].get.responses = {200: {$ref: 'external.yaml'}};
    assert.match(validate(value).errors.join('\n'), /expected a local reference/);
    assert.match(validate({}).errors.join('\n'), /expected an OpenAPI object with paths/);
});
test('requires one nonempty instruction block', () => {
    for (const markdown of ['', instructions(0), instructions() + instructions()]) {
        assert.match(validateCoach(JSON.stringify(schema()), markdown).errors.join('\n'), /exactly one nonempty/);
    }
});
