import express, { Express, Request, Response } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import dotenv from 'dotenv';
import path from 'path';
import apiRouter from './routes/api.js';

dotenv.config();

const app: Express = express();
const PORT = process.env.PORT || 3000;
const publicDir = path.resolve(process.cwd(), 'public');

app.use(helmet({ contentSecurityPolicy: false }));
app.use(cors({ origin: '*' }));
app.use(express.json());
app.use(express.static(publicDir));

// Serve Privacy Policy page
app.get(['/privacy-policy', '/privacy-policy.html'], (_req: Request, res: Response) => {
  res.sendFile(path.join(publicDir, 'privacy-policy.html'));
});

// Root landing & Render health check
app.get('/', (_req: Request, res: Response) => {
  res.json({
    name: 'PriceDropAI Aggregation Engine',
    version: '1.0.0',
    status: 'online',
    privacyPolicy: '/privacy-policy',
    endpoints: {
      health: '/api/health',
      search: '/api/search?q=query',
      product: '/api/product/:id',
    },
  });
});

app.use('/api', apiRouter);

// Start server
app.listen(PORT, () => {
  console.log(`========================================`);
  console.log(`🚀 PriceDropAI Backend running on port ${PORT}`);
  console.log(`🔗 Local: http://localhost:${PORT}`);
  console.log(`🌐 Ready for Render Deployment`);
  console.log(`========================================`);
});
