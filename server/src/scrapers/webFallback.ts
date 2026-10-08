import axios from 'axios';
import { getRandomUserAgent } from '../utils/headers.js';

export function isValidImageUrl(url: string | null | undefined): boolean {
  if (!url || typeof url !== 'string') return false;
  const lower = url.toLowerCase().trim();
  if (!lower.startsWith('http://') && !lower.startsWith('https://')) return false;
  if (lower.includes('s?k=') || lower.includes('/dp/') || lower.includes('/search?') || lower.includes('.html')) return false;

  const isImageExt = lower.includes('.jpg') || lower.includes('.jpeg') || lower.includes('.png') || lower.includes('.webp') || lower.includes('.svg');
  const isImageHost = lower.includes('media-amazon') || lower.includes('images-amazon') || lower.includes('ssl-images-amazon') ||
    lower.includes('flixcart') || lower.includes('meesho') || lower.includes('myntassets') ||
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

export function getCategoryFallbackImage(category: string | null | undefined): string {
  const cat = (category || '').toLowerCase();

  if (cat.includes('mobile') || cat.includes('phone')) {
    return 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=800&auto=format&fit=crop&q=80';
  }
  if (cat.includes('computer') || cat.includes('laptop')) {
    return 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=800&auto=format&fit=crop&q=80';
  }
  if (cat.includes('audio') || cat.includes('headphone')) {
    return 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800&auto=format&fit=crop&q=80';
  }
  if (cat.includes('wearable') || cat.includes('watch')) {
    return 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800&auto=format&fit=crop&q=80';
  }
  if (cat.includes('tv') || cat.includes('entertainment')) {
    return 'https://images.unsplash.com/photo-1593784991095-a205069470b6?w=800&auto=format&fit=crop&q=80';
  }
  if (cat.includes('footwear') || cat.includes('shoe')) {
    return 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&auto=format&fit=crop&q=80';
  }
  if (cat.includes('fashion') || cat.includes('apparel')) {
    return 'https://images.unsplash.com/photo-1445205170230-053b83016050?w=800&auto=format&fit=crop&q=80';
  }
  if (cat.includes('bag') || cat.includes('luggage')) {
    return 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=800&auto=format&fit=crop&q=80';
  }
  if (cat.includes('beauty') || cat.includes('grooming')) {
    return 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?w=800&auto=format&fit=crop&q=80';
  }

  return 'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=800&auto=format&fit=crop&q=80';
}

export async function fetchWebImageFallback(query: string): Promise<string | null> {
  const encoded = encodeURIComponent(query.trim());
  const userAgent = getRandomUserAgent();

  // Tier 1: DuckDuckGo Instant Answers API
  try {
    const res = await axios.get(`https://api.duckduckgo.com/?q=${encoded}&format=json&no_redirect=1&no_html=1`, {
      headers: { 'User-Agent': userAgent },
      timeout: 5000,
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
      timeout: 5000,
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

  // Tier 3: OpenLibrary API (for books/novels)
  if (/book|novel|author|edition/i.test(query)) {
    try {
      const openLibUrl = `https://openlibrary.org/search.json?q=${encoded}&limit=1`;
      const res = await axios.get(openLibUrl, {
        headers: { 'User-Agent': userAgent },
        timeout: 5000,
      });
      const doc = res.data?.docs?.[0];
      if (doc?.cover_i) {
        return `https://covers.openlibrary.org/b/id/${doc.cover_i}-L.jpg`;
      }
    } catch {}
  }

  return null;
}
