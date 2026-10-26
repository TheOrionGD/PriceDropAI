import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { scrapePage } from '../utils/puppeteer.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';
import { optimizeProductImageUrl } from './webFallback.js';

function extractFromHtml(html: string): any[] {
  const products: any[] = [];
  const $ = cheerio.load(html);

  $('a[href*="/p/"]').each((_, el) => {
    const card = $(el);
    const href = card.attr('href');
    if (!href) return;

    let title = card.find('p, span[class*="title"], h5').first().text().trim() ||
                card.find('img').first().attr('alt')?.trim() || '';
    title = title.replace(/₹[\d,]+.*$/, '').trim();
    if (!title || title.length < 3 || title.startsWith('+')) {
      const alt = card.find('img').first().attr('alt')?.trim();
      if (alt && alt.length >= 3) title = alt;
    }
    if (!title || title.length < 3) return;

    const text = card.text();
    const priceMatch = text.match(/₹\s*([\d,]+)/);
    if (!priceMatch) return;
    const price = parseFloat(priceMatch[1].replace(/,/g, ''));
    if (isNaN(price) || price <= 10) return;

    const allPrices = [...text.matchAll(/₹\s*([\d,]+)/g)]
      .map(m => parseFloat(m[1].replace(/,/g, '')))
      .filter(p => !isNaN(p) && p > 10);
    const originalPrice = allPrices.length > 1 && allPrices[1] > price ? allPrices[1] : null;

    const imgEl = card.find('img').first();
    let imageUrl = imgEl.attr('src') || imgEl.attr('data-src') || null;

    products.push({
      title,
      price,
      originalPrice,
      imageUrl,
      landingPageUrl: href,
    });
  });

  return products;
}

function extractFromNextData(html: string): any[] {
  const products: any[] = [];
  const nextDataMatch = html.match(/<script id="__NEXT_DATA__" type="application\/json">([\s\S]*?)<\/script>/);
  if (nextDataMatch) {
    try {
      const data = JSON.parse(nextDataMatch[1]);
      const list = data?.props?.pageProps?.initialState?.searchListing?.listing?.products ||
                   data?.props?.pageProps?.initialState?.plpListing?.listing?.products ||
                   data?.props?.pageProps?.initialState?.search?.products ||
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
  const url = `https://www.meesho.com/search?q=${encoded}&searchType=manual&searchIdentifier=text_search`;

  // Primary: Fetch with jsRender so React search cards hydrate
  let html = await fetchPageHtml(url, { jsRender: true, storeName: 'MEESHO', referer: 'https://www.meesho.com/' });

  // Fallback 1: Direct fast fetch
  if (!html) {
    html = await fetchPageHtml(url, 'https://www.meesho.com/');
  }

  // Fallback 2: Puppeteer browser rendering
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

    if (imageUrl) {
      imageUrl = optimizeProductImageUrl(imageUrl) || imageUrl;
    }

    const discountPercentage = p.originalPrice && p.originalPrice > p.price
      ? Math.round(((p.originalPrice - p.price) / p.originalPrice) * 100)
      : null;

    results.push({
      store: Store.MEESHO,
      title: p.title,
      url: productUrl,
      price: p.price,
      originalPrice: p.originalPrice ?? null,
      discountPercentage,
      imageUrl: imageUrl && imageUrl.startsWith('http') ? imageUrl : null,
      rating: null,
      reviewCount: null,
      availability: Availability.IN_STOCK,
    });
  }

  return results;
}