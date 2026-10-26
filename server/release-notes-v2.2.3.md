## PriceDropAI v2.2.3 (Build 20)

### 🚀 What's New & Enhancements in this Release

- **Safe Room Database Migration (v2 → v3)**:
  - Implemented explicit non-destructive migrations (`MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_1_3`) guaranteeing complete preservation of tracked products, store offers, price snapshots, and search history.
  - Safe defaults for newly added columns (`asin`, `searchQuery`, `imageUrl`, `searchUrl`, `isDirectProductUrl`, `status`, `matchScore`, `lastVerifiedAt`).
  - Removed destructive migration fallback to ensure existing user data is never erased on update.

- **Persistent Deduplication & Canonical Identity**:
  - Transactional alias replacement: obsolete slug IDs are atomically purged when canonical ASINs are discovered.
  - Database migration cleanup safely prunes historical duplicates while preserving the newest record per product.
  - Compose item keys stabilized (`product.asin ?: product.id`) preventing unnecessary UI recomposition.

- **Marketplace Spec Matching & Verified Direct Redirection**:
  - Strict resistance specification matching distinguishing fixed vs. adjustable resistance hand grippers and separate resistance ranges (e.g. 5–60kg vs 10–40kg).
  - Verified Product Detail Page (PDP) identification vs. search URL fallback across all supported stores (Amazon, Flipkart, Croma, Reliance Digital, JioMart, Meesho, eBay).
  - Audited category support matrix: corrected JioMart to support Sports & Fitness equipment; incompatible stores accurately marked.

- **Availability & Pricing Transparency**:
  - Marketplace failures (rate limits, proxy timeouts) now clearly indicate `(Check Store)` with `Live verification unavailable`, rather than falsely claiming product is out of stock.
  - Clear `(Not Carried)` status for stores outside the product category.
  - Store cards for unavailable products now show `—` instead of misleading `₹0`.

- **Android App & Build**:
  - Incremented `versionCode` to **20** and `versionName` to **2.2.3**.
  - Production signed release APK and App Bundle (AAB).

### 📦 Release Assets
- **`app-release.apk`** (~12.4 MB) — Production signed APK for direct Android installation.
- **`app-release.aab`** (~12.1 MB) — Production signed Android App Bundle for Google Play Store upload.
