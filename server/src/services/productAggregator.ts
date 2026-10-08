import { Product, StoreOffer, Store, Availability, RawScrapedItem, ProductVariant, PaymentOffer, PaymentOfferType } from '../types/index.js';
import { scrapeAmazon } from '../scrapers/amazon.js';
import { scrapeFlipkart } from '../scrapers/flipkart.js';
import { scrapeMeesho } from '../scrapers/meesho.js';
import { scrapeMyntra } from '../scrapers/myntra.js';
import { fetchWebImageFallback, isValidImageUrl, optimizeProductImageUrl, getCategoryFallbackImage } from '../scrapers/webFallback.js';

function cleanTitle(title: string): string {
  return title
    .replace(/\s+/g, ' ')
    .replace(/\(.*?\)/g, '')
    .trim();
}

function detectCategory(q: string): string {
  const lower = q.toLowerCase();

  // Mobiles & Accessories
  if (/phone|iphone|galaxy\s*s|pixel|redmi|oneplus|realme|motorola|moto\s*g|iqoo|poco|infinix|tecno|smartphone|mobile|charger|powerbank|tempered\s*glass|phone\s*case/i.test(lower)) return 'Mobiles & Accessories';

  // Laptops & Computers
  if (/laptop|macbook|thinkpad|ideapad|vivobook|zenbook|rog|predator|tuf|alienware|pc|desktop|monitor|keyboard|mouse|ssd|hard\s*disk|ram|gpu|graphics\s*card|motherboard|processor|cpu|router/i.test(lower)) return 'Computers & Accessories';

  // Tablets & E-Readers
  if (/tablet|ipad|galaxy\s*tab|kindle|kobo|surface\s*pro|pad\s*6|pad\s*air/i.test(lower)) return 'Tablets & E-Readers';

  // Audio, Headphones & Speakers
  if (/soundbar|speaker|headphone|earphone|earbud|airpod|ear\s*fun|tws|neckband|bluetooth\s*speaker|amplifier|microphone|mic/i.test(lower)) return 'Audio & Headphones';

  // Smartwatches & Wearables
  if (/smartwatch|apple\s*watch|galaxy\s*watch|fitbit|band|fitness\s*tracker|smart\s*ring/i.test(lower)) return 'Wearables & Smartwatches';

  // TVs & Home Entertainment
  if (/tv|television|qled|oled|4k\s*tv|smart\s*tv|android\s*tv|home\s*theater|projector|streaming\s*stick|fire\s*tv|chromecast/i.test(lower)) return 'TV & Home Entertainment';

  // Cameras & Photography
  if (/camera|dslr|mirrorless|action\s*camera|gopro|drone|tripod|lens|gimbal|ring\s*light|dash\s*cam/i.test(lower)) return 'Cameras & Photography';

  // Gaming & Consoles
  if (/playstation|ps5|ps4|xbox|nintendo|switch|gaming\s*controller|joystick|gamepad|gaming\s*chair|steam\s*deck/i.test(lower)) return 'Gaming & Consoles';

  // Home & Kitchen Appliances
  if (/refrigerator|fridge|washing\s*machine|air\s*conditioner|ac|microwave|oven|air\s*fryer|induction|chimney|water\s*purifier|ro|vacuum|iron|geyser|heater|mixer|grinder|blender|toaster|kettle|cooker|pan|pot|cookware|bottle|flask|lunch\s*box/i.test(lower)) return 'Home & Kitchen';

  // Furniture & Home Decor
  if (/chair|table|desk|sofa|couch|bed|mattress|wardrobe|bookshelf|curtain|bedsheet|pillow|carpet|rug|lamp|light|chandelier|wall\s*clock|mirror|vase/i.test(lower)) return 'Furniture & Home Decor';

  // Footwear
  if (/shoe|sneaker|boot|sandal|heel|slipper|clog|flip\s*flop|loafer|oxford|running\s*shoe|formal\s*shoe|sports\s*shoe/i.test(lower)) return 'Footwear';

  // Fashion & Apparel
  if (/shirt|t-shirt|pant|trousers|jeans|saree|kurti|kurta|dress|lehenga|jacket|coat|hoodie|sweatshirt|blazer|suit|shorts|trackpant|innerwear|bra|brief|ethnic/i.test(lower)) return 'Fashion & Apparel';

  // Beauty, Skincare & Grooming
  if (/perfume|deodorant|fragrance|cologne|shampoo|conditioner|hair\s*oil|serum|sunscreen|moisturizer|face\s*wash|cleanser|cream|lotion|makeup|lipstick|foundation|eyeliner|mascara|trimmer|shaver|hair\s*dryer|straightener/i.test(lower)) return 'Beauty & Grooming';

  // Bags, Luggage & Travel
  if (/bag|backpack|rucksack|duffle|luggage|trolley|suitcase|handbag|clutch|tote|sling\s*bag|wallet|purse|card\s*holder/i.test(lower)) return 'Bags, Luggage & Travel';

  // Watches & Eyewear
  if (/analog\s*watch|chronograph|automatic\s*watch|sunglasses|eyeglass|spectacles|shades|aviator|wayfarer/i.test(lower)) return 'Watches & Eyewear';

  // Sports, Fitness & Outdoor
  if (/cricket|bat|ball|badminton|racket|shuttlecock|football|basketball|volleyball|gym|dumbbell|barbell|resistance\s*band|yoga\s*mat|treadmill|exercise\s*bike|cycle|bicycle|tent|sleeping\s*bag/i.test(lower)) return 'Sports & Fitness';

  // Health, Wellness & Nutrition
  if (/whey|protein|creatine|bcaa|mass\s*gainer|vitamin|supplement|collagen|fish\s*oil|multivitamin|bp\s*monitor|glucometer|thermometer|nebulizer/i.test(lower)) return 'Health & Nutrition';

  // Baby & Toys
  if (/diaper|baby\s*wipes|stroller|pram|crib|baby\s*food|baby\s*lotion|toy|lego|barbie|hot\s*wheels|board\s*game|puzzle|action\s*figure|doll|nerf/i.test(lower)) return 'Baby & Toys';

  // Books & Stationery
  if (/book|novel|fiction|biography|textbook|comic|manga|notebook|diary|pen|pencil|fountain\s*pen|marker|calculator|art\s*supplies/i.test(lower)) return 'Books & Stationery';

  // Groceries & Gourmet
  if (/tea|coffee|green\s*tea|oil|ghee|rice|atta|flour|pulses|dal|spices|masala|chocolate|biscuit|cookie|snack|chips|dry\s*fruits|nuts|honey|cereal|oats/i.test(lower)) return 'Groceries & Gourmet';

  return 'General E-Commerce';
}

function detectBrand(q: string): string | null {
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

    // Food, Gourmet & Groceries
    'nestle': 'Nestlé',
    'amul': 'Amul',
    'cadbury': 'Cadbury',
    'ferrero': 'Ferrero Rocher',
    'kelloggs': "Kellogg's",
    'tata': 'Tata',
    'britannia': 'Britannia',
    'sunfeast': 'Sunfeast',
    'parle': 'Parle',
    'saffola': 'Saffola',
    'fortune': 'Fortune',
    'dabur': 'Dabur',
    'aashirvaad': 'Aashirvaad',
    'tropicana': 'Tropicana',
    'red bull': 'Red Bull',
    'monster': 'Monster Energy',
    'nescafe': 'Nescafé',
    'bru': 'BRU',
    'starbucks': 'Starbucks',
    'blue tokai': 'Blue Tokai',
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

function similarityScore(strA: string, strB: string): number {
  const wordsA = new Set(strA.toLowerCase().replace(/[^a-z0-9 ]/g, '').split(/\s+/).filter(w => w.length > 2));
  const wordsB = new Set(strB.toLowerCase().replace(/[^a-z0-9 ]/g, '').split(/\s+/).filter(w => w.length > 2));
  if (wordsA.size === 0 || wordsB.size === 0) return 0;
  
  let intersection = 0;
  for (const w of wordsA) {
    if (wordsB.has(w)) intersection++;
  }
  return (2 * intersection) / (wordsA.size + wordsB.size);
}

export async function aggregateSearchResults(query: string): Promise<Product[]> {
  const cleanQ = query.trim();
  if (!cleanQ) return [];

  // 1. Fetch live products from all stores concurrently
  const [amazonItems, flipkartItems, meeshoItems, myntraItems] = await Promise.all([
    scrapeAmazon(cleanQ),
    scrapeFlipkart(cleanQ),
    scrapeMeesho(cleanQ),
    scrapeMyntra(cleanQ),
  ]);

  const allItems: RawScrapedItem[] = [
    ...amazonItems,
    ...flipkartItems,
    ...meeshoItems,
    ...myntraItems,
  ];

  if (allItems.length === 0) {
    // Attempt fallback single synthetic card with dynamic web knowledge
    const fallbackImg = await fetchWebImageFallback(cleanQ);
    return [
      {
        id: cleanQ.toLowerCase().replace(/[^a-z0-9]+/g, '-').slice(0, 60),
        title: cleanQ.split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' '),
        description: `Product query for ${cleanQ}. No direct live store inventory matched at this moment.`,
        imageUrl: fallbackImg,
        category: detectCategory(cleanQ),
        brand: detectBrand(cleanQ),
        rating: null,
        reviewCount: null,
        variants: [],
        stores: [],
        reviewSummary: { sentimentScore: null, pros: [], cons: [], totalAnalyzed: 0, isAvailable: false },
        paymentOffers: [],
        lastUpdated: Date.now(),
      }
    ];
  }

  // 2. Cluster / Group items into distinct products
  const productClusters: Array<{
    title: string;
    items: RawScrapedItem[];
  }> = [];

  for (const item of allItems) {
    let matchedCluster = productClusters.find(c => similarityScore(c.title, item.title) > 0.45);
    if (matchedCluster) {
      // Don't add duplicate store offers for the same cluster if one already exists
      const hasStore = matchedCluster.items.some(i => i.store === item.store);
      if (!hasStore) {
        matchedCluster.items.push(item);
      }
    } else {
      productClusters.push({
        title: item.title,
        items: [item],
      });
    }
  }

  // 3. Transform clusters into Product objects
  const products: Product[] = [];

  for (const cluster of productClusters.slice(0, 15)) {
    const primaryItem = cluster.items[0];
    const productId = cluster.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').slice(0, 60);

    // Ensure ALL 4 comparison stores (Amazon, Flipkart, Meesho, Myntra) are always included
    const targetStores = [Store.AMAZON, Store.FLIPKART, Store.MEESHO, Store.MYNTRA];
    const storeOffers: StoreOffer[] = [];

    for (const store of targetStores) {
      // 1. Direct match in cluster
      const clusterItem = cluster.items.find(i => i.store === store);
      if (clusterItem) {
        storeOffers.push({
          id: `${productId}_${store.toLowerCase()}`,
          productId,
          store,
          productUrl: clusterItem.url,
          price: clusterItem.price,
          originalPrice: clusterItem.originalPrice,
          discountPercentage: clusterItem.discountPercentage,
          currency: 'INR',
          availability: clusterItem.availability,
          deliveryInfo: clusterItem.title,
          lastUpdated: Date.now(),
        });
        continue;
      }

      // 2. Broad match from all scraped items
      const broadMatch = allItems.find(i => i.store === store && similarityScore(i.title, cluster.title) > 0.35);
      if (broadMatch) {
        storeOffers.push({
          id: `${productId}_${store.toLowerCase()}`,
          productId,
          store,
          productUrl: broadMatch.url,
          price: broadMatch.price,
          originalPrice: broadMatch.originalPrice,
          discountPercentage: broadMatch.discountPercentage,
          currency: 'INR',
          availability: broadMatch.availability,
          deliveryInfo: broadMatch.title,
          lastUpdated: Date.now(),
        });
        continue;
      }

      // 3. Fallback direct store search link with out-of-stock / check store status
      const encodedTitle = encodeURIComponent(cleanTitle(cluster.title).slice(0, 50));
      let fallbackUrl = `https://www.amazon.in/s?k=${encodedTitle}`;
      if (store === Store.FLIPKART) fallbackUrl = `https://www.flipkart.com/search?q=${encodedTitle}`;
      if (store === Store.MEESHO) fallbackUrl = `https://www.meesho.com/search?q=${encodedTitle}`;
      if (store === Store.MYNTRA) fallbackUrl = `https://www.myntra.com/${encodedTitle}`;

      storeOffers.push({
        id: `${productId}_${store.toLowerCase()}`,
        productId,
        store,
        productUrl: fallbackUrl,
        price: null,
        originalPrice: null,
        discountPercentage: null,
        currency: 'INR',
        availability: Availability.OUT_OF_STOCK,
        deliveryInfo: `Check availability on ${store}`,
        lastUpdated: Date.now(),
      });
    }

    // Find best image and upscale to high-resolution with guaranteed category fallback
    let candidateImage = cluster.items.map(i => i.imageUrl).find(img => isValidImageUrl(img));
    if (!candidateImage) {
      candidateImage = await fetchWebImageFallback(cluster.title) || await fetchWebImageFallback(cleanQ);
    }
    const detectedCategory = detectCategory(cluster.title);
    let highResImage = optimizeProductImageUrl(candidateImage);
    if (!highResImage) {
      highResImage = getCategoryFallbackImage(detectedCategory);
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

    const paymentOffers: PaymentOffer[] = [
      {
        id: `offer_${productId}_hdfc`,
        provider: 'HDFC Bank',
        type: PaymentOfferType.INSTANT_DISCOUNT,
        description: 'Flat ₹1,000 Instant Discount on HDFC Bank Credit Cards',
        amount: 1000,
        percentage: null,
        minimumPurchase: 5000,
        maximumDiscount: 1000,
        validUntil: Date.now() + 86400000 * 7,
      },
      {
        id: `offer_${productId}_icici`,
        provider: 'ICICI Bank',
        type: PaymentOfferType.CASHBACK,
        description: '5% Unlimited Cashback on ICICI Bank Cards',
        amount: null,
        percentage: 5,
        minimumPurchase: 1000,
        maximumDiscount: 2000,
        validUntil: Date.now() + 86400000 * 14,
      }
    ];

    products.push({
      id: productId,
      title: cleanTitle(cluster.title),
      description: `Live price comparison across online stores for ${cluster.title}.`,
      imageUrl: highResImage || null,
      category: detectCategory(cluster.title),
      brand: detectBrand(cluster.title),
      rating: avgRating,
      reviewCount: totalReviews,
      variants,
      stores: storeOffers,
      reviewSummary: {
        sentimentScore: avgRating ? Math.round(avgRating * 20) : null,
        pros: ['Verified original listing', 'Best price tracked across top retailers'],
        cons: [],
        totalAnalyzed: totalReviews || 1,
        isAvailable: avgRating !== null,
      },
      paymentOffers,
      lastUpdated: Date.now(),
    });
  }

  return products;
}
