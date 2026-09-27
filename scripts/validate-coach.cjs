const fs = require('node:fs');
const path = require('node:path');
const yaml = require('js-yaml');

const schemaFile = 'docs/coach/coach-action.openapi.yaml';
const instructionsFile = 'docs/coach/coach-gpt.md';
const methods = new Set(['get', 'put', 'post', 'delete', 'options', 'head', 'patch', 'trace']);

function validateCoach(schemaText, markdown) {
    const errors = [];
    let schema;
    try {
        schema = yaml.load(schemaText, {schema: yaml.JSON_SCHEMA});
    } catch (error) {
        errors.push(`${schemaFile}: ${error.message}`);
    }
    let operationCount = 0;
    if (schema && typeof schema === 'object' && schema.paths && typeof schema.paths === 'object') {
        const ids = new Set();
        for (const [route, item] of Object.entries(schema.paths)) {
            for (const [method, operation] of Object.entries(item)) {
                if (!methods.has(method)) continue;
                operationCount++;
                const location = `${schemaFile}: ${method.toUpperCase()} ${route}`;
                if (typeof operation.operationId !== 'string' || !operation.operationId) {
                    errors.push(`${location}: operationId is required`);
                } else if (ids.has(operation.operationId)) {
                    errors.push(`${location}: duplicate operationId ${operation.operationId}`);
                }
                ids.add(operation.operationId);
                if (typeof operation.description === 'string' && operation.description.length > 300) {
                    errors.push(`${location} (${operation.operationId}): description has ${operation.description.length} characters; maximum 300`);
                }
            }
        }
        if (operationCount > 30) errors.push(`${schemaFile}: ${operationCount} operations; maximum 30`);
        const checkReferences = (value, location) => {
            if (!value || typeof value !== 'object') return;
            for (const [key, child] of Object.entries(value)) {
                if (key === '$ref') {
                    let target = schema;
                    if (typeof child !== 'string' || !child.startsWith('#/')) {
                        errors.push(`${schemaFile}: ${location}/$ref: expected a local reference, received ${JSON.stringify(child)}`);
                        continue;
                    }
                    for (const part of child.slice(2).split('/')) {
                        const decoded = part.replace(/~1/g, '/').replace(/~0/g, '~');
                        target = target && Object.hasOwn(target, decoded) ? target[decoded] : undefined;
                    }
                    if (target === undefined) errors.push(`${schemaFile}: ${location}/$ref: unresolved reference ${child}`);
                } else {
                    checkReferences(child, `${location}/${key}`);
                }
            }
        };
        checkReferences(schema, '#');
    } else if (errors.length === 0) {
        errors.push(`${schemaFile}: expected an OpenAPI object with paths`);
    }
    const blocks = [...markdown.matchAll(/^```text\r?\n([\s\S]*?)\r?\n```\s*$/gm)];
    const instructionLength = blocks.length === 1 ? blocks[0][1].length : 0;
    if (blocks.length !== 1 || instructionLength === 0) {
        errors.push(`${instructionsFile}: expected exactly one nonempty fenced text instruction block; found ${blocks.length}`);
    } else if (instructionLength > 8000) {
        errors.push(`${instructionsFile}: instructions have ${instructionLength} characters; maximum 8000`);
    }
    return {errors, operationCount, instructionLength};
}

if (require.main === module) {
    const root = path.resolve(__dirname, '..');
    const result = validateCoach(fs.readFileSync(path.join(root, schemaFile), 'utf8'), fs.readFileSync(path.join(root, instructionsFile), 'utf8'));
    if (result.errors.length) {
        console.error(result.errors.join('\n'));
        process.exitCode = 1;
    } else {
        console.log(`Coach configuration valid: ${result.operationCount}/30 Actions; ${result.instructionLength}/8000 instruction characters.`);
    }
}

module.exports = {validateCoach};
