const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {test} = require('node:test');
const yaml = require('js-yaml');
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
test('rejects a composition-only request root that the GPT importer skips', () => {
    const value = schema();
    value.components = {schemas: {Write: {type: 'object', properties: {title: {type: 'string'}}}}};
    value.paths['/records/0'].post = {operationId: 'save', requestBody: {content: {'application/json': {schema: {oneOf: [{$ref: '#/components/schemas/Write'}]}}}}};
    assert.match(validate(value).errors.join('\n'), /POST \/records\/0 \(save\): request body must define type: object and properties/);
    value.paths['/records/0'].post.requestBody.content['application/json'].schema = {$ref: '#/components/schemas/Write'};
    assert.deepEqual(validate(value).errors, []);
});
test('rejects composition-only object sections while accepting concrete nullable sections and dictionaries', () => {
    const value = schema();
    value.components = {schemas: {
        ReadSection: {type: ['object', 'null'], properties: {summary: {type: 'string'}}},
        WriteSection: {type: 'object', allOf: [{$ref: '#/components/schemas/ReadSection'}]},
        Counts: {type: 'object', additionalProperties: {type: 'integer'}}
    }};
    assert.match(validate(value).errors.join('\n'), /#\/components\/schemas\/WriteSection: object schema must define properties/);
    value.components.schemas.WriteSection = {type: 'object', properties: {summary: {type: 'string'}}};
    assert.deepEqual(validate(value).errors, []);
});
test('repository reflection import preserves daily and weekly save contracts', () => {
    const root = path.resolve(__dirname, '../..');
    const schemaText = fs.readFileSync(path.join(root, 'docs/coach/coach-action.openapi.yaml'), 'utf8');
    const markdown = fs.readFileSync(path.join(root, 'docs/coach/coach-gpt.md'), 'utf8');
    const result = validateCoach(schemaText, markdown);
    assert.deepEqual(result.errors, []);
    assert.equal(result.operationCount, 30);
    const value = yaml.load(schemaText);
    const operation = value.paths['/reflections/{date}'].post;
    const body = operation.requestBody.content['application/json'].schema;
    const {SaveReflection: daily, SaveWeeklyReflection: weekly, ReflectionWriteSection: section, ReflectionSection: legacy} = value.components.schemas;
    assert.equal(operation.operationId, 'saveReflection');
    assert.equal(operation['x-openai-isConsequential'], true);
    assert.deepEqual(operation.parameters.find(parameter => parameter.name === 'target').schema, {type: 'string', enum: ['DAILY', 'WEEKLY'], default: 'DAILY'});
    assert.equal(body.type, 'object');
    assert.deepEqual(body.required, ['title', 'summary']);
    assert.deepEqual(body.oneOf, [{$ref: '#/components/schemas/SaveReflection'}, {$ref: '#/components/schemas/SaveWeeklyReflection'}]);
    assert.deepEqual(Object.keys(body.properties).sort(), [...new Set([...Object.keys(daily.properties), ...Object.keys(weekly.properties)])].sort());
    assert.equal(body.properties.summary.maxLength, weekly.properties.summary.maxLength);
    assert.equal(daily.properties.summary.maxLength, 200);
    assert.equal(weekly.properties.summary.maxLength, 500);
    assert.equal(daily.additionalProperties, false);
    assert.equal(weekly.additionalProperties, false);
    assert.ok(daily.required.includes('meals') && daily.required.includes('workouts'));
    assert.ok(weekly.required.includes('confirmed'));
    assert.deepEqual(weekly.properties.confirmed.enum, [true]);
    assert.equal(section.type, 'object');
    assert.deepEqual(section.required, ['summary', 'nextAction']);
    assert.deepEqual(section.properties, legacy.properties);
    assert.equal(section.properties.summary.maxLength, 200);
    assert.equal(section.properties.nextAction.maxLength, 120);
    assert.deepEqual(legacy.type, ['object', 'null']);
});
