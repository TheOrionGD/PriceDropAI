import axios from 'axios';
import * as cheerio from 'cheerio';
import { getRandomUserAgent } from '../utils/headers.js';

export async function fetchZenRowsProductImage(query: string): Promise<string | null> {
  const zenrowsKey = process.env.ZENROWS_KEY?.trim();
  if (!zenrowsKey) return null;

  const cleanQ = query.trim();
  const searchUrl = `https://www.amazon.in/s?k=${encodeURIComponent(cleanQ)}`;
  const zenrowsUrl = `https://api.zenrows.com/v1/?apikey=${zenrowsKey}&url=${encodeURIComponent(searchUrl)}&js_render=true&antibot=true&premium_proxy=true`;

  try {
    const res = await axios.get(zenrowsUrl, {
      timeout: 15000,
      headers: { 'Accept': 'text/html' }
    });

    if (res.status === 200 && res.data) {
      const $ = cheerio.load(res.data);
      let foundUrl: string | null = null;

      $('div[data-component-type="s-search-result"]').each((_, el) => {
        if (foundUrl) return;
        const imgEl = $(el).find('img.s-image, img.a-dynamic-image').first();
        let src = imgEl.attr('src') || imgEl.attr('data-image-src');
        
        if (!src || src.includes('grey-pixel') || src.includes('transparent-pixel')) {
          const srcset = imgEl.attr('srcset') || imgEl.attr('data-image-srcset');
          if (srcset) {
            const parts = srcset.split(',').map(s => s.trim().split(' ')[0]).filter(p => p && !p.includes('grey-pixel'));
            if (parts.length > 0) src = parts[parts.length - 1];
          }
        }

        if (src && isValidImageUrl(src)) {
          foundUrl = optimizeProductImageUrl(src);
        }
      });

      if (foundUrl) {
        console.log(`[ZenRows Image Gateway] Successfully resolved image for "${cleanQ}": ${foundUrl}`);
        return foundUrl;
      }
    }
  } catch (err: any) {
    console.warn(`[ZenRows Image Gateway Notice] Unable to fetch dynamic image for "${cleanQ}":`, err.message);
  }

  return null;
}

export function isValidImageUrl(url: string | null | undefined): boolean {
  if (!url || typeof url !== 'string') return false;
  const lower = url.toLowerCase().trim();
  if (!lower.startsWith('http://') && !lower.startsWith('https://')) return false;
  // Exclude landing page / search URLs that are not raw images
  if (lower.includes('s?k=') || (lower.includes('/dp/') && !lower.includes('/images/')) || lower.includes('/search?q=')) return false;
  // Exclude lazy-loading placeholder images
  if (lower.includes('grey-pixel') || lower.includes('transparent-pixel') || lower.includes('1x1') || lower.includes('blank.gif')) return false;

  const isImageExt = lower.includes('.jpg') || lower.includes('.jpeg') || lower.includes('.png') || lower.includes('.webp') || lower.includes('.svg') || lower.includes('.gif') || lower.includes('.avif');
  const isImageHost = lower.includes('media-amazon') || lower.includes('images-amazon') || lower.includes('ssl-images-amazon') ||
    lower.includes('flixcart') || lower.includes('meesho') || lower.includes('myntassets') || lower.includes('myntra') ||
    lower.includes('unsplash') || lower.includes('wikimedia') || lower.includes('wikipedia') ||
    lower.includes('duckduckgo') || lower.includes('bing') || lower.includes('googleusercontent') ||
    lower.includes('openlibrary') || lower.includes('cloudfront') || lower.includes('cdn');

  return isImageExt || isImageHost;
}

export function optimizeProductImageUrl(url: string | null | undefined): string | null {
  if (!url || !isValidImageUrl(url)) return null;

  let optimized = url.trim();

  // 1. Mandatory HTTPS enforcement (prevents Cleartext HTTP & Mixed Content blocking)
  if (optimized.startsWith('http://')) {
    optimized = optimized.replace(/^http:\/\//i, 'https://');
  }

  // 2. Amazon high-res upscaling: replace small thumbnail suffix with high-res standard
  if (optimized.includes('media-amazon.com') || optimized.includes('images-amazon.com')) {
    optimized = optimized.replace(/\._AC_[A-Z0-9,]+_\.jpg/i, '._AC_SL1000_.jpg');
    optimized = optimized.replace(/\._[A-Z0-9,]+_\.jpg/i, '._AC_SL1000_.jpg');
  }

  // 3. Flipkart high-res upscaling: upgrade thumbnail dimensions (312/312 -> 832/832)
  if (optimized.includes('flixcart.com')) {
    optimized = optimized.replace(/\/image\/[0-9]+\/[0-9]+\//i, '/image/832/832/');
  }

  // 4. Meesho upscaling
  if (optimized.includes('meesho.com') || optimized.includes('meeshosupply.com')) {
    optimized = optimized.replace(/\/(128|256)\//i, '/512/');
  }

  // 5. Myntra image assets
  if (optimized.includes('myntassets.com')) {
    if (optimized.startsWith('http://')) {
      optimized = optimized.replace(/^http:\/\//i, 'https://');
    }
  }

  return optimized;
}

export async function fetchWebImageFallback(query: string): Promise<string | null> {
  const cleanQ = query.trim();
  const encoded = encodeURIComponent(cleanQ);
  const userAgent = getRandomUserAgent();

  // Tier 0: ZenRows Anti-Bot Dynamic Image Resolution Gateway
  try {
    const zenrowsImg = await fetchZenRowsProductImage(cleanQ);
    if (zenrowsImg && isValidImageUrl(zenrowsImg)) {
      return zenrowsImg;
    }
  } catch {}

  // Tier 1: DuckDuckGo Instant Answers API
  try {
    const res = await axios.get(`https://api.duckduckgo.com/?q=${encoded}&format=json&no_redirect=1&no_html=1`, {
      headers: { 'User-Agent': userAgent },
      timeout: 4000,
    });
    if (res.data) {
      if (res.data.Image && isValidImageUrl(res.data.Image)) {
        let img = res.data.Image;
        if (img.startsWith('/')) img = `https://duckduckgo.com${img}`;
        return img;
      }
      if (Array.isArray(res.data.RelatedTopics)) {
        for (const topic of res.data.RelatedTopics) {
          if (topic.Icon?.URL && isValidImageUrl(topic.Icon.URL)) {
            let img = topic.Icon.URL;
            if (img.startsWith('/')) img = `https://duckduckgo.com${img}`;
            return img;
          }
        }
      }
    }
  } catch {}

  // Tier 2: Wikipedia Search API
  try {
    const wikiUrl = `https://en.wikipedia.org/w/api.php?action=query&format=json&prop=pageimages&pithumbsize=800&generator=search&gsrsearch=${encoded}&gsrlimit=3`;
    const res = await axios.get(wikiUrl, {
      headers: { 'User-Agent': userAgent },
      timeout: 4000,
    });
    const pages = res.data?.query?.pages;
    if (pages) {
      for (const key of Object.keys(pages)) {
        const thumb = pages[key]?.thumbnail?.source;
        if (isValidImageUrl(thumb)) {
          return thumb;
        }
      }
    }
  } catch {}

  // Tier 3: Wikimedia Commons Media Search API
  try {
    const commonsUrl = `https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=${encoded}&gsrnamespace=6&prop=imageinfo&iiprop=url&iiurlwidth=800&format=json&gsrlimit=3`;
    const res = await axios.get(commonsUrl, {
      headers: { 'User-Agent': userAgent },
      timeout: 4000,
    });
    const pages = res.data?.query?.pages;
    if (pages) {
      for (const key of Object.keys(pages)) {
        const info = pages[key]?.imageinfo?.[0];
        const imgUrl = info?.thumburl || info?.url;
        if (isValidImageUrl(imgUrl)) {
          return imgUrl;
        }
      }
    }
  } catch {}

  // Tier 4: OpenLibrary API (for books/novels)
  if (/book|novel|author|edition/i.test(cleanQ)) {
    try {
      const openLibUrl = `https://openlibrary.org/search.json?q=${encoded}&limit=1`;
      const res = await axios.get(openLibUrl, {
        headers: { 'User-Agent': userAgent },
        timeout: 4000,
      });
      const doc = res.data?.docs?.[0];
      if (doc?.cover_i) {
        return `https://covers.openlibrary.org/b/id/${doc.cover_i}-L.jpg`;
      }
    } catch {}
  }

  return null;
}
