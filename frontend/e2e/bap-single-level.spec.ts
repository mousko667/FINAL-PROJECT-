import { test, expect, request } from '@playwright/test';

/**
 * P8-01: End-to-end BAP lifecycle for a single-level department (DRH).
 *
 * Strategy: drive the workflow via direct API calls rather than full UI forms.
 * The UI is only used for a final smoke check after the workflow completes.
 */

const BASE = 'http://localhost:8080/api/v1';

async function login(username: string, password = 'password123'): Promise<string> {
  const ctx = await request.newContext();
  const res = await ctx.post(`${BASE}/auth/login`, {
    data: { username, password },
  });
  if (!res.ok()) {
    throw new Error(`Login failed for ${username}: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  await ctx.dispose();
  return body.data.accessToken as string;
}

async function api(method: 'GET' | 'POST' | 'PUT', path: string, token: string, body?: object) {
  const ctx = await request.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });
  const fn = method === 'GET' ? ctx.get : method === 'PUT' ? ctx.put : ctx.post;
  const res = await fn.call(ctx, `${BASE}${path}`, body ? { data: body } : undefined);
  const data = await res.json().catch(() => null);
  await ctx.dispose();
  if (!res.ok()) {
    throw new Error(`${method} ${path} -> ${res.status()}: ${JSON.stringify(data)}`);
  }
  return data;
}

async function getInvoiceStatus(invoiceId: string, token: string): Promise<string> {
  const res = await api('GET', `/invoices/${invoiceId}`, token);
  return res.data.status as string;
}

test.describe('P8-01: BAP lifecycle - single-level department (DRH)', () => {
  let invoiceId: string;
  let assistantToken: string;
  let n1Token: string;
  let dafToken: string;

  test.beforeAll(async () => {
    assistantToken = await login('e2e_assistant');
    n1Token = await login('e2e_n1_drh');
    dafToken = await login('e2e_daf');
  });

  test('Step 1: assistant creates a BROUILLON invoice', async () => {
    const deptRes = await api('GET', '/departments', assistantToken);
    const departments = Array.isArray(deptRes.data) ? deptRes.data : deptRes.data.content;
    const drhDept = departments.find((d: { code: string }) => d.code === 'DRH');
    expect(drhDept, 'DRH department must exist').toBeTruthy();

    const created = await api('POST', '/invoices', assistantToken, {
      supplierName: 'E2E Supplier',
      supplierEmail: 'e2e@supplier.test',
      departmentId: drhDept.id,
      amount: 50000,
      currency: 'XAF',
      issueDate: '2026-01-01',
      dueDate: '2026-02-01',
      description: 'E2E P8-01 test invoice',
    });

    invoiceId = created.data.id;
    expect(invoiceId).toBeTruthy();
    expect(created.data.status).toBe('BROUILLON');
  });

  test('Step 2: assistant uploads a document', async () => {
    const ctx = await request.newContext({
      extraHTTPHeaders: { Authorization: `Bearer ${assistantToken}` },
    });
    const pdfBytes = Buffer.from('%PDF-1.4 minimal');
    const res = await ctx.post(`${BASE}/invoices/${invoiceId}/documents`, {
      multipart: {
        file: {
          name: 'e2e-invoice.pdf',
          mimeType: 'application/pdf',
          buffer: pdfBytes,
        },
      },
    });
    const body = await res.json().catch(() => null);
    await ctx.dispose();
    expect(res.ok(), `Document upload failed: ${res.status()} ${JSON.stringify(body)}`).toBeTruthy();
  });

  test('Step 3: assistant submits -> SOUMIS', async () => {
    await api('POST', `/invoices/${invoiceId}/submit`, assistantToken);
    expect(await getInvoiceStatus(invoiceId, assistantToken)).toBe('SOUMIS');
  });

  test('Step 4: N1 reviewer assigns self -> EN_VALIDATION_N1', async () => {
    await api('POST', `/invoices/${invoiceId}/workflow/assign`, n1Token);
    expect(await getInvoiceStatus(invoiceId, n1Token)).toBe('EN_VALIDATION_N1');
  });

  test('Step 5: N1 validates -> VALIDE', async () => {
    await api('POST', `/invoices/${invoiceId}/workflow/validate-n1`, n1Token, {
      comment: 'Conforme - validation N1 E2E',
    });
    expect(await getInvoiceStatus(invoiceId, n1Token)).toBe('VALIDE');
  });

  test('Step 6: DAF approves BON_A_PAYER -> BON_A_PAYER', async () => {
    await api('POST', `/invoices/${invoiceId}/workflow/bon-a-payer`, dafToken, {
      comment: 'BAP accorde - E2E',
    });
    expect(await getInvoiceStatus(invoiceId, dafToken)).toBe('BON_A_PAYER');
  });

  test('Step 7: verify final status in the UI', async ({ page }) => {
    await page.goto('/login');
    await page.fill('#username', 'e2e_daf');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });

    await page.goto(`/invoices/${invoiceId}`);
    const statusBadge = page.locator('[data-testid="invoice-status"]');
    await expect(statusBadge).toBeVisible({ timeout: 10000 });
    await expect(statusBadge).toHaveAttribute('data-status', 'BON_A_PAYER');
  });
});
