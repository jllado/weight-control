const {test, expect} = require('@playwright/test');
const {readFileSync, readdirSync} = require('node:fs');
const path = require('node:path');
const {parse} = require('@vue/compiler-sfc');
const {parse: parseTemplate} = require('@vue/compiler-dom');

const sourceRoot = path.resolve(__dirname, '../../src');
function vueFiles(directory) {
    return readdirSync(directory, {withFileTypes: true}).flatMap(entry => entry.isDirectory() ? vueFiles(path.join(directory, entry.name)) : entry.name.endsWith('.vue') ? [path.join(directory, entry.name)] : []);
}
function elements(node) {
    return [node, ...(node.children || []).flatMap(elements)];
}
function attribute(node, name) {
    return node.props.find(prop => prop.name === name || (prop.name === 'bind' && prop.arg?.content === name));
}

// These are descriptive workflow/confirmation controls, not compact row actions.
const labeledExceptions = new Set(['WorkoutPlan.vue:Edit plan', 'WorkoutPlan.vue:day.lines.length ? \'Edit workout\' : \'Add workout\'', 'WorkoutEditor.vue:Discard', 'StretchingSetList.vue:Delete']);
const navigationExceptions = new Set(['App.vue:pi pi-user', 'Home.vue:pi pi-calendar', 'UrgePause.vue:pi pi-flag', 'NotificationBell.vue:pi pi-bell', 'NotificationBell.vue:pi pi-times']);

test('standardized action source contract prevents legacy record buttons from returning', () => {
    const violations = [];
    for (const file of vueFiles(sourceRoot)) {
        const name = path.basename(file);
        if (['CompactAction.vue', 'ActionButton.vue'].includes(name)) continue;
        const {descriptor} = parse(readFileSync(file, 'utf8'));
        if (!descriptor.template) continue;
        for (const node of elements(parseTemplate(descriptor.template.content))) {
            if (!['Button', 'ActionButton', 'CompactAction'].includes(node.tag)) continue;
            const icon = attribute(node, 'icon');
            const label = attribute(node, 'label');
            const iconValue = icon?.value?.content;
            const labelValue = label?.value?.content || label?.exp?.content;
            if (node.tag === 'CompactAction') {
                if (!icon || !attribute(node, 'aria-label')) violations.push(`${name}: compact actions need an icon and accessible name`);
                if (label) violations.push(`${name}: compact actions must not have visible labels`);
            } else if (['pi pi-pencil', 'pi pi-trash', 'pi pi-star', 'pi pi-arrow-up', 'pi pi-arrow-down', 'pi pi-undo'].includes(iconValue)) {
                if (!labeledExceptions.has(`${name}:${labelValue}`)) violations.push(`${name}: ${iconValue} must use CompactAction`);
            } else if (icon && !label && !navigationExceptions.has(`${name}:${iconValue}`)) {
                violations.push(`${name}: document the specialized icon-only control or use CompactAction`);
            }
        }
    }
    expect(violations).toEqual([]);
});
