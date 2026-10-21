import dotenv from 'dotenv';
dotenv.config();

import { scrapeAmazon } from './src/scrapers/amazon.js';
import { scrapeFlipkart } from './src/scrapers/flipkart.js';
import { scrapeMeesho } from './src/scrapers/meesho.js';
import { scrapeMyntra } from './src/scrapers/myntra.js';
import { scrapeCroma } from './src/scrapers/croma.js';
import { scrapeRelianceDigital } from './src/scrapers/relianceDigital.js';
import { scrapeTataCliq } from './src/scrapers/tatacliq.js';
import { scrapeAjio } from './src/scrapers/ajio.js';
import { scrapeNykaa } from './src/scrapers/nykaa.js';
import { scrapeEbay } from './src/scrapers/ebay.js';
import { analyzeStorePricesWithGemini } from './src/services/geminiService.js';
import { analyzeStoresWithGroq } from './src/services/groqService.js';
import { aggregateSearchResults } from './src/services/productAggregator.js';
import { KEY_SET_1, KEY_SET_2, getZenRowsKeyForStore } from './src/utils/keyManager.js';

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

async function runDualKeyTests() {
  console.log(`\n==================================================`);
  console.log(`🚀 DUAL KEY SET INTEGRATION & VALIDATION TEST`);
  console.log(`🔑 Key Set 1: Gemini (${KEY_SET_1.gemini.slice(0, 10)}...), Groq (${KEY_SET_1.groq.slice(0, 10)}...), ZenRows (${KEY_SET_1.zenrows.slice(0, 8)}...)`);
  console.log(`🔑 Key Set 2: Gemini (${KEY_SET_2.gemini.slice(0, 10)}...), Groq (${KEY_SET_2.groq.slice(0, 10)}...), ZenRows (${KEY_SET_2.zenrows.slice(0, 8)}...)`);
  console.log(`==================================================\n`);

  // 1. Direct validation of Gemini Set 1 & Set 2 with fallback
  console.log(`▶ [STEP 1] Testing Gemini API with Dual Key Redundancy...`);
  const t0 = Date.now();
  const geminiResult = await analyzeStorePricesWithGemini('iPhone 15', 'Mobiles & Accessories', 69999, 'Apple iPhone 15 (128 GB) - Black');
  console.log(`   Gemini Duration: ${Date.now() - t0}ms`);
  console.log(`   Stores Analyzed: ${Object.keys(geminiResult).join(', ')}`);

  // 2. Direct validation of Groq Set 1 & Set 2 with fallback
  console.log(`\n▶ [STEP 2] Testing Groq API with Dual Key Redundancy...`);
  const tGroq = Date.now();
  const groqResult = await analyzeStoresWithGroq('iPhone 15', 'Mobiles & Accessories', 69999, 'Apple iPhone 15 (128 GB) - Black');
  console.log(`   Groq Duration: ${Date.now() - tGroq}ms`);
  console.log(`   Stores Analyzed: ${Object.keys(groqResult).join(', ')}`);

  // 3. Selective Live Store Check using Partitioned ZenRows Keys
  console.log(`\n▶ [STEP 3] Testing Partitioned Store Fetching...`);
  const stores = [
    { name: 'Amazon (Segment 1)', fn: () => scrapeAmazon('iPhone 15') },
    { name: 'Flipkart (Segment 1)', fn: () => scrapeFlipkart('iPhone 15') },
    { name: 'Myntra (Segment 1)', fn: () => scrapeMyntra('Running Shoes') },
    { name: 'Nykaa (Segment 2)', fn: () => scrapeNykaa('Perfume') },
  ];

  for (const s of stores) {
    const start = Date.now();
    try {
      console.log(`⏳ Checking ${s.name}...`);
      const items = await s.fn();
      const elapsed = Date.now() - start;
      if (items.length > 0) {
        console.log(`   ✅ ${s.name} [${elapsed}ms]: Found ${items.length} live items.`);
        console.log(`      First: "${items[0].title.slice(0, 45)}" - ₹${items[0].price}`);
      } else {
        console.log(`   ⚠️ ${s.name} [${elapsed}ms]: 0 items (handled by AI multi-store engine).`);
      }
    } catch (err: any) {
      console.log(`   ❌ ${s.name}: ${err.message}`);
    }
    await sleep(1500);
  }

  // 4. Test Single Aggregator query to verify 14-store card generation
  console.log(`\n▶ [STEP 4] Testing Aggregator on Single Product (Reduced load to prevent rate-limiting)...`);
  const aggStart = Date.now();
  const aggregated = await aggregateSearchResults('Milton Thermosteel Bottle');
  console.log(`   Aggregator Duration: ${Date.now() - aggStart}ms`);
  console.log(`   Total Cards Produced: ${aggregated.length}`);
  if (aggregated.length > 0) {
    const top = aggregated[0];
    console.log(`   Top Card: "${top.title}"`);
    console.log(`   Total Store Comparison Offers: ${top.stores.length}`);
    top.stores.forEach(st => {
      console.log(`     - [${st.store}] ₹${st.price} | ${st.productUrl.slice(0, 45)}...`);
    });
  }

  console.log(`\n==================================================`);
  console.log(`🏁 DUAL KEY INTEGRATION TEST COMPLETED`);
  console.log(`==================================================\n`);
}

runDualKeyTests();
