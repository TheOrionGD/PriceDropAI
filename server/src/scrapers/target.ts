import * as cheerio from 'cheerio';
import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';
import { optimizeProductImageUrl } from './webFallback.js';

export async function scrapeTarget(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.target.com/s?searchTerm=${encoded}`;

    const html = await fetchPageHtml(url, {
      referer: 'https://www.target.com/',
      jsRender: true,
      wait: 2000,
    });
    if (!html) return [];

    const results: RawScrapedItem[] = [];
    const USD_TO_INR = 86;

    // Target Redux state or JSON
    const stateMatch = html.match(/__TGT_DATA__\s*=\s*JSON\.parse\("([\s\S]*?)"\);/);
    if (stateMatch) {
      try {
        const decodedJson = stateMatch[1].replace(/\\"/g, '"').replace(/\\\\/g, '\\');
        const data = JSON.parse(decodedJson);
        const products = data?.search?.response?.products || [];
        for (const p of products) {
          const title = p.item?.product_description?.title;
          const priceVal = p.price?.current_retail;
          const regPriceVal = p.price?.reg_retail;
          if (title && priceVal) {
            results.push({
              store: Store.TARGET,
              title,
              url: p.item?.enrichment?.buy_url || `https://www.target.com/s?searchTerm=${encoded}`,
              price: Math.round(priceVal * USD_TO_INR),
              originalPrice: regPriceVal ? Math.round(regPriceVal * USD_TO_INR) : null,
              discountPercentage: regPriceVal && regPriceVal > priceVal ? Math.round(((regPriceVal - priceVal) / regPriceVal) * 100) : null,
              imageUrl: optimizeProductImageUrl(p.item?.enrichment?.images?.primary_image_url || null),
              rating: p.ratings_and_reviews?.statistics?.rating?.average || 4.4,
              reviewCount: p.ratings_and_reviews?.statistics?.rating?.count || null,
              availability: Availability.IN_STOCK,
            });
          }
        }
      } catch (e) {}
    }

    if (results.length === 0) {
      const $ = cheerio.load(html);
      $('div[data-test="@web/ProductCard/body"], div.styles__StyledCardWrapper').each((_, el) => {
        const card = $(el);
        const title = card.find('a[data-test="product-title"]').first().text().trim();
        const priceText = card.find('span[data-test="current-price"]').first().text().replace(/[^0-9.]/g, '');
        const relHref = card.find('a[data-test="product-title"]').first().attr('href');
        const imgUrl = card.find('picture img').first().attr('src');

        const priceVal = priceText ? parseFloat(priceText) : null;
        if (title && priceVal && priceVal > 0) {
          results.push({
            store: Store.TARGET,
            title,
            url: relHref ? `https://www.target.com${relHref.startsWith('/') ? '' : '/'}${relHref}` : `https://www.target.com/s?searchTerm=${encoded}`,
            price: Math.round(priceVal * USD_TO_INR),
            originalPrice: null,
            discountPercentage: null,
            imageUrl: optimizeProductImageUrl(imgUrl || null),
            rating: 4.4,
            reviewCount: null,
            availability: Availability.IN_STOCK,
          });
        }
      });
    }

    return results.slice(0, 10);
  } catch (err: any) {
    console.warn(`[Target Scraper Error] ${err.message}`);
    return [];
  }
}
