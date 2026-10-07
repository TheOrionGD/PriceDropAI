import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { scrapePage } from '../utils/puppeteer.js';

function extractProductsFromHtml(html: string): any[] {
  const products: any[] = [];
  const scriptRe = /<script[^>]*>([\s\S]{200,})<\/script>/g;
  let sm;
  while ((sm = scriptRe.exec(html)) !== null) {
    const c = sm[1];
    if (!c.includes('landingPageUrl')) continue;

    // Find all product objects starting with {"landingPageUrl"
    const re = /\{"landingPageUrl":/g;
    let m;
    const starts: number[] = [];
    while ((m = re.exec(c)) !== null) starts.push(m.index);

    for (const start of starts) {
      let depth = 0;
      let end = start;
      let inStr = false;
      let escape = false;
      for (let i = start; i < c.length; i++) {
        const ch = c[i];
        if (escape) { escape = false; continue; }
        if (ch === '\\') { escape = true; continue; }
        if (ch === '"') { inStr = !inStr; continue; }
        if (inStr) continue;
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) { end = i + 1; break; } }
      }
      try {
        products.push(JSON.parse(c.slice(start, end)));
      } catch {}
    }
    if (products.length > 0) break;
  }
  return products;
}

export async function scrapeMyntra(query: string): Promise<RawScrapedItem[]> {
  const encoded = encodeURIComponent(query.trim());
  const url = `https://www.myntra.com/${encoded}`;

  const html = await scrapePage(url, async (page: any) => {
    return await page.content();
  });

  if (!html) return [];

  const products = extractProductsFromHtml(html);
  const results: RawScrapedItem[] = [];

  for (const p of products) {
    const title = p.product || p.productName || '';
    if (!title) continue;

    const price = p.price ?? null;
    if (!price || price <= 10) continue;

    const originalPrice = p.mrp || p.originalPrice || null;
    const discountPct = originalPrice && originalPrice > price
      ? Math.round(((originalPrice - price) / originalPrice) * 100)
      : null;

    let productUrl = p.landingPageUrl || '';
    if (productUrl && !productUrl.startsWith('http')) {
      productUrl = `https://www.myntra.com${productUrl.startsWith('/') ? '' : '/'}${productUrl}`;
    }
    if (!productUrl) productUrl = `https://www.myntra.com/${encoded}`;

    let imageUrl = p.searchImage || p.imageUrl || null;
    if (imageUrl && !imageUrl.startsWith('http')) {
      if (imageUrl.startsWith('//')) imageUrl = 'https:' + imageUrl;
      else if (imageUrl.startsWith('/')) imageUrl = `https://www.myntra.com${imageUrl}`;
      else imageUrl = `https://www.myntra.com/${imageUrl}`;
    }

    results.push({
      store: Store.MYNTRA,
      title,
      url: productUrl,
      price,
      originalPrice,
      discountPercentage: discountPct,
      imageUrl: imageUrl && imageUrl.startsWith('http') ? imageUrl : null,
      rating: typeof p.rating === 'number' ? Math.round(p.rating * 100) / 100 : null,
      reviewCount: p.ratingCount ?? null,
      availability: Availability.IN_STOCK,
    });
  }

  return results;
}