import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';
import { optimizeProductImageUrl } from './webFallback.js';

export async function scrapeNykaa(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.nykaa.com/search/result/?q=${encoded}&root=search`;

    const html = await fetchPageHtml(url, {
      referer: 'https://www.nykaa.com/',
      jsRender: false,
    });
    if (!html) return [];

    const results: RawScrapedItem[] = [];

    // Nykaa JSON in window.__PRELOADED_STATE__
    const match = html.match(/window\.__PRELOADED_STATE__\s*=\s*(\{[\s\S]*?\});/);
    if (match) {
      try {
        const state = JSON.parse(match[1]);
        const products = state?.search?.products || state?.products || [];
        for (const p of products) {
          const title = p.title || p.name;
          const price = p.finalPrice || p.price;
          const mrp = p.mrp || p.originalPrice;
          if (title && price && price > 10) {
            results.push({
              store: Store.NYKAA,
              title,
              url: p.actionUrl ? `https://www.nykaa.com${p.actionUrl}` : `https://www.nykaa.com/search/result/?q=${encoded}`,
              price: parseFloat(price),
              originalPrice: mrp ? parseFloat(mrp) : null,
              discountPercentage: p.discount || null,
              imageUrl: optimizeProductImageUrl(p.imageUrl || p.media?.[0]?.url || null),
              rating: p.rating ? parseFloat(p.rating) : 4.4,
              reviewCount: p.ratingCount || null,
              availability: Availability.IN_STOCK,
            });
          }
        }
      } catch (e) {}
    }

    if (results.length === 0) {
      const $ = cheerio.load(html);
      $('div.productWrapper, div[data-test-id="product-card"]').each((_, el) => {
        const card = $(el);
        const title = card.find('.title, .css-xrzmfa, a.css-qlopj5').first().text().trim();
        const priceText = card.find('.css-111z9ua, .post-discount, .css-17x46n5').first().text().replace(/[^0-9]/g, '');
        const mrpText = card.find('.css-yvj7bv, strike').first().text().replace(/[^0-9]/g, '');
        const relHref = card.find('a').first().attr('href');
        const imgUrl = card.find('img').first().attr('src');

        const price = priceText ? parseFloat(priceText) : null;
        const originalPrice = mrpText ? parseFloat(mrpText) : null;

        if (title && price && price > 10) {
          results.push({
            store: Store.NYKAA,
            title,
            url: relHref ? `https://www.nykaa.com${relHref.startsWith('/') ? '' : '/'}${relHref}` : `https://www.nykaa.com/search/result/?q=${encoded}`,
            price,
            originalPrice,
            discountPercentage: originalPrice && originalPrice > price ? Math.round(((originalPrice - price) / originalPrice) * 100) : null,
            imageUrl: optimizeProductImageUrl(imgUrl || null),
            rating: 4.4,
            reviewCount: null,
            availability: Availability.IN_STOCK,
          });
        }
      });
    }

    return results.slice(0, 10);
  } catch (err: any) {
    console.warn(`[Nykaa Scraper Error] ${err.message}`);
    return [];
  }
}
