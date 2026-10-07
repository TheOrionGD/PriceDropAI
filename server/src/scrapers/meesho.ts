import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { scrapePage } from '../utils/puppeteer.js';

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

export async function scrapeMeesho(query: string): Promise<RawScrapedItem[]> {
  const encoded = encodeURIComponent(query.trim());
  const url = `https://www.meesho.com/search?q=${encoded}`;

  const html = await scrapePage(url, async (page: any) => {
    return await page.content();
  });

  if (!html) return [];

  const products = extractFromHtml(html);
  const results: RawScrapedItem[] = [];

  for (const p of products) {
    let productUrl = p.landingPageUrl || '';
    if (productUrl && !productUrl.startsWith('http')) {
      productUrl = `https://www.meesho.com${productUrl.startsWith('/') ? '' : '/'}${productUrl}`;
    }
    if (!productUrl) productUrl = `https://www.meesho.com/${encoded}`;

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