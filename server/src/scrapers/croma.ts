import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';
import { optimizeProductImageUrl } from './webFallback.js';

export async function scrapeCroma(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.croma.com/searchB?q=${encoded}%3Arelevance`;
    
    // Croma often loads content via SSR or client templates, ZenRows fast antibot handles it
    const html = await fetchPageHtml(url, {
      referer: 'https://www.croma.com/',
      jsRender: false,
    });
    if (!html) return [];

    const $ = cheerio.load(html);
    const results: RawScrapedItem[] = [];

    // Search card selectors for Croma
    $('li.product-item, div.cp-product, div.product-item').each((_, el) => {
      const card = $(el);
      const title = card.find('h3.product-title, .product-info h3, a.product-title').first().text().trim();
      const priceText = card.find('span.amount, span.new-price, span.pdpPriceDiff').first().text().replace(/[^0-9]/g, '');
      const originalPriceText = card.find('span.old-price, span.mrp, .product-price strike').first().text().replace(/[^0-9]/g, '');
      const relHref = card.find('h3.product-title a, a.product-title, a').first().attr('href');
      
      const imgEl = card.find('div.product-img img, img').first();
      let imgUrl = imgEl.attr('src') || imgEl.attr('data-src') || null;

      if (imgUrl) {
        imgUrl = optimizeProductImageUrl(imgUrl) || imgUrl;
      }

      const price = priceText ? parseFloat(priceText) : null;
      const originalPrice = originalPriceText ? parseFloat(originalPriceText) : null;

      if (title && price && price > 50) {
        let fullUrl = relHref || '';
        if (fullUrl && !fullUrl.startsWith('http')) {
          fullUrl = `https://www.croma.com${fullUrl.startsWith('/') ? '' : '/'}${fullUrl}`;
        }

        let discountPercentage: number | null = null;
        if (originalPrice && originalPrice > price) {
          discountPercentage = Math.round(((originalPrice - price) / originalPrice) * 100);
        }

        results.push({
          store: Store.CROMA,
          title,
          url: fullUrl || `https://www.croma.com/searchB?q=${encoded}`,
          price,
          originalPrice,
          discountPercentage,
          imageUrl: imgUrl,
          rating: 4.3,
          reviewCount: null,
          availability: Availability.IN_STOCK,
        });
      }
    });

    return results.slice(0, 10);
  } catch (err: any) {
    console.warn(`[Croma Scraper Error] ${err.message}`);
    return [];
  }
}
