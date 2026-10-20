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
import { aggregateSearchResults } from './src/services/productAggregator.js';

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

async function runTimeBasedTests() {
  const query = 'iPhone 15';
  console.log(`\n==================================================`);
  console.log(`⏱️ TIME-BASED LIVE STORE TEST (CONCURRENCY-SAFE)`);
  console.log(`🤖 GEMINI MODEL: gemini-3.6-flash ONLY`);
  console.log(`🔑 ZENROWS_KEY: ${process.env.ZENROWS_KEY ? process.env.ZENROWS_KEY.slice(0, 8) + '...' : 'MISSING'}`);
  console.log(`==================================================\n`);

  // 1. Direct test of Gemini with gemini-3.6-flash ONLY
  console.log(`▶ [STEP 1] Testing Gemini API with gemini-3.6-flash ONLY...`);
  const t0 = Date.now();
  const geminiResult = await analyzeStorePricesWithGemini('iPhone 15', 'Mobiles & Accessories', 69999, 'Apple iPhone 15 (128 GB) - Black');
  console.log(`   Gemini Duration: ${Date.now() - t0}ms`);
  console.log(`   Gemini Result:`, JSON.stringify(geminiResult, null, 2));

  // Small delay between tests to ensure clean network socket allocation
  await sleep(1500);

  // 2. Sequential time-based test for each store to prevent concurrency locks
  console.log(`\n▶ [STEP 2] Testing Individual Stores (Paced to Prevent Concurrency Limit)...`);
  const stores = [
    { name: 'Amazon', fn: () => scrapeAmazon(query) },
    { name: 'Flipkart', fn: () => scrapeFlipkart(query) },
    { name: 'Meesho', fn: () => scrapeMeesho(query) },
    { name: 'Myntra', fn: () => scrapeMyntra('Running Shoes') },
    { name: 'Croma', fn: () => scrapeCroma(query) },
    { name: 'Reliance Digital', fn: () => scrapeRelianceDigital(query) },
    { name: 'Tata CLiQ', fn: () => scrapeTataCliq(query) },
    { name: 'Ajio', fn: () => scrapeAjio('Sneakers') },
    { name: 'Nykaa', fn: () => scrapeNykaa('Perfume') },
    { name: 'eBay', fn: () => scrapeEbay(query) },
  ];

  for (const s of stores) {
    const start = Date.now();
    try {
      console.log(`\n⏳ Checking ${s.name}...`);
      const items = await s.fn();
      const elapsed = Date.now() - start;
      if (items.length > 0) {
        console.log(`   ✅ ${s.name} [${elapsed}ms]: Found ${items.length} items.`);
        console.log(`      1st: "${items[0].title.slice(0, 45)}" - ₹${items[0].price}`);
        console.log(`      URL: ${items[0].url.slice(0, 70)}...`);
      } else {
        console.log(`   ⚠️ ${s.name} [${elapsed}ms]: 0 items returned (stealth challenge or no match).`);
      }
    } catch (err: any) {
      console.log(`   ❌ ${s.name}: ${err.message}`);
    }
    // Respect ZenRows rate limit between queries
    await sleep(2000);
  }

  // 3. Test Full Product Aggregator with Batched Calls
  console.log(`\n▶ [STEP 3] Testing Full Aggregator (Batched + Gemini 3.6 Flash)...`);
  const aggStart = Date.now();
  const aggregated = await aggregateSearchResults('Milton Thermosteel Bottle');
  console.log(`   Aggregator Duration: ${Date.now() - aggStart}ms`);
  console.log(`   Total Cards: ${aggregated.length}`);
  if (aggregated.length > 0) {
    const top = aggregated[0];
    console.log(`   Top Card: "${top.title}"`);
    console.log(`   Offers:`);
    top.stores.forEach(st => {
      console.log(`     - [${st.store}] ₹${st.price} (Lowest: ${st.price === top.stores.map(o => o.price).reduce((a, b) => Math.min(a ?? 999999, b ?? 999999)) ? 'YES' : 'NO'}) | ${st.productUrl.slice(0, 55)}...`);
    });
  }

  console.log(`\n==================================================`);
  console.log(`🏁 TIME-BASED TESTING COMPLETED`);
  console.log(`==================================================\n`);
}

runTimeBasedTests();
