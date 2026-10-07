# PriceDropAI Backend Aggregation Engine

High-performance Node.js & TypeScript multi-platform e-commerce search and price comparison backend for PriceDropAI.

---

## 🚀 Features

* **Multi-Store Concurrent Scraping**: Scrapes Amazon, Flipkart, Meesho, and Myntra simultaneously.
* **Product Clustering & Matching**: Groups identical or variant products across different stores under a single product comparison card.
* **Multi-Item Search Return**: Returns lists of matching products (not just a single item).
* **Dynamic Image & Web Fallback**: Uses live search resolvers (DuckDuckGo, Wikipedia, OpenLibrary) to resolve high-resolution images.
* **Smart In-Memory Caching**: Caches search queries for 20 minutes to maximize response speed (<10ms for cached queries) and prevent rate limiting.
* **Render-Ready**: Native compatibility with [Render](https://render.com) Web Services.

---

## 📦 Project Structure

```
server/
├── render.yaml          # Render Blueprint configuration
├── package.json         # Scripts and dependencies
├── tsconfig.json        # TypeScript configuration
└── src/
    ├── types/           # Domain interfaces matching Android client
    ├── utils/           # Header rotation & realistic browser simulation
    ├── scrapers/        # Store scrapers (Amazon, Flipkart, Meesho, Myntra, Web)
    ├── services/        # Clustering, normalization, and deduplication logic
    ├── routes/          # REST endpoints (/api/search, /api/product, /api/health)
    └── index.ts         # Express entry point
```

---

## 🛠️ Local Development

1. Install dependencies:
   ```bash
   cd server
   npm install
   ```

2. Run in development mode with hot-reloading:
   ```bash
   npm run dev
   ```

3. Test search locally:
   ```bash
   curl "http://localhost:3000/api/search?q=iphone+15"
   ```

4. Build for production:
   ```bash
   npm run build
   npm start
   ```

---

## 🌐 Deploying to Render (Step-by-Step)

### Option 1: Direct Web Service (Recommended)
1. Push your repository to **GitHub** or **GitLab**.
2. Go to [Render Dashboard](https://dashboard.render.com/) and click **New +** -> **Web Service**.
3. Connect your repository.
4. Fill in the settings:
   * **Root Directory**: `server`
   * **Runtime**: `Node`
   * **Build Command**: `npm install && npm run build`
   * **Start Command**: `npm start`
   * **Plan**: Free
5. Click **Create Web Service**. Render will assign a public URL (e.g., `https://pricedropai-backend.onrender.com`).

### Option 2: Render Blueprint
* Render will automatically detect `render.yaml` inside `server/` if you deploy via Blueprint.

---

## 📱 API Endpoints

### `GET /api/search?q={query}`
Returns an array of products with live comparison offers:
```json
[
  {
    "id": "apple-iphone-15-128-gb-black",
    "title": "Apple iPhone 15 (128 GB) - Black",
    "description": "Live price comparison across online stores...",
    "imageUrl": "https://m.media-amazon.com/...",
    "category": "Mobiles",
    "brand": "Apple",
    "rating": 4.6,
    "reviewCount": 3840,
    "stores": [
      {
        "id": "apple-iphone-15-128-gb-black_amazon",
        "productId": "apple-iphone-15-128-gb-black",
        "store": "AMAZON",
        "productUrl": "https://www.amazon.in/dp/...",
        "price": 69999,
        "originalPrice": 79900,
        "discountPercentage": 12,
        "availability": "IN_STOCK"
      },
      {
        "id": "apple-iphone-15-128-gb-black_flipkart",
        "productId": "apple-iphone-15-128-gb-black",
        "store": "FLIPKART",
        "productUrl": "https://www.flipkart.com/...",
        "price": 69999,
        "originalPrice": 79900,
        "discountPercentage": 12,
        "availability": "IN_STOCK"
      }
    ]
  }
]
```

### `GET /api/health`
Health check status for Render and monitoring.
