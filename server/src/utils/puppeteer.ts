import puppeteer, { Browser, Page, LaunchOptions } from 'puppeteer-core';
import { existsSync } from 'fs';
import { execSync } from 'child_process';

let browser: Browser | null = null;
let launchPromise: Promise<Browser> | null = null;

export function findChromePath(): string | null {
  const candidates = [
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    '/usr/bin/google-chrome',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser',
    '/usr/bin/microsoft-edge',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Chromium.app/Contents/MacOS/Chromium',
  ];
  for (const p of candidates) {
    if (existsSync(p)) return p;
  }

  if (process.platform === 'win32') {
    try {
      const out = execSync('where chrome.exe 2>nul', { encoding: 'utf8', timeout: 5000 }).trim();
      if (out) return out.split('\n')[0].trim();
    } catch {}
    try {
      const out = execSync('where msedge.exe 2>nul', { encoding: 'utf8', timeout: 5000 }).trim();
      if (out) return out.split('\n')[0].trim();
    } catch {}
  }

  return null;
}

export async function getBrowser(): Promise<Browser> {
  if (browser && browser.process() != null) return browser;
  if (launchPromise) return launchPromise;

  launchPromise = (async () => {
    const chromePath = findChromePath();
    const options: LaunchOptions = {
      headless: true,
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-dev-shm-usage',
        '--disable-gpu',
        '--disable-features=TranslateUI',
        '--disable-features=NetworkService',
        '--window-size=1366,768',
        '--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
      ],
    };
    if (chromePath) {
      options.executablePath = chromePath;
    }
    try {
      browser = await puppeteer.launch(options);
      console.log('[Puppeteer] Browser launched');
    } catch (e: any) {
      console.error('[Puppeteer] Launch failed:', e.message);
      launchPromise = null;
      throw e;
    }
    return browser;
  })();

  return launchPromise;
}

export async function closeBrowser(): Promise<void> {
  if (browser) {
    try { await browser.close(); } catch {}
    browser = null;
    launchPromise = null;
  }
}

export async function scrapePage<T>(
  url: string,
  scraper: (page: Page) => Promise<T>,
  options: { waitUntil?: 'load' | 'domcontentloaded' | 'networkidle0' | 'networkidle2'; timeout?: number } = {}
): Promise<T | null> {
  let page: Page | null = null;
  try {
    const b = await getBrowser();
    page = await b.newPage();
    await page.setUserAgent(
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36'
    );
    await page.setExtraHTTPHeaders({
      'Accept-Language': 'en-IN,en-US;q=0.9,en;q=0.8',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    });
    await page.goto(url, {
      waitUntil: options.waitUntil || 'domcontentloaded',
      timeout: options.timeout || 45000,
    });
    try { await new Promise((r) => setTimeout(r, 2500)); } catch {}
    return await scraper(page);
  } catch (e: any) {
    console.error(`[Puppeteer] scrapePage error for ${url}:`, e.message);
    return null;
  } finally {
    if (page) {
      try { await page.close(); } catch {}
    }
  }
}

export function parsePrice(text: string | null | undefined): number | null {
  if (!text) return null;
  const cleaned = text.replace(/[^0-9.]/g, '');
  const n = parseFloat(cleaned);
  return isNaN(n) || n <= 0 ? null : n;
}