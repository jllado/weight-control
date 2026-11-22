import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import schemasafe from '@exodus/schemasafe';
import { parse } from 'yaml';

const relative = path => fileURLToPath(new URL(path, import.meta.url));
const { values } = parseArgs({ options: {
    schema: { type: 'string', default: relative('../../docs/coach/coach-action.openapi.yaml') },
    skill: { type: 'string', default: relative('../skills/weight-control-coach/SKILL.md') },
    output: { type: 'string', default: relative('dist/server/index.js') }
} });
const contract = parse(readFileSync(values.schema, 'utf8'));
const pluginDescriptions = {
    getProgressPhotoFiles: 'Retrieve labeled images for the requested sides of one owned photo set. Request only the sides needed for the visual comparison.',
    saveReflection: 'Save the exact confirmed DAILY or WEEKLY proposal with its current warningReview. DAILY summary ≤200, rationale ≤500, findings ≤120, next action ≤200. Correct validation errors and confirm material changes before retrying.'
};

function toolSchema(source, definitions) {
    if (source.$ref) {
        const name = source.$ref.split('/').at(-1);
        const referenced = contract.components.schemas[name];
        if (!definitions) return toolSchema(referenced);
        if (!Object.hasOwn(definitions, name)) definitions[name] = toolSchema(referenced, definitions);
        return { $ref: '#/$defs/' + name };
    }
    const { nullable, ...schema } = source;
    if (nullable) {
        schema.type = [...new Set([].concat(schema.type, 'null'))];
        if (schema.enum) schema.enum = [...new Set([...schema.enum, null])];
    }
    if (schema.properties) {
        schema.properties = Object.fromEntries(Object.entries(schema.properties).map(([name, field]) => [name, toolSchema(field, definitions)]));
    }
    if (schema.items) schema.items = toolSchema(schema.items, definitions);
    for (const clause of ['anyOf', 'oneOf', 'allOf']) {
        if (schema[clause]) schema[clause] = schema[clause].map(part => toolSchema(part, definitions));
    }
    if (schema.format === 'int64') {
        delete schema.format;
        schema.minimum = Math.max(schema.minimum ?? Number.MIN_SAFE_INTEGER, Number.MIN_SAFE_INTEGER);
        schema.maximum = Math.min(schema.maximum ?? Number.MAX_SAFE_INTEGER, Number.MAX_SAFE_INTEGER);
    }
    // The Coach accepts Java LocalTime (HH:mm or HH:mm:ss with optional fractional seconds).
    if (schema.format === 'time') {
        delete schema.format;
        schema.pattern = '^(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9](?:\\.[0-9]{1,9})?)?$';
    }
    return schema;
}

function operationConfig(path, method, operation) {
    const bodySchema = operation.requestBody?.content['application/json'].schema;
    const inputSchema = bodySchema ? toolSchema(bodySchema) : { type: 'object', properties: {} };
    const body = Object.keys(inputSchema.properties);
    const query = [];
    for (const param of operation.parameters ?? []) {
        const field = toolSchema(param.schema);
        if (param.description) field.description = param.description;
        inputSchema.properties[param.name] = field;
        if (param.required) inputSchema.required = [...(inputSchema.required ?? []), param.name];
        if (param.in === 'query') query.push(param.name);
        // Closed body alternatives (DAILY/WEEKLY) validate the same flat tool object.
        for (const branch of [...(inputSchema.oneOf ?? []), ...(inputSchema.anyOf ?? [])]) {
            if (branch.additionalProperties === false) branch.properties[param.name] = field;
        }
    }
    inputSchema.additionalProperties = false;
    if (operation['x-openai-isConsequential']) {
        inputSchema.properties.confirmed = { type: 'boolean', const: true, description: 'Send true after the user confirms the current proposal.' };
        inputSchema.required = [...new Set([...inputSchema.required, 'confirmed'])];
    }
    const destructive = ['put', 'delete'].includes(method) || operation.operationId.startsWith('delete')
        || ['saveCoachWarning', 'saveReflection'].includes(operation.operationId);
    const tool = {
        name: operation.operationId, title: operation.summary,
        description: pluginDescriptions[operation.operationId] ?? operation.description ?? operation.summary,
        inputSchema,
        annotations: { readOnlyHint: method === 'get', openWorldHint: false, destructiveHint: destructive }
    };
    return { tool, path, method: method.toUpperCase(), body, query, allowsNoContent: Object.hasOwn(operation.responses, '204') };
}

const operations = Object.entries(contract.paths).flatMap(([path, methods]) =>
    Object.entries(methods).map(([method, operation]) => operationConfig(path, method, operation)));
// Chat may expose a union-typed tool as a generic object. Supply its exact selected
// branch alongside the context, with shared warning definitions included once.
function reflectionSchemas() {
    const save = operations.find(operation => operation.tool.name === 'saveReflection');
    const body = contract.paths[save.path].post.requestBody.content['application/json'].schema;
    const { date, confirmed } = save.tool.inputSchema.properties;
    return Object.fromEntries(body.oneOf.map(branch => {
        const name = branch.$ref.split('/').at(-1);
        const target = { SaveReflection: 'DAILY', SaveWeeklyReflection: 'WEEKLY' }[name];
        if (!target) throw new Error('Unmapped saveReflection branch: ' + name);
        const definitions = {};
        const schema = toolSchema(contract.components.schemas[name], definitions);
        schema.title = 'saveReflection ' + target + ' tool arguments';
        schema.properties = { date, target: { type: 'string', const: target }, ...schema.properties, confirmed };
        schema.required = [...new Set(['date', 'target', 'confirmed', ...schema.required])];
        schema.$defs = definitions;
        return [target, schema];
    }));
}
const instructions = readFileSync(values.skill, 'utf8').replace(/^---\r?\n[\s\S]*?\r?\n---(?:\r?\n|$)/, '').trim();
const config = { baseUrl: contract.servers[0].url, operations, instructions, reflectionSchemas: reflectionSchemas() };
const validators = operations.map(({ tool }) => {
    const validate = schemasafe.validator(tool.inputSchema, { mode: 'spec', formatAssertion: true, includeErrors: true, isJSON: true });
    return JSON.stringify(tool.name) + ': ' + validate.toModule({ semi: false });
});
// Some MCP clients check fractional multipleOf using binary floating-point division.
// Advertise the decimal limit in words. The compiled validators above enforce it.
const advertisedConfig = JSON.stringify(config, (_key, value) => {
    if (value?.multipleOf !== 0.01 && value?.multipleOf !== 0.001) return value;
    const { multipleOf, ...field } = value;
    const precision = multipleOf === 0.01 ? 2 : 3;
    const hint = 'Use at most ' + precision + ' decimal places.';
    field.description = field.description ? field.description + ' ' + hint : hint;
    return field;
});
const source = readFileSync(relative('worker.mjs'), 'utf8');
mkdirSync(dirname(values.output), { recursive: true });
writeFileSync(values.output, source + '\nexport default createWorker(' + advertisedConfig + ', {\n' + validators.join(',\n') + '\n});\n');
