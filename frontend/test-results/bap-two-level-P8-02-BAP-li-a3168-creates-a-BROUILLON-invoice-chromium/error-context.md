# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: bap-two-level.spec.ts >> P8-02: BAP lifecycle - two-level department (Informatique) >> Step 1: assistant creates a BROUILLON invoice
- Location: e2e\bap-two-level.spec.ts:58:3

# Error details

```
Error: Login failed for e2e_assistant: 401 {"success":false,"message":"Authentication failed: Les identifications sont erronées","timestamp":"2026-04-12T15:12:09.033398075Z"}
```

# Test source

```ts
  1   | import { test, expect, request } from '@playwright/test';
  2   | 
  3   | /**
  4   |  * P8-02: End-to-end BAP lifecycle for a two-level department (Informatique).
  5   |  *
  6   |  * Strategy: drive the workflow via direct API calls rather than full UI forms.
  7   |  * Tests both N1 and N2 approval stages.
  8   |  */
  9   | 
  10  | const BASE = 'http://localhost:8080/api/v1';
  11  | 
  12  | async function login(username: string, password = 'password123'): Promise<string> {
  13  |   const ctx = await request.newContext();
  14  |   const res = await ctx.post(`${BASE}/auth/login`, {
  15  |     data: { username, password },
  16  |   });
  17  |   if (!res.ok()) {
> 18  |     throw new Error(`Login failed for ${username}: ${res.status()} ${await res.text()}`);
      |           ^ Error: Login failed for e2e_assistant: 401 {"success":false,"message":"Authentication failed: Les identifications sont erronées","timestamp":"2026-04-12T15:12:09.033398075Z"}
  19  |   }
  20  |   const body = await res.json();
  21  |   await ctx.dispose();
  22  |   return body.data.accessToken as string;
  23  | }
  24  | 
  25  | async function api(method: 'GET' | 'POST' | 'PUT', path: string, token: string, body?: object) {
  26  |   const ctx = await request.newContext({
  27  |     extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  28  |   });
  29  |   const fn = method === 'GET' ? ctx.get : method === 'PUT' ? ctx.put : ctx.post;
  30  |   const res = await fn.call(ctx, `${BASE}${path}`, body ? { data: body } : undefined);
  31  |   const data = await res.json().catch(() => null);
  32  |   await ctx.dispose();
  33  |   if (!res.ok()) {
  34  |     throw new Error(`${method} ${path} -> ${res.status()}: ${JSON.stringify(data)}`);
  35  |   }
  36  |   return data;
  37  | }
  38  | 
  39  | async function getInvoiceStatus(invoiceId: string, token: string): Promise<string> {
  40  |   const res = await api('GET', `/invoices/${invoiceId}`, token);
  41  |   return res.data.status as string;
  42  | }
  43  | 
  44  | test.describe('P8-02: BAP lifecycle - two-level department (Informatique)', () => {
  45  |   let invoiceId: string;
  46  |   let assistantToken: string;
  47  |   let n1Token: string;
  48  |   let n2Token: string;
  49  |   let dafToken: string;
  50  | 
  51  |   test.beforeAll(async () => {
  52  |     assistantToken = await login('e2e_assistant');
  53  |     n1Token = await login('e2e_n1_info');
  54  |     n2Token = await login('e2e_n2_info');
  55  |     dafToken = await login('e2e_daf');
  56  |   });
  57  | 
  58  |   test('Step 1: assistant creates a BROUILLON invoice', async () => {
  59  |     const deptRes = await api('GET', '/departments', assistantToken);
  60  |     const departments = Array.isArray(deptRes.data) ? deptRes.data : deptRes.data.content;
  61  |     const infoDept = departments.find((d: { code: string }) => d.code === 'INFO');
  62  |     expect(infoDept, 'INFO department must exist').toBeTruthy();
  63  |     expect(infoDept.requires_n2, 'INFO must require N2').toBe(true);
  64  | 
  65  |     const created = await api('POST', '/invoices', assistantToken, {
  66  |       supplierName: 'E2E Supplier Two-Level',
  67  |       supplierEmail: 'e2e2@supplier.test',
  68  |       departmentId: infoDept.id,
  69  |       amount: 75000,
  70  |       currency: 'XAF',
  71  |       issueDate: '2026-01-01',
  72  |       dueDate: '2026-02-01',
  73  |       description: 'E2E P8-02 test invoice (two-level)',
  74  |     });
  75  | 
  76  |     invoiceId = created.data.id;
  77  |     expect(invoiceId).toBeTruthy();
  78  |     expect(created.data.status).toBe('BROUILLON');
  79  |   });
  80  | 
  81  |   test('Step 2: assistant uploads a document', async () => {
  82  |     const ctx = await request.newContext({
  83  |       extraHTTPHeaders: { Authorization: `Bearer ${assistantToken}` },
  84  |     });
  85  |     const pdfBytes = Buffer.from('%PDF-1.4 minimal');
  86  |     const res = await ctx.post(`${BASE}/invoices/${invoiceId}/documents`, {
  87  |       multipart: {
  88  |         file: {
  89  |           name: 'e2e-invoice-2level.pdf',
  90  |           mimeType: 'application/pdf',
  91  |           buffer: pdfBytes,
  92  |         },
  93  |       },
  94  |     });
  95  |     const body = await res.json().catch(() => null);
  96  |     await ctx.dispose();
  97  |     expect(res.ok(), `Document upload failed: ${res.status()} ${JSON.stringify(body)}`).toBeTruthy();
  98  |   });
  99  | 
  100 |   test('Step 3: assistant submits -> SOUMIS', async () => {
  101 |     await api('POST', `/invoices/${invoiceId}/submit`, assistantToken);
  102 |     expect(await getInvoiceStatus(invoiceId, assistantToken)).toBe('SOUMIS');
  103 |   });
  104 | 
  105 |   test('Step 4: N1 reviewer assigns self -> EN_VALIDATION_N1', async () => {
  106 |     await api('POST', `/invoices/${invoiceId}/workflow/assign`, n1Token);
  107 |     expect(await getInvoiceStatus(invoiceId, n1Token)).toBe('EN_VALIDATION_N1');
  108 |   });
  109 | 
  110 |   test('Step 5: N1 validates -> EN_VALIDATION_N2', async () => {
  111 |     // For a two-level department, N1 validation should transition to EN_VALIDATION_N2
  112 |     await api('POST', `/invoices/${invoiceId}/workflow/validate-n1`, n1Token, {
  113 |       comment: 'Conforme - validation N1 (passerelle N2)',
  114 |     });
  115 |     expect(await getInvoiceStatus(invoiceId, n1Token)).toBe('EN_VALIDATION_N2');
  116 |   });
  117 | 
  118 |   test('Step 6: N2 reviewer assigns self and validates -> VALIDE', async () => {
```