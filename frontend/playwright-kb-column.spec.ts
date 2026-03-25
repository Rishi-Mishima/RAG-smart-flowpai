import { test, expect } from '@playwright/test';

const token = process.env.PAI_TOKEN || '';

test('knowledge base column visibility', async ({ page }) => {
  await page.goto('http://127.0.0.1:9529/login', { waitUntil: 'domcontentloaded' });
  await page.evaluate(value => {
    localStorage.setItem('token', value);
  }, token);

  await page.goto('http://127.0.0.1:9529/knowledge-base', { waitUntil: 'networkidle' });
  await expect(page.getByText('文件列表')).toBeVisible();
  await expect(page.getByText('向量化消耗')).toBeVisible();

  const tableText = await page.locator('.n-data-table').innerText();
  console.log(tableText);
});
