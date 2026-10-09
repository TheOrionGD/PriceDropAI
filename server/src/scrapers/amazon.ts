import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';
import { optimizeProductImageUrl } from './webFallback.js';

export async function scrapeAmazon(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.amazon.in/s?k=${encoded}`;
    
    const html = await fetchPageHtml(url, 'https://www.amazon.in/');
    if (!html) return [];

    const $ = cheerio.load(html);
    const results: RawScrapedItem[] = [];

    $('div[data-component-type="s-search-result"]').each((_, el) => {
      const card = $(el);
      
      const title = card.find('h2 span, span.a-size-medium, span.a-size-base-plus').first().text().trim();
      const priceText = card.find('span.a-price-whole').first().text().replace(/[^0-9]/g, '');
      const originalPriceText = card.find('span.a-price.a-text-price span.a-offscreen').first().text().replace(/[^0-9]/g, '');
      const ratingText = card.find('span.a-icon-alt, i.a-icon-star-small span').first().text().trim();
      const reviewCountText = card.find('span.a-size-base.s-underline-text, span.s-underline-text').first().text().replace(/[^0-9]/g, '');
      
      const relHref = card.find('h2 a, a.a-link-normal.s-no-outline').first().attr('href');
      const imgEl = card.find('img.s-image, img.a-dynamic-image').first();
      let imgUrl = imgEl.attr('src') || imgEl.attr('data-image-src');
      if (!imgUrl || imgUrl.includes('grey-pixel') || imgUrl.includes('transparent-pixel')) {
        const srcset = imgEl.attr('srcset') || imgEl.attr('data-image-srcset');
        if (srcset) {
          const parts = srcset.split(',').map(s => s.trim().split(' ')[0]).filter(p => p && !p.includes('grey-pixel'));
          if (parts.length > 0) {
            imgUrl = parts[parts.length - 1];
          }
        }
      }

      if (imgUrl) {
        imgUrl = optimizeProductImageUrl(imgUrl) || imgUrl;
      }

      const price = priceText ? parseFloat(priceText) : null;
      const originalPrice = originalPriceText ? parseFloat(originalPriceText) : null;

      if (title && price && price > 10) {
        let fullUrl = relHref || '';
        if (fullUrl && !fullUrl.startsWith('http')) {
          fullUrl = `https://www.amazon.in${fullUrl}`;
        }

        let discountPercentage: number | null = null;
        if (originalPrice && originalPrice > price) {
          discountPercentage = Math.round(((originalPrice - price) / originalPrice) * 100);
        }

        const ratingMatch = ratingText.match(/^([0-9.]+)/);
        const rating = ratingMatch ? parseFloat(ratingMatch[1]) : null;
        const reviewCount = reviewCountText ? parseInt(reviewCountText, 10) : null;

        results.push({
          store: Store.AMAZON,
          title,
          url: fullUrl || url,
          price,
          originalPrice,
          discountPercentage,
          imageUrl: imgUrl || null,
          rating,
          reviewCount,
          availability: Availability.IN_STOCK,
        });
      }
    });

    return results;
  } catch (err: any) {
    console.error(`[Amazon Scraper] Error for "${query}":`, err.message);
    return [];
  }
}
