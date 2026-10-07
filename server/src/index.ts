import express, { Express, Request, Response } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import dotenv from 'dotenv';
import apiRouter from './routes/api.js';

dotenv.config();

const app: Express = express();
const PORT = process.env.PORT || 3000;

app.use(helmet());
app.use(cors({ origin: '*' }));
app.use(express.json());

// Root landing & Render health check
app.get('/', (_req: Request, res: Response) => {
  res.json({
    name: 'PriceDropAI Aggregation Engine',
    version: '1.0.0',
    status: 'online',
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
