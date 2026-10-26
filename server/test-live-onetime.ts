import dotenv from 'dotenv';
dotenv.config();

import { aggregateSearchResults } from './src/services/productAggregator.js';
import { isVerifiedProductUrl } from './src/utils/storeUrlResolver.js';
import { isValidImageUrl } from './src/scrapers/webFallback.js';
import { Store } from './src/types/index.js';

async function runOneTimeVerificationTest() {
  const query = 'Men Running Shoes';
  console.log(`\n======================================================================`);
  console.log(`🚀 ONE-TIME LIVE SEARCH VERIFICATION TEST`);
  console.log(`🔍 Product Query: "${query}"`);
  console.log(`======================================================================\n`);

  const startTime = Date.now();
  const products = await aggregateSearchResults(query, 'one_time_test');
  const elapsed = Date.now() - startTime;

  console.log(`⏱️ Completed in ${elapsed}ms. Found ${products.length} product cards.\n`);

  if (products.length === 0) {
    console.error('❌ FAILED: Zero products returned.');
    process.exit(1);
  }

  // Inspect the top product
  const p = products[0];
  console.log(`📦 [PRIMARY PRODUCT]`);
  console.log(`   Title:         "${p.title}"`);
  console.log(`   Brand:         ${p.brand || 'N/A'}`);
  console.log(`   Category:      ${p.category}`);
  console.log(`   Rating:        ${p.rating ?? 'N/A'} (${p.reviewCount ?? 0} reviews)`);

  // 1. Verify Image
  console.log(`\n🖼️ [IMAGE VERIFICATION]`);
  console.log(`   Image URL:     ${p.imageUrl ? p.imageUrl : '❌ NULL'}`);
  const hasValidImage = isValidImageUrl(p.imageUrl);
  console.log(`   Valid Photo?:  ${hasValidImage ? '✅ YES (Real high-res photo)' : '❌ INVALID/MISSING'}`);

  // 2. Verify Store Product URLs
  console.log(`\n🛒 [STORE URL ALLOCATION FOR EACH OF THE 4 PLATFORMS]`);
  const requiredStores = [Store.AMAZON, Store.FLIPKART, Store.MEESHO, Store.MYNTRA];

  let verifiedDirectCount = 0;

  for (const store of requiredStores) {
    const offer = p.stores.find(s => s.store === store);
    if (!offer) {
      console.log(`   ❌ [${store}] Missing store offer entirely!`);
      continue;
    }

    const isDirect = isVerifiedProductUrl(store, offer.productUrl);
    console.log(`   🏬 [${store}]`);
    console.log(`      Status:     ${offer.status}`);
    console.log(`      Price:      ₹${offer.price}`);
    console.log(`      Direct?:    ${isDirect ? '✅ DIRECT ITEM URL' : (offer.isDirectProductUrl ? '✅ DIRECT' : '⚠️ Fallback Search URL')}`);
    console.log(`      URL:        ${offer.productUrl}`);
    if (isDirect || offer.isDirectProductUrl) {
      verifiedDirectCount++;
    }
  }

  console.log(`\n======================================================================`);
  console.log(`📊 SUMMARY RESULTS:`);
  console.log(`   - Product Found:          "${p.title}"`);
  console.log(`   - Image Successfully Fetched: ${hasValidImage ? '✅ YES' : '❌ NO'}`);
  console.log(`   - Direct Item URLs Allocated: ${verifiedDirectCount} of 4 platforms`);
  console.log(`======================================================================\n`);
}

runOneTimeVerificationTest().catch(console.error);
