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
    assert.ok(daily.required.includes('warningReview'));
    assert.equal(daily.properties.warningReview.$ref, '#/components/schemas/WarningReviewEnvelope');
    assert.equal(body.properties.warningReview.$ref, daily.properties.warningReview.$ref);
    assert.ok(weekly.required.includes('warningReview'));
    assert.equal(weekly.properties.warningReview.$ref, daily.properties.warningReview.$ref);
    assert.ok(weekly.required.includes('confirmed'));
    assert.deepEqual(weekly.properties.confirmed.enum, [true]);
    assert.equal(section.type, 'object');
    assert.deepEqual(section.required, ['summary', 'nextAction']);
    assert.deepEqual(section.properties, legacy.properties);
    assert.equal(section.properties.summary.maxLength, 200);
    assert.equal(section.properties.nextAction.maxLength, 120);
    assert.equal(daily.properties.nextActions.$ref, '#/components/schemas/DailyNextActionList');
    assert.equal(value.components.schemas.DailyNextActionList.items.maxLength, 200);
    assert.deepEqual(legacy.type, ['object', 'null']);
});

test('daily and weekly warning review schema requires a dated complete set and preserves nullable legacy snapshots', () => {
    const value = yaml.load(fs.readFileSync(path.resolve(__dirname, '../../docs/coach/coach-action.openapi.yaml'), 'utf8'));
    const schemas = value.components.schemas;
    assert.deepEqual(schemas.WarningReviewEnvelope.required, ['reviewedDate', 'reviews', 'newWarnings']);
    assert.equal(schemas.WarningReviewEnvelope.properties.reviews.items.$ref, '#/components/schemas/ExistingWarningReview');
    assert.equal(schemas.WarningReviewEnvelope.properties.newWarnings.items.$ref, '#/components/schemas/NewWarningReview');
    assert.deepEqual(schemas.ExistingWarningReview.required, ['id', 'version', 'assessment']);
    assert.deepEqual(schemas.NewWarningReview.required, ['requestKey', 'type', 'assessment']);
    assert.equal(schemas.WarningAssessment.properties.direction.$ref, '#/components/schemas/WarningDirection');
    assert.deepEqual(schemas.WarningDirection.enum,
        ['IMPROVING', 'UNCHANGED', 'WORSENING', 'RESOLVED', 'INSUFFICIENT_EVIDENCE']);
    assert.equal(schemas.Reflection.properties.warningReview.$ref, '#/components/schemas/WarningReviewSnapshot');
    assert.equal(schemas.WarningReviewSnapshot.properties.items.items.$ref, '#/components/schemas/WarningReviewItem');
    assert.equal(schemas.WarningResponse.properties.progress.$ref, '#/components/schemas/WarningProgress');
    assert.equal(schemas.WeeklyReflection.properties.warningReview.$ref, '#/components/schemas/WarningReviewSnapshot');
    assert.deepEqual(schemas.WarningReviewSnapshot.type, ['object', 'null']);
    assert.equal(schemas.WarningReviewItem.properties.createdAt.format, 'date-time');
    for (const name of ['WarningContent', 'ResolveWarningRequest', 'WarningAssessment']) {
        assert.ok(schemas[name].required.includes('metricsReview'));
        assert.equal(schemas[name].properties.metricsReview.$ref, '#/components/schemas/WarningMetricReview');
    }
    assert.ok(!schemas.WarningReadContent.required.includes('metricsReview'));
    assert.equal(schemas.WarningResponse.properties.content.$ref, '#/components/schemas/WarningReadContent');
    assert.deepEqual(schemas.WarningProgress.properties.goalStatus.enum, ['MET', 'NOT_MET', 'UNKNOWN', null]);
    const review = schemas.WarningMetricReview;
    assert.deepEqual(review.required, ['windowDays', 'windowEnd', 'todayComplete', 'shortStatus', 'metrics']);
    assert.equal(review.additionalProperties, false);
    assert.equal(review.properties.windowDays.minimum, 1);
    assert.equal(review.properties.windowDays.maximum, 90);
    assert.equal(review.properties.shortStatus.maxLength, 120);
    assert.equal(review.properties.metrics.minItems, 1);
    assert.equal(review.properties.metrics.maxItems, 12);
    assert.equal(review.properties.metrics.items.$ref, '#/components/schemas/WarningMetric');
    const metric = schemas.WarningMetric;
    assert.deepEqual(metric.properties.aggregation.enum, ['SUM', 'MEAN', 'COUNT', 'LATEST']);
    for (const field of ['value', 'minimum', 'maximum']) {
        assert.equal(metric.properties[field].type, 'number');
        assert.equal(metric.properties[field].nullable, true);
    }
    for (const [field, maximum] of Object.entries({key: 80, label: 60, source: 120, unit: 24, goalBasis: 500})) {
        assert.ok(metric.required.includes(field));
        assert.equal(metric.properties[field].maxLength, maximum);
    }
});


test('reflection schema exposes compact limits and actionable validation errors', () => {
    const value = yaml.load(fs.readFileSync(path.resolve(__dirname, '../../docs/coach/coach-action.openapi.yaml'), 'utf8'));
    const operation = value.paths['/reflections/{date}'].post;
    const body = operation.requestBody.content['application/json'].schema;
    assert.match(body.properties.summary.description, /DAILY maximum 200.*WEEKLY maximum 500/);
    const insights = value.components.schemas.InsightList;
    assert.equal(insights.minItems, 1);
    assert.equal(insights.maxItems, 1);
    assert.equal(insights.items.maxLength, 120);
    assert.equal(body.properties.nextActions.$ref, '#/components/schemas/DailyNextActionList');
    assert.equal(operation.responses['400'].content['application/json'].schema.$ref, '#/components/schemas/ReflectionValidationResponse');
    const errors = value.components.schemas.ReflectionValidationResponse;
    assert.deepEqual(errors.required, ['message']);
    assert.deepEqual(errors.properties.errors.items.required, ['field', 'message']);
    assert.deepEqual(Object.keys(errors.properties.errors.items.properties), ['field', 'message']);
});

test('Coach meal writes default duration and require a food breakdown', () => {
    const value = yaml.load(fs.readFileSync(path.resolve(__dirname, '../../docs/coach/coach-action.openapi.yaml'), 'utf8'));
    const meal = value.components.schemas.MealWrite;
    assert.ok(!meal.required.includes('durationMinutes'));
    assert.equal(meal.properties.durationMinutes.default, 30);
    assert.ok(meal.required.includes('dishes'));
    assert.equal(meal.properties.dishes.minItems, 1);
});

test('Coach meal replacement exposes the required nutrient snapshot version directly', () => {
    const value = yaml.load(fs.readFileSync(path.resolve(__dirname, '../../docs/coach/coach-action.openapi.yaml'), 'utf8'));
    const create = value.components.schemas.MealWrite;
    const replace = value.paths['/coach/meals/{id}'].put.requestBody.content['application/json'].schema;
    assert.ok(create.required.includes('nutrientSchemaVersion'));
    assert.equal(replace.properties.nutrientSchemaVersion.const, 2);
    assert.equal(replace.additionalProperties, false);
    assert.ok(!Object.hasOwn(replace.properties, 'id'));
    assert.match(replace.properties.nutrientSchemaVersion.description, /full meal replacement/i);
    assert.equal(replace.anyOf[0].$ref, '#/components/schemas/MealReplaceWrite');
    assert.equal(value.components.schemas.MealReplaceWrite.properties.dishes.items.$ref,
        '#/components/schemas/CoachMealDishWrite');
    assert.equal(create.properties.dishes.items.$ref, '#/components/schemas/CoachMealDishComplete');
    assert.equal(replace.properties.dishes.items.$ref, '#/components/schemas/CoachMealDishWrite');
    const complete = value.components.schemas.CoachMealDishComplete.allOf[1];
    const reference = value.components.schemas.CoachDishReferenceComplete.allOf[1];
    for (const field of ['omega3Milligrams', 'epaDhaMilligrams', 'alaMilligrams', 'fiberGrams']) {
        assert.ok(complete.required.includes(field));
        assert.equal(complete.properties[field].type, 'number');
        assert.ok(reference.required.includes(field));
        assert.equal(reference.properties[field].type, 'number');
    }
    assert.deepEqual(value.components.schemas.CoachMealDishWrite.properties.fiberGrams.type, ['number', 'null']);
});

test('manual and legacy food transport permits independent nullable total Omega-3', () => {
    const value = yaml.load(fs.readFileSync(path.resolve(__dirname, '../../docs/coach/coach-action.openapi.yaml'), 'utf8'));
    const schemas = value.components.schemas;
    for (const name of ['DishReferenceWrite', 'MealDishWrite', 'CoachMealDishWrite', 'CoachDishRecipeIngredient']) {
        const shape = name === 'DishReferenceWrite' ? schemas[name].allOf[1] : schemas[name];
        assert.ok(!shape.required.includes('omega3Milligrams'), `${name} still requires legacy total Omega`);
        assert.deepEqual(shape.properties.omega3Milligrams.type, ['number', 'null']);
        assert.ok(shape.required.includes('epaDhaMilligrams'));
    }
});

test('completed workout read and replacement accept exact snapshot seconds and legacy nulls', () => {
    const value = yaml.load(fs.readFileSync(path.resolve(__dirname, '../../docs/coach/coach-action.openapi.yaml'), 'utf8'));
    const schemas = value.components.schemas;
    const snapshot = schemas.RecordedPlanTargetSnapshot;
    for (const name of ['CoachWorkoutRecord', 'CoachWorkoutWrite']) {
        assert.equal(schemas[name].properties.plannedTargets.items.$ref, '#/components/schemas/RecordedPlanTargetSnapshot');
    }
    assert.equal(snapshot.properties.segments.items.$ref, '#/components/schemas/RecordedPlanSegment');
    const seconds = schemas.RecordedPlanSegment.properties.durationSeconds;
    assert.ok(93 >= seconds.minimum && (seconds.multipleOf === undefined || 93 % seconds.multipleOf === 0));
    assert.deepEqual(schemas.CoachWorkoutRecord.properties.saunaRoundsMinutes.type, ['array', 'null']);
    assert.deepEqual(snapshot.properties.exerciseDescription.type, ['string', 'null']);
    const planRead = schemas.WorkoutPlanEditContext;
    const cardioValues = ['SPEED_KPH', 'CADENCE_RPM', null];
    assert.deepEqual(planRead.properties.exercises.items.properties.cardioMetric.enum, cardioValues);
    const planSession = schemas.WorkoutPlanResponse.properties.days.items.properties.sessions.items;
    assert.ok(planSession.properties.saunaSession && planSession.properties.saunaRoundsMinutes);
    assert.deepEqual(planSession.properties.lines.items.properties.cardioMetric.enum, cardioValues);
});

test('workout exercise lookup is a bounded plan-free branch of the existing Action', () => {
    const value = yaml.load(fs.readFileSync(path.resolve(__dirname, '../../docs/coach/coach-action.openapi.yaml'), 'utf8'));
    const operation = value.paths['/coach/active-plan'].get;
    const query = operation.parameters.find(parameter => parameter.name === 'exerciseQuery');
    assert.equal(query.in, 'query');
    assert.equal(query.required, false);
    assert.equal(query.schema.minLength, 2);
    assert.ok(operation.responses['200'].content['application/json'].schema.anyOf.some(item => item.$ref === '#/components/schemas/WorkoutExerciseSearchResult'));
    const result = value.components.schemas.WorkoutExerciseSearchResult;
    assert.deepEqual(result.required, ['query', 'exercises']);
    assert.deepEqual(Object.keys(result.properties), ['query', 'exercises']);
    assert.equal(result.properties.exercises.maxItems, 20);
    assert.equal(result.properties.exercises.items.$ref, '#/components/schemas/WorkoutPlanExerciseChoice');
});

test('weekly nutrition remains optional for legacy snapshots and freezes every comparison period', () => {
    const value = yaml.load(fs.readFileSync(path.resolve(__dirname, '../../docs/coach/coach-action.openapi.yaml'), 'utf8'));
    const models = value.components.schemas;
    assert.ok(!models.WeeklySummarySnapshot.required.includes('nutrition'));
    assert.deepEqual(models.WeeklySummarySnapshot.properties.nutrition.oneOf, [
        {$ref: '#/components/schemas/WeeklyNutritionSnapshot'}, {type: 'null'}
    ]);
    assert.deepEqual(models.WeeklyNutritionSnapshot.required, ['capturedAt', 'targetLabel', 'targets', 'current', 'previous', 'yearAgo']);
    for (const period of ['current', 'previous', 'yearAgo']) assert.equal(models.WeeklyNutritionSnapshot.properties[period].$ref, '#/components/schemas/WeeklyNutritionPeriod');
    for (const property of ['days', 'datedTargets', 'measures', 'completedDays', 'recordedDays']) assert.ok(models.WeeklyNutritionPeriod.required.includes(property));
    assert.equal(models.WeeklyNutritionPeriod.properties.days.items.$ref, '#/components/schemas/NutritionDailyTotal');
    assert.ok(models.WeeklyNutritionPeriod.properties.datedTargets.items.required.includes('fatPercentage'));
    assert.ok(models.WeeklyNutritionMeasure.required.includes('eligibleDays'));
    assert.ok(models.WeeklyNutritionMeasure.required.includes('estimatedFoods'));
    assert.ok(models.WeeklyNutritionMeasure.required.includes('unavailableReason'));
    assert.equal(models.WeeklySummarySnapshot.properties.personalRecords.items.properties.subjectLabel.type[1], 'null');
});
