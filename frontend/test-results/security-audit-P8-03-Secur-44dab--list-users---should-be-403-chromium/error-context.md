# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: security-audit.spec.ts >> P8-03: Security Authorization Tests >> User Management Endpoints (ADMIN only) >> Assistant cannot list users - should be 403
- Location: e2e\security-audit.spec.ts:50:5

# Error details

```
TypeError: Cannot read properties of undefined (reading 'accessToken')
```

# Test source

```ts
  1   | import { test, expect, request } from '@playwright/test';
  2   | 
  3   | /**
  4   |  * P8-03: Security audit test suite
  5   |  * Verifies that all endpoints reject unauthorized roles with 403 Forbidden
  6   |  */
  7   | 
  8   | const BASE = 'http://localhost:8080/api/v1';
  9   | 
  10  | async function login(username: string, password = 'password123'): Promise<string> {
  11  |   const ctx = await request.newContext();
  12  |   const res = await ctx.post(`${BASE}/auth/login`, {
  13  |     data: { username, password },
  14  |   });
  15  |   const body = await res.json();
  16  |   await ctx.dispose();
> 17  |   return body.data.accessToken as string;
      |                    ^ TypeError: Cannot read properties of undefined (reading 'accessToken')
  18  | }
  19  | 
  20  | async function apiWithStatus(
  21  |   method: 'GET' | 'POST' | 'PUT',
  22  |   path: string,
  23  |   token: string,
  24  |   body?: object
  25  | ): Promise<{ status: number; data: any }> {
  26  |   const ctx = await request.newContext({
  27  |     extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  28  |   });
  29  |   const fn = method === 'GET' ? ctx.get : method === 'PUT' ? ctx.put : ctx.post;
  30  |   const res = await fn.call(ctx, `${BASE}${path}`, body ? { data: body } : undefined);
  31  |   const data = await res.json().catch(() => null);
  32  |   await ctx.dispose();
  33  |   return { status: res.status(), data };
  34  | }
  35  | 
  36  | test.describe('P8-03: Security Authorization Tests', () => {
  37  |   let assistantToken: string;
  38  |   let n1Token: string;
  39  |   let dafToken: string;
  40  |   let auditToken: string;
  41  | 
  42  |   test.beforeAll(async () => {
  43  |     assistantToken = await login('e2e_assistant');
  44  |     n1Token = await login('e2e_n1_drh');
  45  |     dafToken = await login('e2e_daf');
  46  |     auditToken = await login('e2e_auditor');
  47  |   });
  48  | 
  49  |   test.describe('User Management Endpoints (ADMIN only)', () => {
  50  |     test('Assistant cannot list users - should be 403', async () => {
  51  |       const result = await apiWithStatus('GET', '/users', assistantToken);
  52  |       expect(result.status).toBe(403);
  53  |     });
  54  | 
  55  |     test('Assistant cannot create user - should be 403', async () => {
  56  |       const result = await apiWithStatus('POST', '/users', assistantToken, {
  57  |         username: 'test_user',
  58  |         email: 'test@oct.test',
  59  |         password: 'Password123!',
  60  |         firstName: 'Test',
  61  |         lastName: 'User',
  62  |       });
  63  |       expect(result.status).toBe(403);
  64  |     });
  65  | 
  66  |     test('N1 cannot update user - should be 403', async () => {
  67  |       const userId = '00000000-0000-0000-0000-000000000000';
  68  |       const result = await apiWithStatus('PUT', `/users/${userId}`, n1Token, {
  69  |         firstName: 'Updated',
  70  |       });
  71  |       expect(result.status).toBe(403);
  72  |     });
  73  | 
  74  |     test('Auditor cannot delete user - should be 403', async () => {
  75  |       const userId = '00000000-0000-0000-0000-000000000000';
  76  |       const result = await apiWithStatus('POST', `/users/${userId}/deactivate`, auditToken);
  77  |       expect(result.status).toBe(403);
  78  |     });
  79  |   });
  80  | 
  81  |   test.describe('Department Management Endpoints (ADMIN only)', () => {
  82  |     test('Assistant cannot list departments - should be 403', async () => {
  83  |       const result = await apiWithStatus('GET', '/departments', assistantToken);
  84  |       // Departments are readable by all, this might return 200
  85  |       // Let's test update instead
  86  |     });
  87  | 
  88  |     test('Assistant cannot update department approval config - should be 403', async () => {
  89  |       const deptId = '00000000-0000-0000-0000-000000000001';
  90  |       const result = await apiWithStatus('PUT', `/departments/${deptId}`, assistantToken, {
  91  |         requires_n2: true,
  92  |       });
  93  |       expect(result.status).toBe(403);
  94  |     });
  95  | 
  96  |     test('N1 cannot update department - should be 403', async () => {
  97  |       const deptId = '00000000-0000-0000-0000-000000000001';
  98  |       const result = await apiWithStatus('PUT', `/departments/${deptId}`, n1Token, {
  99  |         requires_n2: false,
  100 |       });
  101 |       expect(result.status).toBe(403);
  102 |     });
  103 |   });
  104 | 
  105 |   test.describe('Approval Workflow Endpoints (Role-specific)', () => {
  106 |     test('Assistant cannot validate (N1 action) - should be 403', async () => {
  107 |       const invoiceId = '00000000-0000-0000-0000-000000000002';
  108 |       const result = await apiWithStatus('POST', `/invoices/${invoiceId}/workflow/validate-n1`, assistantToken, {
  109 |         comment: 'Unauthorized attempt',
  110 |       });
  111 |       expect([403, 404]).toContain(result.status); // 404 if invoice doesn't exist, 403 if role check
  112 |     });
  113 | 
  114 |     test('N1 cannot perform BON_A_PAYER (DAF only) - should be 403', async () => {
  115 |       const invoiceId = '00000000-0000-0000-0000-000000000003';
  116 |       const result = await apiWithStatus('POST', `/invoices/${invoiceId}/workflow/bon-a-payer`, n1Token, {
  117 |         comment: 'Unauthorized attempt',
```