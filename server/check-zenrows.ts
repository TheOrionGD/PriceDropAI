import axios from 'axios';
import dotenv from 'dotenv';
dotenv.config();

async function checkZenRowsKey(label: string, key?: string) {
  if (!key) {
    console.log(`⚪ ${label}: Not configured`);
    return;
  }
  const masked = `${key.slice(0, 8)}...${key.slice(-4)}`;
  const testUrl = `https://api.zenrows.com/v1/?apikey=${key}&url=${encodeURIComponent('https://httpbin.org/ip')}&mode=auto`;

  console.log(`🔍 Testing ${label} (${masked})...`);
  try {
    const start = Date.now();
    const res = await axios.get(testUrl, { timeout: 15000 });
    const duration = Date.now() - start;
    console.log(`✅ ${label} (${masked}) - Status ${res.status} OK in ${duration}ms! Data:`, res.data);
  } catch (err: any) {
    const status = err?.response?.status;
    const data = err?.response?.data;
    console.log(`❌ ${label} (${masked}) - Status ${status || 'Network/Timeout Error'}:`, data || err?.message);
  }
}

async function checkScrapingAnt(key?: string) {
  if (!key) {
    console.log(`⚪ SCRAPINGANT_KEY: Not configured`);
    return;
  }
  const masked = `${key.slice(0, 8)}...${key.slice(-4)}`;
  const testUrl = `https://api.scrapingant.com/v2/general?url=${encodeURIComponent('https://httpbin.org/ip')}&x-api-key=${key}&browser=false`;

  console.log(`🔍 Testing SCRAPINGANT_KEY (${masked})...`);
  try {
    const start = Date.now();
    const res = await axios.get(testUrl, { timeout: 20000 });
    const duration = Date.now() - start;
    console.log(`✅ SCRAPINGANT_KEY (${masked}) - Status ${res.status} OK in ${duration}ms! Data:`, res.data);
  } catch (err: any) {
    const status = err?.response?.status;
    const data = err?.response?.data;
    console.log(`❌ SCRAPINGANT_KEY (${masked}) - Status ${status || 'Network/Timeout Error'}:`, data || err?.message);
  }
}

async function main() {
  console.log('====================================');
  console.log('  Gateway Key Health Check');
  console.log('====================================\n');
  await checkZenRowsKey('ZENROWS_KEY1', process.env.ZENROWS_KEY1);
  console.log('');
  await checkZenRowsKey('ZENROWS_KEY2', process.env.ZENROWS_KEY2);
  console.log('');
  await checkScrapingAnt(process.env.SCRAPINGANT_KEY);
  console.log('\n====================================');
}

main();
