// Probe the og JS port with the 26-item alphabet list to see how it
// avoids the white-leak when content overflows the viewport.
import { chromium } from 'playwright';

const URL_26 = 'https://avidrucker.github.io/pwa-autofocus-app/?list=JTVCJTdCJTIyaWQlMjIlM0EwJTJDJTIydGV4dCUyMiUzQSUyMmElMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJyZWFkeSUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMSUyQyUyMnRleHQlMjIlM0ElMjJiJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0EyJTJDJTIydGV4dCUyMiUzQSUyMmMlMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlMkMlN0IlMjJpZCUyMiUzQTMlMkMlMjJ0ZXh0JTIyJTNBJTIyZCUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBNCUyQyUyMnRleHQlMjIlM0ElMjJlJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0E1JTJDJTIydGV4dCUyMiUzQSUyMmYlMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlMkMlN0IlMjJpZCUyMiUzQTYlMkMlMjJ0ZXh0JTIyJTNBJTIyZyUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBNyUyQyUyMnRleHQlMjIlM0ElMjJoJTIyJTJDJTIyc3RhdHVzJTIyJTNBJTIybmV3JTIyJTdEJTJDJTdCJTIyaWQlMjIlM0E4JTJDJTIydGV4dCUyMiUzQSUyMmklMjIlMkMlMjJzdGF0dXMlMjIlM0ElMjJuZXclMjIlN0QlMkMlN0IlMjJpZCUyMiUzQTklMkMlMjJ0ZXh0JTIyJTNBJTIyaiUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMTAlMkMlMjJ0ZXh0JTIyJTNBJTIyayUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMTElMkMlMjJ0ZXh0JTIyJTNBJTIybCUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMTIlMkMlMjJ0ZXh0JTIyJTNBJTIybSUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMTMlMkMlMjJ0ZXh0JTIyJTNBJTIybiUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMTQlMkMlMjJ0ZXh0JTIyJTNBJTIybyUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMTUlMkMlMjJ0ZXh0JTIyJTNBJTIycCUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMTYlMkMlMjJ0ZXh0JTIyJTNBJTIycSUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMTclMkMlMjJ0ZXh0JTIyJTNBJTIyciUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMTglMkMlMjJ0ZXh0JTIyJTNBJTIycyUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMTklMkMlMjJ0ZXh0JTIyJTNBJTIydCUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMjAlMkMlMjJ0ZXh0JTIyJTNBJTIydSUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMjElMkMlMjJ0ZXh0JTIyJTNBJTIydiUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMjIlMkMlMjJ0ZXh0JTIyJTNBJTIydyUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMjMlMkMlMjJ0ZXh0JTIyJTNBJTIyeCUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMjQlMkMlMjJ0ZXh0JTIyJTNBJTIyeSUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCUyQyU3QiUyMmlkJTIyJTNBMjUlMkMlMjJ0ZXh0JTIyJTNBJTIyeiUyMiUyQyUyMnN0YXR1cyUyMiUzQSUyMm5ldyUyMiU3RCU1RA==';

const browser = await chromium.launch();
try {
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 800 } })).newPage();
  await page.goto(URL_26, { waitUntil: 'networkidle' });
  await page.waitForSelector('h1', { timeout: 5000 });
  await page.waitForTimeout(300);

  const dump = await page.evaluate(() => {
    const grab = (el) => {
      if (!el) return null;
      const cs = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      return {
        tag: el.tagName,
        className: el.className,
        bbox: { w: r.width, h: r.height },
        bg: cs.backgroundColor,
        height: cs.height,
        minHeight: cs.minHeight,
        display: cs.display
      };
    };
    return {
      pageScrollH: document.documentElement.scrollHeight,
      viewportH: window.innerHeight,
      html: grab(document.documentElement),
      body: grab(document.body),
      root: grab(document.getElementById('root')),
      main: grab(document.querySelector('main')),
      // Find the task list section (`task-list` class).
      taskList: grab(document.querySelector('.task-list')),
      // Page footer (the "You have N items" + benchmark paragraphs).
      lastP: grab(document.querySelectorAll('p')[document.querySelectorAll('p').length - 1])
    };
  });
  console.log('=== OG dark, 26 items ===');
  console.log(JSON.stringify(dump, null, 2));
} finally {
  await browser.close();
}
