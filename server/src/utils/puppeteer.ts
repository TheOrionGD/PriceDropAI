import puppeteer, { Browser, Page, LaunchOptions } from 'puppeteer-core';
import { existsSync, readdirSync } from 'fs';
import { join } from 'path';
import { execSync } from 'child_process';

let browser: Browser | null = null;
let launchPromise: Promise<Browser> | null = null;

function findCachedChromePath(): string | null {
  const home = process.env.HOME || process.env.USERPROFILE || '';
  const cacheDirs = [
    process.env.PUPPETEER_CACHE_DIR,
    '/opt/render/.cache/puppeteer',
    home ? join(home, '.cache', 'puppeteer') : null,
    './.cache/puppeteer',
  ].filter(Boolean) as string[];

  for (const cacheDir of cacheDirs) {
    if (!existsSync(cacheDir)) continue;
    try {
      const findExecutable = (dir: string, depth = 0): string | null => {
        if (depth > 6) return null;
        const entries = readdirSync(dir, { withFileTypes: true });
        for (const entry of entries) {
          const fullPath = join(dir, entry.name);
          if (entry.isDirectory()) {
            const found = findExecutable(fullPath, depth + 1);
            if (found) return found;
          } else if (entry.isFile()) {
            if (
              entry.name === 'chrome' ||
              entry.name === 'chrome.exe' ||
              entry.name === 'chromium'
            ) {
              return fullPath;
            }
          }
        }
        return null;
      };
      const found = findExecutable(cacheDir);
      if (found) return found;
    } catch {}
  }
  return null;
}

export function findChromePath(): string | null {
  if (process.env.PUPPETEER_EXECUTABLE_PATH && existsSync(process.env.PUPPETEER_EXECUTABLE_PATH)) {
    return process.env.PUPPETEER_EXECUTABLE_PATH;
  }

  const cachedPath = findCachedChromePath();
  if (cachedPath) return cachedPath;

  const candidates = [
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser',
    '/snap/bin/chromium',
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
    if (!chromePath) {
      launchPromise = null;
      throw new Error('No executablePath or channel specified for puppeteer-core (Chrome/Chromium binary missing on system)');
    }

    const options: LaunchOptions = {
      headless: true,
      executablePath: chromePath,
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
    try {
      browser = await puppeteer.launch(options);
      console.log(`[Puppeteer] Browser launched using binary: ${chromePath}`);
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

let loggedMissingChromeWarning = false;

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
    if (!loggedMissingChromeWarning) {
      console.warn(`[Puppeteer Notice] Page rendering skipped: ${e.message}`);
      loggedMissingChromeWarning = true;
    }
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