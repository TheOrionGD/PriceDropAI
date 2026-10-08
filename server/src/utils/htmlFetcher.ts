import axios from 'axios';
import { getBrowserHeaders } from './headers.js';

export async function fetchPageHtml(url: string, referer?: string): Promise<string | null> {
  const zenrowsKey = process.env.ZENROWS_KEY?.trim();

  // Tier 1: If ZenRows API Key is provided, use its anti-bot residential proxy
  if (zenrowsKey) {
    try {
      const zenrowsUrl = `https://api.zenrows.com/v1/?apikey=${zenrowsKey}&url=${encodeURIComponent(url)}&antibot=true&js_render=true&premium_proxy=true`;
      const response = await axios.get(zenrowsUrl, {
        timeout: 30000,
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
      console.warn(`[ZenRows Gateway Notice] ${url}: Request failed${status ? ` (status ${status})` : ''}: ${detail}. Falling back to direct fetch.`);
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
      return typeof response.data === 'string' ? response.data : JSON.stringify(response.data);
    }
  } catch (err: any) {
    console.error(`[Direct Fetch Error] ${url}:`, err.message);
  }

  return null;
}
