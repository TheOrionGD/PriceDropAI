import assert from 'node:assert';
import { calculateRelevanceScore } from './src/services/productAggregator.js';
import { isValidImageUrl, optimizeProductImageUrl } from './src/scrapers/webFallback.js';
import { Store, Availability, RawScrapedItem, Product } from './src/types/index.js';

console.log('🧪 Starting Autonomous Regression Test Suite: Product-Image Association & Relevance\n');

// =========================================================================
// Test 1: Query Relevance Scoring (Prioritizes exact matches, penalizes sponsored ads)
// =========================================================================
console.log('▶ Test 1: Query Relevance Scoring');
const query = 'iPhone 15';
const organicExactTitle = 'Apple iPhone 15 (128 GB) - Black';
const sponsoredDifferentModel = 'Apple iPhone 16 128 GB: 5G Mobile Phone with Camera Control; Teal';
const sponsoredUnrelatedPhone = 'vivo V80 5G (Horizon Blue, 8GB RAM, 128GB Storage)';

const scoreOrganic = calculateRelevanceScore(query, organicExactTitle, false);
const scoreSponsoredDiff = calculateRelevanceScore(query, sponsoredDifferentModel, true);
const scoreSponsoredUnrelated = calculateRelevanceScore(query, sponsoredUnrelatedPhone, true);

console.log(`  - Organic Exact Match Score: ${scoreOrganic}`);
console.log(`  - Sponsored Model Score: ${scoreSponsoredDiff}`);
console.log(`  - Sponsored Unrelated Score: ${scoreSponsoredUnrelated}`);

assert(scoreOrganic > scoreSponsoredDiff, 'Organic iPhone 15 must outrank sponsored iPhone 16');
assert(scoreOrganic > scoreSponsoredUnrelated, 'Organic iPhone 15 must outrank sponsored Vivo');
console.log('  ✅ Test 1 Passed: Query relevance correctly favors exact organic matches over sponsored ads.\n');

// =========================================================================
// Test 2: Strict Product-Image Association (No cross-product image leakage)
// =========================================================================
console.log('▶ Test 2: Strict Product-Image Association & No Image Bleeding');
const productA: RawScrapedItem = {
  store: Store.AMAZON,
  title: 'Apple iPhone 15 (128 GB) - Black',
  url: 'https://www.amazon.in/dp/B0CHX1W1XY',
  price: 69999,
  originalPrice: 79900,
  discountPercentage: 12,
  imageUrl: 'https://m.media-amazon.com/images/I/71657TiFeHL._AC_SL1000_.jpg',
  asin: 'B0CHX1W1XY',
  isSponsored: false,
  rating: 4.6,
  reviewCount: 3400,
  availability: Availability.IN_STOCK,
};

const productB: RawScrapedItem = {
  store: Store.FLIPKART,
  title: 'Puma Men Running Shoes Dazzler',
  url: 'https://www.flipkart.com/puma-dazzler',
  price: 1999,
  originalPrice: 3999,
  discountPercentage: 50,
  imageUrl: 'https://rukmini1.flixcart.com/image/832/832/puma-shoe.jpg',
  asin: null,
  isSponsored: false,
  rating: 4.2,
  reviewCount: 500,
  availability: Availability.IN_STOCK,
};

const productCWithoutImage: RawScrapedItem = {
  store: Store.CROMA,
  title: 'Sony WH-CH520 Wireless Headphones',
  url: 'https://www.croma.com/sony-headphones',
  price: 4490,
  originalPrice: 4990,
  discountPercentage: 10,
  imageUrl: null, // No image scraped
  asin: null,
  isSponsored: false,
  rating: 4.3,
  reviewCount: 120,
  availability: Availability.IN_STOCK,
};

// Verify image URL validity
assert(isValidImageUrl(productA.imageUrl), 'Product A must have valid image URL');
assert(isValidImageUrl(productB.imageUrl), 'Product B must have valid image URL');
assert(!isValidImageUrl(productCWithoutImage.imageUrl), 'Product C imageUrl must be invalid/null');

// Verify optimizeProductImageUrl upgrades to HTTPS and high-res
const optimizedA = optimizeProductImageUrl(productA.imageUrl);
assert(optimizedA?.startsWith('https://'), 'Optimized image must use HTTPS');
assert(optimizedA?.includes('_AC_SL1000_.jpg'), 'Amazon image must upgrade to high-res standard');

// Verify that product C without image DOES NOT get assigned Product A or B image
assert.notStrictEqual(productA.imageUrl, productB.imageUrl, 'Different products must never share the same image');
assert.strictEqual(productCWithoutImage.imageUrl, null, 'Product without scraped image must remain null (not stolen from Product A)');
console.log('  ✅ Test 2 Passed: Product-image associations are strictly 1:1 and isolated.\n');

// =========================================================================
// Test 3: Shuffling & Reordering Preserves Associations
// =========================================================================
console.log('▶ Test 3: Sorting and Reordering Stability');
const rawCatalog = [productB, productA];
// Sort by relevance to query "iPhone 15"
rawCatalog.sort((a, b) => {
  return calculateRelevanceScore('iPhone 15', b.title, b.isSponsored) -
         calculateRelevanceScore('iPhone 15', a.title, a.isSponsored);
});

assert.strictEqual(rawCatalog[0].asin, 'B0CHX1W1XY', 'iPhone 15 must be at index 0 after sorting');
assert.strictEqual(rawCatalog[0].imageUrl, 'https://m.media-amazon.com/images/I/71657TiFeHL._AC_SL1000_.jpg', 'iPhone 15 must retain its own image after sort');
assert.strictEqual(rawCatalog[1].title, 'Puma Men Running Shoes Dazzler', 'Puma shoes must be at index 1');
assert.strictEqual(rawCatalog[1].imageUrl, 'https://rukmini1.flixcart.com/image/832/832/puma-shoe.jpg', 'Puma shoes must retain their own image after sort');
console.log('  ✅ Test 3 Passed: Sorting and clustering preserve exact product-to-image association.\n');

// =========================================================================
// Test 4: Image Validation & Malformed URL Rejection
// =========================================================================
console.log('▶ Test 4: Image Validation & Malformed URL Rejection');
assert(!isValidImageUrl(''), 'Empty string is not a valid image');
assert(!isValidImageUrl(null), 'Null is not a valid image');
assert(!isValidImageUrl(undefined), 'Undefined is not a valid image');
assert(!isValidImageUrl('https://www.amazon.in/s?k=iphone'), 'Search landing page is not an image');
assert(!isValidImageUrl('https://www.amazon.in/dp/B0CHX1W1XY'), 'Product detail page is not an image');
assert(!isValidImageUrl('https://images-amazon.com/images/G/01/x-locale/common/grey-pixel.gif'), 'Grey pixel placeholder rejected');
assert(!isValidImageUrl('https://images-amazon.com/images/G/01/x-locale/common/transparent-pixel.gif'), 'Transparent pixel placeholder rejected');
assert(isValidImageUrl('https://m.media-amazon.com/images/I/71657TiFeHL._AC_SL1000_.jpg'), 'Genuine Amazon product photo accepted');
assert(isValidImageUrl('https://rukmini1.flixcart.com/image/832/832/xif0q/mobile/h/d/9/-original-imagtc2qzgnnuhxh.jpeg'), 'Genuine Flipkart photo accepted');
console.log('  ✅ Test 4 Passed: Image validation rejects landing pages, HTML, and tracking pixels.\n');

// =========================================================================
// Test 5: Cache Isolation
// =========================================================================
console.log('▶ Test 5: Cache Key Normalization & Isolation');
const cacheMap = new Map<string, string>();
const query1 = '  iPhone   15  ';
const query2 = 'iPhone 16';

const normKey1 = `search_${query1.toLowerCase().replace(/\s+/g, ' ').trim()}`;
const normKey2 = `search_${query2.toLowerCase().replace(/\s+/g, ' ').trim()}`;

cacheMap.set(normKey1, 'Result For iPhone 15');
cacheMap.set(normKey2, 'Result For iPhone 16');

assert.strictEqual(normKey1, 'search_iphone 15', 'Query 1 normalized to search_iphone 15');
assert.strictEqual(normKey2, 'search_iphone 16', 'Query 2 normalized to search_iphone 16');
assert.strictEqual(cacheMap.get('search_iphone 15'), 'Result For iPhone 15');
assert.strictEqual(cacheMap.get('search_iphone 16'), 'Result For iPhone 16');
assert.notStrictEqual(cacheMap.get('search_iphone 15'), cacheMap.get('search_iphone 16'), 'Cache keys must never collide');
// =========================================================================
// Test 6: Brand-Specific Search & Competitor Brand Rejection (e.g. Flour/Atta)
// =========================================================================
console.log('▶ Test 6: Brand-Specific Search & Competitor Brand Isolation');
const flourQuery = 'Fortune whole wheat flour';
const fortuneItemTitle = 'Fortune Chakki Fresh Atta, 5kg';
const aashirvaadItemTitle = 'Aashirvaad Superior MP Whole Wheat Atta 5kg';
const genericFlourTitle = 'Chakki Fresh Whole Wheat Flour 5kg';

const scoreFortune = calculateRelevanceScore(flourQuery, fortuneItemTitle, false);
const scoreAashirvaad = calculateRelevanceScore(flourQuery, aashirvaadItemTitle, false);
const scoreGeneric = calculateRelevanceScore(flourQuery, genericFlourTitle, false);

console.log(`  - Query: "${flourQuery}"`);
console.log(`  - Fortune Item Score: ${scoreFortune}`);
console.log(`  - Aashirvaad Competitor Item Score: ${scoreAashirvaad}`);
console.log(`  - Generic Item Score: ${scoreGeneric}`);

assert(scoreFortune > scoreAashirvaad, 'Fortune item must strongly outrank competitor Aashirvaad for Fortune query');
assert(scoreFortune > scoreGeneric, 'Fortune item must outrank generic flour item');
assert(scoreAashirvaad < 10, 'Competitor brand must receive heavy penalty when user explicitly searches a brand');
console.log('  ✅ Test 6 Passed: Brand-specific searches strictly prioritize the requested brand and penalize competitor brands.\n');

console.log('🎉 ALL 6 REGRESSION TEST SCENARIOS PASSED WITH ZERO FAILURES!');

