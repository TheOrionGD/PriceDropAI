/**
 * Gemini Service Replacement Layer
 * Fully backed by ultra-fast Groq LPU models (openai/gpt-oss-120b).
 * Completely eliminates slow Gemini timeouts, cooldowns, and 503 errors across the codebase.
 */
import { Store, Availability, ReviewSummary, RatingDistribution } from '../types/index.js';
import {
  analyzeStoresWithGroq,
  generateCopilotResponseWithGroq,
  generateProductReviewSummaryWithGroq,
  GROQ_STORES,
  GROQ_CHAT_MODELS
} from './groqService.js';

export interface AiStoreAnalysis {
  price: number;
  originalPrice: number;
  discountPercentage: number;
  availability: Availability;
  deliveryInfo: string;
}

export interface CopilotResponse {
  replyText: string;
  suggestedSearchQuery?: string;
  isVerifiedFact: boolean;
}

export const GEMINI_ANALYSIS_MODELS = GROQ_CHAT_MODELS;
export const GEMINI_CHAT_MODELS = GROQ_CHAT_MODELS;
export const GEMINI_STORES = GROQ_STORES;

/**
 * Delegated to Groq LPU model
 */
export async function analyzeStorePricesWithGemini(
  query: string,
  category: string,
  referencePrice: number,
  primaryTitle: string
): Promise<Record<string, AiStoreAnalysis>> {
  return analyzeStoresWithGroq(query, category, referencePrice, primaryTitle);
}

/**
 * Delegated to Groq LPU model
 */
export async function generateCopilotResponseWithGemini(
  userPrompt: string,
  productTitle?: string,
  lowestPrice?: number,
  priceHistoryCount?: number
): Promise<CopilotResponse> {
  return generateCopilotResponseWithGroq(userPrompt, productTitle, lowestPrice, priceHistoryCount);
}

/**
 * Delegated to Groq LPU model
 */
export async function generateProductReviewSummaryWithGemini(
  productTitle: string,
  stores: Store[],
  avgRating: number | null,
  reviewCount: number | null,
  actualRatingDistribution: RatingDistribution | null = null
): Promise<ReviewSummary> {
  return generateProductReviewSummaryWithGroq(productTitle, stores, avgRating, reviewCount, actualRatingDistribution);
}
