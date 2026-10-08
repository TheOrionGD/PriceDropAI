import { aggregateSearchResults } from './src/services/productAggregator.js';

async function testImageFetching() {
  const testQueries = ['iPhone 15', 'Nike Running Shoes', 'Sony Headphones'];

  console.log('🧪 Testing End-to-End Image Fetching & High-Res Resolution...\n');

  for (const q of testQueries) {
    console.log(`🔍 Searching: "${q}"...`);
    try {
      const results = await aggregateSearchResults(q);
      console.log(`  Found ${results.length} aggregated products.`);
      results.forEach((p, idx) => {
        console.log(`  📦 Product #${idx + 1}: "${p.title}"`);
        console.log(`     🖼️ Image URL: ${p.imageUrl ? p.imageUrl : '❌ NULL'}`);
      });
    } catch (err: any) {
      console.error(`  ❌ Error searching "${q}":`, err.message);
    }
    console.log('--------------------------------------------------');
  }
}

testImageFetching();
