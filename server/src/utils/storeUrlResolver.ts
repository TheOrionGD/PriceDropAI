import { Store } from '../types/index.js';

export enum MarketplaceStatus {
  AVAILABLE = 'AVAILABLE',
  OUT_OF_STOCK = 'OUT_OF_STOCK',
  NO_RESULTS = 'NO_RESULTS',
  UNSUPPORTED_CATEGORY = 'UNSUPPORTED_CATEGORY',
  EXTRACTION_FAILED = 'EXTRACTION_FAILED',
  RATE_LIMITED = 'RATE_LIMITED',
  UNKNOWN = 'UNKNOWN',
}

export function toStoreEnum(store: Store | string): Store {
  if (!store) return Store.AMAZON;
  const s = String(store).toUpperCase().replace(/[\s-]/g, '_');
  if (s === 'RELIANCEDIGITAL') return Store.RELIANCE_DIGITAL;
  if (s === 'TATACLIQ') return Store.TATACLIQ;
  return s as Store;
}

/**
 * Normalizes user queries while preserving meaningful specifications
 * (e.g. resistance range, size, weight, adjustability, storage).
 */
export function normalizeSearchQuery(query: string): string {
  if (!query) return '';
  return query
    .toLowerCase()
    .replace(/[\u2018\u2019]/g, "'")
    .replace(/[\u201C\u201D]/g, '"')
    .replace(/\b(\d+)\s*(?:kg|g|lbs|pounds)?\s*(?:-|to)\s*(\d+)\s*(kg|g|lbs|pounds)\b/gi, '$1-$2$3') // normalize "5kg to 60kg", "5 - 60 kg" to "5-60kg"
    .replace(/(\d+)\s*-\s*(\d+)/g, '$1-$2') // normalize "5 - 60" to "5-60"
    .replace(/(\d+)\s*(kg|g|lbs|gb|tb|ml|ltr|l)\b/gi, '$1$2') // normalize "60 kg" to "60kg"
    .replace(/[^\w\s\-.'"]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Extracts key product specifications to prevent merging distinct product variants
 * (such as a 5kg fixed gripper vs a 5-60kg adjustable gripper).
 */
export interface ProductSpecs {
  resistanceRange?: string | null; // e.g. "5-60kg"
  weight?: string | null;          // e.g. "60kg"
  isAdjustable?: boolean;
  storage?: string | null;         // e.g. "128gb"
  capacity?: string | null;        // e.g. "1ltr"
}

export function extractProductSpecs(text: string): ProductSpecs {
  const lower = text.toLowerCase();
  const specs: ProductSpecs = {};

  // Resistance range e.g. "5-60kg", "5kg to 60kg", "5 to 60 kg", "10-40kg"
  const rangeMatch = lower.match(/\b(\d+)\s*(?:kg|lbs|pounds)?\s*(?:-|to)\s*(\d+)\s*(kg|lbs|pounds)?\b/);
  if (rangeMatch) {
    const unit = rangeMatch[3] || 'kg';
    specs.resistanceRange = `${rangeMatch[1]}-${rangeMatch[2]}${unit}`;
  }

  // Single weight e.g. "60kg", "5kg", "100lbs"
  if (!specs.resistanceRange) {
    const weightMatch = lower.match(/\b(\d+)\s*(kg|lbs|pounds)\b/);
    if (weightMatch) {
      specs.weight = `${weightMatch[1]}${weightMatch[2]}`;
    }
  }

  // Adjustability
  if (/\badjustable\b/i.test(lower)) {
    specs.isAdjustable = true;
  } else if (/\bfixed\b/i.test(lower)) {
    specs.isAdjustable = false;
  }

  // Storage
  const storageMatch = lower.match(/\b(64gb|128gb|256gb|512gb|1tb|2tb)\b/);
  if (storageMatch) {
    specs.storage = storageMatch[1];
  }

  // Capacity / Volume
  const capMatch = lower.match(/\b(\d+(?:\.\d+)?)\s*(ltr|litre|liter|ml)\b/);
  if (capMatch) {
    specs.capacity = `${capMatch[1]}${capMatch[2]}`;
  }

  return specs;
}

/**
 * Checks whether specifications of two products clash, which prevents
 * merging a 5kg fixed gripper with a 5-60kg adjustable gripper.
 */
export function areSpecsIncompatible(specsA: ProductSpecs, specsB: ProductSpecs): boolean {
  if (specsA.resistanceRange && specsB.resistanceRange && specsA.resistanceRange !== specsB.resistanceRange) {
    return true;
  }
  if (specsA.resistanceRange && specsB.weight && !specsA.resistanceRange.includes(specsB.weight)) {
    return true;
  }
  if (specsB.resistanceRange && specsA.weight && !specsB.resistanceRange.includes(specsA.weight)) {
    return true;
  }
  if (specsA.weight && specsB.weight && specsA.weight !== specsB.weight) {
    return true;
  }
  if (specsA.isAdjustable !== undefined && specsB.isAdjustable !== undefined && specsA.isAdjustable !== specsB.isAdjustable) {
    return true;
  }
  if (specsA.storage && specsB.storage && specsA.storage !== specsB.storage) {
    return true;
  }
  if (specsA.capacity && specsB.capacity && specsA.capacity !== specsB.capacity) {
    return true;
  }
  return false;
}

/**
 * Determines whether a URL is a verified direct product detail page or merely a search/listing page.
 */
export function isVerifiedProductUrl(store: Store | string, url: string | null | undefined): boolean {
  if (!url || typeof url !== 'string') return false;
  const cleanUrl = url.trim();
  if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://')) return false;

  const lower = cleanUrl.toLowerCase();
  const storeEnum = toStoreEnum(store);

  switch (storeEnum) {
    case Store.AMAZON:
      // Amazon product detail pages have /dp/<ASIN> or /gp/product/<ASIN>
      return (/\/dp\/[a-z0-9]{10}/i.test(cleanUrl) || /\/gp\/product\/[a-z0-9]{10}/i.test(cleanUrl)) && !/[?&]k=/i.test(cleanUrl);

    case Store.FLIPKART:
      // Flipkart product detail pages have /p/itm... or pid=... or /itm...
      return (cleanUrl.includes('/p/itm') || cleanUrl.includes('/p/p') || /[?&]pid=[a-z0-9]+/i.test(cleanUrl)) && !lower.includes('/search');

    case Store.MEESHO:
      // Meesho product pages have /p/<idOrSlug>
      return /\/p\/[a-z0-9]+/i.test(cleanUrl) && !lower.includes('/search');

    case Store.MYNTRA:
      // Myntra product detail pages have /[brand-and-name]/[numeric-id]/buy or /p/
      return /\/\d{5,}\/buy/i.test(cleanUrl) || (/\/\d{5,}(?:\?|$)/.test(cleanUrl) && !lower.includes('/search'));

    case Store.CROMA:
      // Croma product pages have /p/<id> or end with product html
      return /\/p\/[a-z0-9]+/i.test(cleanUrl) && !lower.includes('/searchb');

    case Store.RELIANCE_DIGITAL:
      // Reliance Digital product pages have /p/<id> or /item/<id>
      return (/\/p\/\d+/i.test(cleanUrl) || /\/item\/\d+/i.test(cleanUrl)) && !lower.includes('/search');

    case Store.TATACLIQ:
      // Tata CLiQ product pages have /p-mp... or /p-...
      return /\/p-mp\d+/i.test(cleanUrl) || /\/p-\d+/i.test(cleanUrl);

    case Store.AJIO:
      // AJIO product detail pages have /p/<numeric-or-alpha-id>
      return /\/p\/[a-z0-9_]+/i.test(cleanUrl) && !lower.includes('/search');

    case Store.NYKAA:
      // Nykaa product detail pages have /p/<id>
      return /\/p\/\d+/i.test(cleanUrl) && !lower.includes('/search');

    case Store.BLINKIT:
      // Blinkit product pages have /prn/.../prid/<id>
      return /\/prid\/\d+/i.test(cleanUrl);

    case Store.EBAY:
      // eBay item detail pages have /itm/<id>
      return /\/itm\/\d+/i.test(cleanUrl);

    case Store.WALMART:
      // Walmart item detail pages have /ip/.../<id>
      return /\/ip\/[^\/]+\/\d+/i.test(cleanUrl);

    case Store.TARGET:
      // Target product detail pages have /p/.../-/A-<id>
      return /\/p\/.*\/-\/a-\d+/i.test(cleanUrl);

    case Store.JIOMART:
      // JioMart product pages have /p/...
      return /\/p\/\d+/i.test(cleanUrl) && !lower.includes('/search') && !lower.includes('/products?q=');

    default:
      return false;
  }
}

/**
 * Builds a properly constructed, encoded marketplace search URL as a safe fallback.
 */
export function buildMarketplaceSearchUrl(store: Store | string, searchTerm: string): string {
  const cleanTerm = searchTerm
    .replace(/[^a-zA-Z0-9\s\-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 60);

  const encoded = encodeURIComponent(cleanTerm);
  const hyphenated = cleanTerm.toLowerCase().replace(/\s+/g, '-');
  const storeEnum = toStoreEnum(store);

  switch (storeEnum) {
    case Store.AMAZON:
      return `https://www.amazon.in/s?k=${encoded}`;
    case Store.FLIPKART:
      return `https://www.flipkart.com/search?q=${encoded}`;
    case Store.MEESHO:
      return `https://www.meesho.com/search?q=${encoded}&searchType=manual&searchIdentifier=text_search`;
    case Store.MYNTRA:
      return `https://www.myntra.com/${hyphenated}`;
    case Store.JIOMART:
      return `https://www.jiomart.com/search/${encoded}`;
    case Store.BLINKIT:
      return `https://blinkit.com/s/?q=${encoded}`;
    case Store.CROMA:
      return `https://www.croma.com/searchB?q=${encoded}%3Arelevance`;
    case Store.RELIANCE_DIGITAL:
      return `https://www.reliancedigital.in/search?q=${encoded}:relevance`;
    case Store.TATACLIQ:
      return `https://www.tatacliq.com/search/?searchCategory=all&text=${encoded}`;
    case Store.AJIO:
      return `https://www.ajio.com/search/?text=${encoded}`;
    case Store.NYKAA:
      return `https://www.nykaa.com/search/result/?q=${encoded}&root=search`;
    case Store.EBAY:
      return `https://www.ebay.com/sch/i.html?_nkw=${encoded}`;
    case Store.WALMART:
      return `https://www.walmart.com/search?q=${encoded}`;
    case Store.TARGET:
      return `https://www.target.com/s?searchTerm=${encoded}`;
    default:
      return `https://www.google.com/search?q=${encoded}+shopping`;
  }
}

/**
 * Category compatibility engine.
 * Accurately determines if a marketplace carries a category.
 * Prevents assigning Sports & Fitness gear to electronics stores (Croma, Reliance Digital)
 * or fashion stores (Myntra, Ajio), and prevents fabricated prices.
 */
export function isCategorySupportedByStore(store: Store | string, category: string, query: string = ''): boolean {
  const cat = (category || '').toLowerCase();
  const q = (query || '').toLowerCase();
  const storeEnum = toStoreEnum(store);

  const isSportsOrFitness = cat.includes('sports') || cat.includes('fitness') ||
    /\b(gripper|hand gripper|wrist|forearm|gym|dumbbell|barbell|weight plate|kettlebell|pull up bar|push up bar|resistance band|yoga mat|skipping rope|jump rope|treadmill|exercise bike|cycling|cricket|badminton|football|basketball|volleyball)\b/i.test(q);

  const isTechOrElectronics = cat.includes('mobile') || cat.includes('computer') || cat.includes('tablet') ||
    cat.includes('tv') || cat.includes('audio') || cat.includes('video') || cat.includes('cameras') || cat.includes('gaming') ||
    /\b(phone|iphone|galaxy|pixel|redmi|oneplus|realme|laptop|macbook|thinkpad|monitor|keyboard|mouse|headphone|earphone|earbud|airpod|soundbar|speaker|smartwatch|television|fridge|refrigerator|ac|air conditioner|washing machine|microwave)\b/i.test(q);

  const isFashionOrFootwear = cat.includes('fashion') || cat.includes('footwear') || cat.includes('apparel') ||
    /\b(shirt|t-shirt|pant|jeans|saree|kurti|kurta|dress|jacket|coat|hoodie|shoe|sneaker|boot|sandal|heel|slipper)\b/i.test(q);

  const isBeautyOrCosmetics = cat.includes('beauty') || cat.includes('grooming') || cat.includes('skincare') ||
    /\b(perfume|deodorant|fragrance|shampoo|conditioner|serum|sunscreen|moisturizer|face wash|lipstick|makeup|foundation|eyeliner|trimmer|shaver)\b/i.test(q);

  const isGrocery = cat.includes('grocer') || cat.includes('gourmet') || cat.includes('food') ||
    /\b(atta|flour|rice|dal|pulses|oil|ghee|sugar|tea|coffee|biscuit|cookie|chips|snack|chocolate|spices|masala|milk)\b/i.test(q);

  const isHeavyFurniture = cat.includes('furniture') ||
    /\b(sofa|couch|bed|mattress|wardrobe|bookshelf|dining table)\b/i.test(q);

  const isHighEndFlagship = /\b(macbook|iphone 15|iphone 16|iphone 14|s24|s23|fold|playstation 5|ps5|xbox series|rtx)\b/i.test(q);

  switch (storeEnum) {
    case Store.CROMA:
    case Store.RELIANCE_DIGITAL:
      // STRICT Consumer Electronics & Appliances specialists:
      // They do NOT sell sports/gym fitness equipment, clothes, shoes, makeup, groceries, or furniture!
      if (isSportsOrFitness) return false;
      if (isFashionOrFootwear) return false;
      if (isBeautyOrCosmetics) return false;
      if (isGrocery) return false;
      if (isHeavyFurniture) return false;
      return isTechOrElectronics;

    case Store.MYNTRA:
    case Store.AJIO:
      // Fashion, Footwear, Lifestyle & Personal Care:
      // They do NOT sell gym fitness equipment, electronics, heavy appliances, or groceries!
      if (isSportsOrFitness) return false;
      if (isTechOrElectronics) return false;
      if (isGrocery) return false;
      if (isHeavyFurniture) return false;
      return isFashionOrFootwear || isBeautyOrCosmetics;

    case Store.NYKAA:
      // Cosmetics, Beauty, Skincare & Nykaa Fashion:
      // Does NOT sell sports/gym equipment, electronics, groceries, or furniture!
      if (isSportsOrFitness) return false;
      if (isTechOrElectronics) return false;
      if (isGrocery) return false;
      if (isHeavyFurniture) return false;
      return isBeautyOrCosmetics || isFashionOrFootwear;

    case Store.BLINKIT:
      // Quick grocery delivery & daily essentials:
      // Does NOT sell gym weights/grippers, laptops, large TVs, appliances, or flagships
      if (isSportsOrFitness) return false;
      if (isHighEndFlagship) return false;
      if (isHeavyFurniture) return false;
      return isGrocery || isBeautyOrCosmetics || /\b(cable|charger|bulb|earphone)\b/i.test(q);

    case Store.JIOMART:
      // Open horizontal marketplace with Groceries, Electronics, Fashion, Home & Sports department:
      if (/\b(zara|h&m|gucci|prada)\b/i.test(q)) return false;
      return true;

    case Store.TATACLIQ:
      // Fashion, Luxury & Consumer Electronics:
      // Does NOT carry gym fitness equipment or perishable groceries
      if (isSportsOrFitness) return false;
      if (isGrocery) return false;
      return isTechOrElectronics || isFashionOrFootwear || isBeautyOrCosmetics;

    case Store.EBAY:
    case Store.WALMART:
    case Store.TARGET:
      if (isGrocery && /\b(amul|patanjali|haldiram|aashirvaad)\b/i.test(q)) return false;
      return true;

    case Store.MEESHO:
      // Budget marketplace with fashion, household, and basic fitness accessories (resistance bands, grippers)
      if (isHighEndFlagship) return false;
      return true;

    case Store.AMAZON:
    case Store.FLIPKART:
      // Universal horizontal e-commerce platforms carry all categories
      return true;

    default:
      return true;
  }
}
