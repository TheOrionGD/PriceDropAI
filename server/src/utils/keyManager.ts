import dotenv from 'dotenv';
dotenv.config();

export interface KeyPair {
  gemini: string;
  groq: string;
  zenrows: string;
}

export const KEY_SET_1: KeyPair = {
  gemini: process.env.GEMINI_API_KEY1?.trim() || process.env.GEMINI_API_KEY?.trim() || '',
  groq: process.env.GROQ_API_KEY1?.trim() || process.env.GROQ_API_KEY?.trim() || '',
  zenrows: process.env.ZENROWS_KEY1?.trim() || process.env.ZENROWS_KEY?.trim() || '',
};

export const KEY_SET_2: KeyPair = {
  gemini: process.env.GEMINI_API_KEY2?.trim() || process.env.GEMINI_API_KEY?.trim() || '',
  groq: process.env.GROQ_API_KEY2?.trim() || process.env.GROQ_API_KEY?.trim() || '',
  zenrows: process.env.ZENROWS_KEY2?.trim() || process.env.ZENROWS_KEY?.trim() || '',
};

// Store Segment 1 (6-7 stores assigned to Key Set 1)
export const STORES_SET_1 = [
  'AMAZON',
  'FLIPKART',
  'MEESHO',
  'MYNTRA',
  'CROMA',
  'RELIANCE_DIGITAL',
  'JIOMART'
] as const;

// Store Segment 2 (6-7 stores assigned to Key Set 2)
export const STORES_SET_2 = [
  'TATACLIQ',
  'AJIO',
  'NYKAA',
  'EBAY',
  'WALMART',
  'TARGET',
  'BLINKIT'
] as const;

export type StoreKeySegment = 1 | 2;

/**
 * Returns which Key Set (1 or 2) is assigned to a specific store platform.
 */
export function getSegmentForStore(storeName: string): StoreKeySegment {
  const normalized = storeName.toUpperCase().replace(/[\s-]/g, '_');
  if ((STORES_SET_1 as readonly string[]).includes(normalized)) {
    return 1;
  }
  return 2;
}

import { isZenRowsKeyDisabled } from './zenrowsState.js';

/**
 * Returns the active ZenRows key for a specific store platform with automatic failover between Key 1 & Key 2.
 */
export function getZenRowsKeyForStore(storeName?: string): string {
  const primarySet = storeName ? getSegmentForStore(storeName) : 1;
  const primaryKey = primarySet === 1 ? KEY_SET_1.zenrows : KEY_SET_2.zenrows;
  const alternateKey = primarySet === 1 ? KEY_SET_2.zenrows : KEY_SET_1.zenrows;

  if (primaryKey && !isZenRowsKeyDisabled(primaryKey)) {
    return primaryKey;
  }
  if (alternateKey && !isZenRowsKeyDisabled(alternateKey)) {
    return alternateKey;
  }
  return '';
}

/**
 * Returns the Gemini key for a segment.
 */
export function getGeminiKey(segment: StoreKeySegment = 1): string {
  return segment === 1 ? KEY_SET_1.gemini : KEY_SET_2.gemini;
}

/**
 * Returns the Groq key for a segment.
 */
export function getGroqKey(segment: StoreKeySegment = 1): string {
  return segment === 1 ? KEY_SET_1.groq : KEY_SET_2.groq;
}
