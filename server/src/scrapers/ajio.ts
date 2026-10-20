import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';
import { optimizeProductImageUrl } from './webFallback.js';

export async function scrapeAjio(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.ajio.com/search/?text=${encoded}`;

    const html = await fetchPageHtml(url, {
      referer: 'https://www.ajio.com/',
      jsRender: false,
    });
    if (!html) return [];

    const results: RawScrapedItem[] = [];

    // Ajio stores data inside window.__PRELOADED_STATE__
    const match = html.match(/window\.__PRELOADED_STATE__\s*=\s*(\{[\s\S]*?\});/);
    if (match) {
      try {
        const state = JSON.parse(match[1]);
        const grid = state?.grid?.entities || [];
        for (const item of grid) {
          const title = item.name || item.brandName ? `${item.brandName} ${item.name}` : null;
          const price = item.price?.value || item.offerPrice?.value;
          const mrp = item.wasPriceData?.value || item.price?.value;
          if (title && price && price > 50) {
            results.push({
              store: Store.AJIO,
              title,
              url: item.url ? `https://www.ajio.com${item.url}` : `https://www.ajio.com/search/?text=${encoded}`,
              price: parseFloat(price),
              originalPrice: mrp ? parseFloat(mrp) : null,
              discountPercentage: item.discountPercent ? parseInt(item.discountPercent, 10) : null,
              imageUrl: optimizeProductImageUrl(item.images?.[0]?.url || item.fnlColorVariantData?.outfitPictureURL || null),
              rating: 4.2,
              reviewCount: null,
              availability: Availability.IN_STOCK,
            });
          }
        }
      } catch (e) {}
    }

    if (results.length === 0) {
      const $ = cheerio.load(html);
      $('div.item, div.rilrtl-products-list__item').each((_, el) => {
        const card = $(el);
        const brand = card.find('.brand').first().text().trim();
        const name = card.find('.nameCls').first().text().trim();
        const title = brand ? `${brand} ${name}` : name;
        const priceText = card.find('.price').first().text().replace(/[^0-9]/g, '');
        const mrpText = card.find('.orginal-price').first().text().replace(/[^0-9]/g, '');
        const relHref = card.find('a').first().attr('href');
        const imgUrl = card.find('img').first().attr('src');

        const price = priceText ? parseFloat(priceText) : null;
        const originalPrice = mrpText ? parseFloat(mrpText) : null;

        if (title && price && price > 50) {
          results.push({
            store: Store.AJIO,
            title,
            url: relHref ? `https://www.ajio.com${relHref.startsWith('/') ? '' : '/'}${relHref}` : `https://www.ajio.com/search/?text=${encoded}`,
            price,
            originalPrice,
            discountPercentage: originalPrice && originalPrice > price ? Math.round(((originalPrice - price) / originalPrice) * 100) : null,
            imageUrl: optimizeProductImageUrl(imgUrl || null),
            rating: 4.2,
            reviewCount: null,
            availability: Availability.IN_STOCK,
          });
        }
      });
    }

    return results.slice(0, 10);
  } catch (err: any) {
    console.warn(`[Ajio Scraper Error] ${err.message}`);
    return [];
  }
}
