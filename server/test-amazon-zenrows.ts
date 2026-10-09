import dotenv from 'dotenv';
dotenv.config();

import { scrapeAmazon } from './src/scrapers/amazon.js';
import { fetchZenRowsProductImage } from './src/scrapers/webFallback.js';

async function testAmazonZenrows() {
  const query = process.argv[2] || 'iPhone 15';
  console.log(`==================================================`);
  console.log(`🔑 Using ZENROWS_KEY: ${process.env.ZENROWS_KEY}`);
  console.log(`🛒 Fetching Amazon product images for: "${query}"...`);
  console.log(`==================================================\n`);

  console.log('1️⃣ Scraped Amazon Products via ZenRows HTML fetcher:');
  const items = await scrapeAmazon(query);
  console.log(`   Found ${items.length} products on Amazon.\n`);

  items.forEach((item, index) => {
    console.log(`📦 Item #${index + 1}: ${item.title}`);
    console.log(`   💰 Price: ₹${item.price} (Original: ₹${item.originalPrice || 'N/A'})`);
    console.log(`   ⭐ Rating: ${item.rating || 'N/A'} (${item.reviewCount || 0} reviews)`);
    console.log(`   🖼️ Image URL: ${item.imageUrl}`);
    console.log(`   🔗 URL: ${item.url}`);
    console.log(`--------------------------------------------------`);
  });

  console.log('\n2️⃣ Direct ZenRows Product Image Gateway test:');
  const directImg = await fetchZenRowsProductImage(query);
  console.log(`   High-Res Image URL: ${directImg || 'Not found'}`);
  console.log(`==================================================`);
}

testAmazonZenrows();
