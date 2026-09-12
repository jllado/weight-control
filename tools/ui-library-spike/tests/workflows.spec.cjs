const {test, expect} = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const evidence = path.resolve(__dirname, '../../../tmp/ui-library-spike/evidence');
const photo = {name: 'synthetic.svg', mimeType: 'image/svg+xml', buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="64" height="80"><rect width="64" height="80" fill="#007ad9"/></svg>')};
async function openForm(page) { await page.getByRole('button', {name: 'New', exact: true}).click(); return page.getByRole('dialog', {name: 'Weight', exact: true}); }
async function selectTag(page) {
  await page.locator('.p-multiselect').click();
  await page.getByRole('option', {name: 'At home', exact: true}).click();
  await page.locator('.p-multiselect').click();
  await expect(page.getByRole('listbox')).toBeHidden();
}
for (const width of [390, 575, 640, 960, 1280]) {
  test(`layout and interactions at ${width}px`, async ({page}, testInfo) => {
    fs.mkdirSync(evidence, {recursive: true});
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.setViewportSize({width, height: 900});
    await page.goto('/');
    await expect(page.getByRole('cell', {name: 'Morning', exact: true})).toBeVisible();
    await page.getByLabel('Filter history').fill('no-match');
    await expect(page.getByText('No measurements found.')).toBeVisible();
    await page.getByLabel('Filter history').fill('');
    const capture = async name => {
      const overflow = await page.evaluate(() => [...document.querySelectorAll('body *')].filter(element => element.getBoundingClientRect().right > innerWidth + 1 && getComputedStyle(element).position !== 'absolute').map(element => ({tag: element.tagName, class: element.className, right: element.getBoundingClientRect().right})));
      fs.writeFileSync(path.join(evidence, `${testInfo.project.name}-${name}-${width}-overflow.json`), JSON.stringify(overflow, null, 2));
      await page.screenshot({path: path.join(evidence, `${testInfo.project.name}-${name}-${width}.png`), fullPage: true, animations: 'disabled'});
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
    };
    await capture('history');
    await page.getByRole('button', {name: 'Show history information'}).click();
    await expect(page.getByText('Measurements use local calendar dates and kilograms.')).toBeVisible();
    await page.keyboard.press('Escape');
    const dialog = await openForm(page);
    await dialog.getByLabel('Weight (kg)', {exact: true}).fill('79.25');
    await dialog.locator('input[type=file]').setInputFiles(photo);
    await expect(dialog.getByRole('img', {name: 'Selected front photo'})).toHaveJSProperty('naturalWidth', 64);
    await selectTag(page);
    await capture('form');
    await dialog.getByLabel('Date', {exact: true}).click();
    await expect(page.getByRole('grid')).toBeVisible();
    await capture('date');
    await page.keyboard.press('Escape');
    await dialog.getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(page.getByRole('button', {name: 'New', exact: true})).toBeFocused();
    await page.getByRole('tab', {name: 'Charts', exact: true}).click();
    await expect(page.getByRole('img', {name: /Weight decreased/})).toBeVisible();
    await capture('chart');
    await page.getByRole('tab', {name: 'Workout', exact: true}).click();
    await capture('workout');
    const metrics = await page.getByRole('tab', {name: 'History', exact: true}).evaluate(element => {
      const style = getComputedStyle(element), box = element.getBoundingClientRect();
      return {height: box.height, color: style.color, background: style.backgroundColor, radius: style.borderRadius, font: style.fontSize};
    });
    fs.writeFileSync(path.join(evidence, `${testInfo.project.name}-${width}.json`), JSON.stringify({width, errors, metrics}, null, 2));
    expect(errors).toEqual([]);
  });
}
test('validation, failed save, pending controls, and retry preserve the draft', async ({page}) => {
  await page.goto('/');
  const dialog = await openForm(page);
  await dialog.getByRole('button', {name: 'Save', exact: true}).click();
  await expect(dialog.getByRole('alert')).toContainText('Enter a date');
  await selectTag(page);
  await dialog.getByLabel('Weight (kg)', {exact: true}).fill('79.25');
  await dialog.locator('input[type=file]').setInputFiles(photo);
  await dialog.getByLabel('Simulate a failed save').check();
  const writes = [];
  page.on('request', request => { if (request.url().includes('/simulation/measurements')) writes.push(request.postDataJSON()); });
  await dialog.getByRole('button', {name: 'Save', exact: true}).click();
  await expect(dialog.getByRole('button', {name: 'Saving…', exact: true})).toBeDisabled();
  await expect(dialog.getByRole('button', {name: 'Saving…', exact: true})).toHaveAttribute('aria-busy', 'true');
  await expect(dialog.getByRole('button', {name: 'Cancel', exact: true})).toBeDisabled();
  await expect(dialog.locator('fieldset')).toHaveAttribute('inert', '');
  await expect(dialog.getByLabel('Weight (kg)', {exact: true})).toBeDisabled();
  await page.keyboard.press('Escape');
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('Could not save. Your draft has been retained.')).toBeVisible();
  await expect(dialog.getByLabel('Weight (kg)', {exact: true})).toHaveValue('79.25');
  await expect(dialog.getByRole('img', {name: 'Selected front photo'})).toHaveJSProperty('naturalWidth', 64);
  expect(writes).toHaveLength(1);
  await dialog.getByLabel('Simulate a failed save').uncheck();
  await dialog.getByRole('button', {name: 'Save', exact: true}).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByText('Saved measurements: 1')).toBeVisible();
  expect(writes).toHaveLength(2);
  expect(writes[1].weight).toBe(79.25);
  expect(writes[1].tags).toEqual(['At home']);
});
test('menu keyboard operation and deferred chart loading', async ({page}) => {
  const scripts = [];
  page.on('request', request => { if (request.resourceType() === 'script') scripts.push(request.url()); });
  await page.goto('/');
  expect(scripts.some(url => url.includes('TrendChart'))).toBe(false);
  await page.getByRole('button', {name: 'More', exact: true}).focus();
  await page.keyboard.press('Enter');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await expect(page.getByText('Saved measurements', {exact: true})).toBeVisible();
  await page.getByRole('tab', {name: 'Charts', exact: true}).click();
  await expect(page.locator('canvas')).toBeVisible();
  expect(scripts.some(url => url.includes('TrendChart'))).toBe(true);
});

test('ordered selection and keyboard tabs retain their meaning', async ({page}) => {
  await page.goto('/');
  await page.getByRole('tab', {name: 'History', exact: true}).focus();
  await page.keyboard.press('ArrowRight');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('region', {name: 'Workout selection'})).toBeVisible();
  await page.getByRole('option', {name: 'Stretch', exact: true}).click();
  await page.getByRole('button', {name: 'Move Up', exact: true}).click();
  await expect(page.getByText('Selected: Stretch, Squat', {exact: true})).toBeVisible();
  await page.getByRole('option', {name: 'Walk', exact: true}).click();
  await page.getByRole('button', {name: 'Move to Target', exact: true}).click();
  await expect(page.getByText('Selected: Stretch, Squat, Walk', {exact: true})).toBeVisible();
});

test('wide history scrolls and date/period controls preserve submitted values', async ({page}) => {
  await page.setViewportSize({width: 390, height: 900});
  await page.goto('/');
  const table = page.locator('.p-datatable-wrapper, .p-datatable-table-container');
  expect(await table.evaluate(element => { element.scrollLeft = 200; return element.scrollLeft; })).toBeGreaterThan(0);
  const dialog = await openForm(page);
  await selectTag(page);
  await dialog.getByLabel('Date', {exact: true}).fill('');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('grid')).toBeHidden();
  await dialog.getByRole('button', {name: 'Save', exact: true}).click();
  await expect(dialog.getByRole('alert')).toContainText('Enter a date');
  await dialog.getByLabel('Date', {exact: true}).fill('12/09/2026');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('grid')).toBeHidden();
  await page.keyboard.press('Tab');
  await dialog.locator('.p-dropdown, .p-select').click();
  await page.getByRole('option', {name: 'Evening', exact: true}).click();
  const request = page.waitForRequest('**/simulation/measurements');
  await dialog.getByRole('button', {name: 'Save', exact: true}).click();
  const body = (await request).postDataJSON();
  expect(body.period).toBe('Evening');
  expect(await page.evaluate(value => { const date = new Date(value); return [date.getFullYear(), date.getMonth() + 1, date.getDate()]; }, body.date)).toEqual([2026, 9, 12]);
  await expect(dialog).toBeHidden();
});
