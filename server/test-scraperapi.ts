import axios from 'axios';
import dotenv from 'dotenv';
dotenv.config();

async function checkScraperAPI(key?: string) {
  if (!key) {
    console.log(`⚪ SCRAPERAPI_KEY: Not configured`);
    return;
  }
  const masked = `${key.slice(0, 8)}...${key.slice(-4)}`;
  const testUrl = `http://api.scraperapi.com?api_key=${key}&url=${encodeURIComponent('https://www.amazon.in/s?k=iPhone+15')}&country_code=in`;

  console.log(`🔍 Testing SCRAPERAPI_KEY (${masked}) against Amazon India...`);
  try {
    const start = Date.now();
    const res = await axios.get(testUrl, { timeout: 35000 });
    const duration = Date.now() - start;
    const html = typeof res.data === 'string' ? res.data : JSON.stringify(res.data);
    const hasCards = html.includes('s-search-result');
    console.log(`✅ SCRAPERAPI_KEY (${masked}) - Status ${res.status} OK in ${duration}ms! HTML bytes: ${html.length}, Contains Amazon Cards: ${hasCards}`);
  } catch (err: any) {
    const status = err?.response?.status;
    const data = err?.response?.data;
    console.log(`❌ SCRAPERAPI_KEY (${masked}) - Status ${status || 'Network/Timeout Error'}:`, data || err?.message);
  }
}

async function main() {
  console.log('====================================');
  console.log('  ScraperAPI Amazon Verification');
  console.log('====================================\n');
  await checkScraperAPI(process.env.SCRAPERAPI_KEY);
  console.log('\n====================================');
}

main();
