import dotenv from 'dotenv';
dotenv.config();

import { analyzeStorePricesWithGemini, GEMINI_STORES } from './src/services/geminiService.js';
import { analyzeStoresWithGroq, GROQ_STORES } from './src/services/groqService.js';
import { aggregateSearchResults } from './src/services/productAggregator.js';

async function testDualEngine() {
  console.log('\n==================================================');
  console.log('🤖 DUAL-AI ENGINE TEST (6 GEMINI + 6 GROQ = 12 STORES)');
  console.log(`🔑 GEMINI KEY: ${process.env.GEMINI_API_KEY ? 'CONFIGURED' : 'MISSING'}`);
  console.log(`🔑 GROQ KEY:   ${process.env.GROQ_API_KEY ? 'CONFIGURED' : 'MISSING'}`);
  console.log('==================================================\n');

  console.log(`Gemini Stores (6): ${GEMINI_STORES.join(', ')}`);
  console.log(`Groq Stores (6):   ${GROQ_STORES.join(', ')}`);

  console.log('\n▶ Testing Groq LLaMA-3.3-70B (6 Stores)...');
  const groqRes = await analyzeStoresWithGroq('Sony WH-1000XM5 Headphones', 'Audio & Headphones', 28990, 'Sony WH-1000XM5 Noise Cancelling Headphones');
  console.log('Groq 6 Stores Result:', JSON.stringify(groqRes, null, 2));

  console.log('\n▶ Testing Full Product Aggregation with 12 Stores (0 Errors)...');
  const results = await aggregateSearchResults('Sony WH-1000XM5');
  console.log(`Total comparison cards: ${results.length}`);
  if (results.length > 0) {
    const card = results[0];
    console.log(`\nProduct: "${card.title}"`);
    console.log(`Total Store Offers: ${card.stores.length}/12`);
    card.stores.forEach(s => {
      console.log(`   [${s.store.padEnd(16)}] ₹${s.price?.toString().padEnd(6)} | ${s.productUrl.slice(0, 65)}...`);
    });
  }
  console.log('\n==================================================\n');
}

testDualEngine();
