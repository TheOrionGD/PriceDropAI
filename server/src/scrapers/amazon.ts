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
      
      const titleSpans = card.find('h2 a span').map((_, s) => $(s).text().trim()).get().filter(Boolean);
      let title = titleSpans.length > 0
        ? titleSpans.join(' ')
        : (card.find('h2 a').text().trim() ||
           card.find('h2').text().trim() ||
           card.find('span.a-size-medium, span.a-size-base-plus').last().text().trim());
      title = title
        .replace(/([A-Z]{2,})([A-Z][a-z]+)/g, '$1 $2')
        .replace(/\s+/g, ' ')
        .trim();
      
      const priceText = card.find('span.a-price-whole').first().text().replace(/[^0-9]/g, '');
      const originalPriceText = card.find('span.a-price.a-text-price span.a-offscreen').first().text().replace(/[^0-9]/g, '');
      const ratingText = card.find('span.a-icon-alt, i.a-icon-star-small span').first().text().trim();
      const reviewCountText = card.find('span.a-size-base.s-underline-text, span.s-underline-text').first().text().replace(/[^0-9]/g, '');
      
      const relHref = card.find('h2 a, a.a-link-normal.s-no-outline').first().attr('href');
      
      // Extract ASIN from card attribute or URL
      let asin = card.attr('data-asin')?.trim() || null;
      if (!asin && relHref) {
        const decodedHref = decodeURIComponent(relHref);
        const asinMatch = decodedHref.match(/\/dp\/([A-Z0-9]{10})/i) || decodedHref.match(/\/gp\/product\/([A-Z0-9]{10})/i);
        if (asinMatch) {
          asin = asinMatch[1].toUpperCase();
        }
      }

      // Detect whether this card is a sponsored advertisement
      const isSponsored = card.find('.puis-sponsored-label-text, .s-sponsored-label-info-icon, .s-label-popover-default, .puis-label-popover-default').length > 0 ||
                          card.hasClass('AdHolder') ||
                          (card.attr('data-component-props') || '').includes('s-sponsored') ||
                          card.find('span.a-badge-text:contains("Sponsored")').length > 0;

      // Extract product image strictly from this specific card
      const imgEl = card.find('img.s-image, img.a-dynamic-image, .s-product-image-container img').first();
      let imgUrl = imgEl.attr('src') || imgEl.attr('data-image-src') || imgEl.attr('data-src') || imgEl.attr('data-old-hires');
      if (!imgUrl || imgUrl.includes('grey-pixel') || imgUrl.includes('transparent-pixel') || imgUrl.includes('1x1') || imgUrl.includes('blank.gif')) {
        const srcset = imgEl.attr('srcset') || imgEl.attr('data-image-srcset');
        if (srcset) {
          const parts = srcset.split(',').map(s => s.trim().split(' ')[0]).filter(p => p && !p.includes('grey-pixel') && !p.includes('transparent-pixel') && !p.includes('1x1'));
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
        let fullUrl = asin ? `https://www.amazon.in/dp/${asin}` : (relHref || '');
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
          asin: asin || null,
          isSponsored: !!isSponsored,
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
