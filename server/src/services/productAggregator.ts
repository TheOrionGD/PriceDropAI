import { Product, StoreOffer, Store, Availability, RawScrapedItem, ProductVariant, PaymentOffer, PaymentOfferType, MarketplaceStatus } from '../types/index.js';
import { calculatePaymentOffers, calculateEmiPlans } from './bankingService.js';
import { scrapeAmazon } from '../scrapers/amazon.js';
import { scrapeFlipkart } from '../scrapers/flipkart.js';
import { scrapeMeesho } from '../scrapers/meesho.js';
import { scrapeMyntra } from '../scrapers/myntra.js';
import { fetchWebImageFallback, isValidImageUrl, optimizeProductImageUrl } from '../scrapers/webFallback.js';
import { generateProductReviewSummaryWithGroq } from './groqService.js';
import axios from 'axios';
import { getGroqKey } from '../utils/keyManager.js';
import {
  normalizeSearchQuery,
  extractProductSpecs,
  areSpecsIncompatible,
  isVerifiedProductUrl,
  buildMarketplaceSearchUrl,
  isCategorySupportedByStore
} from '../utils/storeUrlResolver.js';

export type ActiveStore = Store.AMAZON | Store.FLIPKART | Store.MEESHO | Store.MYNTRA;

export const ACTIVE_MARKETPLACES: ActiveStore[] = [
  Store.AMAZON,
  Store.FLIPKART,
  Store.MEESHO,
  Store.MYNTRA,
];

function cleanTitle(title: string): string {
  return title
    .replace(/([A-Z]{2,})([A-Z][a-z]+)/g, '$1 $2')
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

export function cleanFeaturesBeforeBrandDetection(text: string): string {
  return text
    .replace(/([A-Z]{2,})([A-Z][a-z]+)/g, '$1 $2')
    .replace(/\b(?:active\s+)?noise\s+(?:cancellation|cancelling|reduction|isolation)\b/gi, ' ')
    .replace(/\bnoise\s+cancel(?:ling)?\b/gi, ' ')
    .replace(/\bpaper\s+boat\b/gi, ' ')
    .replace(/\breal\s+(?:leather|wood|fruit|juice)\b/gi, ' ')
    .replace(/\bapple\s+(?:cider|flavor|flavour|scent)\b/gi, ' ');
}

export function detectBrand(q: string): string | null {
  const lower = cleanFeaturesBeforeBrandDetection(q).toLowerCase();

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
    'truke': 'truke',
    'boat': 'boAt',
    'noise': 'Noise',
    'boult': 'Boult',
    'fire-boltt': 'Fire-Boltt',
    'fireboltt': 'Fire-Boltt',
    'ptron': 'pTron',
    'hammer': 'Hammer',
    'wings': 'Wings',
    'cmf': 'CMF',
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

  // Prioritize matching brand name if it appears at the start of the title/query
  const firstWordMatch = lower.trim().match(/^([a-z0-9\-]+)\b/);
  if (firstWordMatch && brandCatalog[firstWordMatch[1]]) {
    return brandCatalog[firstWordMatch[1]];
  }

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

export type DiagnosticRejectionReason =
  | 'BRAND_CONFLICT'
  | 'MODEL_CONFLICT'
  | 'SPECIFICATION_CONFLICT'
  | 'LOW_TITLE_SIMILARITY'
  | 'MISSING_REQUIRED_IDENTITY'
  | 'PROVIDER_FAILED'
  | 'PARSE_FAILED';

export interface MatchEvaluation {
  isMatch: boolean;
  score: number;
  threshold: number;
  rejectionReason: DiagnosticRejectionReason | null;
  modelA: string;
  modelB: string;
  brandA: string | null;
  brandB: string | null;
}

export interface ExtractedProductEntity {
  index: number;
  brand: string;
  model: string;
  category: string;
}

const COMMON_STOP_WORDS = new Set([
  'new', 'launch', 'newly', 'launched', 'latest', 'original', 'genuine', 'authentic',
  'best', 'top', 'exclusive', 'special', 'edition', 'sale', 'hot', 'deal', 'offer',
  'made', 'in', 'india', 'true', 'truly', 'wireless', 'bluetooth', 'earbuds', 'earbud',
  'earphone', 'earphones', 'headphone', 'headphones', 'headset', 'tws', 'neckband',
  'inear', 'in-ear', 'gaming', 'calls', 'calling', 'stereo', 'sound', 'audio', 'bass',
  'hifi', 'mic', 'mics', 'microphone', 'enc', 'anc', 'noise', 'cancellation', 'cancelling',
  'driver', 'drivers', 'battery', 'playtime', 'playback', 'hours', 'hour', 'hrs',
  'fast', 'charging', 'charge', 'case', 'cover', 'with', 'and', 'for', 'the', 'w/', 'by',
  'deep', 'pure', 'crystal', 'clear', 'rich', 'hd', 'pro', 'max', 'plus',
  'black', 'white', 'blue', 'green', 'red', 'gold', 'silver', 'grey', 'gray', 'matte',
  'carbon', 'midnight', 'charcoal', 'indigo', 'raven', 'ash', 'finish', 'leather',
  'spatial', 'lossless', 'dynamic', 'titanium', 'beryllium', 'battle', 'mode', 'modes'
]);

export function extractModelTokens(title: string, brand?: string | null): string[] {
  let clean = title.toLowerCase()
    .replace(/\(.*?\)/g, ' ')
    .replace(/\[.*?\]/g, ' ')
    .replace(/\|.*$/g, ' ')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  if (brand) {
    clean = clean.replace(new RegExp(`\\b${brand.toLowerCase()}\\b`, 'g'), ' ');
  }

  clean = clean
    .replace(/\b(new launch|newly launched|true wireless in ear|true wireless|truly wireless|bluetooth headset|gaming earbuds|gaming tws)\b/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  const words = clean.split(' ').filter(w => w.length > 0);
  const tokens: string[] = [];

  for (const w of words) {
    if (/^\d+(?:ms|mm|h|hrs|hour|hours|mah|db|bit)$/i.test(w) || /^bt\d+/i.test(w)) {
      break;
    }
    if (COMMON_STOP_WORDS.has(w) && w !== 'buds' && w !== 'air' && w !== 'flex' && w !== 'v2' && w !== 'ultra') {
      continue;
    }
    tokens.push(w);
    if (tokens.length >= 4) break;
  }

  return tokens;
}

/**
 * Real-time AI batch entity extractor powered by Groq LPU models.
 * Runs in parallel across candidate titles to identify canonical brand and clean model names.
 */
export async function extractEntitiesWithAi(titles: string[], timeoutMs: number = 5500): Promise<ExtractedProductEntity[]> {
  const apiKey = getGroqKey(1) || getGroqKey(2) || process.env.GROQ_API_KEY?.trim() || process.env.GROQ_API_KEY1?.trim();
  if (!apiKey || titles.length === 0) return [];

  try {
    const payload = titles.map((t, idx) => ({ index: idx, title: t }));
    const res = await axios.post(
      'https://api.groq.com/openai/v1/chat/completions',
      {
        model: 'openai/gpt-oss-120b',
        messages: [
          {
            role: 'system',
            content: `You are an expert e-commerce product entity extractor. For each product title in the JSON list, extract:
- "brand": Canonical brand/manufacturer name (e.g. "Apple", "Samsung", "boAt", "Sony", "truke", "Nike", "Puma", "Milton", "Philips", "Realme", "OnePlus").
- "model": Core model line/name/number without SEO fluff or marketing keywords (e.g. "iPhone 15", "Galaxy S24", "Airdopes 141", "BTG Ultra", "Mega 10", "Buds Elite V2", "Air Max 90", "WH-1000XM5").
- "category": Concise product category.
Output strictly JSON:
{
  "products": [
    { "index": 0, "brand": "Brand", "model": "Model", "category": "Category" }
  ]
}`
          },
          {
            role: 'user',
            content: JSON.stringify(payload)
          }
        ],
        temperature: 0.1,
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

    const parsed = JSON.parse(res.data?.choices?.[0]?.message?.content || '{}');
    if (Array.isArray(parsed?.products)) {
      return parsed.products;
    }
  } catch (err: any) {
    // Fall back smoothly to deterministic extractor
  }
  return [];
}

export function evaluateProductMatch(
  titleA: string,
  titleB: string,
  knownBrandA?: string | null,
  knownModelA?: string | null,
  knownBrandB?: string | null,
  knownModelB?: string | null
): MatchEvaluation {
  const brandA = knownBrandA || detectBrand(titleA);
  const brandB = knownBrandB || detectBrand(titleB);

  // 1. Strict Brand Conflict Check
  if (brandA && brandB && brandA.toLowerCase() !== brandB.toLowerCase()) {
    return {
      isMatch: false,
      score: 0,
      threshold: 60,
      rejectionReason: 'BRAND_CONFLICT',
      modelA: knownModelA || '',
      modelB: knownModelB || '',
      brandA,
      brandB,
    };
  }

  // 2. Category mismatch check
  const catA = detectCategory(titleA);
  const catB = detectCategory(titleB);
  if (catA !== 'General E-Commerce' && catB !== 'General E-Commerce' && catA !== catB) {
    return {
      isMatch: false,
      score: 0,
      threshold: 60,
      rejectionReason: 'SPECIFICATION_CONFLICT',
      modelA: knownModelA || '',
      modelB: knownModelB || '',
      brandA,
      brandB,
    };
  }

  // 3. Incompatible specs check (resistance, weight, adjustability, storage, volume)
  const specsA = extractProductSpecs(titleA);
  const specsB = extractProductSpecs(titleB);
  if (areSpecsIncompatible(specsA, specsB)) {
    return {
      isMatch: false,
      score: 0,
      threshold: 60,
      rejectionReason: 'SPECIFICATION_CONFLICT',
      modelA: knownModelA || '',
      modelB: knownModelB || '',
      brandA,
      brandB,
    };
  }

  // 4. Model extraction and numeric verification
  const mTokensA = knownModelA ? knownModelA.toLowerCase().split(/\s+/) : extractModelTokens(titleA, brandA);
  const mTokensB = knownModelB ? knownModelB.toLowerCase().split(/\s+/) : extractModelTokens(titleB, brandB);
  const modelA = mTokensA.join(' ');
  const modelB = mTokensB.join(' ');

  // Numeric Model Conflict (e.g. Mega 10 vs Mega 7, iPhone 15 vs 16, S24 vs S23)
  const numbersA = mTokensA.filter(t => /^\d+$/.test(t));
  const numbersB = mTokensB.filter(t => /^\d+$/.test(t));
  if (numbersA.length > 0 && numbersB.length > 0) {
    const hasSharedNumber = numbersA.some(n => numbersB.includes(n));
    if (!hasSharedNumber) {
      return {
        isMatch: false,
        score: 10,
        threshold: 60,
        rejectionReason: 'MODEL_CONFLICT',
        modelA,
        modelB,
        brandA,
        brandB,
      };
    }
  }

  // Distinct Sub-Model Conflict Check
  const SUB_MODELS = ['btg', 'mega', 'elite', 'aura', 'flex', 'echo', 'vibe', 'liberty', 'resonancex', 'dyno', 'bass', 'q1', 'air', 'clarity'];
  const subModelA = mTokensA.find(t => SUB_MODELS.includes(t));
  const subModelB = mTokensB.find(t => SUB_MODELS.includes(t));

  if (subModelA && subModelB && subModelA !== subModelB) {
    return {
      isMatch: false,
      score: 15,
      threshold: 60,
      rejectionReason: 'MODEL_CONFLICT',
      modelA,
      modelB,
      brandA,
      brandB,
    };
  }

  // Version Discrepancy (e.g. Buds Elite V1 vs V2)
  const hasV2A = /\bv2\b/i.test(titleA);
  const hasV2B = /\bv2\b/i.test(titleB);
  if (hasV2A !== hasV2B) {
    return {
      isMatch: false,
      score: 25,
      threshold: 60,
      rejectionReason: 'MODEL_CONFLICT',
      modelA,
      modelB,
      brandA,
      brandB,
    };
  }

  // 5. Symmetric Token Overlap & Dice Ratio
  const normalizeTokens = (str: string) => {
    return new Set(
      str.toLowerCase()
        .replace(/[^a-z0-9\s]/g, ' ')
        .split(/\s+/)
        .filter(w => w.length > 2 && !COMMON_STOP_WORDS.has(w))
    );
  };

  const tokensA = normalizeTokens(titleA);
  const tokensB = normalizeTokens(titleB);

  if (tokensA.size === 0 || tokensB.size === 0) {
    return {
      isMatch: false,
      score: 0,
      threshold: 60,
      rejectionReason: 'MISSING_REQUIRED_IDENTITY',
      modelA,
      modelB,
      brandA,
      brandB,
    };
  }

  let matchedCount = 0;
  for (const t of tokensA) {
    if (tokensB.has(t)) {
      matchedCount++;
    }
  }

  const minSize = Math.min(tokensA.size, tokensB.size);
  const overlapRatio = minSize > 0 ? (matchedCount / minSize) : 0;
  const diceRatio = (2 * matchedCount) / (tokensA.size + tokensB.size);

  const mIntersection = mTokensA.filter(w => mTokensB.includes(w));
  let isModelMatch = false;

  if (subModelA && subModelB && subModelA === subModelB) {
    isModelMatch = true;
  } else if (mIntersection.length >= 2 || (mIntersection.length === 1 && mTokensA.length === 1 && mTokensB.length === 1)) {
    isModelMatch = true;
  }

  let score = Math.round((overlapRatio * 60) + (diceRatio * 40));
  if (isModelMatch) {
    score = Math.max(score, 88);
  }

  const threshold = 60;
  const isMatch = score >= threshold;

  return {
    isMatch,
    score,
    threshold,
    rejectionReason: isMatch ? null : 'LOW_TITLE_SIMILARITY',
    modelA,
    modelB,
    brandA,
    brandB,
  };
}

export function similarityScore(strA: string, strB: string): number {
  const evalRes = evaluateProductMatch(strA, strB);
  return evalRes.isMatch ? (evalRes.score / 100) : 0;
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


export interface StoreAuditInfo {
  store: Store;
  searchAttempted: boolean;
  provider: string;
  providerOutcome: 'SUCCESS' | 'EMPTY' | 'FAILED' | 'UNSUPPORTED_CATEGORY';
  rawCandidatesCount: number;
  parsedCandidatesCount: number;
  candidatesSample: Array<{ title: string; price: number | null }>;
  rejectedCount: number;
  rejections: Array<{ title: string; reason: DiagnosticRejectionReason; matchedAgainst?: string }>;
  survivedAggregationCount: number;
  serializedOffersCount: number;
}

export async function aggregateSearchResults(query: string, reqId?: string): Promise<Product[]> {
  const cleanQ = normalizeSearchQuery(query);
  if (!cleanQ) return [];

  const detectedCategory = detectCategory(cleanQ);
  const queryBrand = detectBrand(cleanQ);

  const storeAudits: Record<ActiveStore, StoreAuditInfo> = {
    [Store.AMAZON]: {
      store: Store.AMAZON,
      searchAttempted: false,
      provider: 'ScraperAPI',
      providerOutcome: 'EMPTY',
      rawCandidatesCount: 0,
      parsedCandidatesCount: 0,
      candidatesSample: [],
      rejectedCount: 0,
      rejections: [],
      survivedAggregationCount: 0,
      serializedOffersCount: 0,
    },
    [Store.FLIPKART]: {
      store: Store.FLIPKART,
      searchAttempted: false,
      provider: 'ScraperAPI',
      providerOutcome: 'EMPTY',
      rawCandidatesCount: 0,
      parsedCandidatesCount: 0,
      candidatesSample: [],
      rejectedCount: 0,
      rejections: [],
      survivedAggregationCount: 0,
      serializedOffersCount: 0,
    },
    [Store.MEESHO]: {
      store: Store.MEESHO,
      searchAttempted: false,
      provider: 'Direct/ScraperAPI',
      providerOutcome: 'EMPTY',
      rawCandidatesCount: 0,
      parsedCandidatesCount: 0,
      candidatesSample: [],
      rejectedCount: 0,
      rejections: [],
      survivedAggregationCount: 0,
      serializedOffersCount: 0,
    },
    [Store.MYNTRA]: {
      store: Store.MYNTRA,
      searchAttempted: false,
      provider: 'Direct/ScraperAPI',
      providerOutcome: 'EMPTY',
      rawCandidatesCount: 0,
      parsedCandidatesCount: 0,
      candidatesSample: [],
      rejectedCount: 0,
      rejections: [],
      survivedAggregationCount: 0,
      serializedOffersCount: 0,
    },
  };

  // 1. Parallel scrape of strictly the 4 active marketplaces
  const scrapeTasks: Array<{ store: ActiveStore; fn: () => Promise<RawScrapedItem[]> }> = [];

  for (const store of ACTIVE_MARKETPLACES) {
    const activeStore = store as ActiveStore;
    if (isCategorySupportedByStore(activeStore, detectedCategory, cleanQ)) {
      storeAudits[activeStore].searchAttempted = true;
      if (activeStore === Store.AMAZON) scrapeTasks.push({ store: activeStore, fn: () => scrapeAmazon(cleanQ) });
      else if (activeStore === Store.FLIPKART) scrapeTasks.push({ store: activeStore, fn: () => scrapeFlipkart(cleanQ) });
      else if (activeStore === Store.MEESHO) scrapeTasks.push({ store: activeStore, fn: () => scrapeMeesho(cleanQ) });
      else if (activeStore === Store.MYNTRA) scrapeTasks.push({ store: activeStore, fn: () => scrapeMyntra(cleanQ) });
    } else {
      storeAudits[activeStore].searchAttempted = false;
      storeAudits[activeStore].providerOutcome = 'UNSUPPORTED_CATEGORY';
    }
  }

  const taskResults = await Promise.allSettled(
    scrapeTasks.map(t => t.fn().then(items => ({ store: t.store, items })))
  );

  const validStoreItems: Record<ActiveStore, RawScrapedItem[]> = {
    [Store.AMAZON]: [],
    [Store.FLIPKART]: [],
    [Store.MEESHO]: [],
    [Store.MYNTRA]: [],
  };

  for (const res of taskResults) {
    if (res.status === 'fulfilled') {
      const { store, items } = res.value;
      storeAudits[store].rawCandidatesCount = items.length;
      const valid = items.filter(it => it.title && it.price && it.price > 10 && it.url);
      storeAudits[store].parsedCandidatesCount = valid.length;
      storeAudits[store].providerOutcome = valid.length > 0 ? 'SUCCESS' : 'EMPTY';
      storeAudits[store].candidatesSample = valid.slice(0, 3).map(it => ({ title: it.title.slice(0, 60), price: it.price ?? 0 }));
      validStoreItems[store] = valid;
    } else {
      // Find which store failed
      const failedIdx = taskResults.indexOf(res);
      const failedStore = scrapeTasks[failedIdx]?.store;
      if (failedStore) {
        storeAudits[failedStore].providerOutcome = 'FAILED';
      }
    }
  }

  // 2. Build candidate pool for real-time AI entity extraction (top 4-5 items per store)
  const candidatePool: RawScrapedItem[] = [];
  for (const store of ACTIVE_MARKETPLACES) {
    candidatePool.push(...validStoreItems[store].slice(0, 5));
  }

  let aiEntities: ExtractedProductEntity[] = [];
  if (candidatePool.length > 0) {
    try {
      aiEntities = await extractEntitiesWithAi(candidatePool.map(c => c.title), 5500);
    } catch {
      aiEntities = [];
    }
  }

  const aiEntityMap = new Map<string, ExtractedProductEntity>();
  aiEntities.forEach((ent, idx) => {
    if (candidatePool[idx]) {
      aiEntityMap.set(`${candidatePool[idx].store}_${candidatePool[idx].title}`, ent);
    }
  });

  // 3. Collect all items to cluster (relevance filtered for multi-word queries)
  const allScraped: RawScrapedItem[] = [
    ...validStoreItems[Store.AMAZON],
    ...validStoreItems[Store.FLIPKART],
    ...validStoreItems[Store.MEESHO],
    ...validStoreItems[Store.MYNTRA],
  ];

  if (allScraped.length === 0) {
    return [];
  }

  const isShortQuery = cleanQ.split(/\s+/).length <= 2;
  const queryWords = cleanQ.toLowerCase().replace(/[^a-z0-9 ]/g, '').split(/\s+/).filter(w => w.length > 1);

  const relevantItems = allScraped.filter(item => {
    if (isShortQuery || queryWords.length === 0) return true;
    const titleLower = item.title.toLowerCase();

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

  const itemsToCluster = relevantItems.length > 0 ? relevantItems : allScraped;

  // 4. Real-Time AI Entity Segregation & Clustering
  interface ProductCluster {
    brand: string;
    model: string;
    category: string;
    title: string;
    items: RawScrapedItem[];
    relevanceScore: number;
    asin?: string | null;
  }

  const productClusters: ProductCluster[] = [];

  for (const item of itemsToCluster) {
    const aiKey = `${item.store}_${item.title}`;
    const aiEnt = aiEntityMap.get(aiKey);

    const itemBrand = (aiEnt?.brand || detectBrand(item.title) || 'Generic').trim();
    const itemModel = (aiEnt?.model || extractModelTokens(item.title, itemBrand).join(' ')).trim();
    const itemCategory = (aiEnt?.category || detectCategory(item.title)).trim();
    const itemScore = calculateRelevanceScore(cleanQ, item.title, item.isSponsored);

    let matchedCluster: ProductCluster | undefined;

    for (const c of productClusters) {
      // 1. Strict Brand Isolation: Never merge distinct brands
      if (c.brand && itemBrand && c.brand.toLowerCase() !== itemBrand.toLowerCase()) {
        storeAudits[item.store as ActiveStore]?.rejections.push({
          title: item.title.slice(0, 50),
          reason: 'BRAND_CONFLICT',
          matchedAgainst: c.title.slice(0, 50),
        });
        continue;
      }

      // 2. AI Model Matching (Generic across all brands & models)
      const cNorm = c.model.toLowerCase().replace(/[^a-z0-9]/g, '');
      const iNorm = itemModel.toLowerCase().replace(/[^a-z0-9]/g, '');

      if (cNorm && iNorm) {
        if (cNorm === iNorm) {
          matchedCluster = c;
          break;
        }

        // Substring / variant model match check
        if (cNorm.includes(iNorm) || iNorm.includes(cNorm)) {
          const cNums: string[] = c.model.match(/\d+/g) || [];
          const iNums: string[] = itemModel.match(/\d+/g) || [];
          const numConflict = cNums.length > 0 && iNums.length > 0 && !cNums.some(n => iNums.includes(n));
          const hasV2C = /\bv2\b/i.test(c.title);
          const hasV2I = /\bv2\b/i.test(item.title);

          if (!numConflict && (hasV2C === hasV2I)) {
            matchedCluster = c;
            break;
          }
        }
      }

      // 3. Fallback: evaluateProductMatch
      const evalRes = evaluateProductMatch(c.title, item.title, c.brand, c.model, itemBrand, itemModel);
      if (evalRes.isMatch) {
        matchedCluster = c;
        break;
      } else if (evalRes.rejectionReason) {
        storeAudits[item.store as ActiveStore]?.rejections.push({
          title: item.title.slice(0, 50),
          reason: evalRes.rejectionReason,
          matchedAgainst: c.title.slice(0, 50),
        });
      }
    }

    if (matchedCluster) {
      if (!matchedCluster.items.some(i => i.store === item.store)) {
        matchedCluster.items.push(item);
        if (storeAudits[item.store as ActiveStore]) {
          storeAudits[item.store as ActiveStore].survivedAggregationCount++;
        }
      }
      if (!matchedCluster.asin && item.asin) {
        matchedCluster.asin = item.asin;
      }
      if (itemScore > matchedCluster.relevanceScore) {
        matchedCluster.relevanceScore = itemScore;
        matchedCluster.title = item.title;
      }
    } else {
      productClusters.push({
        brand: itemBrand,
        model: itemModel,
        category: itemCategory,
        title: item.title,
        items: [item],
        relevanceScore: itemScore,
        asin: item.asin || null,
      });
      if (storeAudits[item.store as ActiveStore]) {
        storeAudits[item.store as ActiveStore].survivedAggregationCount++;
      }
    }
  }

  // 5. Sort clusters: queried brand matches first, then multi-store clusters & relevance
  productClusters.sort((a, b) => {
    if (queryBrand) {
      const qbLower = queryBrand.toLowerCase();
      const aBrandMatch = a.brand.toLowerCase() === qbLower || a.title.toLowerCase().includes(qbLower);
      const bBrandMatch = b.brand.toLowerCase() === qbLower || b.title.toLowerCase().includes(qbLower);
      if (aBrandMatch && !bBrandMatch) return -1;
      if (!aBrandMatch && bBrandMatch) return 1;
    }
    // Prefer clusters with multiple stores
    if (a.items.length !== b.items.length) {
      return b.items.length - a.items.length;
    }
    return b.relevanceScore - a.relevanceScore;
  });

  // 6. Transform clusters into Product objects
  const products: Product[] = [];
  const selectedClusters = productClusters.slice(0, 12);

  for (let clusterIdx = 0; clusterIdx < selectedClusters.length; clusterIdx++) {
    const cluster = selectedClusters[clusterIdx];
    const primaryItem = cluster.items[0];
    const productId = cluster.asin
      ? `amazon_${cluster.asin.toLowerCase()}`
      : `prod_${cluster.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').slice(0, 60)}`;

    // Image resolution strictly from this cluster's items
    let highResImage: string | null = null;
    const amazonItem = cluster.items.find(i => i.store === Store.AMAZON && isValidImageUrl(i.imageUrl));
    if (amazonItem?.imageUrl) {
      highResImage = optimizeProductImageUrl(amazonItem.imageUrl) || amazonItem.imageUrl;
    }

    if (!highResImage) {
      const anyScrapedWithImage = cluster.items.find(i => isValidImageUrl(i.imageUrl));
      if (anyScrapedWithImage?.imageUrl) {
        highResImage = optimizeProductImageUrl(anyScrapedWithImage.imageUrl) || anyScrapedWithImage.imageUrl;
      }
    }

    if (!highResImage) {
      try {
        const clusterTitleImg = await fetchWebImageFallback(cleanTitle(cluster.title));
        if (clusterTitleImg && isValidImageUrl(clusterTitleImg)) {
          highResImage = optimizeProductImageUrl(clusterTitleImg) || clusterTitleImg;
        }
      } catch { }
    }

    const storeOffers: StoreOffer[] = [];
    const category = cluster.category || detectCategory(cluster.title);

    for (const store of ACTIVE_MARKETPLACES) {
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

      let scrapedItem = cluster.items.find(i => i.store === store);

      // 1. Secondary pool lookup: check validStoreItems[store] for best brand & model match
      if (!scrapedItem) {
        const pool = validStoreItems[store] || [];
        const clusterBrandLower = (cluster.brand || '').toLowerCase();

        let bestCandidate: RawScrapedItem | null = null;
        let bestScore = -1;

        for (const item of pool) {
          const itemBrand = (detectBrand(item.title) || '').toLowerCase();
          if (clusterBrandLower && clusterBrandLower !== 'generic') {
            if (itemBrand && itemBrand !== clusterBrandLower && !item.title.toLowerCase().includes(clusterBrandLower)) {
              continue;
            }
          }

          const specsCluster = extractProductSpecs(cluster.title);
          const specsItem = extractProductSpecs(item.title);
          if (areSpecsIncompatible(specsCluster, specsItem)) {
            continue;
          }

          const score = calculateRelevanceScore(cluster.title, item.title, item.isSponsored);
          if (score > bestScore && score >= 25) {
            bestScore = score;
            bestCandidate = item;
          }
        }

        if (bestCandidate) {
          scrapedItem = bestCandidate;
          cluster.items.push(bestCandidate);
        }
      }

      // 2. Targeted direct store resolution: for primary cluster, if still missing and category is supported
      if (!scrapedItem && clusterIdx === 0 && isSupported) {
        try {
          const targetedQuery = `${cluster.brand && cluster.brand !== 'Generic' ? cluster.brand : ''} ${cluster.model || cleanTitle(cluster.title)}`.trim();
          let targetedItems: RawScrapedItem[] = [];
          if (store === Store.AMAZON) targetedItems = await scrapeAmazon(targetedQuery);
          else if (store === Store.FLIPKART) targetedItems = await scrapeFlipkart(targetedQuery);
          else if (store === Store.MEESHO) targetedItems = await scrapeMeesho(targetedQuery);
          else if (store === Store.MYNTRA) targetedItems = await scrapeMyntra(targetedQuery);

          if (targetedItems.length > 0) {
            const topTargeted = targetedItems.find(it => {
              if (!it.price || it.price <= 10 || !it.url) return false;
              const itCat = detectCategory(it.title);
              if (category !== 'General E-Commerce' && itCat !== 'General E-Commerce' && itCat !== category) {
                return false;
              }
              const score = calculateRelevanceScore(cluster.title, it.title, it.isSponsored);
              return score >= 25;
            });
            if (topTargeted) {
              scrapedItem = topTargeted;
              cluster.items.push(topTargeted);
            }
          }
        } catch {}
      }

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
        const activeS = store as ActiveStore;
        if (storeAudits[activeS]) {
          storeAudits[activeS].serializedOffersCount++;
        }
      } else {
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

    // Detect variants (storage or color tokens)
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
    const productCategory = category;

    // Bank offers & EMI plans
    const paymentOffers = calculatePaymentOffers(lowestOfferPrice, primaryStore, productCategory);
    const emiPlans = calculateEmiPlans(lowestOfferPrice, primaryStore);

    // Bounded Review Summary: Cluster 0 gets Groq AI with 2.5s timeout race; others get deterministic summary
    const availableStores = storeOffers.filter(s => s.status === MarketplaceStatus.AVAILABLE).map((s) => s.store);
    let reviewSummary: any = null;

    if (clusterIdx === 0) {
      try {
        const groqPromise = generateProductReviewSummaryWithGroq(
          cluster.title,
          availableStores.length > 0 ? availableStores : [Store.AMAZON],
          avgRating,
          totalReviews
        );
        const timeoutPromise = new Promise<null>((resolve) => setTimeout(() => resolve(null), 2500));
        reviewSummary = await Promise.race([groqPromise, timeoutPromise]);
      } catch {
        reviewSummary = null;
      }
    }

    if (!reviewSummary) {
      reviewSummary = {
        sentimentScore: avgRating ? Math.round(avgRating * 20) : null,
        pros: [],
        cons: [],
        totalAnalyzed: totalReviews || 0,
        isAvailable: false,
        ratingDistribution: null,
        featureScores: null,
        confidenceScore: null,
        evidenceCoverage: 'AI sentiment analysis available on demand.',
        dataFreshness: Date.now(),
        analysisStatus: 'UNAVAILABLE',
        sourceMetadata: availableStores.join(', ') || 'N/A',
      };
    }

    products.push({
      id: productId,
      asin: cluster.asin || null,
      searchQuery: cleanQ,
      title: cleanTitle(cluster.title),
      description: `Live price comparison across online stores for ${cluster.title}.`,
      imageUrl: highResImage || null,
      category: productCategory,
      brand: cluster.brand,
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

  // 7. Structured diagnostic audit report
  for (const store of ACTIVE_MARKETPLACES) {
    const s = store as ActiveStore;
    if (storeAudits[s]) {
      storeAudits[s].rejectedCount = storeAudits[s].rejections.length;
    }
  }

  const auditReport = {
    reqId: reqId || 'no_req_id',
    query: cleanQ,
    detectedCategory,
    stores: storeAudits,
    totalClusters: productClusters.length,
    multiStoreClusters: productClusters.filter(c => c.items.length > 1).length,
    productsReturned: products.length,
  };

  console.log(`[Marketplace Audit] [${reqId || 'no_req_id'}] ${JSON.stringify(auditReport)}`);

  return products;
}
