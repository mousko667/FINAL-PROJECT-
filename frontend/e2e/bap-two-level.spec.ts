import { test, expect, request } from '@playwright/test';

/**
 * P8-02: End-to-end BAP lifecycle for a two-level department (Informatique).
 *
 * Strategy: drive the workflow via direct API calls rather than full UI forms.
 * Tests both N1 and N2 approval stages.
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

test.describe('P8-02: BAP lifecycle - two-level department (Informatique)', () => {
  let invoiceId: string;
  let assistantToken: string;
  let n1Token: string;
  let n2Token: string;
  let dafToken: string;

  test.beforeAll(async () => {
    assistantToken = await login('e2e_assistant');
    n1Token = await login('e2e_n1_info');
    n2Token = await login('e2e_n2_info');
    dafToken = await login('e2e_daf');
  });

  test('Step 1: assistant creates a BROUILLON invoice', async () => {
    const deptRes = await api('GET', '/departments', assistantToken);
    const departments = Array.isArray(deptRes.data) ? deptRes.data : deptRes.data.content;
    const infoDept = departments.find((d: { code: string }) => d.code === 'INFO');
    expect(infoDept, 'INFO department must exist').toBeTruthy();
    expect(infoDept.requires_n2, 'INFO must require N2').toBe(true);

    const created = await api('POST', '/invoices', assistantToken, {
      supplierName: 'E2E Supplier Two-Level',
      supplierEmail: 'e2e2@supplier.test',
      departmentId: infoDept.id,
      amount: 75000,
      currency: 'XAF',
      issueDate: '2026-01-01',
      dueDate: '2026-02-01',
      description: 'E2E P8-02 test invoice (two-level)',
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
          name: 'e2e-invoice-2level.pdf',
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

  test('Step 5: N1 validates -> EN_VALIDATION_N2', async () => {
    // For a two-level department, N1 validation should transition to EN_VALIDATION_N2
    await api('POST', `/invoices/${invoiceId}/workflow/validate-n1`, n1Token, {
      comment: 'Conforme - validation N1 (passerelle N2)',
    });
    expect(await getInvoiceStatus(invoiceId, n1Token)).toBe('EN_VALIDATION_N2');
  });

  test('Step 6: N2 reviewer assigns self and validates -> VALIDE', async () => {
    // N2 should be able to assign and then validate
    await api('POST', `/invoices/${invoiceId}/workflow/assign`, n2Token);
    await api('POST', `/invoices/${invoiceId}/workflow/validate-n2`, n2Token, {
      comment: 'Conforme - validation N2',
    });
    expect(await getInvoiceStatus(invoiceId, n2Token)).toBe('VALIDE');
  });

  test('Step 7: DAF approves BON_A_PAYER -> BON_A_PAYER', async () => {
    await api('POST', `/invoices/${invoiceId}/workflow/bon-a-payer`, dafToken, {
      comment: 'BAP accorde - E2E two-level',
    });
    expect(await getInvoiceStatus(invoiceId, dafToken)).toBe('BON_A_PAYER');
  });

  test('Step 8: verify final status in the UI', async ({ page }) => {
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
