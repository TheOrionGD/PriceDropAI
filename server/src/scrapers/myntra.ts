import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';

export async function scrapeMyntra(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.myntra.com/${encoded}`;

    const html = await fetchPageHtml(url, 'https://www.myntra.com/');
    if (!html) return [];

    const $ = cheerio.load(html);
    const results: RawScrapedItem[] = [];

    $('li.product-base').each((_, el) => {
      const card = $(el);
      const brand = card.find('h3.product-brand').first().text().trim();
      const productTitle = card.find('h4.product-product').first().text().trim();
      const title = brand ? `${brand} ${productTitle}` : productTitle || query;

      const priceText = card.find('span.product-discountedPrice, span.product-price').first().text().replace(/[^0-9]/g, '');
      const originalPriceText = card.find('span.product-strike').first().text().replace(/[^0-9]/g, '');
      const relHref = card.find('a').first().attr('href') || '';
      const fullUrl = relHref.startsWith('http') ? relHref : `https://www.myntra.com/${relHref}`;

      const imgUrl = card.find('picture.img-responsive img, img.product-image').first().attr('src');
      const ratingText = card.find('div.product-ratingsContainer span').first().text().trim();

      const price = priceText ? parseFloat(priceText) : null;
      const originalPrice = originalPriceText ? parseFloat(originalPriceText) : null;

      if (price && price > 10) {
        let discountPercentage: number | null = null;
        if (originalPrice && originalPrice > price) {
          discountPercentage = Math.round(((originalPrice - price) / originalPrice) * 100);
        }

        const rating = ratingText ? parseFloat(ratingText) : null;

        results.push({
          store: Store.MYNTRA,
          title,
          url: fullUrl,
          price,
          originalPrice,
          discountPercentage,
          imageUrl: imgUrl && imgUrl.startsWith('http') ? imgUrl : null,
          rating,
          reviewCount: null,
          availability: Availability.IN_STOCK,
        });
      }
    });

    return results;
  } catch (err: any) {
    console.error(`[Myntra Scraper] Error for "${query}":`, err.message);
    return [];
  }
}
