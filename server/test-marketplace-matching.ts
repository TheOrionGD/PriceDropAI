import test from 'node:test';
import assert from 'node:assert/strict';
import {
  normalizeSearchQuery,
  extractProductSpecs,
  areSpecsIncompatible,
  isCategorySupportedByStore,
  isVerifiedProductUrl,
  buildMarketplaceSearchUrl,
  MarketplaceStatus,
} from './src/utils/storeUrlResolver.js';
import { detectCategory, calculateRelevanceScore } from './src/services/productAggregator.js';

test('1. Search query normalization preserves meaningful specs and removes noise', () => {
  const q1 = normalizeSearchQuery('Hand Gripper');
  const q2 = normalizeSearchQuery('hand   gripper');
  const q3 = normalizeSearchQuery('hand gripper!');

  assert.equal(q1, 'hand gripper');
  assert.equal(q2, 'hand gripper');
  assert.equal(q3, 'hand gripper');

  // Verify resistance specifications are preserved
  const specQuery = normalizeSearchQuery('5KG To 60KG Weight Adjustable Hand Gripper');
  assert.match(specQuery, /5-60kg/);
  assert.match(specQuery, /adjustable/);
});

test('2. Incompatible product specs are strictly distinguished (fixed vs adjustable resistance)', () => {
  const specsFixed5kg = extractProductSpecs('5kg Fixed Steel Hand Gripper');
  const specsRange5to60 = extractProductSpecs('5kg to 60kg adjustable forearm hand gripper');
  const specsRange10to40 = extractProductSpecs('10kg to 40kg adjustable hand gripper');

  assert.equal(specsFixed5kg.weight, '5kg');
  assert.equal(specsFixed5kg.isAdjustable, false);

  assert.equal(specsRange5to60.resistanceRange, '5-60kg');
  assert.equal(specsRange5to60.isAdjustable, true);

  // Incompatible check: 5kg fixed vs 5-60kg adjustable must be detected as incompatible!
  assert.equal(areSpecsIncompatible(specsFixed5kg, specsRange5to60), true);

  // 10-40kg vs 5-60kg is incompatible
  assert.equal(areSpecsIncompatible(specsRange10to40, specsRange5to60), true);

  // Similar specs compatible
  const specsRange5to60Variant = extractProductSpecs('5 - 60 kg power adjustable hand gripper');
  assert.equal(areSpecsIncompatible(specsRange5to60, specsRange5to60Variant), false);
});

test('3. Category detection correctly identifies Sports & Fitness for hand gripper', () => {
  const category = detectCategory('5kg to 60kg adjustable hand gripper forearm workout');
  assert.equal(category, 'Sports & Fitness');

  const electronicsCategory = detectCategory('Sony WH-1000XM5 wireless headphones');
  assert.equal(electronicsCategory, 'Audio & Headphones');

  const fashionCategory = detectCategory('Cotton slim fit casual shirt for men');
  assert.equal(fashionCategory, 'Fashion & Apparel');
});

test('4. Category-aware store matching isolates incompatible marketplaces', () => {
  const fitnessCat = 'Sports & Fitness';

  // Stores that do NOT sell fitness equipment
  assert.equal(isCategorySupportedByStore('croma', fitnessCat), false);
  assert.equal(isCategorySupportedByStore('reliancedigital', fitnessCat), false);
  assert.equal(isCategorySupportedByStore('myntra', fitnessCat), false);
  assert.equal(isCategorySupportedByStore('ajio', fitnessCat), false);
  assert.equal(isCategorySupportedByStore('nykaa', fitnessCat), false);
  assert.equal(isCategorySupportedByStore('tatacliq', fitnessCat), false);

  // Stores that DO sell general/sports items
  assert.equal(isCategorySupportedByStore('amazon', fitnessCat), true);
  assert.equal(isCategorySupportedByStore('flipkart', fitnessCat), true);
  assert.equal(isCategorySupportedByStore('meesho', fitnessCat), true);
  assert.equal(isCategorySupportedByStore('jiomart', fitnessCat), true); // JioMart carries sports & fitness goods
  assert.equal(isCategorySupportedByStore('ebay', fitnessCat), true);
});

test('5. Direct product URL verification distinguishes item detail pages from search pages', () => {
  // Amazon product detail page
  assert.equal(isVerifiedProductUrl('amazon', 'https://www.amazon.in/dp/B08XYZ1234'), true);
  assert.equal(isVerifiedProductUrl('amazon', 'https://www.amazon.in/Brand-Product/dp/B08XYZ1234/ref=sr_1_1'), true);
  // Amazon search page
  assert.equal(isVerifiedProductUrl('amazon', 'https://www.amazon.in/s?k=hand+gripper'), false);

  // Flipkart product page
  assert.equal(isVerifiedProductUrl('flipkart', 'https://www.flipkart.com/brand-gripper/p/itm123456789'), true);
  assert.equal(isVerifiedProductUrl('flipkart', 'https://www.flipkart.com/search?q=hand+gripper'), false);

  // Croma product page
  assert.equal(isVerifiedProductUrl('croma', 'https://www.croma.com/p/243567'), true);
  assert.equal(isVerifiedProductUrl('croma', 'https://www.croma.com/search/?text=headphones'), false);

  // Reliance Digital product page
  assert.equal(isVerifiedProductUrl('reliancedigital', 'https://www.reliancedigital.in/item/491901234'), true);
  assert.equal(isVerifiedProductUrl('reliancedigital', 'https://www.reliancedigital.in/search?q=tv'), false);

  // eBay listing page
  assert.equal(isVerifiedProductUrl('ebay', 'https://www.ebay.com/itm/123456789012'), true);
  assert.equal(isVerifiedProductUrl('ebay', 'https://www.ebay.com/sch/i.html?_nkw=gripper'), false);
});

test('6. Safe search URL fallback generates valid marketplace URLs for all stores', () => {
  const query = 'hand gripper';
  const stores = ['amazon', 'flipkart', 'croma', 'reliancedigital', 'tatacliq', 'ajio', 'ebay', 'myntra', 'jiomart', 'meesho'];

  for (const store of stores) {
    const searchUrl = buildMarketplaceSearchUrl(store, query);
    assert.ok(searchUrl.startsWith('http'), `Search URL for ${store} must start with http`);
    assert.ok(searchUrl.includes('gripper') || searchUrl.includes('hand'), `Search URL for ${store} must encode query`);
  }
});

test('7. Relevance scoring prevents unrelated cross-marketplace product matching', () => {
  const query = '5KG To 60KG Weight Adjustable High Quality Polypropylene Hand Gripper';
  const candidateClose = 'Adjustable Hand Gripper 5 to 60 kg For Forearm Workout';
  const candidateUnrelatedGoPro = 'GoPro The Handler Floating Hand Grip for Action Cameras';
  const candidateWrongResistance = '100KG Fixed Heavy Duty Steel Hand Gripper';

  const scoreClose = calculateRelevanceScore(query, candidateClose, false);
  const scoreGoPro = calculateRelevanceScore(query, candidateUnrelatedGoPro, false);
  const scoreWrongRes = calculateRelevanceScore(query, candidateWrongResistance, false);

  assert.ok(scoreClose > scoreGoPro, `Close match should outscore GoPro grip: ${scoreClose} vs ${scoreGoPro}`);
  assert.ok(scoreGoPro < 50, `Unrelated GoPro grip should have low score: ${scoreGoPro}`);
});

test('8. Marketplace status enum contracts are standardized', () => {
  assert.equal(MarketplaceStatus.AVAILABLE, 'AVAILABLE');
  assert.equal(MarketplaceStatus.OUT_OF_STOCK, 'OUT_OF_STOCK');
  assert.equal(MarketplaceStatus.NO_RESULTS, 'NO_RESULTS');
  assert.equal(MarketplaceStatus.UNSUPPORTED_CATEGORY, 'UNSUPPORTED_CATEGORY');
  assert.equal(MarketplaceStatus.EXTRACTION_FAILED, 'EXTRACTION_FAILED');
  assert.equal(MarketplaceStatus.RATE_LIMITED, 'RATE_LIMITED');
  assert.equal(MarketplaceStatus.UNKNOWN, 'UNKNOWN');
});
