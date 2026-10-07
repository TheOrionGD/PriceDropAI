import axios from 'axios';
import { getBrowserHeaders } from './headers.js';

export async function fetchPageHtml(url: string, referer?: string): Promise<string | null> {
  const zenrowsKey = process.env.ZENROWS_KEY?.trim();

  // Tier 1: If ZenRows API Key is provided, use its anti-bot residential proxy
  if (zenrowsKey) {
    try {
      const zenrowsUrl = `https://api.zenrows.com/v1/?apikey=${zenrowsKey}&url=${encodeURIComponent(url)}&antibot=true`;
      const response = await axios.get(zenrowsUrl, {
        timeout: 20000,
        maxContentLength: 3 * 1024 * 1024,
        maxBodyLength: 3 * 1024 * 1024,
        validateStatus: (status) => status === 200,
      });

      if (response.status === 200 && response.data) {
        return typeof response.data === 'string' ? response.data : JSON.stringify(response.data);
      }
    } catch (err: any) {
      console.warn(`[ZenRows Gateway Notice] ${url}: ${err.message}. Falling back to direct fetch.`);
    }
  }

  // Tier 2: Direct stealth fetch with rotating realistic headers
  try {
    const response = await axios.get(url, {
      headers: getBrowserHeaders(referer || 'https://www.google.com/'),
      timeout: 10000,
      maxContentLength: 3 * 1024 * 1024,
      maxBodyLength: 3 * 1024 * 1024,
      validateStatus: (status) => status < 400,
    });

    if (response.status === 200 && response.data) {
      return typeof response.data === 'string' ? response.data : JSON.stringify(response.data);
    }
  } catch (err: any) {
    console.error(`[Direct Fetch Error] ${url}:`, err.message);
  }

  return null;
}
