import assert from 'node:assert/strict';
import { calculatePaymentOffers, calculateEmiPlans, VERIFIED_BANK_OFFER_RULES } from './src/services/bankingService.js';
import { Store } from './src/types/index.js';

console.log('🧪 Starting Banking Service & EMI Unit Tests...');

// 1. Minimum purchase requirement test
{
  console.log('Test 1: Low-price product ineligible for high min-purchase offer');
  const lowPrice = 499;
  const offers = calculatePaymentOffers(lowPrice, Store.AMAZON);

  const hdfcOffer = offers.find(o => o.provider === 'HDFC Bank');
  assert.ok(hdfcOffer, 'HDFC offer should be present in results');
  assert.equal(hdfcOffer.isEligible, false, 'Offer should be marked ineligible for ₹499 item with ₹5,000 min spend');
  assert.equal(hdfcOffer.eligibilityStatus, 'INELIGIBLE_MIN_PURCHASE');
  assert.ok(hdfcOffer.ineligibilityReason?.includes('Minimum purchase'), 'Should provide clear ineligibility reason');
  assert.equal(hdfcOffer.calculatedDiscount, 0, 'Ineligible offer must have 0 effective discount');
  console.log('  ✓ Low-price min-purchase check passed');
}

// 2. High-price product eligible with capped maximum discount
{
  console.log('Test 2: Eligible purchase with discount cap applied');
  const highPrice = 50000;
  const offers = calculatePaymentOffers(highPrice, Store.AMAZON);

  const hdfcOffer = offers.find(o => o.provider === 'HDFC Bank');
  assert.ok(hdfcOffer);
  assert.equal(hdfcOffer.isEligible, true);
  assert.equal(hdfcOffer.eligibilityStatus, 'ELIGIBLE');
  // 10% of 50000 is 5000, but cap is 1500
  assert.equal(hdfcOffer.calculatedDiscount, 1500);
  assert.equal(hdfcOffer.payablePrice, 50000 - 1500);
  console.log('  ✓ Discount cap check passed');
}

// 3. Store-exclusive offer restrictions
{
  console.log('Test 3: Store restriction (Amazon Pay ICICI on Amazon vs Flipkart)');
  const price = 10000;
  const amazonOffers = calculatePaymentOffers(price, Store.AMAZON);
  const flipkartOffers = calculatePaymentOffers(price, Store.FLIPKART);

  const amazonPayOnAmazon = amazonOffers.find(o => o.provider === 'Amazon Pay ICICI');
  assert.ok(amazonPayOnAmazon);
  assert.equal(amazonPayOnAmazon.isEligible, true);

  const amazonPayOnFlipkart = flipkartOffers.find(o => o.provider === 'Amazon Pay ICICI');
  assert.ok(amazonPayOnFlipkart);
  assert.equal(amazonPayOnFlipkart.isEligible, false);
  assert.equal(amazonPayOnFlipkart.eligibilityStatus, 'INELIGIBLE_STORE');

  const flipkartAxisOnFlipkart = flipkartOffers.find(o => o.provider === 'Flipkart Axis Bank');
  assert.ok(flipkartAxisOnFlipkart);
  assert.equal(flipkartAxisOnFlipkart.isEligible, true);

  const flipkartAxisOnAmazon = amazonOffers.find(o => o.provider === 'Flipkart Axis Bank');
  assert.ok(flipkartAxisOnAmazon);
  assert.equal(flipkartAxisOnAmazon.isEligible, false);
  console.log('  ✓ Store restriction check passed');
}

// 4. Cashback vs Instant Discount differentiation
{
  console.log('Test 4: Cashback does not reduce upfront payable amount');
  const price = 20000;
  const offers = calculatePaymentOffers(price, Store.AMAZON);
  const amazonPayOffer = offers.find(o => o.provider === 'Amazon Pay ICICI');

  assert.ok(amazonPayOffer);
  assert.equal(amazonPayOffer.isCashback, true);
  assert.equal(amazonPayOffer.payablePrice, 20000, 'Upfront payable price should remain 20000 for cashback');
  assert.equal(amazonPayOffer.calculatedDiscount, 1000, '5% of 20000 is 1000 cashback');
  console.log('  ✓ Cashback vs instant discount check passed');
}

// 5. No-Cost EMI calculation and interest subvention
{
  console.log('Test 5: No-Cost EMI calculator with interest subvention and statutory fees');
  const price = 60000;
  const emiPlans = calculateEmiPlans(price, Store.AMAZON);

  assert.ok(emiPlans.length > 0, 'EMI plans should be generated for ₹60,000 product');

  const hdfc6m = emiPlans.find(p => p.provider === 'HDFC Bank' && p.tenureMonths === 6);
  assert.ok(hdfc6m, 'HDFC 6m plan should exist');
  assert.equal(hdfc6m.isNoCostEmi, true);
  assert.equal(hdfc6m.monthlyInstallment, 10000, '60000 / 6 months = 10000 per month');
  assert.equal(hdfc6m.totalInstallmentAmount, 60000);
  assert.ok(hdfc6m.interestAmount > 0, 'Interest amount should be computed via amortization');
  assert.equal(hdfc6m.subventionDiscount, hdfc6m.interestAmount, 'Merchant covers entire interest on No-Cost EMI');
  assert.equal(hdfc6m.processingFee, 199);
  assert.equal(hdfc6m.taxes, 35.82); // 18% of 199
  assert.equal(hdfc6m.effectiveTotalCost, 60000 + 199 + 35.82);
  console.log('  ✓ No-Cost EMI calculation check passed');
}

// 6. EMI threshold check (< ₹3,000)
{
  console.log('Test 6: Products below ₹3,000 cannot generate EMI plans');
  const lowPriceEmi = calculateEmiPlans(2499, Store.AMAZON);
  assert.equal(lowPriceEmi.length, 0, 'No EMI plans should be generated below ₹3,000');
  console.log('  ✓ EMI threshold check passed');
}

// 7. Decimal safety & rounding check
{
  console.log('Test 7: Decimal safety with fractional numbers');
  const oddPrice = 4999.99;
  const offers = calculatePaymentOffers(oddPrice, Store.AMAZON);
  for (const offer of offers) {
    if (offer.calculatedDiscount) {
      assert.ok(!isNaN(offer.calculatedDiscount));
      assert.equal(offer.calculatedDiscount, Math.round(offer.calculatedDiscount * 100) / 100);
    }
  }
  console.log('  ✓ Decimal safety check passed');
}

console.log('🎉 ALL 7 BANKING SERVICE UNIT TESTS PASSED SUCCESSFULLY!');
