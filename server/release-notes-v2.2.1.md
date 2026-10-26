## PriceDropAI v2.2.1 (Build 18)

### 🚀 What's New & Fixed in this Release

- **Product & Image Integrity Fix**:
  - Eliminated cross-cluster image bleeding where unrelated products could inherit images from adjacent clusters.
  - Enforced strict 1:1 product-to-ASIN card binding across Amazon and supported stores.
  - Added organic query relevance scoring and sponsored-card filtering so the most accurate product is prioritized at index 0.

- **Multi-Gateway Anti-Bot Scraping Pipeline**:
  - Integrated **ScraperAPI** as high-concurrency gateway for Amazon and e-commerce stores with automated circuit breaking.
  - Integrated **ScrapingAnt** gateway for supported retail platforms.
  - Preserved dual **ZenRows** gateway sets with automatic failover and direct stealth scraper fallback.

- **Android App & UI Stability**:
  - Added stable Compose `LazyColumn` item keys (`key = { it.id }`) preventing stale image painters during fast list scrolling and view recycling.
  - Added Room cache query token relevance validation to prevent stale cached cards from briefly flashing before fresh network responses.
  - Added end-to-end request correlation tracking (`X-Request-Id`) across Android networking, Render APIs, and backend diagnostics.

### 📦 Release Assets
- **`app-release.apk`** (12.3 MB) — Production signed APK for direct Android installation.
- **`app-release.aab`** (12.0 MB) — Production signed Android App Bundle for Google Play Store upload.
