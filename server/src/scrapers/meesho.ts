import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';

export async function scrapeMeesho(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.meesho.com/search?q=${encoded}`;

    const html = await fetchPageHtml(url, 'https://www.meesho.com/');
    if (!html) return [];

    const $ = cheerio.load(html);
    const results: RawScrapedItem[] = [];

    $('a[href*="/p/"]').each((_, el) => {
      const card = $(el);
      const relHref = card.attr('href') || '';
      const fullUrl = relHref.startsWith('http') ? relHref : `https://www.meesho.com${relHref}`;

      const title = card.find('p, h5, span').first().text().trim() || query;
      const priceText = card.find('h5:contains("₹"), span:contains("₹"), p:contains("₹")').first().text().replace(/[^0-9]/g, '');
      const price = priceText ? parseFloat(priceText) : null;
      const imgUrl = card.find('img').first().attr('src');
      const ratingText = card.find('span:contains("★"), span:contains("4.") , span:contains("3.")').first().text().trim();
      const ratingMatch = ratingText.match(/([0-9.]+)/);

      if (price && price > 10) {
        results.push({
          store: Store.MEESHO,
          title: title.length > 5 ? title : query,
          url: fullUrl,
          price,
          originalPrice: null,
          discountPercentage: null,
          imageUrl: imgUrl && imgUrl.startsWith('http') ? imgUrl : null,
          rating: ratingMatch ? parseFloat(ratingMatch[1]) : null,
          reviewCount: null,
          availability: Availability.IN_STOCK,
        });
      }
    });

    return results;
  } catch (err: any) {
    console.error(`[Meesho Scraper] Error for "${query}":`, err.message);
    return [];
  }
}
