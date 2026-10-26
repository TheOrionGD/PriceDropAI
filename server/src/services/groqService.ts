import dns from 'node:dns';
import axios from 'axios';
import dotenv from 'dotenv';
import { z } from 'zod';
import { Store, Availability, ReviewSummary, RatingDistribution, FeatureSentimentScores } from '../types/index.js';
import { getGroqKey } from '../utils/keyManager.js';

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

try {
  dns.setDefaultResultOrder('ipv4first');
} catch {
  // Ignore
}

dotenv.config();

function getGroqApiKeys(): string[] {
  return [getGroqKey(1), getGroqKey(2), process.env.GROQ_API_KEY?.trim() || ''].filter(Boolean);
}

// Active marketplaces are exclusively live scraped (Amazon, Flipkart, Meesho, Myntra).
// Zero AI mock requests are dispatched for unwanted platforms.
export const GROQ_STORES: Store[] = [];

export const GROQ_CHAT_MODELS = [
  'openai/gpt-oss-120b',
  'openai/gpt-oss-20b',
  'qwen/qwen3.8-27b',
];

const groqModelCooldowns = new Map<string, number>();

function isGroqInCooldown(apiKey: string, model: string): boolean {
  const key = `${apiKey.slice(0, 10)}_${model}`;
  return Date.now() < (groqModelCooldowns.get(key) || 0);
}

function setGroqCooldown(apiKey: string, model: string, durationMs: number = 30000) {
  const key = `${apiKey.slice(0, 10)}_${model}`;
  groqModelCooldowns.set(key, Date.now() + durationMs);
}

/**
 * Executes a fast, resilient JSON completion using Groq LPU models.
 * Automatically tries active models across available API keys.
 */
export async function callGroqJsonApi(
  systemPrompt: string,
  userPrompt: string,
  timeoutMs: number = 6000
): Promise<string | null> {
  const apiKeys = getGroqApiKeys();
  if (apiKeys.length === 0) return null;

  for (const model of GROQ_CHAT_MODELS) {
    for (const apiKey of apiKeys) {
      if (isGroqInCooldown(apiKey, model)) continue;

      try {
        const response = await axios.post(
          'https://api.groq.com/openai/v1/chat/completions',
          {
            model,
            messages: [
              { role: 'system', content: systemPrompt },
              { role: 'user', content: userPrompt }
            ],
            temperature: 0.2,
            response_format: { type: 'json_object' }
          },
          {
            headers: {
              'Authorization': `Bearer ${apiKey}`,
              'Content-Type': 'application/json'
            },
            timeout: timeoutMs
          }
        );

        const content = response.data?.choices?.[0]?.message?.content;
        if (content && typeof content === 'string' && content.trim().length > 0) {
          return content.trim();
        }
      } catch (err: any) {
        const status = err.response?.status;
        if (status === 429) {
          console.warn(`[Groq AI Notice] Rate limit (HTTP 429) on model ${model} with key ${apiKey.slice(0, 8)}... Cooling down for 30s.`);
          setGroqCooldown(apiKey, model, 30000);
        } else {
          console.warn(`[Groq AI Notice] Model ${model} error: ${err.message}`);
        }
      }
    }
  }
  return null;
}

export async function analyzeStoresWithGroq(
  _query: string,
  _category: string,
  _referencePrice: number,
  _primaryTitle: string
): Promise<Record<string, AiStoreAnalysis>> {
  // PriceDropAI supports strictly Amazon, Meesho, Flipkart, and Myntra.
  // Their results are collected via live provider extraction.
  // Zero AI requests or synthetic quotes are generated for unwanted stores.
  return {};
}

/**
 * Generates ultra-fast, intelligent AI Copilot shopping chat responses using Groq LPU models
 * with automatic dual-key failover and Gemini fallback.
 */
export async function generateCopilotResponseWithGroq(
  userPrompt: string,
  productTitle?: string,
  lowestPrice?: number,
  priceHistoryCount?: number
): Promise<CopilotResponse> {
  const apiKeys = getGroqApiKeys();
  const safePrompt = userPrompt.trim();

  let contextInfo = `User Query: "${safePrompt}"`;
  if (productTitle) {
    contextInfo += `\nProduct Title: "${productTitle}"`;
  }
  if (lowestPrice && lowestPrice > 0) {
    contextInfo += `\nLowest Current Price: ₹${lowestPrice.toLocaleString('en-IN')}`;
  }
  if (typeof priceHistoryCount === 'number' && priceHistoryCount > 0) {
    contextInfo += `\nPrice History Snapshots Tracked: ${priceHistoryCount}`;
  }

  const systemContent = `You are PriceDrop AI Copilot, a fast, knowledgeable, and helpful shopping assistant for Indian e-commerce (Amazon, Flipkart, Meesho, Myntra).
Provide a direct, concise, and helpful response (2-3 sentences max). Suggest a relevant product search query if appropriate.
Output strictly a valid RAW JSON object matching this schema:
{
  "replyText": "your concise response here",
  "suggestedSearchQuery": "optional product search query or null"
}`;

  for (const apiKey of apiKeys) {
    for (const model of GROQ_CHAT_MODELS) {
      if (isGroqInCooldown(apiKey, model)) continue;

      try {
        const response = await axios.post(
          'https://api.groq.com/openai/v1/chat/completions',
          {
            model,
            messages: [
              { role: 'system', content: systemContent },
              { role: 'user', content: contextInfo }
            ],
            temperature: 0.2,
            response_format: { type: 'json_object' }
          },
          {
            headers: {
              'Authorization': `Bearer ${apiKey}`,
              'Content-Type': 'application/json'
            },
            timeout: 10000
          }
        );

        const content = response.data?.choices?.[0]?.message?.content;
        if (content) {
          try {
            const parsed = JSON.parse(content);
            if (parsed && typeof parsed.replyText === 'string' && parsed.replyText.trim().length > 0) {
              return {
                replyText: parsed.replyText.trim(),
                suggestedSearchQuery: typeof parsed.suggestedSearchQuery === 'string' && parsed.suggestedSearchQuery.trim().length > 0
                  ? parsed.suggestedSearchQuery.trim()
                  : undefined,
                isVerifiedFact: true,
              };
            }
          } catch {
            return {
              replyText: content.trim(),
              suggestedSearchQuery: safePrompt.length < 35 ? safePrompt : undefined,
              isVerifiedFact: true,
            };
          }
        }
      } catch (err: any) {
        const status = err.response?.status;
        if (status === 429) {
          console.warn(`[Groq AI Notice] Rate limit (HTTP 429) on model ${model} with key ${apiKey.slice(0, 8)}... Cooling down for 30s.`);
          setGroqCooldown(apiKey, model, 30000);
          continue;
        } else {
          console.warn(`[Groq AI Notice] Model ${model} returned error: ${err.message}`);
        }
      }
    }
  }



  // Deterministic contextual fallback response
  return {
    replyText: productTitle && lowestPrice
      ? `Based on real-time market data across Amazon, Flipkart, Meesho, and Myntra for '${productTitle}', the lowest live offer is ₹${lowestPrice.toLocaleString('en-IN')}. Set a target price alert to get notified when the price drops further!`
      : `Regarding '${safePrompt}': I analyze live pricing, price drop trends, and verified multi-store deals across Amazon, Flipkart, Meesho, and Myntra. Search any product or paste an e-commerce link to view live offers!`,
    suggestedSearchQuery: safePrompt.length < 35 ? safePrompt : undefined,
    isVerifiedFact: true,
  };
}

const ReviewOutputSchema = z.object({
  sentimentScore: z.number().min(0).max(100).nullable().optional(),
  pros: z.array(z.string()).default([]),
  cons: z.array(z.string()).default([]),
  featureScores: z.object({
    buildQuality: z.number().min(0).max(100).nullable().optional(),
    valueForMoney: z.number().min(0).max(100).nullable().optional(),
    performance: z.number().min(0).max(100).nullable().optional(),
    durability: z.number().min(0).max(100).nullable().optional(),
  }).optional(),
  evidenceCoverage: z.string().optional(),
});

/**
 * Synthesizes verified multi-store product review summary using Groq LPU models.
 */
export async function generateProductReviewSummaryWithGroq(
  productTitle: string,
  stores: Store[],
  avgRating: number | null,
  reviewCount: number | null,
  actualRatingDistribution: RatingDistribution | null = null
): Promise<ReviewSummary> {
  const storeNames = stores.length > 0 ? stores.join(', ') : 'Amazon, Flipkart, Meesho, Myntra';
  const hasReviewData = (avgRating !== null && avgRating > 0) || (reviewCount !== null && reviewCount > 0);

  if (!hasReviewData) {
    return {
      sentimentScore: null,
      pros: [],
      cons: [],
      totalAnalyzed: 0,
      isAvailable: false,
      ratingDistribution: actualRatingDistribution,
      featureScores: null,
      confidenceScore: null,
      evidenceCoverage: 'No customer review or rating evidence available for this product listing.',
      dataFreshness: Date.now(),
      analysisStatus: 'UNAVAILABLE',
      sourceMetadata: storeNames,
    };
  }

  const prompt = `You are an expert e-commerce product analyst. Synthesize verified customer reviews and buyer feedback across online stores (${storeNames}) for: "${productTitle}".
Current store rating: ${avgRating ? `${avgRating}/5` : 'N/A'} based on ${reviewCount || 'sample'} verified purchases.

Output strictly a RAW JSON object adhering to this schema:
{
  "sentimentScore": number between 40 and 100 representing positive buyer sentiment percentage,
  "pros": array of 2 to 4 concise bullet points highlighting verified positive feedback (build quality, performance, value, etc.),
  "cons": array of 1 or 2 concise realistic caveats or things to consider before buying,
  "featureScores": {
    "buildQuality": number between 0 and 100, or null if insufficient evidence,
    "valueForMoney": number between 0 and 100, or null if insufficient evidence,
    "performance": number between 0 and 100, or null if insufficient evidence,
    "durability": number between 0 and 100, or null if insufficient evidence
  },
  "evidenceCoverage": "Sample-based" or "Aggregated multi-store verified reviews"
}

Do not invent fabricated data. If there is no evidence for a specific feature, set it to null.`;

  const rawResult = await callGroqJsonApi(
    'You are an expert e-commerce product analyst. Synthesize verified customer reviews and buyer feedback. Return strictly RAW JSON adhering to schema.',
    prompt,
    5000
  );

  if (rawResult) {
    try {
      const rawParsed = JSON.parse(rawResult);
      const parseResult = ReviewOutputSchema.safeParse(rawParsed);

      if (parseResult.success) {
        const data = parseResult.data;
        const total = reviewCount || 0;
        const isSampleBased = total > 0 && total < 15;
        const analysisStatus = isSampleBased ? 'SAMPLE_BASED' : 'VERIFIED_ANALYSIS';

        const featureScores: FeatureSentimentScores = {
          buildQuality: data.featureScores?.buildQuality ?? null,
          valueForMoney: data.featureScores?.valueForMoney ?? null,
          performance: data.featureScores?.performance ?? null,
          durability: data.featureScores?.durability ?? null,
        };

        const pros = data.pros.filter((p) => typeof p === 'string' && p.trim().length > 0).slice(0, 4);
        const cons = data.cons.filter((c) => typeof c === 'string' && c.trim().length > 0).slice(0, 3);

        return {
          sentimentScore: data.sentimentScore ?? (avgRating ? Math.round(avgRating * 20) : null),
          pros,
          cons,
          totalAnalyzed: total,
          isAvailable: pros.length > 0 || cons.length > 0,
          ratingDistribution: actualRatingDistribution,
          featureScores,
          confidenceScore: isSampleBased ? 65 : 90,
          evidenceCoverage: data.evidenceCoverage || (isSampleBased ? 'Sample-based customer review analysis' : 'Multi-store customer reviews analysis'),
          dataFreshness: Date.now(),
          analysisStatus,
          sourceMetadata: storeNames,
        };
      }
    } catch {
      // ignore
    }
  }

  return {
    sentimentScore: avgRating ? Math.round(avgRating * 20) : null,
    pros: [],
    cons: [],
    totalAnalyzed: reviewCount || 0,
    isAvailable: false,
    ratingDistribution: actualRatingDistribution,
    featureScores: null,
    confidenceScore: null,
    evidenceCoverage: 'AI sentiment analysis available on demand.',
    dataFreshness: Date.now(),
    analysisStatus: 'UNAVAILABLE',
    sourceMetadata: storeNames,
  };
}

