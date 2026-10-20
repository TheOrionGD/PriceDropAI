import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';
import { optimizeProductImageUrl } from './webFallback.js';

export async function scrapeRelianceDigital(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.reliancedigital.in/search?q=${encoded}:relevance`;
    
    const html = await fetchPageHtml(url, {
      referer: 'https://www.reliancedigital.in/',
      jsRender: false,
    });
    if (!html) return [];

    const $ = cheerio.load(html);
    const results: RawScrapedItem[] = [];

    // Search card selectors
    $('li.grid, div.sp, div.product-card').each((_, el) => {
      const card = $(el);
      const title = card.find('.sp__name, .product-card__title, p.sp__name').first().text().trim();
      const priceText = card.find('.gpc__price, .sp__price, span.TextWeb__Text-sc-1cyx778-0').first().text().replace(/[^0-9]/g, '');
      const originalPriceText = card.find('.mrp, strike, .sp__mrp').first().text().replace(/[^0-9]/g, '');
      const relHref = card.find('a').first().attr('href');
      
      const imgEl = card.find('img').first();
      let imgUrl = imgEl.attr('data-srcset') || imgEl.attr('data-src') || imgEl.attr('src') || null;
      if (imgUrl && imgUrl.startsWith('//')) {
        imgUrl = `https:${imgUrl}`;
      }

      if (imgUrl) {
        imgUrl = optimizeProductImageUrl(imgUrl) || imgUrl;
      }

      const price = priceText ? parseFloat(priceText) : null;
      const originalPrice = originalPriceText ? parseFloat(originalPriceText) : null;

      if (title && price && price > 50) {
        let fullUrl = relHref || '';
        if (fullUrl && !fullUrl.startsWith('http')) {
          fullUrl = `https://www.reliancedigital.in${fullUrl.startsWith('/') ? '' : '/'}${fullUrl}`;
        }

        let discountPercentage: number | null = null;
        if (originalPrice && originalPrice > price) {
          discountPercentage = Math.round(((originalPrice - price) / originalPrice) * 100);
        }

        results.push({
          store: Store.RELIANCE_DIGITAL,
          title,
          url: fullUrl || `https://www.reliancedigital.in/search?q=${encoded}`,
          price,
          originalPrice,
          discountPercentage,
          imageUrl: imgUrl,
          rating: 4.2,
          reviewCount: null,
          availability: Availability.IN_STOCK,
        });
      }
    });

    return results.slice(0, 10);
  } catch (err: any) {
    console.warn(`[Reliance Digital Scraper Error] ${err.message}`);
    return [];
  }
}
