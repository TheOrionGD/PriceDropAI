import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';
import { optimizeProductImageUrl } from './webFallback.js';

export async function scrapeTataCliq(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.tatacliq.com/search/?searchCategory=all&text=${encoded}`;

    const html = await fetchPageHtml(url, {
      referer: 'https://www.tatacliq.com/',
      jsRender: false,
    });
    if (!html) return [];

    const results: RawScrapedItem[] = [];

    // Check for JSON-LD or Next data
    const nextMatch = html.match(/<script id="__NEXT_DATA__" type="application\/json">([\s\S]*?)<\/script>/);
    if (nextMatch) {
      try {
        const nextData = JSON.parse(nextMatch[1]);
        const products = nextData?.props?.pageProps?.searchResult?.products ||
                         nextData?.props?.pageProps?.products || [];
        for (const p of products) {
          const title = p.productName || p.name || p.title;
          const price = p.sellingPrice || p.price || p.offerPrice;
          const origPrice = p.mrpPrice || p.originalPrice || null;
          if (title && price && price > 50) {
            results.push({
              store: Store.TATACLIQ,
              title,
              url: p.webURL ? `https://www.tatacliq.com${p.webURL}` : `https://www.tatacliq.com/search/?searchCategory=all&text=${encoded}`,
              price: parseFloat(price),
              originalPrice: origPrice ? parseFloat(origPrice) : null,
              discountPercentage: p.discountPercent || null,
              imageUrl: optimizeProductImageUrl(p.imageURL || p.imageUrl || null),
              rating: p.averageRating ? parseFloat(p.averageRating) : 4.2,
              reviewCount: p.ratingCount || null,
              availability: Availability.IN_STOCK,
            });
          }
        }
      } catch (e) {}
    }

    // HTML selector fallback
    if (results.length === 0) {
      const $ = cheerio.load(html);
      $('div.ProductModule__base, div[data-test="product-card"]').each((_, el) => {
        const card = $(el);
        const title = card.find('h3, .ProductDescription__productName').first().text().trim();
        const priceText = card.find('.ProductDescription__price, h3.ProductDescription__boldText').first().text().replace(/[^0-9]/g, '');
        const originalPriceText = card.find('strike, .ProductDescription__mrp').first().text().replace(/[^0-9]/g, '');
        const relHref = card.find('a').first().attr('href');
        const imgEl = card.find('img').first();
        const imgUrl = imgEl.attr('src') || imgEl.attr('data-src');

        const price = priceText ? parseFloat(priceText) : null;
        const originalPrice = originalPriceText ? parseFloat(originalPriceText) : null;

        if (title && price && price > 50) {
          results.push({
            store: Store.TATACLIQ,
            title,
            url: relHref ? `https://www.tatacliq.com${relHref.startsWith('/') ? '' : '/'}${relHref}` : `https://www.tatacliq.com/search/?searchCategory=all&text=${encoded}`,
            price,
            originalPrice,
            discountPercentage: originalPrice && originalPrice > price ? Math.round(((originalPrice - price) / originalPrice) * 100) : null,
            imageUrl: optimizeProductImageUrl(imgUrl || null),
            rating: 4.1,
            reviewCount: null,
            availability: Availability.IN_STOCK,
          });
        }
      });
    }

    return results.slice(0, 10);
  } catch (err: any) {
    console.warn(`[Tata CLiQ Scraper Error] ${err.message}`);
    return [];
  }
}
