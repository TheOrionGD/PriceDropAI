import { fetchPageHtml } from './src/utils/htmlFetcher.js';

async function main() {
  console.log('Testing Amazon fetch via ScrapingAnt failover...');
  const start = Date.now();
  const html = await fetchPageHtml('https://www.amazon.in/s?k=iPhone+15');
  const duration = Date.now() - start;
  console.log(`Fetch completed in ${duration}ms`);
  console.log('HTML received length:', html?.length || 0);
  console.log('Contains product results:', (html || '').includes('s-search-result'));
}

main();
