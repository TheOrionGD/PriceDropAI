import { RawScrapedItem, Store, Availability } from '../types/index.js';
import { fetchPageHtml } from '../utils/htmlFetcher.js';

export async function scrapeMyntra(query: string): Promise<RawScrapedItem[]> {
  try {
    const encoded = encodeURIComponent(query.trim());
    const url = `https://www.myntra.com/search?rawQuery=${encoded}`;

    const html = await fetchPageHtml(url, 'https://www.myntra.com/');
    if (!html) return [];

    const myxIdx = html.indexOf('window.__myx =');
    if (myxIdx < 0) return [];

    const scriptEnd = html.indexOf('</script>', myxIdx);
    if (scriptEnd < 0) return [];

    const jsContent = html.slice(myxIdx, scriptEnd);
    const jsonStart = jsContent.indexOf('{');
    if (jsonStart < 0) return [];

    const jsonStr = jsContent.slice(jsonStart).trim().replace(/;$/, '');
    const myx = JSON.parse(jsonStr);

    const searchData = myx?.searchData?.results;
    if (!searchData || !searchData.totalCount || searchData.totalCount === 0) {
      return [];
    }

    const products = searchData.products;
    if (!Array.isArray(products) || products.length === 0) {
      return [];
    }

    const results: RawScrapedItem[] = [];

    for (const p of products) {
      const title = p.productName || p.product || '';
      if (!title) continue;

      const price = p.price ?? null;
      if (!price || price <= 10) continue;

      const originalPrice = p.mrp || p.originalPrice || null;
      const discountPct = originalPrice && originalPrice > price
        ? Math.round(((originalPrice - price) / originalPrice) * 100)
        : null;

      let productUrl = p.landingPageUrl || '';
      if (productUrl && !productUrl.startsWith('http')) {
        productUrl = `https://www.myntra.com/${productUrl.startsWith('/') ? productUrl.slice(1) : productUrl}`;
      }
      if (!productUrl) productUrl = `https://www.myntra.com/search?rawQuery=${encoded}`;

      let imageUrl = p.searchImage || p.imageUrl || null;
      if (imageUrl && !imageUrl.startsWith('http')) {
        if (imageUrl.startsWith('//')) imageUrl = 'https:' + imageUrl;
        else if (imageUrl.startsWith('/')) imageUrl = `https://www.myntra.com${imageUrl}`;
        else imageUrl = `https://www.myntra.com/${imageUrl}`;
      }

      results.push({
        store: Store.MYNTRA,
        title,
        url: productUrl,
        price,
        originalPrice,
        discountPercentage: discountPct,
        imageUrl: imageUrl && imageUrl.startsWith('http') ? imageUrl : null,
        rating: typeof p.rating === 'number' ? Math.round(p.rating * 100) / 100 : null,
        reviewCount: p.ratingCount ?? null,
        availability: Availability.IN_STOCK,
      });
    }

    return results;
  } catch (err: any) {
    console.error(`[Myntra Scraper] Error for "${query}":`, err.message);
    return [];
  }
}