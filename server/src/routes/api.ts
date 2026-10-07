import { Router, Request, Response } from 'express';
import NodeCache from 'node-cache';
import { aggregateSearchResults } from '../services/productAggregator.js';

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

export default router;
