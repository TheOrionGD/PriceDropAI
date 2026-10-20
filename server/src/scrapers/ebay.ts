import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';
import { optimizeProductImageUrl } from './webFallback.js';

export async function scrapeEbay(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.ebay.com/sch/i.html?_nkw=${encoded}&_sop=12`;

    const html = await fetchPageHtml(url, {
      referer: 'https://www.ebay.com/',
      jsRender: false,
    });
    if (!html) return [];

    const $ = cheerio.load(html);
    const results: RawScrapedItem[] = [];

    // Conversion rate USD to INR (approx 85)
    const USD_TO_INR = 86;

    $('li.s-item, div.s-item__wrapper').each((_, el) => {
      const card = $(el);
      const title = card.find('.s-item__title span, .s-item__title').first().text().trim();
      if (!title || title.toLowerCase().includes('shop on ebay')) return;

      const priceStr = card.find('.s-item__price').first().text().trim();
      const strikeStr = card.find('.s-item__trending-price .STRIKETHROUGH, .s-item__strike').first().text().trim();
      const relHref = card.find('a.s-item__link').first().attr('href');
      const imgUrl = card.find('.s-item__image-img img, img').first().attr('src');

      // Extract price (handles $59.99 or INR 4,999)
      const numMatch = priceStr.match(/([0-9,.]+)/);
      if (!numMatch) return;
      let price = parseFloat(numMatch[1].replace(/,/g, ''));
      if (priceStr.includes('$')) {
        price = Math.round(price * USD_TO_INR);
      }

      let originalPrice: number | null = null;
      if (strikeStr) {
        const strikeMatch = strikeStr.match(/([0-9,.]+)/);
        if (strikeMatch) {
          originalPrice = parseFloat(strikeMatch[1].replace(/,/g, ''));
          if (strikeStr.includes('$')) originalPrice = Math.round(originalPrice * USD_TO_INR);
        }
      }

      if (title && price && price > 50) {
        results.push({
          store: Store.EBAY,
          title,
          url: relHref || `https://www.ebay.com/sch/i.html?_nkw=${encoded}`,
          price,
          originalPrice,
          discountPercentage: originalPrice && originalPrice > price ? Math.round(((originalPrice - price) / originalPrice) * 100) : null,
          imageUrl: optimizeProductImageUrl(imgUrl || null),
          rating: 4.5,
          reviewCount: null,
          availability: Availability.IN_STOCK,
        });
      }
    });

    return results.slice(0, 10);
  } catch (err: any) {
    console.warn(`[eBay Scraper Error] ${err.message}`);
    return [];
  }
}
