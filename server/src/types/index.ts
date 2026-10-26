export enum Store {
  AMAZON = 'AMAZON',
  FLIPKART = 'FLIPKART',
  MEESHO = 'MEESHO',
  MYNTRA = 'MYNTRA',
  CROMA = 'CROMA',
  RELIANCE_DIGITAL = 'RELIANCE_DIGITAL',
  JIOMART = 'JIOMART',
  TATACLIQ = 'TATACLIQ',
  AJIO = 'AJIO',
  NYKAA = 'NYKAA',
  BLINKIT = 'BLINKIT',
  EBAY = 'EBAY',
  WALMART = 'WALMART',
  TARGET = 'TARGET',
}

export const ACTIVE_STORES: Store[] = [
  Store.AMAZON,
  Store.FLIPKART,
  Store.MEESHO,
  Store.MYNTRA,
];

export enum Availability {
  IN_STOCK = 'IN_STOCK',
  OUT_OF_STOCK = 'OUT_OF_STOCK',
  LIMITED_STOCK = 'LIMITED_STOCK',
  UNKNOWN = 'UNKNOWN',
}

export enum PaymentOfferType {
  INSTANT_DISCOUNT = 'INSTANT_DISCOUNT',
  CASHBACK = 'CASHBACK',
  EMI_DISCOUNT = 'EMI_DISCOUNT',
  REWARDS = 'REWARDS',
}

export enum MarketplaceStatus {
  AVAILABLE = 'AVAILABLE',
  OUT_OF_STOCK = 'OUT_OF_STOCK',
  NO_RESULTS = 'NO_RESULTS',
  UNSUPPORTED_CATEGORY = 'UNSUPPORTED_CATEGORY',
  EXTRACTION_FAILED = 'EXTRACTION_FAILED',
  RATE_LIMITED = 'RATE_LIMITED',
  UNKNOWN = 'UNKNOWN',
}

export interface StoreOffer {
  id: string;
  productId: string;
  store: Store;
  productUrl: string;
  searchUrl?: string;
  isDirectProductUrl?: boolean;
  status?: MarketplaceStatus;
  matchScore?: number | null;
  price: number | null;
  originalPrice: number | null;
  discountPercentage: number | null;
  currency: string;
  availability: Availability;
  deliveryInfo: string | null;
  imageUrl?: string | null;
  lastVerifiedAt?: number;
  lastUpdated: number;
}

export interface ProductVariant {
  id: string;
  productId: string;
  name: string;
  type: string;
  price: number | null;
  isAvailable: boolean;
}

export type PaymentMethod = 'CREDIT_CARD' | 'DEBIT_CARD' | 'UPI' | 'NET_BANKING' | 'EMI';
export type CardType = 'CREDIT' | 'DEBIT' | 'ANY';
export type DiscountType = 'PERCENTAGE' | 'FIXED' | 'CASHBACK';
export type EligibilityStatus = 'ELIGIBLE' | 'INELIGIBLE_MIN_PURCHASE' | 'INELIGIBLE_STORE' | 'INELIGIBLE_EXPIRED' | 'UNVERIFIED';

export interface PaymentOffer {
  id: string;
  provider: string;
  type: PaymentOfferType;
  description: string;
  amount: number | null;
  percentage: number | null;
  minimumPurchase: number | null;
  maximumDiscount: number | null;
  validUntil: number | null;
  // Extended production fields
  displayName?: string;
  paymentMethod?: PaymentMethod;
  cardType?: CardType;
  discountType?: DiscountType;
  discountValue?: number;
  maximumEligibleDiscount?: number | null;
  calculatedDiscount?: number;
  payablePrice?: number;
  eligibilityStatus?: EligibilityStatus;
  ineligibilityReason?: string;
  isEligible?: boolean;
  terms?: string;
  source?: string;
  sourceUrl?: string;
  lastVerifiedAt?: number;
  isCashback?: boolean;
  emiEligible?: boolean;
}

export interface EmiPlan {
  id: string;
  provider: string;
  displayName: string;
  tenureMonths: number;
  monthlyInstallment: number;
  totalInstallmentAmount: number;
  interestAmount: number;
  subventionDiscount: number;
  processingFee: number;
  taxes: number;
  effectiveTotalCost: number;
  isNoCostEmi: boolean;
  eligibilityStatus: EligibilityStatus;
  ineligibilityReason?: string;
  source: string;
  sourceUrl?: string;
  terms?: string;
}

export interface RatingBreakdownItem {
  count: number;
  percentage: number;
}

export interface RatingDistribution {
  fiveStar: RatingBreakdownItem;
  fourStar: RatingBreakdownItem;
  threeStar: RatingBreakdownItem;
  twoStar: RatingBreakdownItem;
  oneStar: RatingBreakdownItem;
}

export interface FeatureSentimentScores {
  buildQuality: number | null;
  valueForMoney: number | null;
  performance: number | null;
  durability: number | null;
}

export type ReviewAnalysisStatus = 'VERIFIED_ANALYSIS' | 'SAMPLE_BASED' | 'UNAVAILABLE' | 'INSUFFICIENT_DATA';

export interface ReviewSummary {
  sentimentScore: number | null;
  pros: string[];
  cons: string[];
  totalAnalyzed: number | null;
  isAvailable: boolean;
  ratingDistribution?: RatingDistribution | null;
  featureScores?: FeatureSentimentScores | null;
  confidenceScore?: number | null;
  evidenceCoverage?: string | null;
  dataFreshness?: number | null;
  analysisStatus?: ReviewAnalysisStatus;
  sourceMetadata?: string;
}

export interface Product {
  id: string;
  asin?: string | null;
  searchQuery?: string | null;
  title: string;
  description: string | null;
  imageUrl: string | null;
  category: string | null;
  brand: string | null;
  rating: number | null;
  reviewCount: number | null;
  variants: ProductVariant[];
  stores: StoreOffer[];
  reviewSummary: ReviewSummary;
  paymentOffers: PaymentOffer[];
  emiPlans?: EmiPlan[];
  lastUpdated: number;
}

export interface RawScrapedItem {
  store: Store;
  title: string;
  url: string;
  price: number | null;
  originalPrice: number | null;
  discountPercentage: number | null;
  imageUrl: string | null;
  asin?: string | null;
  isSponsored?: boolean;
  rating: number | null;
  reviewCount: number | null;
  availability: Availability;
  isDirectProductUrl?: boolean;
  searchUrl?: string;
}
