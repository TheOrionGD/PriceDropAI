import axios from 'axios';
import { getBrowserHeaders } from './headers.js';
import { isZenRowsDisabled, disableZenRows } from './zenrowsState.js';
import { scrapePage } from './puppeteer.js';
import dotenv from 'dotenv';

dotenv.config();

export async function fetchPageHtml(url: string, referer?: string): Promise<string | null> {
  const zenrowsKey = process.env.ZENROWS_KEY?.trim();

  // Tier 1: If ZenRows API Key is provided and active, use anti-bot gateway
  if (zenrowsKey && !isZenRowsDisabled()) {
    try {
      // First attempt fast antibot fetch (1 credit)
      const zenrowsUrl = `https://api.zenrows.com/v1/?apikey=${zenrowsKey}&url=${encodeURIComponent(url)}&antibot=true`;
      const response = await axios.get(zenrowsUrl, {
        timeout: 20000,
        maxContentLength: 5 * 1024 * 1024,
        maxBodyLength: 5 * 1024 * 1024,
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
        disableZenRows(`Usage limit / Auth error (status ${status}). Falling back to direct & Puppeteer fetch.`);
      } else {
        console.warn(`[ZenRows Gateway Notice] ${url}: Request failed${status ? ` (status ${status})` : ''}: ${detail}. Falling back to direct fetch.`);
      }
    }
  }

  // Tier 2: Direct stealth fetch with rotating realistic headers
  try {
    const response = await axios.get(url, {
      headers: getBrowserHeaders(referer || 'https://www.google.com/'),
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

