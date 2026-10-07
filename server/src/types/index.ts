export enum Store {
  AMAZON = 'AMAZON',
  FLIPKART = 'FLIPKART',
  MEESHO = 'MEESHO',
  MYNTRA = 'MYNTRA',
}

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

export interface StoreOffer {
  id: string;
  productId: string;
  store: Store;
  productUrl: string;
  price: number | null;
  originalPrice: number | null;
  discountPercentage: number | null;
  currency: string;
  availability: Availability;
  deliveryInfo: string | null;
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
}

export interface ReviewSummary {
  sentimentScore: number | null;
  pros: string[];
  cons: string[];
  totalAnalyzed: number | null;
  isAvailable: boolean;
}

export interface Product {
  id: string;
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
  rating: number | null;
  reviewCount: number | null;
  availability: Availability;
}
