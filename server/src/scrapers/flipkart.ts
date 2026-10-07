import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';

function parseState(html: string): any {
  const idx = html.indexOf('window.__INITIAL_STATE__');
  if (idx < 0) throw new Error('INITIAL_STATE not found');
  const eqIdx = html.indexOf('=', idx);
  const openBrace = html.indexOf('{', eqIdx);
  let depth = 0;
  let closeBrace = -1;
  let inStr = false;
  let escape = false;
  for (let i = openBrace; i < html.length; i++) {
    const ch = html[i];
    if (escape) { escape = false; continue; }
    if (ch === '\\') { escape = true; continue; }
    if (ch === '"') { inStr = !inStr; continue; }
    if (inStr) continue;
    if (ch === '{') depth++;
    else if (ch === '}') { depth--; if (depth === 0) { closeBrace = i; break; } }
  }
  return JSON.parse(html.slice(openBrace, closeBrace + 1));
}

function parsePrice(text: string | null | undefined): number | null {
  if (!text) return null;
  const cleaned = text.replace(/[^0-9.]/g, '');
  const n = parseFloat(cleaned);
  return isNaN(n) || n <= 0 ? null : n;
}

function getFirstPrice(v: any): { price: number | null; originalPrice: number | null } {
  const prices = v?.pricing?.prices;
  if (!Array.isArray(prices) || prices.length === 0) return { price: null, originalPrice: null };
  let selling: number | null = null;
  let special: number | null = null;
  for (const p of prices) {
    const val = parsePrice(String(p.value));
    if (val === null) continue;
    if (p.priceType === 'FSP') selling = val;
    else if (p.priceType === 'SPECIAL_PRICE') special = val;
    if (!selling) selling = val;
    if (!special && p.name !== 'Selling Price') special = val;
  }
  return {
    price: special || selling,
    originalPrice: selling,
  };
}

function getImage(v: any): string | null {
  const images = v?.media?.images;
  if (!Array.isArray(images) || images.length === 0) return null;
  const url = images[0].url;
  if (!url) return null;
  // Template: http://rukmini1.flixcart.com/image/{@width}/{@height}/xif0q/...
  let resolved = url
    .replace(/\{@width\}/g, '500')
    .replace(/\{@height\}/g, '500')
    .replace(/\{@quality\}/g, '70');
  if (resolved.startsWith('//')) resolved = 'https:' + resolved;
  return resolved.startsWith('http') ? resolved : null;
}

export async function scrapeFlipkart(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.flipkart.com/search?q=${encoded}`;
    const html = await fetchPageHtml(url, 'https://www.flipkart.com/');
    if (!html) return [];

    let state: any;
    try {
      state = parseState(html);
    } catch {
      return [];
    }

    const page = state.pageDataV4?.page;
    if (!page || !page.data) return [];

    const results: RawScrapedItem[] = [];
    const seenIds = new Set<string>();

    for (const [slotKey, slotVal] of Object.entries(page.data)) {
      const slot: any = slotVal;
      if (!Array.isArray(slot)) continue;
      for (const w of slot) {
        if (!w?.widget || w.widget.type !== 'PRODUCT_SUMMARY') continue;
        const products = w.widget.data?.products;
        if (!Array.isArray(products)) continue;
        for (const p of products) {
          const v = p.productInfo?.value;
          if (!v) continue;
          const id = v.id;
          if (!id || seenIds.has(id)) continue;
          seenIds.add(id);

          const title = v.titles?.title || v.titles?.newTitle || '';
          if (!title) continue;

          const { price, originalPrice } = getFirstPrice(v);
          if (!price || price <= 10) continue;

          const baseUrl = v.baseUrl || '';
          const fullUrl = baseUrl.startsWith('http')
            ? baseUrl
            : `https://www.flipkart.com${baseUrl}`;

          const discountPct = originalPrice && originalPrice > price
            ? Math.round(((originalPrice - price) / originalPrice) * 100)
            : null;

          results.push({
            store: Store.FLIPKART,
            title,
            url: fullUrl,
            price,
            originalPrice,
            discountPercentage: discountPct,
            imageUrl: getImage(v),
            rating: parsePrice(String(v.ratingsAndReviews?.rating)) ?? null,
            reviewCount: v.ratingsAndReviews?.reviewCount ?? null,
            availability: Availability.IN_STOCK,
          });
        }
      }
    }

    return results;
  } catch (err: any) {
    console.error(`[Flipkart Scraper] Error for "${query}":`, err.message);
    return [];
  }
}