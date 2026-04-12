# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: bap-single-level.spec.ts >> P8-01: BAP lifecycle - single-level department (DRH) >> Step 1: assistant creates a BROUILLON invoice
- Location: e2e\bap-single-level.spec.ts:56:3

# Error details

```
Error: Login failed for e2e_assistant: 401 {"success":false,"message":"Authentication failed: Les identifications sont erronées","timestamp":"2026-04-12T15:12:07.652008785Z"}
```

# Test source

```ts
  1   | import { test, expect, request } from '@playwright/test';
  2   | 
  3   | /**
  4   |  * P8-01: End-to-end BAP lifecycle for a single-level department (DRH).
  5   |  *
  6   |  * Strategy: drive the workflow via direct API calls rather than full UI forms.
  7   |  * The UI is only used for a final smoke check after the workflow completes.
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
      |           ^ Error: Login failed for e2e_assistant: 401 {"success":false,"message":"Authentication failed: Les identifications sont erronées","timestamp":"2026-04-12T15:12:07.652008785Z"}
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
  44  | test.describe('P8-01: BAP lifecycle - single-level department (DRH)', () => {
  45  |   let invoiceId: string;
  46  |   let assistantToken: string;
  47  |   let n1Token: string;
  48  |   let dafToken: string;
  49  | 
  50  |   test.beforeAll(async () => {
  51  |     assistantToken = await login('e2e_assistant');
  52  |     n1Token = await login('e2e_n1_drh');
  53  |     dafToken = await login('e2e_daf');
  54  |   });
  55  | 
  56  |   test('Step 1: assistant creates a BROUILLON invoice', async () => {
  57  |     const deptRes = await api('GET', '/departments', assistantToken);
  58  |     const departments = Array.isArray(deptRes.data) ? deptRes.data : deptRes.data.content;
  59  |     const drhDept = departments.find((d: { code: string }) => d.code === 'DRH');
  60  |     expect(drhDept, 'DRH department must exist').toBeTruthy();
  61  | 
  62  |     const created = await api('POST', '/invoices', assistantToken, {
  63  |       supplierName: 'E2E Supplier',
  64  |       supplierEmail: 'e2e@supplier.test',
  65  |       departmentId: drhDept.id,
  66  |       amount: 50000,
  67  |       currency: 'XAF',
  68  |       issueDate: '2026-01-01',
  69  |       dueDate: '2026-02-01',
  70  |       description: 'E2E P8-01 test invoice',
  71  |     });
  72  | 
  73  |     invoiceId = created.data.id;
  74  |     expect(invoiceId).toBeTruthy();
  75  |     expect(created.data.status).toBe('BROUILLON');
  76  |   });
  77  | 
  78  |   test('Step 2: assistant uploads a document', async () => {
  79  |     const ctx = await request.newContext({
  80  |       extraHTTPHeaders: { Authorization: `Bearer ${assistantToken}` },
  81  |     });
  82  |     const pdfBytes = Buffer.from('%PDF-1.4 minimal');
  83  |     const res = await ctx.post(`${BASE}/invoices/${invoiceId}/documents`, {
  84  |       multipart: {
  85  |         file: {
  86  |           name: 'e2e-invoice.pdf',
  87  |           mimeType: 'application/pdf',
  88  |           buffer: pdfBytes,
  89  |         },
  90  |       },
  91  |     });
  92  |     const body = await res.json().catch(() => null);
  93  |     await ctx.dispose();
  94  |     expect(res.ok(), `Document upload failed: ${res.status()} ${JSON.stringify(body)}`).toBeTruthy();
  95  |   });
  96  | 
  97  |   test('Step 3: assistant submits -> SOUMIS', async () => {
  98  |     await api('POST', `/invoices/${invoiceId}/submit`, assistantToken);
  99  |     expect(await getInvoiceStatus(invoiceId, assistantToken)).toBe('SOUMIS');
  100 |   });
  101 | 
  102 |   test('Step 4: N1 reviewer assigns self -> EN_VALIDATION_N1', async () => {
  103 |     await api('POST', `/invoices/${invoiceId}/workflow/assign`, n1Token);
  104 |     expect(await getInvoiceStatus(invoiceId, n1Token)).toBe('EN_VALIDATION_N1');
  105 |   });
  106 | 
  107 |   test('Step 5: N1 validates -> VALIDE', async () => {
  108 |     await api('POST', `/invoices/${invoiceId}/workflow/validate-n1`, n1Token, {
  109 |       comment: 'Conforme - validation N1 E2E',
  110 |     });
  111 |     expect(await getInvoiceStatus(invoiceId, n1Token)).toBe('VALIDE');
  112 |   });
  113 | 
  114 |   test('Step 6: DAF approves BON_A_PAYER -> BON_A_PAYER', async () => {
  115 |     await api('POST', `/invoices/${invoiceId}/workflow/bon-a-payer`, dafToken, {
  116 |       comment: 'BAP accorde - E2E',
  117 |     });
  118 |     expect(await getInvoiceStatus(invoiceId, dafToken)).toBe('BON_A_PAYER');
```