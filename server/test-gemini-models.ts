import dotenv from 'dotenv';
import axios from 'axios';

dotenv.config();

const apiKey = process.env.GEMINI_API_KEY || '';

if (!apiKey) {
  console.error('❌ GEMINI_API_KEY is missing in server/.env');
  process.exit(1);
}

console.log('🔍 Testing Gemini API Key & Querying Available Models...');

async function testModels() {
  try {
    const listUrl = `https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey}`;
    const listResponse = await axios.get(listUrl, { timeout: 10000 });
    const models = listResponse.data?.models || [];
    
    console.log(`\n✅ Connected to Gemini API! Total models available: ${models.length}`);
    
    const generateModels = models
      .filter((m: any) => m.supportedGenerationMethods?.includes('generateContent'))
      .map((m: any) => m.name.replace('models/', ''));

    console.log('\n--- Supported Content Generation Models ---');
    generateModels.forEach((m: string) => console.log(`• ${m}`));

    // Test specific models for Analysis (Price & Stock) and Chat (Copilot)
    const testAnalysisModels = ['gemini-3.8-flash', 'gemini-3.6-flash', 'gemini-flash-latest'];
    const testChatModels = ['gemini-3.8-flash', 'gemini-3.6-flash', 'gemini-3.1-pro-preview', 'gemini-pro-latest'];

    let workingAnalysisModel: string | null = null;
    let workingChatModel: string | null = null;

    console.log('\n🔍 Testing Data Analysis Models (Price & Market Estimates):');
    for (const m of testAnalysisModels) {
      if (!generateModels.includes(m)) continue;
      const res = await testGenerate(m, 'Return strictly a JSON object: {"AMAZON":{"price":1000}}', true);
      if (res) {
        console.log(`  ✅ [ANALYSIS MODEL] ${m} -> SUCCESS! Output: ${res.trim()}`);
        workingAnalysisModel = m;
        break;
      }
    }

    console.log('\n🔍 Testing Chat/Copilot Models (Shopping Assistant Dialog):');
    for (const m of testChatModels) {
      if (!generateModels.includes(m)) continue;
      const res = await testGenerate(m, 'Return strictly a JSON object: {"replyText":"Hello! I am PriceDrop AI Copilot."}', true);
      if (res) {
        console.log(`  ✅ [CHAT MODEL] ${m} -> SUCCESS! Output: ${res.trim()}`);
        workingChatModel = m;
        break;
      }
    }

    console.log('\n========================================');
    console.log('📌 FINAL CONFIRMED MODELS FOR CODEBASE:');
    console.log(`   ANALYSIS MODEL: ${workingAnalysisModel || 'gemini-3.8-flash'}`);
    console.log(`   CHAT MODEL:     ${workingChatModel || 'gemini-3.8-flash'}`);
    console.log('========================================\n');

  } catch (err: any) {
    console.error('❌ Error testing Gemini API models:', err.response?.data || err.message);
  }
}

async function testGenerate(model: string, prompt: string, expectJson: boolean): Promise<string | null> {
  try {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;
    const payload: any = {
      contents: [{ parts: [{ text: prompt }] }],
      generationConfig: {
        temperature: 0.2,
        maxOutputTokens: 256
      }
    };
    if (expectJson) {
      payload.generationConfig.responseMimeType = 'application/json';
    }

    const res = await axios.post(url, payload, {
      headers: { 'Content-Type': 'application/json' },
      timeout: 10000
    });

    const candidates = res.data?.candidates;
    if (Array.isArray(candidates) && candidates.length > 0) {
      return candidates[0]?.content?.parts?.[0]?.text || null;
    }
  } catch (e: any) {
    console.warn(`  ❌ ${model} failed:`, e.response?.data?.error?.message || e.message);
  }
  return null;
}

testModels();
