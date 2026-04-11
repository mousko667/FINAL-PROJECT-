import { chromium } from 'playwright';

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  page.on('console', msg => {
    if (msg.type() === 'error') {
      console.log(`PAGE ERROR: ${msg.text()}`);
    }
  });
  page.on('pageerror', exception => {
    console.log(`UNCATCHED EXCEPTION: ${exception}`);
  });
  
  await page.goto('http://localhost:3000/login');
  await page.waitForTimeout(2000);
  await browser.close();
})();
