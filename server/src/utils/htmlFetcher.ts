import axios from 'axios';
import { getBrowserHeaders } from './headers.js';
import { isZenRowsKeyDisabled, disableZenRowsKey } from './zenrowsState.js';
import { isScrapingAntKeyDisabled, disableScrapingAntKey } from './scrapingAntState.js';
import { isScraperApiKeyDisabled, disableScraperApiKey } from './scraperApiState.js';
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

export interface FetchResultDetails {
  html: string | null;
  provider: string;
  outcome: 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'USAGE_LIMIT' | 'RATE_LIMITED';
  durationMs: number;
}

export async function fetchPageHtmlWithDetails(url: string, optionsOrReferer?: string | FetchHtmlOptions): Promise<FetchResultDetails> {
  const options: FetchHtmlOptions = typeof optionsOrReferer === 'string'
    ? { referer: optionsOrReferer }
    : (optionsOrReferer || {});

  const detectedStore = options.storeName || detectStoreFromUrl(url);
  const isAmazonUrl = detectedStore === 'AMAZON' || url.toLowerCase().includes('amazon.');
  const t0 = Date.now();

  // Tier 1: ScraperAPI gateway (Active primary provider with country targeting)
  const scraperApiKey = process.env.SCRAPERAPI_KEY?.trim();
  if (scraperApiKey && !isScraperApiKeyDisabled(scraperApiKey)) {
    try {
      let saUrl = `http://api.scraperapi.com?api_key=${scraperApiKey}&url=${encodeURIComponent(url)}&country_code=in`;
      if (options.jsRender) {
        saUrl += '&render=true';
      }
      const response = await axios.get(saUrl, {
        timeout: 30000,
        maxContentLength: 8 * 1024 * 1024,
        maxBodyLength: 8 * 1024 * 1024,
        validateStatus: (status) => status === 200,
      });

      if (response.status === 200 && response.data) {
        const html = typeof response.data === 'string' ? response.data : JSON.stringify(response.data);
        return {
          html,
          provider: 'ScraperAPI',
          outcome: 'SUCCESS',
          durationMs: Date.now() - t0,
        };
      }
    } catch (err: any) {
      const status = err.response?.status;
      if (status === 401 || status === 403 || status === 429) {
        disableScraperApiKey(scraperApiKey, `Usage limit / Auth error (status ${status})`);
      } else {
        console.warn(`[ScraperAPI Notice] ${url}: ${err.message}`);
      }
    }
  }

  // Tier 2: ScrapingAnt gateway failover (non-Amazon domains)
  const scrapingAntKey = process.env.SCRAPINGANT_KEY?.trim();
  if (scrapingAntKey && !isAmazonUrl && !isScrapingAntKeyDisabled(scrapingAntKey)) {
    try {
      const saParams = new URLSearchParams();
      saParams.append('url', url);
      saParams.append('x-api-key', scrapingAntKey);
      saParams.append('browser', options.jsRender ? 'true' : 'false');

      const saUrl = `https://api.scrapingant.com/v2/general?${saParams.toString()}`;
      const response = await axios.get(saUrl, {
        timeout: 25000,
        maxContentLength: 8 * 1024 * 1024,
        maxBodyLength: 8 * 1024 * 1024,
        validateStatus: (status) => status === 200,
      });

      if (response.status === 200 && response.data) {
        const html = typeof response.data === 'string' ? response.data : JSON.stringify(response.data);
        return {
          html,
          provider: 'ScrapingAnt',
          outcome: 'SUCCESS',
          durationMs: Date.now() - t0,
        };
      }
    } catch (err: any) {
      const status = err.response?.status;
      if (status === 402 || status === 401 || status === 403) {
        disableScrapingAntKey(scrapingAntKey, `Usage limit (status ${status})`);
      } else {
        console.warn(`[ScrapingAnt Notice] ${url}: ${err.message}`);
      }
    }
  }

  // Tier 3: Direct stealth fetch with rotating realistic headers
  try {
    const response = await axios.get(url, {
      headers: getBrowserHeaders(options.referer || 'https://www.google.com/'),
      timeout: 20000,
      maxContentLength: 5 * 1024 * 1024,
      maxBodyLength: 5 * 1024 * 1024,
      validateStatus: (status) => status < 400,
    });

    if (response.status === 200 && response.data) {
      const htmlStr = typeof response.data === 'string' ? response.data : JSON.stringify(response.data);
      if (!htmlStr.includes('api-services-support@amazon.com') && !htmlStr.includes('Type the characters you see in this image')) {
        return {
          html: htmlStr,
          provider: 'DirectStealth',
          outcome: 'SUCCESS',
          durationMs: Date.now() - t0,
        };
      }
    }
  } catch (err: any) {
    // Continue to next tier
  }

  // Tier 4: ZenRows anti-bot gateway (Fallback - only attempted if keys are active & not disabled)
  const candidateKeys = [
    getZenRowsKeyForStore(detectedStore),
    process.env.ZENROWS_KEY1?.trim(),
    process.env.ZENROWS_KEY2?.trim(),
    process.env.ZENROWS_KEY?.trim()
  ].filter((k): k is string => !!k && !isZenRowsKeyDisabled(k));

  const uniqueKeys = Array.from(new Set(candidateKeys));

  for (const zenrowsKey of uniqueKeys) {
    try {
      let zenrowsUrl: string;
      if (isAmazonUrl || options.premiumProxy) {
        zenrowsUrl = `https://api.zenrows.com/v1/?apikey=${zenrowsKey}&url=${encodeURIComponent(url)}&premium_proxy=true&antibot=true`;
      } else {
        zenrowsUrl = `https://api.zenrows.com/v1/?apikey=${zenrowsKey}&url=${encodeURIComponent(url)}&mode=auto`;
      }
      if (options.proxyCountry) zenrowsUrl += `&proxy_country=${options.proxyCountry}`;
      if (options.jsRender && !zenrowsUrl.includes('mode=auto')) zenrowsUrl += `&js_render=true`;

      const response = await axios.get(zenrowsUrl, {
        timeout: 25000,
        maxContentLength: 8 * 1024 * 1024,
        maxBodyLength: 8 * 1024 * 1024,
        validateStatus: (status) => status === 200,
      });

      if (response.status === 200 && response.data) {
        const html = typeof response.data === 'string' ? response.data : JSON.stringify(response.data);
        return {
          html,
          provider: 'ZenRows',
          outcome: 'SUCCESS',
          durationMs: Date.now() - t0,
        };
      }
    } catch (err: any) {
      const status = err.response?.status;
      if (status === 402 || status === 401 || status === 403) {
        disableZenRowsKey(zenrowsKey, `Usage limit (status ${status})`);
      }
    }
  }

  // Tier 5: Puppeteer Headless Chrome browser scraping fallback
  try {
    const puppeteerHtml = await scrapePage(url, async (page) => {
      return await page.content();
    }, { timeout: 35000, waitUntil: 'domcontentloaded' });

    if (puppeteerHtml && puppeteerHtml.length > 500) {
      return {
        html: puppeteerHtml,
        provider: 'Puppeteer',
        outcome: 'SUCCESS',
        durationMs: Date.now() - t0,
      };
    }
  } catch (e: any) {
    console.warn(`[Puppeteer Fetch Warning] ${url}: ${e.message}`);
  }

  return {
    html: null,
    provider: 'None',
    outcome: 'FAILED',
    durationMs: Date.now() - t0,
  };
}

export async function fetchPageHtml(url: string, optionsOrReferer?: string | FetchHtmlOptions): Promise<string | null> {
  const details = await fetchPageHtmlWithDetails(url, optionsOrReferer);
  return details.html;
}


