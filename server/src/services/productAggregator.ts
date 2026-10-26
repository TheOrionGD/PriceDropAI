import { Product, StoreOffer, Store, Availability, RawScrapedItem, ProductVariant, PaymentOffer, PaymentOfferType, MarketplaceStatus } from '../types/index.js';
import { calculatePaymentOffers, calculateEmiPlans } from './bankingService.js';
import { scrapeAmazon } from '../scrapers/amazon.js';
import { scrapeFlipkart } from '../scrapers/flipkart.js';
import { scrapeMeesho } from '../scrapers/meesho.js';
import { scrapeMyntra } from '../scrapers/myntra.js';
import { scrapeCroma } from '../scrapers/croma.js';
import { scrapeRelianceDigital } from '../scrapers/relianceDigital.js';
import { scrapeTataCliq } from '../scrapers/tatacliq.js';
import { scrapeAjio } from '../scrapers/ajio.js';
import { scrapeNykaa } from '../scrapers/nykaa.js';
import { scrapeEbay } from '../scrapers/ebay.js';
import { scrapeWalmart } from '../scrapers/walmart.js';
import { scrapeTarget } from '../scrapers/target.js';
import { fetchZenRowsProductImage, fetchWebImageFallback, isValidImageUrl, optimizeProductImageUrl } from '../scrapers/webFallback.js';
import { analyzeStorePricesWithGemini, GEMINI_STORES, generateProductReviewSummaryWithGemini } from './geminiService.js';
import { analyzeStoresWithGroq, GROQ_STORES } from './groqService.js';
import {
  normalizeSearchQuery,
  extractProductSpecs,
  areSpecsIncompatible,
  isVerifiedProductUrl,
  buildMarketplaceSearchUrl,
  isCategorySupportedByStore
} from '../utils/storeUrlResolver.js';

function cleanTitle(title: string): string {
  return title
    .replace(/\s+/g, ' ')
    .replace(/\(.*?\)/g, '')
    .trim();
}

export function detectCategory(q: string): string {
  const lower = q.toLowerCase();

  // Audio, Headphones & Speakers (Prioritized before Mobiles so headphone doesn't match phone)
  if (/\b(soundbars?|speakers?|headphones?|earphones?|earbuds?|airpods?|ear\s*fun|tws|neckbands?|bluetooth\s*speakers?|amplifiers?|microphones?|mics?)\b/i.test(lower)) return 'Audio & Headphones';

  // Mobiles & Accessories
  if (/\b(phone|phones|smartphone|mobile|charger|powerbank|tempered\s*glass|phone\s*case)\b|iphone|galaxy\s*s|pixel|redmi|oneplus|realme|motorola|moto\s*g|iqoo|poco|infinix|tecno/i.test(lower)) return 'Mobiles & Accessories';

  // Smartwatches & Wearables
  if (/smartwatch|apple\s*watch|galaxy\s*watch|fitbit|band|fitness\s*tracker|smart\s*ring/i.test(lower)) return 'Wearables & Smartwatches';

  // TVs & Home Entertainment
  if (/tv|television|qled|oled|4k\s*tv|smart\s*tv|android\s*tv|home\s*theater|projector|streaming\s*stick|fire\s*tv|chromecast/i.test(lower)) return 'TV & Home Entertainment';

  // Cameras & Photography
  if (/camera|dslr|mirrorless|action\s*camera|gopro|drone|tripod|lens|gimbal|ring\s*light|dash\s*cam/i.test(lower)) return 'Cameras & Photography';

  // Gaming & Consoles
  if (/playstation|ps5|ps4|xbox|nintendo|switch|gaming\s*controller|joystick|gamepad|gaming\s*chair|steam\s*deck/i.test(lower)) return 'Gaming & Consoles';

  // Sports, Fitness & Outdoor (Prioritized before furniture to prevent words like 'adjustable' from matching 'table')
  if (/\b(gripper|hand\s*gripper|wrist|forearm|finger\s*exerciser|cricket|bat|ball|badminton|racket|shuttlecock|football|basketball|volleyball|gym|dumbbell|barbell|weight\s*plate|kettlebell|pull\s*up|push\s*up|resistance\s*band|exercise\s*band|yoga\s*mat|skipping\s*rope|jump\s*rope|ab\s*roller|treadmill|exercise\s*bike|cycle|bicycle|tent|sleeping\s*bag|workout)\b/i.test(lower)) return 'Sports & Fitness';

  // Home & Kitchen Appliances
  if (/\b(refrigerator|fridge|washing\s*machine|air\s*conditioner|ac|microwave|oven|air\s*fryer|induction|chimney|water\s*purifier|vacuum|iron|geyser|heater|mixer|grinder|blender|toaster|kettle|cooker|cookware|bottle|flask|lunch\s*box)\b|\bro\b|\bpan\b|\bpot\b/i.test(lower)) return 'Home & Kitchen';

  // Furniture & Home Decor
  if (/\b(chair|desk|sofa|couch|mattress|wardrobe|bookshelf|curtain|bedsheet|pillow|carpet|chandelier|wall\s*clock|mirror|vase)\b|\btable\b|\bbed\b|\brug\b|\blamp\b/i.test(lower)) return 'Furniture & Home Decor';

  // Footwear
  if (/\b(shoe|sneaker|boot|sandal|heel|slipper|clog|flip\s*flop|loafer|oxford|running\s*shoe|formal\s*shoe|sports\s*shoe)\b/i.test(lower)) return 'Footwear';

  // Fashion & Apparel
  if (/\b(shirt|t-shirt|pant|trousers|jeans|saree|kurti|kurta|dress|lehenga|jacket|coat|hoodie|sweatshirt|blazer|suit|shorts|trackpant|innerwear|bra|brief|ethnic)\b/i.test(lower)) return 'Fashion & Apparel';

  // Beauty, Skincare & Grooming
  if (/\b(perfume|deodorant|fragrance|cologne|shampoo|conditioner|hair\s*oil|serum|sunscreen|moisturizer|face\s*wash|cleanser|cream|lotion|makeup|lipstick|foundation|eyeliner|mascara|trimmer|shaver|hair\s*dryer|straightener)\b/i.test(lower)) return 'Beauty & Grooming';

  // Bags, Luggage & Travel
  if (/\b(bag|backpack|rucksack|duffle|luggage|trolley|suitcase|handbag|clutch|tote|sling\s*bag|wallet|purse|card\s*holder)\b/i.test(lower)) return 'Bags, Luggage & Travel';

  // Watches & Eyewear
  if (/\b(analog\s*watch|chronograph|automatic\s*watch|sunglasses|eyeglass|spectacles|shades|aviator|wayfarer)\b/i.test(lower)) return 'Watches & Eyewear';

  // Health, Wellness & Nutrition
  if (/\b(whey|protein|creatine|bcaa|mass\s*gainer|vitamin|supplement|collagen|fish\s*oil|multivitamin|bp\s*monitor|glucometer|thermometer|nebulizer)\b/i.test(lower)) return 'Health & Nutrition';

  // Baby & Toys
  if (/\b(diaper|baby\s*wipes|stroller|pram|crib|baby\s*food|baby\s*lotion|toy|lego|barbie|hot\s*wheels|board\s*game|puzzle|action\s*figure|doll|nerf)\b/i.test(lower)) return 'Baby & Toys';

  // Books & Stationery
  if (/book|novel|fiction|biography|textbook|comic|manga|notebook|diary|pen|pencil|fountain\s*pen|marker|calculator|art\s*supplies/i.test(lower)) return 'Books & Stationery';

  // Groceries & Gourmet
  if (/tea|coffee|green\s*tea|oil|ghee|rice|atta|flour|pulses|dal|spices|masala|chocolate|biscuit|cookie|snack|chips|dry\s*fruits|nuts|honey|cereal|oats/i.test(lower)) return 'Groceries & Gourmet';

  return 'General E-Commerce';
}

export function detectBrand(q: string): string | null {
  const lower = q.toLowerCase();

  // Curated brands list with exact professional casing
  const brandCatalog: Record<string, string> = {
    // Tech & Electronics
    'apple': 'Apple',
    'iphone': 'Apple',
    'ipad': 'Apple',
    'macbook': 'Apple',
    'airpods': 'Apple',
    'samsung': 'Samsung',
    'galaxy': 'Samsung',
    'google': 'Google',
    'pixel': 'Google',
    'oneplus': 'OnePlus',
    'xiaomi': 'Xiaomi',
    'redmi': 'Redmi',
    'poco': 'POCO',
    'realme': 'Realme',
    'vivo': 'Vivo',
    'oppo': 'Oppo',
    'motorola': 'Motorola',
    'moto': 'Motorola',
    'iqoo': 'iQOO',
    'nothing': 'Nothing',
    'infinix': 'Infinix',
    'tecno': 'Tecno',
    'honor': 'Honor',
    'sony': 'Sony',
    'bravia': 'Sony',
    'playstation': 'PlayStation',
    'ps5': 'PlayStation',
    'xbox': 'Xbox',
    'nintendo': 'Nintendo',
    'asus': 'ASUS',
    'rog': 'ASUS ROG',
    'dell': 'Dell',
    'alienware': 'Alienware',
    'hp': 'HP',
    'lenovo': 'Lenovo',
    'thinkpad': 'Lenovo',
    'acer': 'Acer',
    'msi': 'MSI',
    'razer': 'Razer',
    'logitech': 'Logitech',
    'lg': 'LG',
    'panasonic': 'Panasonic',
    'canon': 'Canon',
    'nikon': 'Nikon',
    'fujifilm': 'Fujifilm',
    'gopro': 'GoPro',
    'dji': 'DJI',
    'sandisk': 'SanDisk',
    'kingston': 'Kingston',
    'corsair': 'Corsair',
    'crucial': 'Crucial',
    'seagate': 'Seagate',
    'western digital': 'Western Digital',
    'tp-link': 'TP-Link',
    'netgear': 'Netgear',
    'intel': 'Intel',
    'amd': 'AMD',
    'nvidia': 'NVIDIA',

    // Audio & Wearables
    'boat': 'boAt',
    'noise': 'Noise',
    'boult': 'Boult',
    'fire-boltt': 'Fire-Boltt',
    'jbl': 'JBL',
    'bose': 'Bose',
    'sennheiser': 'Sennheiser',
    'marshall': 'Marshall',
    'skullcandy': 'Skullcandy',
    'jabra': 'Jabra',
    'anker': 'Anker',
    'soundcore': 'Soundcore',
    'mivi': 'Mivi',
    'portronics': 'Portronics',
    'zebronics': 'Zebronics',
    'edifier': 'Edifier',
    'fitbit': 'Fitbit',
    'garmin': 'Garmin',
    'amazfit': 'Amazfit',
    'fossil': 'Fossil',
    'titan': 'Titan',
    'fastrack': 'Fastrack',
    'casio': 'Casio',
    'g-shock': 'G-Shock',
    'timex': 'Timex',
    'tissot': 'Tissot',
    'daniel wellington': 'Daniel Wellington',

    // Home & Kitchen Appliances
    'philips': 'Philips',
    'bajaj': 'Bajaj',
    'havells': 'Havells',
    'crompton': 'Crompton',
    'orient': 'Orient',
    'usha': 'Usha',
    'prestige': 'Prestige',
    'hawkins': 'Hawkins',
    'pigeon': 'Pigeon',
    'milton': 'Milton',
    'cello': 'Cello',
    'borosil': 'Borosil',
    'morphy richards': 'Morphy Richards',
    'wonderchef': 'Wonderchef',
    'butterfly': 'Butterfly',
    'whirlpool': 'Whirlpool',
    'godrej': 'Godrej',
    'haier': 'Haier',
    'bosch': 'Bosch',
    'ifb': 'IFB',
    'siemens': 'Siemens',
    'voltas': 'Voltas',
    'daikin': 'Daikin',
    'blue star': 'Blue Star',
    'hitachi': 'Hitachi',
    'carrier': 'Carrier',
    'lloyd': 'Lloyd',
    'kent': 'Kent',
    'eureka forbes': 'Eureka Forbes',
    'aquaguard': 'Aquaguard',
    'pureit': 'Pureit',
    'dyson': 'Dyson',
    'roomba': 'iRobot Roomba',

    // Footwear & Sportswear
    'nike': 'Nike',
    'jordan': 'Nike Jordan',
    'adidas': 'Adidas',
    'puma': 'Puma',
    'reebok': 'Reebok',
    'under armour': 'Under Armour',
    'skechers': 'Skechers',
    'asics': 'Asics',
    'new balance': 'New Balance',
    'woodland': 'Woodland',
    'red tape': 'Red Tape',
    'bata': 'Bata',
    'sparx': 'Sparx',
    'campus': 'Campus',
    'crocs': 'Crocs',
    'clarks': 'Clarks',
    'birkenstock': 'Birkenstock',
    'hush puppies': 'Hush Puppies',
    'metro': 'Metro',
    'mochi': 'Mochi',

    // Fashion & Apparel
    'levis': "Levi's",
    "levi's": "Levi's",
    'zara': 'Zara',
    'h&m': 'H&M',
    'hm': 'H&M',
    'marks & spencer': 'Marks & Spencer',
    'allen solly': 'Allen Solly',
    'van heusen': 'Van Heusen',
    'peter england': 'Peter England',
    'louis philippe': 'Louis Philippe',
    'arrow': 'Arrow',
    'us polo': 'U.S. Polo Assn.',
    'tommy hilfiger': 'Tommy Hilfiger',
    'calvin klein': 'Calvin Klein',
    'jack & jones': 'Jack & Jones',
    'flying machine': 'Flying Machine',
    'pepe jeans': 'Pepe Jeans',
    'wrangler': 'Wrangler',
    'spykar': 'Spykar',
    'biba': 'Biba',
    'w': 'W for Woman',
    'aurelia': 'Aurelia',
    'fabindia': 'Fabindia',
    'manyavar': 'Manyavar',
    'libas': 'Libas',
    'global desi': 'Global Desi',
    'roadster': 'Roadster',
    'hrx': 'HRX',
    'wrogn': 'Wrogn',
    'snitch': 'Snitch',
    'zudio': 'Zudio',
    'max': 'Max Fashion',
    'pantaloons': 'Pantaloons',
    'westside': 'Westside',
    'raymond': 'Raymond',

    // Beauty, Skincare & Grooming
    "l'oreal": "L'Oréal",
    'loreal': "L'Oréal",
    'maybelline': 'Maybelline',
    'lakme': 'Lakmé',
    'nivea': 'Nivea',
    'garnier': 'Garnier',
    'dove': 'Dove',
    'mamaearth': 'Mamaearth',
    'minimalist': 'Minimalist',
    'the derma co': 'The Derma Co',
    'plum': 'Plum',
    'dot & key': 'Dot & Key',
    'wow': 'WOW Skin Science',
    'cetaphil': 'Cetaphil',
    'neutrogena': 'Neutrogena',
    'biotique': 'Biotique',
    'himalaya': 'Himalaya',
    'forest essentials': 'Forest Essentials',
    'kama ayurveda': 'Kama Ayurveda',
    'nykaa': 'Nykaa',
    'sugar': 'SUGAR Cosmetics',
    'clinique': 'Clinique',
    'mac': 'M.A.C',
    'estee lauder': 'Estée Lauder',
    'beardo': 'Beardo',
    'ustraa': 'Ustraa',
    'bombay shaving company': 'Bombay Shaving Company',
    'the man company': 'The Man Company',
    'braun': 'Braun',
    'gillette': 'Gillette',

    // Bags & Luggage
    'american tourister': 'American Tourister',
    'samsonite': 'Samsonite',
    'safari': 'Safari',
    'skybags': 'Skybags',
    'vip': 'VIP',
    'wildcraft': 'Wildcraft',
    'aristocrat': 'Aristocrat',
    'mokobara': 'Mokobara',
    'lavie': 'Lavie',
    'caprese': 'Caprese',
    'baggit': 'Baggit',
    'lino perros': 'Lino Perros',

    // Sports & Fitness
    'decathlon': 'Decathlon',
    'quechua': 'Quechua',
    'domyos': 'Domyos',
    'kalenji': 'Kalenji',
    'cosco': 'Cosco',
    'yonex': 'Yonex',
    'li-ning': 'Li-Ning',
    'nivia': 'Nivia',
    'optimum nutrition': 'Optimum Nutrition',
    'muscleblaze': 'MuscleBlaze',
    'myprotein': 'MyProtein',
    'dymatize': 'Dymatize',
    'as-it-is': 'AS-IT-IS',
    'gnc': 'GNC',
    'hercules': 'Hercules',
    'hero': 'Hero Cycles',

    // Books, Stationery & Hobbies
    'penguin': 'Penguin',
    'harpercollins': 'HarperCollins',
    'oxford': 'Oxford',
    'classmate': 'Classmate',
    'parker': 'Parker',
    'camlin': 'Camlin',
    'faber-castell': 'Faber-Castell',
    'staedtler': 'Staedtler',
    'casio calculator': 'Casio',

    // Toys & Baby
    'lego': 'LEGO',
    'barbie': 'Barbie',
    'hot wheels': 'Hot Wheels',
    'fisher-price': 'Fisher-Price',
    'funskool': 'Funskool',
    'nerf': 'NERF',
    'hasbro': 'Hasbro',
    'pampers': 'Pampers',
    'huggies': 'Huggies',
    'mamypoko': 'MamyPoko',
    'chicco': 'Chicco',
    'sebamed': 'Sebamed',

    // Food, Gourmet, Atta / Flours, Grains & Groceries
    'aashirvaad': 'Aashirvaad',
    'fortune': 'Fortune',
    'pillsbury': 'Pillsbury',
    'nature fresh': 'Nature Fresh',
    'shakti bhog': 'Shakti Bhog',
    'rajdhani': 'Rajdhani',
    'patanjali': 'Patanjali',
    'tata sampann': 'Tata Sampann',
    'tata': 'Tata',
    'daawat': 'Daawat',
    'india gate': 'India Gate',
    'kohinoor': 'Kohinoor',
    'slurrp farm': 'Slurrp Farm',
    'true elements': 'True Elements',
    'organic tattva': 'Organic Tattva',
    '24 mantra': '24 Mantra',
    'catch': 'Catch',
    'everest': 'Everest',
    'mdh': 'MDH',
    'badshah': 'Badshah',
    'suhana': 'Suhana',
    'eastern': 'Eastern',
    'ramdev': 'Ramdev',
    'saffola': 'Saffola',
    'dhara': 'Dhara',
    'gemini cooking oil': 'Gemini',
    'emami': 'Emami',
    'dalda': 'Dalda',
    'ananda': 'Ananda',
    'gowardhan': 'Gowardhan',
    'amul': 'Amul',
    'mother dairy': 'Mother Dairy',
    'milky mist': 'Milky Mist',
    'nestle': 'Nestlé',
    'britannia': 'Britannia',
    'sunfeast': 'Sunfeast',
    'parle': 'Parle',
    'cadbury': 'Cadbury',
    'ferrero': 'Ferrero Rocher',
    'kelloggs': "Kellogg's",
    'bagrrys': "Bagrry's",
    'haldiram': 'Haldiram',
    'bikaji': 'Bikaji',
    'bikano': 'Bikano',
    'balaji': 'Balaji',
    'lays': "Lay's",
    'kurkure': 'Kurkure',
    'bingo': 'Bingo!',
    'doritos': 'Doritos',
    'pringles': 'Pringles',
    'quaker': 'Quaker',
    'kissan': 'Kissan',
    'maggi': 'Maggi',
    'top ramen': 'Top Ramen',
    'knorr': 'Knorr',
    "ching's": "Ching's Secret",
    'chings': "Ching's Secret",
    'veeba': 'Veeba',
    'dr oetker': 'Dr. Oetker',
    'del monte': 'Del Monte',
    'nutella': 'Nutella',
    'hersheys': "Hershey's",
    'bournvita': 'Bournvita',
    'horlicks': 'Horlicks',
    'boost': 'Boost',
    'complan': 'Complan',
    'pediasure': 'PediaSure',
    'ensure': 'Ensure',
    'protinex': 'Protinex',
    'dabur': 'Dabur',
    'tropicana': 'Tropicana',
    'real fruit': 'Real',
    'paper boat': 'Paper Boat',
    'red bull': 'Red Bull',
    'monster': 'Monster Energy',
    'nescafe': 'Nescafé',
    'bru': 'BRU',
    'starbucks': 'Starbucks',
    'blue tokai': 'Blue Tokai',
    'sleepy owl': 'Sleepy Owl',
    'wagh bakri': 'Wagh Bakri',
    'taj mahal': 'Taj Mahal',
    'red label': 'Red Label',
  };

  for (const [key, displayName] of Object.entries(brandCatalog)) {
    // Match whole words or boundary patterns
    const regex = new RegExp(`\\b${key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'i');
    if (regex.test(lower)) {
      return displayName;
    }
  }

  return null;
}

// Common Indian e-commerce synonym pairs to bridge user searches (e.g., 'flour' <-> 'atta', 'mobile' <-> 'phone')
const SYNONYMS: Record<string, string[]> = {
  'flour': ['flour', 'atta', 'chakki', 'gehu', 'maida', 'besan'],
  'atta': ['atta', 'flour', 'chakki', 'gehu'],
  'wheat': ['wheat', 'atta', 'gehu', 'chakki'],
  'oil': ['oil', 'tel', 'refined'],
  'rice': ['rice', 'chawal', 'basmati'],
  'pulses': ['pulses', 'pulse', 'dal', 'daal', 'lentil', 'lentils'],
  'dal': ['dal', 'daal', 'pulses', 'lentils'],
  'sugar': ['sugar', 'cheeni', 'shakar'],
  'tea': ['tea', 'chai', 'patti'],
  'coffee': ['coffee', 'kafi'],
  'ghee': ['ghee', 'butter'],
  'phone': ['phone', 'mobile', 'smartphone'],
  'mobile': ['mobile', 'phone', 'smartphone'],
  'earbuds': ['earbuds', 'earphone', 'earphones', 'tws', 'airpods', 'headphones', 'neckband'],
  'headphones': ['headphones', 'headphone', 'earphone', 'earphones', 'tws', 'earbuds'],
  'laptop': ['laptop', 'notebook', 'macbook'],
};

function similarityScore(strA: string, strB: string): number {
  // CRITICAL: Different brands must NEVER be clustered together into the same product!
  const brandA = detectBrand(strA);
  const brandB = detectBrand(strB);
  if (brandA && brandB && brandA.toLowerCase() !== brandB.toLowerCase()) {
    return 0;
  }

  // CRITICAL: Different specific categories must NEVER be merged! (e.g. camera grip vs fitness hand gripper)
  const catA = detectCategory(strA);
  const catB = detectCategory(strB);
  if (catA !== 'General E-Commerce' && catB !== 'General E-Commerce' && catA !== catB) {
    return 0;
  }

  // CRITICAL: Incompatible specs (e.g. 5kg fixed vs 5-60kg adjustable, 128gb vs 256gb) must NOT be merged!
  const specsA = extractProductSpecs(strA);
  const specsB = extractProductSpecs(strB);
  if (areSpecsIncompatible(specsA, specsB)) {
    return 0;
  }

  const wordsA = new Set(strA.toLowerCase().replace(/[^a-z0-9 ]/g, '').split(/\s+/).filter(w => w.length > 2));
  const wordsB = new Set(strB.toLowerCase().replace(/[^a-z0-9 ]/g, '').split(/\s+/).filter(w => w.length > 2));
  if (wordsA.size === 0 || wordsB.size === 0) return 0;

  let intersection = 0;
  for (const w of wordsA) {
    if (wordsB.has(w)) {
      intersection++;
    } else {
      const syns = SYNONYMS[w];
      if (syns && syns.some(s => wordsB.has(s))) {
        intersection += 0.8;
      }
    }
  }
  return (2 * intersection) / (wordsA.size + wordsB.size);
}

export function calculateRelevanceScore(query: string, title: string, isSponsored?: boolean): number {
  const cleanQ = normalizeSearchQuery(query);
  const titleLower = title.trim().toLowerCase();
  if (!cleanQ || !titleLower) return 0;

  let score = 0;

  // Category mismatch check: e.g. "hand gripper" vs GoPro mount/camera grip or phone case
  const queryCat = detectCategory(cleanQ);
  const titleCat = detectCategory(titleLower);
  if (queryCat !== 'General E-Commerce' && titleCat !== 'General E-Commerce' && queryCat !== titleCat) {
    return 0;
  }

  // Specifications evaluation (adjustability, resistance range, storage)
  const qSpecs = extractProductSpecs(cleanQ);
  const tSpecs = extractProductSpecs(titleLower);
  if (areSpecsIncompatible(qSpecs, tSpecs)) {
    score -= 80;
  } else {
    if (qSpecs.resistanceRange && tSpecs.resistanceRange === qSpecs.resistanceRange) {
      score += 50;
    }
    if (qSpecs.isAdjustable && tSpecs.isAdjustable) {
      score += 35;
    }
    if (qSpecs.storage && tSpecs.storage === qSpecs.storage) {
      score += 40;
    }
  }

  // 1. Exact phrase match bonus
  if (titleLower.includes(cleanQ)) {
    score += 60;
  }

  // 2. Brand-Aware Prioritization & Competitor Brand Penalty
  const queryBrand = detectBrand(cleanQ);
  const titleBrand = detectBrand(titleLower);

  if (queryBrand) {
    const qbLower = queryBrand.toLowerCase();
    const hasQueryBrand = titleLower.includes(qbLower) || (titleBrand && titleBrand.toLowerCase() === qbLower);

    if (hasQueryBrand) {
      // User specifically asked for this brand and title delivers it
      score += 80;
    } else if (titleBrand && titleBrand.toLowerCase() !== qbLower) {
      // User asked for Brand A (e.g. Fortune), but this item belongs to Brand B (e.g. Aashirvaad)
      score -= 120;
    } else {
      // Item completely omits the requested brand
      score -= 50;
    }
  }

  // 3. Query words matching ratio with synonym expansion (e.g. 'flour' matches 'atta')
  const queryTokens = cleanQ.replace(/[^a-z0-9 ]/g, '').split(/\s+/).filter(w => w.length > 0);
  const titleTokens = new Set(titleLower.replace(/[^a-z0-9 ]/g, '').split(/\s+/).filter(w => w.length > 0));

  if (queryTokens.length > 0) {
    let matchedCount = 0;
    for (const token of queryTokens) {
      if (titleTokens.has(token) || titleLower.includes(token)) {
        matchedCount++;
      } else {
        const syns = SYNONYMS[token];
        if (syns && syns.some(s => titleTokens.has(s) || titleLower.includes(s))) {
          matchedCount += 0.9;
        }
      }
    }
    const tokenMatchRatio = matchedCount / queryTokens.length;
    score += tokenMatchRatio * 40;

    if (matchedCount === 0) {
      score -= 50;
    }
  }

  // 4. Sponsored penalty: Prioritize organic product matches over paid advertisements
  if (isSponsored) {
    score -= 25;
  }

  return Math.max(0, score);
}

function isProductCategorySupportedByStore(store: Store, query: string, category: string): boolean {
  return isCategorySupportedByStore(store, category, query);
}

export async function aggregateSearchResults(query: string): Promise<Product[]> {
  const cleanQ = normalizeSearchQuery(query);
  if (!cleanQ) return [];

  const detectedCategory = detectCategory(cleanQ);

  // 1. Scrape Amazon as primary source of items, prices, and high-res images
  let amazonItems: RawScrapedItem[] = [];

  try {
    amazonItems = await scrapeAmazon(cleanQ);
  } catch (err: any) {
    console.warn(`[Amazon Scraper Notice] ${err.message}`);
  }

  // 2. Fetch other stores in parallel, but ONLY if they support the detected category
  const otherTasks: Array<() => Promise<RawScrapedItem[]>> = [];
  if (isCategorySupportedByStore(Store.FLIPKART, detectedCategory, cleanQ)) {
    otherTasks.push(() => scrapeFlipkart(cleanQ).catch(() => []));
  }
  if (isCategorySupportedByStore(Store.MEESHO, detectedCategory, cleanQ)) {
    otherTasks.push(() => scrapeMeesho(cleanQ).catch(() => []));
  }
  if (isCategorySupportedByStore(Store.MYNTRA, detectedCategory, cleanQ)) {
    otherTasks.push(() => scrapeMyntra(cleanQ).catch(() => []));
  }
  if (isCategorySupportedByStore(Store.CROMA, detectedCategory, cleanQ)) {
    otherTasks.push(() => scrapeCroma(cleanQ).catch(() => []));
  }
  if (isCategorySupportedByStore(Store.RELIANCE_DIGITAL, detectedCategory, cleanQ)) {
    otherTasks.push(() => scrapeRelianceDigital(cleanQ).catch(() => []));
  }
  if (isCategorySupportedByStore(Store.TATACLIQ, detectedCategory, cleanQ)) {
    otherTasks.push(() => scrapeTataCliq(cleanQ).catch(() => []));
  }
  if (isCategorySupportedByStore(Store.AJIO, detectedCategory, cleanQ)) {
    otherTasks.push(() => scrapeAjio(cleanQ).catch(() => []));
  }
  if (isCategorySupportedByStore(Store.EBAY, detectedCategory, cleanQ)) {
    otherTasks.push(() => scrapeEbay(cleanQ).catch(() => []));
  }
  if (isCategorySupportedByStore(Store.NYKAA, detectedCategory, cleanQ)) {
    otherTasks.push(() => scrapeNykaa(cleanQ).catch(() => []));
  }

  const allItems: RawScrapedItem[] = [...amazonItems];
  const otherResults = await Promise.allSettled(otherTasks.map(fn => fn()));
  for (const res of otherResults) {
    if (res.status === 'fulfilled' && Array.isArray(res.value)) {
      allItems.push(...res.value);
    }
  }

  if (allItems.length === 0) {
    return [];
  }

  // Filter items by relevance for multi-word queries. For 1-2 word queries (e.g. "shoes"), trust store search relevance
  const isShortQuery = cleanQ.split(/\s+/).length <= 2;
  const queryWords = cleanQ.toLowerCase().replace(/[^a-z0-9 ]/g, '').split(/\s+/).filter(w => w.length > 1);
  const queryBrand = detectBrand(cleanQ);

  const relevantItems = allItems.filter(item => {
    if (isShortQuery || queryWords.length === 0) return true;
    const titleLower = item.title.toLowerCase();

    // If query specified a brand and this item belongs to that brand, ALWAYS preserve it
    if (queryBrand) {
      const qbLower = queryBrand.toLowerCase();
      if (titleLower.includes(qbLower) || detectBrand(titleLower)?.toLowerCase() === qbLower) {
        return true;
      }
    }

    let matchCount = 0;
    for (const w of queryWords) {
      if (titleLower.includes(w)) {
        matchCount++;
      } else {
        const syns = SYNONYMS[w];
        if (syns && syns.some(s => titleLower.includes(s))) {
          matchCount++;
        }
      }
    }
    return matchCount >= Math.max(1, Math.ceil(queryWords.length * 0.4));
  });

  const itemsToCluster = relevantItems.length > 0 ? relevantItems : allItems;

  // 2. Cluster / Group items into distinct products
  const productClusters: Array<{
    title: string;
    items: RawScrapedItem[];
    relevanceScore: number;
    asin?: string | null;
  }> = [];

  for (const item of itemsToCluster) {
    const itemBrand = detectBrand(item.title);

    let matchedCluster = productClusters.find(c => {
      const clusterBrand = detectBrand(c.title);
      // STRICT BRAND ISOLATION: Never merge different brands into the same cluster!
      if (clusterBrand && itemBrand && clusterBrand.toLowerCase() !== itemBrand.toLowerCase()) {
        return false;
      }
      return similarityScore(c.title, item.title) > 0.45;
    });

    const itemScore = calculateRelevanceScore(cleanQ, item.title, item.isSponsored);

    if (matchedCluster) {
      // Don't add duplicate store offers for the same cluster if one already exists
      const hasStore = matchedCluster.items.some(i => i.store === item.store);
      if (!hasStore) {
        matchedCluster.items.push(item);
      }
      if (!matchedCluster.asin && item.asin) {
        matchedCluster.asin = item.asin;
      }
      if (itemScore > matchedCluster.relevanceScore) {
        matchedCluster.relevanceScore = itemScore;
        // Promote the better-matching organic item title as the cluster title
        matchedCluster.title = item.title;
      }
    } else {
      productClusters.push({
        title: item.title,
        items: [item],
        relevanceScore: itemScore,
        asin: item.asin || null,
      });
    }
  }

  // Sort clusters so requested brand and best organic match to user's query is at index 0
  productClusters.sort((a, b) => {
    if (queryBrand) {
      const qbLower = queryBrand.toLowerCase();
      const aBrandMatch = a.title.toLowerCase().includes(qbLower) || detectBrand(a.title)?.toLowerCase() === qbLower;
      const bBrandMatch = b.title.toLowerCase().includes(qbLower) || detectBrand(b.title)?.toLowerCase() === qbLower;
      if (aBrandMatch && !bBrandMatch) return -1;
      if (!aBrandMatch && bBrandMatch) return 1;
    }
    return b.relevanceScore - a.relevanceScore;
  });

  // 3. Transform clusters into Product objects with dynamic rate, link, and verified image allocation
  const products: Product[] = [];
  const selectedClusters = productClusters.slice(0, 12);

  for (let clusterIdx = 0; clusterIdx < selectedClusters.length; clusterIdx++) {
    const cluster = selectedClusters[clusterIdx];
    const primaryItem = cluster.items[0];
    const productId = cluster.asin
      ? `amazon_${cluster.asin.toLowerCase()}`
      : cluster.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').slice(0, 60);

    // 1. Resolve product image STRICTLY from items belonging to this specific cluster
    let highResImage: string | null = null;
    const amazonItem = cluster.items.find(i => i.store === Store.AMAZON && isValidImageUrl(i.imageUrl));
    if (amazonItem?.imageUrl) {
      highResImage = optimizeProductImageUrl(amazonItem.imageUrl) || amazonItem.imageUrl;
    }

    // Check all scraped cluster items if Amazon image not in cluster
    if (!highResImage) {
      const anyScrapedWithImage = cluster.items.find(i => isValidImageUrl(i.imageUrl));
      if (anyScrapedWithImage?.imageUrl) {
        highResImage = optimizeProductImageUrl(anyScrapedWithImage.imageUrl) || anyScrapedWithImage.imageUrl;
      }
    }

    // Fallback: If this cluster has no image, attempt image resolution ONLY for this cluster's exact title
    if (!highResImage) {
      try {
        const clusterTitleImg = await fetchWebImageFallback(cleanTitle(cluster.title));
        if (clusterTitleImg && isValidImageUrl(clusterTitleImg)) {
          highResImage = optimizeProductImageUrl(clusterTitleImg) || clusterTitleImg;
        }
      } catch { }
    }

    const ALL_MARKETPLACES: Store[] = [
      Store.AMAZON,
      Store.FLIPKART,
      Store.MEESHO,
      Store.MYNTRA,
      Store.CROMA,
      Store.RELIANCE_DIGITAL,
      Store.JIOMART,
      Store.TATACLIQ,
      Store.AJIO,
      Store.NYKAA,
      Store.BLINKIT,
      Store.EBAY,
      Store.WALMART,
      Store.TARGET,
    ];

    const storeOffers: StoreOffer[] = [];
    const category = detectCategory(cluster.title);

    for (const store of ALL_MARKETPLACES) {
      const isSupported = isCategorySupportedByStore(store, category, cleanQ);
      const searchUrl = buildMarketplaceSearchUrl(store, cleanQ);

      if (!isSupported) {
        storeOffers.push({
          id: `${productId}_${store.toLowerCase()}`,
          productId,
          store,
          productUrl: searchUrl,
          searchUrl,
          isDirectProductUrl: false,
          status: MarketplaceStatus.UNSUPPORTED_CATEGORY,
          matchScore: null,
          price: 0,
          originalPrice: 0,
          discountPercentage: 0,
          currency: 'INR',
          availability: Availability.OUT_OF_STOCK,
          deliveryInfo: `(Store does not carry ${category})`,
          imageUrl: highResImage,
          lastVerifiedAt: Date.now(),
          lastUpdated: Date.now(),
        });
        continue;
      }

      // Check if cluster has a verified scraped item for this store
      const scrapedItem = cluster.items.find(i => i.store === store);
      if (scrapedItem && scrapedItem.price && scrapedItem.price > 10) {
        const isDirect = isVerifiedProductUrl(store, scrapedItem.url);
        const offerImg = scrapedItem.imageUrl ? (optimizeProductImageUrl(scrapedItem.imageUrl) || scrapedItem.imageUrl) : highResImage;
        const matchScore = calculateRelevanceScore(cleanQ, scrapedItem.title, scrapedItem.isSponsored);

        storeOffers.push({
          id: `${productId}_${store.toLowerCase()}`,
          productId,
          store,
          productUrl: scrapedItem.url,
          searchUrl,
          isDirectProductUrl: isDirect,
          status: scrapedItem.availability === Availability.OUT_OF_STOCK ? MarketplaceStatus.OUT_OF_STOCK : MarketplaceStatus.AVAILABLE,
          matchScore,
          price: scrapedItem.price,
          originalPrice: scrapedItem.originalPrice,
          discountPercentage: scrapedItem.discountPercentage,
          currency: 'INR',
          availability: scrapedItem.availability,
          deliveryInfo: scrapedItem.title,
          imageUrl: offerImg,
          lastVerifiedAt: Date.now(),
          lastUpdated: Date.now(),
        });
      } else {
        // Store supports category, but no verified matching item found
        storeOffers.push({
          id: `${productId}_${store.toLowerCase()}`,
          productId,
          store,
          productUrl: searchUrl,
          searchUrl,
          isDirectProductUrl: false,
          status: MarketplaceStatus.NO_RESULTS,
          matchScore: null,
          price: 0,
          originalPrice: 0,
          discountPercentage: 0,
          currency: 'INR',
          availability: Availability.OUT_OF_STOCK,
          deliveryInfo: 'No matching products found',
          imageUrl: highResImage,
          lastVerifiedAt: Date.now(),
          lastUpdated: Date.now(),
        });
      }
    }

    // Ratings & Reviews
    const validRatings = cluster.items.map(i => i.rating).filter((r): r is number => r !== null && r > 0);
    const avgRating = validRatings.length > 0 ? Number((validRatings.reduce((a, b) => a + b, 0) / validRatings.length).toFixed(1)) : null;

    const validReviews = cluster.items.map(i => i.reviewCount).filter((r): r is number => r !== null && r > 0);
    const totalReviews = validReviews.length > 0 ? validReviews.reduce((a, b) => a + b, 0) : null;

    // Detect variants (e.g., storage or color tokens)
    const variants: ProductVariant[] = [];
    const storageMatches = cluster.title.match(/(64GB|128GB|256GB|512GB|1TB|8GB|16GB|32GB)/ig);
    if (storageMatches) {
      storageMatches.forEach((s, idx) => {
        variants.push({
          id: `${productId}_var_${idx}`,
          productId,
          name: s.toUpperCase(),
          type: 'Storage',
          price: primaryItem.price,
          isAvailable: true,
        });
      });
    }

    const primaryOffer = storeOffers.find((s) => s.price !== null && s.price > 0);
    const lowestOfferPrice = primaryOffer?.price ?? null;
    const primaryStore = primaryOffer?.store ?? null;
    const productCategory = detectCategory(cluster.title);

    // Calculate real dynamic bank offers & No-Cost EMI plans
    const paymentOffers = calculatePaymentOffers(lowestOfferPrice, primaryStore, productCategory);
    const emiPlans = calculateEmiPlans(lowestOfferPrice, primaryStore);

    // AI Review Summary generation across all queried stores
    const availableStores = storeOffers.map((s) => s.store);
    const reviewSummary = await generateProductReviewSummaryWithGemini(
      cluster.title,
      availableStores,
      avgRating,
      totalReviews
    );

    products.push({
      id: productId,
      asin: cluster.asin || null,
      searchQuery: cleanQ,
      title: cleanTitle(cluster.title),
      description: `Live price comparison across online stores for ${cluster.title}.`,
      imageUrl: highResImage || null,
      category: productCategory,
      brand: detectBrand(cluster.title),
      rating: avgRating,
      reviewCount: totalReviews,
      variants,
      stores: storeOffers,
      reviewSummary,
      paymentOffers,
      emiPlans,
      lastUpdated: Date.now(),
    });
  }

  return products;
}
