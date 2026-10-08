import { Router, Request, Response } from 'express';
import NodeCache from 'node-cache';
import { aggregateSearchResults } from '../services/productAggregator.js';
import { generateCopilotResponseWithGemini } from '../services/geminiService.js';

const router = Router();

// Bounded, memory-safe in-memory cache (Max 300 entries, no object cloning overhead)
const cache = new NodeCache({
  stdTTL: 900,
  checkperiod: 60,
  maxKeys: 300,
  useClones: false,
});

// Health check with live memory monitoring
router.get('/health', (_req: Request, res: Response) => {
  const mem = process.memoryUsage();
  res.json({
    status: 'ok',
    service: 'PriceDropAI Server',
    uptime: `${Math.round(process.uptime())}s`,
    memory: {
      rss: `${Math.round(mem.rss / 1024 / 1024)} MB`,
      heapUsed: `${Math.round(mem.heapUsed / 1024 / 1024)} MB`,
      heapTotal: `${Math.round(mem.heapTotal / 1024 / 1024)} MB`,
    },
    cachedKeys: cache.keys().length,
    timestamp: Date.now(),
  });
});

// Search products endpoint: GET /api/search?q=query
router.get('/search', async (req: Request, res: Response) => {
  const query = (req.query.q as string || '').trim();
  if (!query) {
    res.json([]);
    return;
  }

  const cacheKey = `search_${query.toLowerCase()}`;
  const cached = cache.get(cacheKey);
  if (cached) {
    res.json(cached);
    return;
  }

  try {
    const products = await aggregateSearchResults(query);
    cache.set(cacheKey, products);
    res.json(products);
  } catch (err: any) {
    console.error(`[Search API Error] for "${query}":`, err);
    res.status(500).json({
      error: 'Failed to aggregate products',
      message: err.message,
    });
  }
});

// Single product lookup / refresh endpoint: GET /api/product/:id
router.get('/product/:id', async (req: Request, res: Response) => {
  const productId = String(req.params.id || '');
  const rawQuery = productId.replace(/-/g, ' ');

  try {
    const products = await aggregateSearchResults(rawQuery);
    const product = products.find(p => p.id === productId) || products[0];
    if (!product) {
      res.status(404).json({ error: 'Product not found' });
      return;
    }
    res.json(product);
  } catch (err: any) {
    res.status(500).json({ error: 'Failed to fetch product', message: err.message });
  }
});

// AI Copilot endpoint: POST /api/copilot
router.post('/copilot', async (req: Request, res: Response) => {
  const { userPrompt, productTitle, lowestPrice, priceHistoryCount } = req.body || {};
  if (!userPrompt || typeof userPrompt !== 'string') {
    res.status(400).json({ error: 'Missing userPrompt in request body' });
    return;
  }

  try {
    const copilotResult = await generateCopilotResponseWithGemini(
      userPrompt,
      productTitle,
      lowestPrice,
      priceHistoryCount
    );
    res.json(copilotResult);
  } catch (err: any) {
    console.error('[Copilot Error]:', err.message);
    res.status(500).json({
      replyText: `I can help compare live prices across Amazon, Flipkart, Meesho, and Myntra. Search any product or paste an e-commerce link!`,
      isVerifiedFact: true,
    });
  }
});

// Image Proxy endpoint to bypass hotlink & CORS restrictions: GET /api/image-proxy?url=...
router.get('/image-proxy', async (req: Request, res: Response) => {
  let targetUrl = (req.query.url as string || '').trim();
  if (!targetUrl) {
    res.status(400).send('Missing url parameter');
    return;
  }

  if (targetUrl.startsWith('http://')) {
    targetUrl = targetUrl.replace(/^http:\/\//i, 'https://');
  }

  try {
    const axiosModule = await import('axios');
    const response = await axiosModule.default.get(targetUrl, {
      responseType: 'arraybuffer',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
        'Accept': 'image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8',
        'Referer': '',
      },
      timeout: 8000,
    });

    const contentType = String(response.headers['content-type'] || 'image/jpeg');
    res.setHeader('Content-Type', contentType);
    res.setHeader('Cache-Control', 'public, max-age=86400');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.send(Buffer.from(response.data));
  } catch (err: any) {
    console.warn(`[Image Proxy Fallback] Redirecting directly to ${targetUrl}`);
    res.redirect(targetUrl);
  }
});

export default router;

