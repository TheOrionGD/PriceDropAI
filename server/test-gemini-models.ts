import dotenv from 'dotenv';
import { analyzeStorePricesWithGemini, generateCopilotResponseWithGemini } from './src/services/geminiService.js';

dotenv.config();

async function testGeminiModels() {
  console.log('🤖 Testing Gemini API Models Integration...\n');

  console.log('1. Testing analyzeStorePricesWithGemini...');
  const analysis = await analyzeStorePricesWithGemini('Milton Water Bottle', 'Kitchen & Dining', 500, 'Milton Thermosteel Water Bottle 1L');
  console.log('   Analysis Output:', JSON.stringify(analysis, null, 2));

  console.log('\n2. Testing generateCopilotResponseWithGemini...');
  const chatResponse = await generateCopilotResponseWithGemini('Which store offers the best discount on Milton Water Bottle?');
  console.log('   Chat Response Output:', JSON.stringify(chatResponse, null, 2));

  console.log('\n✅ Gemini Model API test completed successfully.');
}

testGeminiModels().catch(err => {
  console.error('❌ Test failed with error:', err);
});
