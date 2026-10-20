import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';
import { optimizeProductImageUrl } from './webFallback.js';

export async function scrapeWalmart(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.walmart.com/search?q=${encoded}`;

    // Walmart blocks basic clients aggressively; ZenRows handles it
    const html = await fetchPageHtml(url, {
      referer: 'https://www.walmart.com/',
      jsRender: true,
      wait: 2000,
    });
    if (!html) return [];

    const results: RawScrapedItem[] = [];
    const USD_TO_INR = 86;

    // Check __NEXT_DATA__
    const nextMatch = html.match(/<script id="__NEXT_DATA__" type="application\/json">([\s\S]*?)<\/script>/);
    if (nextMatch) {
      try {
        const nextData = JSON.parse(nextMatch[1]);
        const stacks = nextData?.props?.pageProps?.initialData?.searchResult?.itemStacks || [];
        for (const stack of stacks) {
          const items = stack?.items || [];
          for (const it of items) {
            const title = it.name || it.title;
            const priceVal = it.priceInfo?.currentPrice?.price || it.price;
            const listPriceVal = it.priceInfo?.wasPrice?.price || it.listPrice;
            if (title && priceVal) {
              const price = Math.round(priceVal * USD_TO_INR);
              const originalPrice = listPriceVal ? Math.round(listPriceVal * USD_TO_INR) : null;
              results.push({
                store: Store.WALMART,
                title,
                url: it.canonicalUrl ? `https://www.walmart.com${it.canonicalUrl}` : `https://www.walmart.com/search?q=${encoded}`,
                price,
                originalPrice,
                discountPercentage: originalPrice && originalPrice > price ? Math.round(((originalPrice - price) / originalPrice) * 100) : null,
                imageUrl: optimizeProductImageUrl(it.image || it.imageInfo?.thumbnailUrl || null),
                rating: it.rating?.averageRating || 4.3,
                reviewCount: it.rating?.numberOfReviews || null,
                availability: Availability.IN_STOCK,
              });
            }
          }
        }
      } catch (e) {}
    }

    if (results.length === 0) {
      const $ = cheerio.load(html);
      $('div[data-item-id], div.mb0.ph0-xl').each((_, el) => {
        const card = $(el);
        const title = card.find('span[data-automation-id="product-title"]').first().text().trim();
        const priceText = card.find('div[data-automation-id="product-price"] span.w_iUH7').first().text().replace(/[^0-9.]/g, '');
        const relHref = card.find('a').first().attr('href');
        const imgUrl = card.find('img').first().attr('src');

        const priceVal = priceText ? parseFloat(priceText) : null;
        if (title && priceVal && priceVal > 0) {
          const price = Math.round(priceVal * USD_TO_INR);
          results.push({
            store: Store.WALMART,
            title,
            url: relHref ? `https://www.walmart.com${relHref.startsWith('/') ? '' : '/'}${relHref}` : `https://www.walmart.com/search?q=${encoded}`,
            price,
            originalPrice: null,
            discountPercentage: null,
            imageUrl: optimizeProductImageUrl(imgUrl || null),
            rating: 4.3,
            reviewCount: null,
            availability: Availability.IN_STOCK,
          });
        }
      });
    }

    return results.slice(0, 10);
  } catch (err: any) {
    console.warn(`[Walmart Scraper Error] ${err.message}`);
    return [];
  }
}
