import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';

export async function scrapeFlipkart(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.flipkart.com/search?q=${encoded}`;

    const html = await fetchPageHtml(url, 'https://www.flipkart.com/');
    if (!html) return [];

    const $ = cheerio.load(html);
    const results: RawScrapedItem[] = [];

    $('div[data-id], div.cPHDOP, div._1AtVbE').each((_, el) => {
      const card = $(el);

      const title = card.find('div.KzDlHZ, div._4rR01T, a.s1Q9rs, a.wjcEIp').first().text().trim();
      const priceText = card.find('div.Nx9bqj, div._30jeq3, div.hl05eU div._25b18c div').first().text().replace(/[^0-9]/g, '');
      const originalPriceText = card.find('div.yRaY8j, div._3I9_wc').first().text().replace(/[^0-9]/g, '');
      const discountText = card.find('div.UkUFwK, div._3Ay6Sb').first().text().replace(/[^0-9]/g, '');
      const ratingText = card.find('div.XQDdHH, div._3LWZlK').first().text().trim();
      const reviewCountText = card.find('span.WJhBDe, span._2_R_DZ').first().text().replace(/[^0-9]/g, '');

      const relHref = card.find('a.CGtC5Q, a._1fQZEK, a.s1Q9rs, a.VJA3rP, a[href*="/p/"]').first().attr('href');
      const imgUrl = card.find('img._53qgcR, img.DByuf4, img._396cs4, img[src*="flixcart"]').first().attr('src')
        || card.find('img').first().attr('src');

      const price = priceText ? parseFloat(priceText) : null;
      const originalPrice = originalPriceText ? parseFloat(originalPriceText) : null;
      const discountPercentage = discountText ? parseFloat(discountText) : null;

      if (title && price && price > 10) {
        let fullUrl = relHref || '';
        if (fullUrl && !fullUrl.startsWith('http')) {
          fullUrl = `https://www.flipkart.com${fullUrl}`;
        }

        const rating = ratingText ? parseFloat(ratingText) : null;
        const reviewCount = reviewCountText ? parseInt(reviewCountText, 10) : null;

        results.push({
          store: Store.FLIPKART,
          title,
          url: fullUrl || url,
          price,
          originalPrice,
          discountPercentage,
          imageUrl: imgUrl && imgUrl.startsWith('http') ? imgUrl : null,
          rating,
          reviewCount,
          availability: Availability.IN_STOCK,
        });
      }
    });

    return results;
  } catch (err: any) {
    console.error(`[Flipkart Scraper] Error for "${query}":`, err.message);
    return [];
  }
}
