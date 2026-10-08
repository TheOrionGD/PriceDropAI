import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { scrapePage } from '../utils/puppeteer.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';

function extractFromHtml(html: string): any[] {
  const products: any[] = [];
  // Match each product card: <a href="/.../p/..."> ... </a>
  const cardRe = /<a\s+href="(\/[^"]*\/p\/[^"]+)"[^>]*>([\s\S]*?)<\/a>/g;
  let m;
  while ((m = cardRe.exec(html)) !== null) {
    const href = m[1];
    const body = m[2];

    // Title from img alt or <p> text
    const altMatch = body.match(/<img[^>]*\balt="([^"]*)"/);
    const pMatch = body.match(/<p[^>]*>([^<]{3,120})<\/p>/);
    const title = (altMatch ? altMatch[1] : (pMatch ? pMatch[1] : '')).trim();
    if (!title || title.length < 3) continue;

    // Price from <h5> or <span> with ₹
    const priceMatch = body.match(/₹\s*([\d,]+)/);
    if (!priceMatch) continue;
    const price = parseFloat(priceMatch[1].replace(/,/g, ''));
    if (isNaN(price) || price <= 10) continue;

    // Image
    const imgMatch = body.match(/<img[^>]*\bsrc="([^"]+)"/);
    let imageUrl = imgMatch ? imgMatch[1] : null;

    products.push({
      title,
      price,
      originalPrice: null,
      imageUrl,
      landingPageUrl: href,
    });
  }
  return products;
}

function extractFromNextData(html: string): any[] {
  const products: any[] = [];
  const nextDataMatch = html.match(/<script id="__NEXT_DATA__" type="application\/json">([\s\S]*?)<\/script>/);
  if (nextDataMatch) {
    try {
      const data = JSON.parse(nextDataMatch[1]);
      const list = data?.props?.pageProps?.initialState?.search?.products ||
                   data?.props?.pageProps?.data?.products ||
                   data?.props?.pageProps?.products || [];
      for (const item of list) {
        const title = item.name || item.title;
        const price = item.price || item.minPrice;
        if (title && price && price > 10) {
          products.push({
            title,
            price,
            originalPrice: item.originalPrice || item.mrp || null,
            imageUrl: item.images?.[0] || item.image || null,
            landingPageUrl: item.slug ? `/p/${item.slug}` : (item.id ? `/p/${item.id}` : null),
          });
        }
      }
    } catch {}
  }
  return products;
}

export async function scrapeMeesho(query: string): Promise<RawScrapedItem[]> {
  const encoded = encodeURIComponent(query.trim());
  const url = `https://www.meesho.com/search?q=${encoded}`;

  // Primary: Fast direct HTML fetch without requiring Chrome/Puppeteer
  let html = await fetchPageHtml(url, 'https://www.meesho.com/');

  // Secondary fallback: Puppeteer browser rendering if direct HTML fetch returned null
  if (!html) {
    html = await scrapePage(url, async (page: any) => {
      return await page.content();
    });
  }

  if (!html) return [];

  let products = extractFromHtml(html);
  if (products.length === 0) {
    products = extractFromNextData(html);
  }

  const results: RawScrapedItem[] = [];

  for (const p of products) {
    let productUrl = p.landingPageUrl || '';
    if (productUrl && !productUrl.startsWith('http')) {
      productUrl = `https://www.meesho.com${productUrl.startsWith('/') ? '' : '/'}${productUrl}`;
    }
    if (!productUrl) productUrl = `https://www.meesho.com/search?q=${encoded}`;

    let imageUrl = p.imageUrl || null;
    if (imageUrl && !imageUrl.startsWith('http')) {
      if (imageUrl.startsWith('//')) imageUrl = 'https:' + imageUrl;
      else if (imageUrl.startsWith('/')) imageUrl = `https://www.meesho.com${imageUrl}`;
      else imageUrl = `https://www.meesho.com/${imageUrl}`;
    }

    results.push({
      store: Store.MEESHO,
      title: p.title,
      url: productUrl,
      price: p.price,
      originalPrice: p.originalPrice ?? null,
      discountPercentage: null,
      imageUrl: imageUrl && imageUrl.startsWith('http') ? imageUrl : null,
      rating: null,
      reviewCount: null,
      availability: Availability.IN_STOCK,
    });
  }

  return results;
}