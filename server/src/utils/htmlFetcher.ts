import axios from 'axios';
import { getBrowserHeaders } from './headers.js';
import { isZenRowsKeyDisabled, disableZenRowsKey } from './zenrowsState.js';
import { scrapePage } from './puppeteer.js';
import { getZenRowsKeyForStore } from './keyManager.js';
import dotenv from 'dotenv';

dotenv.config();

export interface FetchHtmlOptions {
  referer?: string;
  jsRender?: boolean;
  wait?: number;
  premiumProxy?: boolean;
  proxyCountry?: string;
  storeName?: string;
}

function detectStoreFromUrl(url: string): string {
  const lower = url.toLowerCase();
  if (lower.includes('amazon.')) return 'AMAZON';
  if (lower.includes('flipkart.')) return 'FLIPKART';
  if (lower.includes('meesho.')) return 'MEESHO';
  if (lower.includes('myntra.')) return 'MYNTRA';
  if (lower.includes('croma.')) return 'CROMA';
  if (lower.includes('reliancedigital.')) return 'RELIANCE_DIGITAL';
  if (lower.includes('jiomart.')) return 'JIOMART';
  if (lower.includes('tatacliq.')) return 'TATACLIQ';
  if (lower.includes('ajio.')) return 'AJIO';
  if (lower.includes('nykaa.')) return 'NYKAA';
  if (lower.includes('ebay.')) return 'EBAY';
  if (lower.includes('walmart.')) return 'WALMART';
  if (lower.includes('target.')) return 'TARGET';
  if (lower.includes('blinkit.')) return 'BLINKIT';
  return 'OTHER';
}

export async function fetchPageHtml(url: string, optionsOrReferer?: string | FetchHtmlOptions): Promise<string | null> {
  const options: FetchHtmlOptions = typeof optionsOrReferer === 'string'
    ? { referer: optionsOrReferer }
    : (optionsOrReferer || {});

  const detectedStore = options.storeName || detectStoreFromUrl(url);
  const zenrowsKey = getZenRowsKeyForStore(detectedStore);

  // Tier 1: If ZenRows API Key is provided and active, use anti-bot gateway
  if (zenrowsKey && !isZenRowsKeyDisabled(zenrowsKey)) {
    try {
      let zenrowsUrl = `https://api.zenrows.com/v1/?apikey=${zenrowsKey}&url=${encodeURIComponent(url)}&mode=auto`;
      if (options.proxyCountry) zenrowsUrl += `&proxy_country=${options.proxyCountry}`;

      const response = await axios.get(zenrowsUrl, {
        timeout: 12000,
        maxContentLength: 8 * 1024 * 1024,
        maxBodyLength: 8 * 1024 * 1024,
        validateStatus: (status) => status === 200,
      });

      if (response.status === 200 && response.data) {
        return typeof response.data === 'string' ? response.data : JSON.stringify(response.data);
      }
    } catch (err: any) {
      const status = err.response?.status;
      const respData = err.response?.data;
      const detail = respData ? (typeof respData === 'object' ? JSON.stringify(respData) : respData) : err.message;
      
      if (status === 402 || status === 401 || status === 403) {
        disableZenRowsKey(zenrowsKey, `Usage limit / Auth error (status ${status}) on key ${zenrowsKey.slice(0, 8)}... Falling back to alternate key or direct fetch.`);
      } else {
        console.warn(`[ZenRows Gateway Notice] ${url}: Request failed${status ? ` (status ${status})` : ''}: ${detail}.`);
      }
    }
  }

  // Tier 2: Direct stealth fetch with rotating realistic headers
  try {
    const response = await axios.get(url, {
      headers: getBrowserHeaders(options.referer || 'https://www.google.com/'),
      timeout: 12000,
      maxContentLength: 5 * 1024 * 1024,
      maxBodyLength: 5 * 1024 * 1024,
      validateStatus: (status) => status < 400,
    });

    if (response.status === 200 && response.data) {
      const htmlStr = typeof response.data === 'string' ? response.data : JSON.stringify(response.data);
      // Ensure response is not a captcha block
      if (!htmlStr.includes('api-services-support@amazon.com') && !htmlStr.includes('Type the characters you see in this image')) {
        return htmlStr;
      }
    }
  } catch (err: any) {
    console.warn(`[Direct Fetch Notice] ${url}: ${err.message}. Trying Puppeteer browser rendering.`);
  }

  // Tier 3: Puppeteer Headless Chrome browser scraping fallback
  try {
    const puppeteerHtml = await scrapePage(url, async (page) => {
      return await page.content();
    }, { timeout: 20000, waitUntil: 'domcontentloaded' });

    if (puppeteerHtml && puppeteerHtml.length > 500) {
      return puppeteerHtml;
    }
  } catch (e: any) {
    console.warn(`[Puppeteer Fetch Warning] ${url}: ${e.message}`);
  }

  return null;
}

