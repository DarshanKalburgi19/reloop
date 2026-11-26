/*
 * DEV-ONLY in-memory mock of the ReLoop REST contracts, used by `node server.js --mock` to preview and smoke-test the
 * UI where the Java services cannot run. It is NOT the backend and has no persistence; restarting resets it.
 *
 * - Every response carries "X-ReLoop-Mock: true"; the UI shows a "Mock backend" banner when it sees it.
 * - It mirrors the request/response shapes in docs/*.md and the service DTOs closely enough to exercise every page,
 *   including failures (401/403/404/409/422, validation fieldErrors, declined payments).
 * - Fee maths matches order-service: fee = round_half_up(base × rate), ReLoop total = customer fee + commission.
 */
'use strict';

const crypto = require('crypto');

const RATE = { MARKETPLACE_PURCHASE: { c: 0.02, p: 0.02 }, REPAIR_BOOKING: { c: 0.02, p: 0.02 } };
const fee = (paise, rate) => Math.round(paise * rate);
const now = () => new Date().toISOString().slice(0, 19);
const rupees = (paise) => Math.round(paise) / 100;
const paise = (r) => Math.round(Number(r) * 100);
let seq = 1000;
const nextId = () => ++seq;

// ------------------------------------------------------------------ seed data
const accounts = [
  { id: 1, role: 'CUSTOMER', status: 'ACTIVE', name: 'Asha Patel', email: 'customer.demo@reloop.local', password: 'ReLoopDemo#Cust2026', phone: '+919800000001', createdAt: '2026-10-01T10:00:00', customer: { firstName: 'Asha', lastName: 'Patel', address: '12 Riverfront Road, Navrangpura', city: 'Ahmedabad', state: 'Gujarat', pincode: '380009' } },
  { id: 2, role: 'CUSTOMER', status: 'ACTIVE', name: 'Karan Desai', email: 'karan.demo@reloop.local', password: 'ReLoopDemo#Karan2026', phone: '+919800000003', createdAt: '2026-10-02T10:00:00', customer: { firstName: 'Karan', lastName: 'Desai', address: '21 Ghod Dod Road', city: 'Surat', state: 'Gujarat', pincode: '395007' } },
  { id: 3, role: 'CUSTOMER', status: 'ACTIVE', name: 'Nisha Trivedi', email: 'nisha.demo@reloop.local', password: 'ReLoopDemo#Nisha2026', phone: '+919800000004', createdAt: '2026-10-03T10:00:00', customer: { firstName: 'Nisha', lastName: 'Trivedi', address: '8 Race Course Circle, Alkapuri', city: 'Vadodara', state: 'Gujarat', pincode: '390007' } },
  { id: 4, role: 'REPAIRER', status: 'ACTIVE', name: 'Ravi Mehta', email: 'repairer.demo@reloop.local', password: 'ReLoopDemo#Rep2026', phone: '+919800000002', createdAt: '2026-10-04T10:00:00', repairer: { repairerProfileId: 1, verificationStatus: 'UNVERIFIED', workshop: { workshopName: 'Mehta Fix-It Workshop (Demo)', repairExpertise: ['Mobile phones', 'Laptops', 'Small appliances'], workshopAddress: '5 CG Road, Ellisbridge', city: 'Ahmedabad', pincode: '380006', latitude: 23.0225, longitude: 72.5714, serviceHours: 'Mon-Sat 10:00-19:00', repairMethods: ['IN_PERSON', 'SEND_ITEM', 'PICKUP_DROPOFF'], verificationStatus: 'UNVERIFIED' } } },
];
const tokens = new Map();

const P = (id, sellerId, title, category, price, condition, city, desc) => ({
  id, sellerId, sellerName: accounts.find((a) => a.id === sellerId).name, title, category, pricePaise: price * 100, condition, city,
  description: desc, pickupLocation: city + ' centre', availability: 'ACTIVE', passportCode: 'RLP-' + (100000 + id),
  images: ['/demo-images/placeholders/' + category.toLowerCase().replace(/_/g, '-') + '.svg'], createdAt: `2026-11-${String(10 + (id % 14)).padStart(2, '0')}T09:30:00`, updatedAt: null, soldAt: null,
});
const products = [
  P(1, 2, 'Refurbished iPhone 12, 128 GB', 'ELECTRONICS', 28999, 'VERY_GOOD', 'Surat', 'Battery health 88%. Minor scuffs on the frame, screen is flawless. Box and cable included.'),
  P(2, 3, 'Teak wood study table', 'FURNITURE', 6500, 'GOOD', 'Vadodara', 'Solid teak, 120 × 60 cm. Light scratches on the top, sturdy legs.'),
  P(3, 2, 'Hercules 21-speed cycle', 'SPORTS_TOYS', 7200, 'GOOD', 'Surat', 'Serviced last month. New brake pads.'),
  P(4, 3, 'Prestige induction cooktop', 'HOME_KITCHEN', 1800, 'LIKE_NEW', 'Rajkot', 'Used for three months. All functions work.'),
  P(5, 2, 'Levi’s denim jacket (M)', 'FASHION', 1500, 'VERY_GOOD', 'Surat', 'Worn a handful of times.'),
  P(6, 3, 'UPSC prep book set (12 books)', 'BOOKS', 2200, 'FAIR', 'Vadodara', 'Some highlighting. Complete set.'),
  P(7, 2, 'LG 190 L single-door fridge', 'APPLIANCES', 9800, 'GOOD', 'Surat', 'Cools well. Small dent on the side.'),
  P(8, 1, 'Wooden bookshelf, 5 shelves', 'FURNITURE', 3200, 'GOOD', 'Ahmedabad', 'Easy to dismantle for transport.'),
];

const HOURS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'].map((d) => ({ day: d, opens: '10:00', closes: '19:00' }));
const repairers = [
  { id: 1, ownerAccountId: 4, businessName: 'Mehta Fix-It Workshop (Demo)', description: 'Phone, laptop and small-appliance repairs on CG Road since 2012. Genuine parts where available.', categories: ['MOBILE', 'LAPTOP', 'HOME_APPLIANCE'], expertise: ['Screen replacement', 'Battery swap', 'Board-level repair'],
    services: [{ id: 11, name: 'Phone screen replacement', description: 'Display and digitiser replacement', category: 'MOBILE', priceType: 'STARTING_FROM', price: 1499, estimatedDays: 1 }, { id: 12, name: 'Laptop diagnostics', description: 'Full hardware check', category: 'LAPTOP', priceType: 'FIXED', price: 499, estimatedDays: 1 }, { id: 13, name: 'Mixer / grinder repair', description: 'Motor and switch repair', category: 'HOME_APPLIANCE', priceType: 'STARTING_FROM', price: 349, estimatedDays: 2 }],
    phone: '+919800000002', addressLine: '5 CG Road, Ellisbridge', city: 'Ahmedabad', pincode: '380006', latitude: 23.0225, longitude: 72.5714, workingHours: HOURS, repairMethods: ['IN_PERSON', 'SEND_ITEM', 'PICKUP_DROPOFF'], availability: 'AVAILABLE', availabilityNote: null, rating: { average: 4.6, count: 38 }, verificationStatus: 'UNVERIFIED', status: 'ACTIVE', demo: true },
  { id: 2, ownerAccountId: null, businessName: 'Surat Cycle Care (Demo)', description: 'Bicycle servicing, puncture repair and gear tuning for all brands.', categories: ['BICYCLE'], expertise: ['Gear tuning', 'Wheel truing'],
    services: [{ id: 21, name: 'Full cycle service', description: 'Clean, lube, adjust', category: 'BICYCLE', priceType: 'FIXED', price: 599, estimatedDays: 1 }],
    phone: '+919811111111', addressLine: 'Athwa Lines', city: 'Surat', pincode: '395001', latitude: 21.1702, longitude: 72.8311, workingHours: HOURS, repairMethods: ['IN_PERSON', 'PICKUP_DROPOFF'], availability: 'AVAILABLE', availabilityNote: null, rating: { average: 4.8, count: 21 }, verificationStatus: 'VERIFIED', status: 'ACTIVE', demo: true },
  { id: 3, ownerAccountId: null, businessName: 'Baroda TV & Electricals (Demo)', description: 'LED TV panel and power-board repair, wiring and home electricals.', categories: ['TV', 'ELECTRICAL'], expertise: ['LED backlight', 'Power supply'],
    services: [{ id: 31, name: 'LED TV backlight repair', description: 'Backlight strip replacement', category: 'TV', priceType: 'STARTING_FROM', price: 1999, estimatedDays: 3 }],
    phone: '+919822222222', addressLine: 'Alkapuri', city: 'Vadodara', pincode: '390007', latitude: 22.3072, longitude: 73.1812, workingHours: HOURS, repairMethods: ['IN_PERSON', 'SEND_ITEM'], availability: 'BUSY', availabilityNote: 'Booked until Friday', rating: { average: null, count: 0 }, verificationStatus: 'UNVERIFIED', status: 'ACTIVE', demo: true },
];

const LOCATIONS = [
  { code: 'AMD_CG_ROAD', label: 'CG Road hub', city: 'Ahmedabad', latitude: 23.0300, longitude: 72.5600 },
  { code: 'AMD_NAVRANGPURA', label: 'Navrangpura', city: 'Ahmedabad', latitude: 23.0365, longitude: 72.5611 },
  { code: 'SRT_ATHWA', label: 'Athwa Lines', city: 'Surat', latitude: 21.1800, longitude: 72.8100 },
  { code: 'BDQ_ALKAPURI', label: 'Alkapuri', city: 'Vadodara', latitude: 22.3100, longitude: 73.1700 },
  { code: 'RAJ_RACE_COURSE', label: 'Race Course', city: 'Rajkot', latitude: 22.3000, longitude: 70.8000 },
  { code: 'GNR_SECTOR_21', label: 'Sector 21', city: 'Gandhinagar', latitude: 23.2300, longitude: 72.6500 },
];
const FLOW = ['BOOKED', 'PICKED_UP', 'IN_TRANSIT', 'IN_TRANSIT', 'IN_TRANSIT', 'NEAR_DESTINATION', 'DELIVERED'];

const orders = [];
const txns = [];
const payouts = [];
const repairRequests = [];
const quotes = [];
const parts = [
  { id: 1, repairerId: 1, sku: 'SCR-IP12', partName: 'iPhone 12 display assembly', category: 'SCREEN_DISPLAY', description: 'OEM-grade OLED', quantity: 4, unitCostPaise: 520000, lowStockThreshold: 2, status: 'ACTIVE', createdAt: '2026-11-01T10:00:00' },
  { id: 2, repairerId: 1, sku: 'BAT-UNI-4000', partName: 'Li-ion battery 4000 mAh', category: 'BATTERY', description: null, quantity: 1, unitCostPaise: 85000, lowStockThreshold: 3, status: 'ACTIVE', createdAt: '2026-11-02T10:00:00' },
  { id: 3, repairerId: 1, sku: 'USB-C-PORT', partName: 'USB-C charging port', category: 'CHARGING_POWER', description: null, quantity: 0, unitCostPaise: 12000, lowStockThreshold: 5, status: 'ACTIVE', createdAt: '2026-11-03T10:00:00' },
];
const movements = [];
const deliveries = [];

// ------------------------------------------------------------------ helpers
function send(res, status, body, headers) {
  const payload = body === undefined ? '' : (typeof body === 'string' ? body : JSON.stringify(body));
  res.writeHead(status, Object.assign({ 'Content-Type': typeof body === 'string' ? 'text/html; charset=utf-8' : 'application/json', 'X-ReLoop-Mock': 'true', 'Cache-Control': 'no-store' }, headers || {}));
  res.end(payload);
}
class HttpError extends Error {
  constructor(status, message, fieldErrors) { super(message); this.status = status; this.fieldErrors = fieldErrors; }
}
const fail = (status, message, fieldErrors) => { throw new HttpError(status, message, fieldErrors); };
const ERR = { 400: 'Bad Request', 401: 'Unauthorized', 403: 'Forbidden', 404: 'Not Found', 409: 'Conflict', 422: 'Unprocessable Entity' };

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (c) => { data += c; if (data.length > 1e6) req.destroy(); });
    req.on('end', () => { try { resolve(data ? JSON.parse(data) : {}); } catch (e) { resolve(null); } });
  });
}
function auth(req, role) {
  const h = req.headers.authorization || '';
  const t = h.startsWith('Bearer ') ? h.slice(7) : null;
  const id = t && tokens.get(t);
  const acc = id && accounts.find((a) => a.id === id);
  if (!acc) fail(401, 'Authentication required');
  if (role && acc.role !== role) fail(403, 'This endpoint is for ' + role.toLowerCase() + ' accounts');
  return acc;
}
function optionalAuth(req) { try { return auth(req); } catch (e) { return null; } }
function page(list, q, defSize) {
  const size = Math.min(Number(q.get('size')) || defSize || 20, 100);
  const p = Math.max(Number(q.get('page')) || 0, 0);
  const totalPages = Math.max(1, Math.ceil(list.length / size));
  return { content: list.slice(p * size, p * size + size), page: p, size, totalElements: list.length, totalPages: list.length ? totalPages : 0, first: p === 0, last: p >= totalPages - 1 };
}
function required(body, fields) {
  const fe = {};
  fields.forEach((f) => { if (body[f] === undefined || body[f] === null || body[f] === '' || (Array.isArray(body[f]) && !body[f].length)) fe[f] = 'must not be blank'; });
  if (Object.keys(fe).length) fail(400, 'Validation failed', fe);
}
function issue(acc) {
  const token = 'mock.' + acc.id + '.' + crypto.randomBytes(12).toString('hex');
  tokens.set(token, acc.id);
  const { password, ...account } = acc;
  void password;
  return { tokenType: 'Bearer', accessToken: token, expiresAt: new Date(Date.now() + 3600e3).toISOString(), expiresInSeconds: 3600, account };
}
const pub = (acc) => { const { password, ...a } = acc; void password; return a; };

// ------------------------------------------------------------------ views
function productSummary(p) {
  return { id: p.id, sellerId: p.sellerId, sellerName: p.sellerName, title: p.title, category: p.category, price: rupees(p.pricePaise), pricePaise: p.pricePaise, currency: 'INR', condition: p.condition, city: p.city, storeName: null, pickupLocation: p.pickupLocation, thumbnailUrl: p.images[0] || null, availability: p.availability, passportCode: p.passportCode, createdAt: p.createdAt };
}
function productView(p) {
  return Object.assign(productSummary(p), { description: p.description, store: null, images: p.images.map((u, i) => ({ url: u, altText: p.title, primary: i === 0, placeholder: u.startsWith('/demo-images/') })), available: p.availability === 'ACTIVE', updatedAt: p.updatedAt, soldAt: p.soldAt });
}
function repairerSummary(r, center) {
  const cheapest = r.services.length ? Math.min(...r.services.map((s) => s.price)) : null;
  const out = { id: r.id, businessName: r.businessName, city: r.city, addressLine: r.addressLine, latitude: r.latitude, longitude: r.longitude, categories: r.categories, repairMethods: r.repairMethods, startingPrice: cheapest, startingPriceLabel: cheapest ? 'From ₹' + cheapest.toLocaleString('en-IN') : null, rating: r.rating, availability: r.availability, verificationStatus: r.verificationStatus, demo: r.demo, demoNotice: r.demo ? 'Demo listing for local development' : undefined, distanceKm: null };
  if (center) out.distanceKm = Math.round(km(center, [r.latitude, r.longitude]) * 10) / 10;
  return out;
}
function missing(r) {
  const m = [];
  if (!r.description) m.push('description');
  if (!r.categories.length) m.push('categories');
  if (!r.services.length) m.push('services');
  if (!r.workingHours.length) m.push('workingHours');
  if (!r.pincode) m.push('pincode');
  return m;
}
function repairerDetail(r, viewer) {
  return Object.assign(repairerSummary(r), {
    description: r.description, expertise: r.expertise, services: r.services.map((s) => Object.assign({}, s, { priceLabel: (s.priceType === 'STARTING_FROM' ? 'From ₹' : '₹') + s.price.toLocaleString('en-IN') })),
    phone: r.phone, pincode: r.pincode, location: { latitude: r.latitude, longitude: r.longitude }, mapUrl: `https://www.openstreetmap.org/?mlat=${r.latitude}&mlon=${r.longitude}#map=16/${r.latitude}/${r.longitude}`,
    workingHours: r.workingHours, workingHoursSummary: r.workingHours.length ? 'Mon–Sat 10:00–19:00' : null, availabilityNote: r.availabilityNote,
    recentReviews: r.rating.count ? [{ rating: 5, comment: 'Quick and honest. Fixed my phone the same day.', reviewerName: 'Meera S.', createdAt: '2026-11-12T12:00:00', demo: true }] : [],
    status: r.status, listedInSearch: missing(r).length === 0, missingForListing: missing(r), ownedByViewer: !!viewer && viewer.id === r.ownerAccountId, createdAt: '2026-10-01T10:00:00', updatedAt: null,
  });
}
function km(a, b) {
  const R = 6371; const dLat = (b[0] - a[0]) * Math.PI / 180; const dLng = (b[1] - a[1]) * Math.PI / 180;
  const x = Math.sin(dLat / 2) ** 2 + Math.cos(a[0] * Math.PI / 180) * Math.cos(b[0] * Math.PI / 180) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(x));
}
const ACTIONS = {
  REPAIRER: { REQUESTED: ['accept', 'reject'], ACCEPTED: ['inspect', 'cancel'], INSPECTED: ['quote', 'cancel'], QUOTED: ['revise-quote', 'cancel'], QUOTE_REJECTED: ['revise-quote', 'cancel'], AWAITING_PAYMENT: ['revise-quote', 'cancel'], PAID: ['progress'], IN_PROGRESS: ['progress', 'complete'] },
  CUSTOMER: { REQUESTED: ['cancel'], ACCEPTED: ['cancel'], INSPECTED: ['cancel'], QUOTED: ['approve-quote', 'reject-quote', 'cancel'], QUOTE_REJECTED: ['cancel'], AWAITING_PAYMENT: ['pay', 'cancel'] },
};
function quoteView(rr) {
  const q = quotes.filter((x) => x.repairRequestId === rr.id && x.status !== 'SUPERSEDED').pop();
  if (!q) return null;
  return { quoteId: q.id, revision: q.revision, state: q.status, repairAmount: rupees(q.base), customerFee: rupees(q.cFee), totalPayable: rupees(q.base + q.cFee), totalPayableLabel: '₹' + rupees(q.base + q.cFee).toLocaleString('en-IN'), repairerCommission: rupees(q.pFee), repairerNet: rupees(q.base - q.pFee), summary: q.summary, estimatedDays: q.estimatedDays, sentAt: q.sentAt };
}
function rrView(rr, viewer) {
  const r = repairers.find((x) => x.id === rr.repairerProfileId);
  return Object.assign({}, rr, {
    repairer: { profileId: r.id, businessName: r.businessName, phone: r.phone, addressLine: r.addressLine, city: r.city, pincode: r.pincode, location: { latitude: r.latitude, longitude: r.longitude } },
    quote: quoteView(rr), allowedActions: (ACTIONS[viewer.role][rr.status] || []).slice(),
  });
}
function rrSummary(rr) {
  const r = repairers.find((x) => x.id === rr.repairerProfileId);
  const q = quoteView(rr);
  return { id: rr.id, requestNumber: rr.requestNumber, status: rr.status, customerAccountId: rr.customerAccountId, repairerProfileId: rr.repairerProfileId, repairerBusinessName: r.businessName, category: rr.category, itemDescription: rr.itemDescription, deliveryMethod: rr.deliveryMethod, quoteTotalPayable: q ? q.totalPayable : null, createdAt: rr.createdAt, updatedAt: rr.updatedAt };
}
function move(rr, to, actor, note) {
  rr.history.push({ fromStatus: rr.status, toStatus: to, actor, note: note || null, at: now() });
  rr.status = to;
  rr.updatedAt = now();
  const stamp = { ACCEPTED: 'acceptedAt', INSPECTED: 'inspectedAt', QUOTED: 'quotedAt', AWAITING_PAYMENT: 'approvedAt', PAID: 'paidAt', IN_PROGRESS: 'inProgressAt', COMPLETED: 'completedAt' }[to];
  if (stamp && !rr[stamp]) rr[stamp] = now();
  if (['REJECTED', 'CANCELLED'].includes(to)) { rr.closedAt = now(); rr.closeReason = note || null; }
}
function partView(p) {
  const stockStatus = p.quantity === 0 ? 'OUT_OF_STOCK' : (p.quantity <= p.lowStockThreshold ? 'LOW_STOCK' : 'IN_STOCK');
  return Object.assign({}, p, { unitCost: rupees(p.unitCostPaise), unitCostDisplay: '₹' + rupees(p.unitCostPaise).toFixed(2), stockStatus, lowStock: stockStatus !== 'IN_STOCK', stockValuePaise: p.quantity * p.unitCostPaise, stockValue: rupees(p.quantity * p.unitCostPaise), updatedAt: null });
}
function addMovement(p, type, change, rrId, note) {
  const rr = rrId ? repairRequests.find((x) => x.id === rrId) : null;
  const m = { id: nextId(), repairerId: p.repairerId, sparePartId: p.id, sparePartName: p.partName, sku: p.sku, repairRequestId: rrId || null, repairRequestNumber: rr ? rr.requestNumber : null, movementType: type, quantity: Math.abs(change), quantityChange: change, stockAfter: p.quantity, unitCostPaise: p.unitCostPaise, totalCostPaise: Math.abs(change) * p.unitCostPaise, totalCost: rupees(Math.abs(change) * p.unitCostPaise), note: note || null, movementDate: now(), createdAt: now() };
  movements.push(m);
  return m;
}
function txView(t) {
  return Object.assign({}, t, { receiptUrl: '/api/transactions/' + t.transactionId + '/receipt' });
}
function orderView(o) {
  return Object.assign({}, o, { payments: txns.filter((t) => t.orderId === o.id).map((t) => ({ transactionId: t.transactionId, status: t.status, method: t.method, amountPaise: t.totalChargedPaise, failureReason: t.failureReason, attemptedAt: t.attemptedAt })) });
}
function totals(list) {
  const t = { transactionCount: list.length, grossPaise: 0, commissionPaise: 0, netPaise: 0, refundedGrossPaise: 0, currentNetPaise: 0, pendingPayoutPaise: 0, paidOutPaise: 0 };
  list.forEach((p) => { t.grossPaise += p.grossAmountPaise; t.commissionPaise += p.commissionPaise; t.netPaise += p.netAmountPaise; t.refundedGrossPaise += p.refundedGrossPaise; t.currentNetPaise += p.currentNetPaise; if (p.status === 'PENDING') t.pendingPayoutPaise += p.currentNetPaise; });
  return t;
}
function deliveryView(d) {
  const o = LOCATIONS.find((l) => l.code === d.originCode);
  const de = LOCATIONS.find((l) => l.code === d.destinationCode);
  const steps = FLOW.length - 1;
  const route = [];
  for (let i = 0; i <= steps; i++) route.push([o.latitude + (de.latitude - o.latitude) * i / steps, o.longitude + (de.longitude - o.longitude) * i / steps]);
  const pct = Math.round(d.step / steps * 100);
  const dist = Math.round(km([o.latitude, o.longitude], [de.latitude, de.longitude]) * 13) / 10;
  return {
    simulated: true, simulationNotice: 'Simulated delivery for demonstration. Positions follow a predefined route between Gujarat demo locations and move only when someone presses "Demo: advance". No GPS or courier integration.',
    trackingNumber: d.trackingNumber, deliveryType: d.deliveryType, deliveryTypeLabel: { STORE_TO_CUSTOMER: 'Seller to buyer', CUSTOMER_TO_REPAIRER: 'Customer to workshop', REPAIRER_TO_CUSTOMER: 'Workshop to customer' }[d.deliveryType],
    carrier: 'ReLoop Demo Logistics (simulated)', status: FLOW[d.step], statusLabel: null, statusFlow: ['BOOKED', 'PICKED_UP', 'IN_TRANSIT', 'NEAR_DESTINATION', 'DELIVERED'], nextStatus: d.step < steps ? FLOW[d.step + 1] : null, canAdvance: d.step < steps,
    repairRequestId: d.repairRequestId, orderId: d.orderId, referenceNumber: d.referenceNumber, customerAccountId: d.customerAccountId, partnerAccountId: d.partnerAccountId,
    origin: o, destination: de, currentPosition: { latitude: route[d.step][0], longitude: route[d.step][1] }, progressPercent: pct, routeDistanceKm: dist, remainingDistanceKm: Math.round(dist * (1 - pct / 100) * 10) / 10,
    estimatedArrivalAt: d.step < steps ? new Date(Date.now() + (steps - d.step) * 3600e3).toISOString().slice(0, 19) : null, bookedAt: d.bookedAt, pickedUpAt: d.pickedUpAt, deliveredAt: d.deliveredAt, lastUpdatedAt: d.lastUpdatedAt,
    route, history: d.history,
  };
}
function deliverySummary(d) {
  const v = deliveryView(d);
  return { simulated: true, trackingNumber: v.trackingNumber, deliveryType: v.deliveryType, status: v.status, repairRequestId: v.repairRequestId, orderId: v.orderId, referenceNumber: v.referenceNumber, originLabel: v.origin.label, originCity: v.origin.city, destinationLabel: v.destination.label, destinationCity: v.destination.city, progressPercent: v.progressPercent, estimatedArrivalAt: v.estimatedArrivalAt, lastUpdatedAt: v.lastUpdatedAt };
}
const partyOf = (d, acc) => d.customerAccountId === acc.id || d.partnerAccountId === acc.id;
const myProfile = (acc) => repairers.find((r) => r.ownerAccountId === acc.id);

// ------------------------------------------------------------------ routes
const routes = [];
const on = (method, pattern, fn) => routes.push({ method, re: new RegExp('^' + pattern.replace(/:(\w+)/g, '(?<$1>[^/]+)') + '$'), fn });

// auth
on('POST', '/api/auth/login', (c) => {
  const acc = accounts.find((a) => a.email.toLowerCase() === String(c.body.email || '').toLowerCase());
  if (!acc || acc.password !== c.body.password) fail(401, 'Invalid email or password');
  return issue(acc);
});
function register(c, role) {
  const b = c.body;
  required(b, role === 'CUSTOMER' ? ['name', 'email', 'phone', 'password', 'address', 'city', 'pincode'] : ['name', 'email', 'phone', 'password', 'workshopName', 'repairExpertise', 'workshopAddress', 'city', 'latitude', 'longitude', 'serviceHours', 'repairMethods']);
  if (!/^\+?[0-9]{10,15}$/.test(b.phone)) fail(400, 'Validation failed', { phone: 'must be 10-15 digits, optionally starting with +' });
  if (accounts.some((a) => a.email.toLowerCase() === String(b.email).toLowerCase())) fail(409, 'An account with this email already exists');
  const acc = { id: nextId(), role, status: 'ACTIVE', name: b.name, email: b.email, password: b.password, phone: b.phone, createdAt: now() };
  if (role === 'CUSTOMER') {
    const [firstName, ...rest] = b.name.split(' ');
    acc.customer = { firstName, lastName: rest.join(' '), address: b.address, city: b.city, state: b.state || null, pincode: b.pincode };
  } else {
    const prof = { id: nextId(), ownerAccountId: acc.id, businessName: b.workshopName, description: null, categories: [], expertise: b.repairExpertise, services: [], phone: b.phone, addressLine: b.workshopAddress, city: b.city, pincode: b.pincode || null, latitude: b.latitude, longitude: b.longitude, workingHours: [], repairMethods: b.repairMethods, availability: 'AVAILABLE', availabilityNote: null, rating: { average: null, count: 0 }, verificationStatus: 'UNVERIFIED', status: 'ACTIVE', demo: false };
    repairers.push(prof);
    acc.repairer = { repairerProfileId: prof.id, verificationStatus: 'UNVERIFIED', workshop: { workshopName: b.workshopName, repairExpertise: b.repairExpertise, workshopAddress: b.workshopAddress, city: b.city, pincode: b.pincode, latitude: b.latitude, longitude: b.longitude, serviceHours: b.serviceHours, repairMethods: b.repairMethods, verificationStatus: 'UNVERIFIED' } };
  }
  accounts.push(acc);
  c.status = 201;
  return issue(acc);
}
on('POST', '/api/auth/register/customer', (c) => register(c, 'CUSTOMER'));
on('POST', '/api/auth/register/repairer', (c) => register(c, 'REPAIRER'));
on('GET', '/api/auth/me', (c) => pub(auth(c.req)));

// products
on('GET', '/api/products', (c) => {
  const q = c.query;
  let list = products.filter((p) => p.availability === 'ACTIVE');
  if (q.get('q')) list = list.filter((p) => (p.title + ' ' + p.description).toLowerCase().includes(q.get('q').toLowerCase()));
  ['category', 'condition'].forEach((k) => { if (q.get(k)) list = list.filter((p) => p[k] === q.get(k)); });
  if (q.get('city')) list = list.filter((p) => p.city.toLowerCase() === q.get('city').toLowerCase());
  if (q.get('minPrice')) list = list.filter((p) => p.pricePaise >= paise(q.get('minPrice')));
  if (q.get('maxPrice')) list = list.filter((p) => p.pricePaise <= paise(q.get('maxPrice')));
  const sort = q.get('sort') || 'newest';
  list = list.slice().sort((a, b) => sort === 'price_asc' ? a.pricePaise - b.pricePaise : sort === 'price_desc' ? b.pricePaise - a.pricePaise : sort === 'oldest' ? a.createdAt.localeCompare(b.createdAt) : b.createdAt.localeCompare(a.createdAt));
  return page(list.map(productSummary), q, 20);
});
on('GET', '/api/products/mine', (c) => { const acc = auth(c.req, 'CUSTOMER'); return page(products.filter((p) => p.sellerId === acc.id && p.availability !== 'INACTIVE').map(productSummary), c.query); });
on('GET', '/api/products/:id', (c) => {
  const p = products.find((x) => x.id === Number(c.params.id));
  const viewer = optionalAuth(c.req);
  if (!p || (p.availability !== 'ACTIVE' && (!viewer || viewer.id !== p.sellerId) && p.availability !== 'SOLD')) fail(404, 'Product not found');
  return productView(p);
});
on('GET', '/api/products/:id/passport', (c) => {
  const p = products.find((x) => x.id === Number(c.params.id));
  if (!p) fail(404, 'Product not found');
  return { passportCode: p.passportCode, productId: p.id, title: p.title, category: p.category, condition: p.condition, availability: p.availability, issuedAt: p.createdAt, listingCount: 1, ownershipTransfers: p.soldAt ? 1 : 0, listingIds: [p.id], history: [{ eventType: 'LISTED', occurredAt: p.createdAt, productId: p.id, actorRole: 'SELLER', price: rupees(p.pricePaise), details: 'Listed in ' + p.city }].concat(p.soldAt ? [{ eventType: 'SOLD', occurredAt: p.soldAt, productId: p.id, actorRole: 'BUYER', price: rupees(p.pricePaise), details: 'Sold through ReLoop' }] : []) };
});
function productBody(b, create) {
  required(b, ['title', 'description', 'category', 'price', 'condition', 'city']);
  if (String(b.title).length < 3) fail(400, 'Validation failed', { title: 'size must be between 3 and 200' });
  if (!(Number(b.price) >= 1)) fail(400, 'Validation failed', { price: 'must be greater than or equal to 1.00' });
  void create;
}
on('POST', '/api/products', (c) => {
  const acc = auth(c.req, 'CUSTOMER');
  productBody(c.body, true);
  const b = c.body;
  const p = { id: nextId(), sellerId: acc.id, sellerName: acc.name, title: b.title, description: b.description, category: b.category, pricePaise: paise(b.price), condition: b.condition, city: b.city, pickupLocation: b.pickupLocation, availability: 'ACTIVE', passportCode: b.passportCode || 'RLP-' + nextId(), images: (b.imageUrls && b.imageUrls.length ? b.imageUrls : ['/demo-images/placeholders/other.svg']), createdAt: now(), updatedAt: null, soldAt: null };
  products.push(p);
  c.status = 201;
  return productView(p);
});
on('PUT', '/api/products/:id', (c) => {
  const acc = auth(c.req, 'CUSTOMER');
  const p = products.find((x) => x.id === Number(c.params.id) && x.sellerId === acc.id);
  if (!p) fail(404, 'Product not found');
  productBody(c.body, false);
  const b = c.body;
  Object.assign(p, { title: b.title, description: b.description, category: b.category, pricePaise: paise(b.price), condition: b.condition, city: b.city, pickupLocation: b.pickupLocation, images: b.imageUrls && b.imageUrls.length ? b.imageUrls : p.images, updatedAt: now() });
  if (b.status) p.availability = b.status;
  return productView(p);
});
on('DELETE', '/api/products/:id', (c) => {
  const acc = auth(c.req, 'CUSTOMER');
  const p = products.find((x) => x.id === Number(c.params.id) && x.sellerId === acc.id);
  if (!p) fail(404, 'Product not found');
  if (p.availability === 'RESERVED') fail(409, 'This product has a pending order and cannot be removed');
  p.availability = 'INACTIVE';
  c.status = 204;
  return undefined;
});

// fees
on('GET', '/api/fee-rates', () => Object.entries(RATE).map(([k, r]) => ({ transactionType: k, customerFeeRate: r.c, providerCommissionRate: r.p, source: 'DEFAULT', note: null, updatedAt: null })));

// orders
on('POST', '/api/orders', (c) => {
  const acc = auth(c.req, 'CUSTOMER');
  const p = products.find((x) => x.id === Number(c.body.productId));
  if (!p) fail(404, 'Product not found');
  if (p.sellerId === acc.id) fail(409, 'You cannot buy your own listing');
  const existing = orders.find((o) => o.productId === p.id && o.buyerAccountId === acc.id && o.status === 'PENDING_PAYMENT');
  if (existing) return orderView(existing);
  if (p.availability !== 'ACTIVE') fail(409, 'This product is no longer available');
  const r = RATE.MARKETPLACE_PURCHASE;
  const base = p.pricePaise; const cf = fee(base, r.c); const pf = fee(base, r.p);
  const o = { id: nextId(), orderNumber: 'ORD-2026-' + (seq + 1), transactionType: 'MARKETPLACE_PURCHASE', status: 'PENDING_PAYMENT', buyerAccountId: acc.id, sellerAccountId: p.sellerId, productId: p.id, productTitle: p.title, items: [{ productId: p.id, title: p.title, unitPricePaise: base, quantity: 1, lineTotalPaise: base }], subtotalPaise: base, customerFeeRate: r.c, customerFeePaise: cf, totalPaise: base + cf, providerCommissionRate: r.p, providerCommissionPaise: pf, sellerNetPaise: base - pf, platformRevenuePaise: cf + pf, refundedAmountPaise: 0, amountDuePaise: base + cf, currency: 'INR', createdAt: now(), expiresAt: new Date(Date.now() + 30 * 60e3).toISOString().slice(0, 19), paidAt: null, cancelledAt: null, cancelReason: null, lastPaymentFailure: null };
  p.availability = 'RESERVED';
  orders.push(o);
  c.status = 201;
  return orderView(o);
});
on('GET', '/api/orders', (c) => {
  const acc = auth(c.req, 'CUSTOMER');
  const seller = c.query.get('role') === 'seller';
  return orders.filter((o) => (seller ? o.sellerAccountId : o.buyerAccountId) === acc.id).slice().reverse().map(orderView);
});
on('GET', '/api/orders/:id', (c) => {
  const acc = auth(c.req, 'CUSTOMER');
  const o = orders.find((x) => x.id === Number(c.params.id) && (x.buyerAccountId === acc.id || x.sellerAccountId === acc.id));
  if (!o) fail(404, 'Order not found');
  return orderView(o);
});
on('POST', '/api/orders/:id/cancel', (c) => {
  const acc = auth(c.req, 'CUSTOMER');
  const o = orders.find((x) => x.id === Number(c.params.id) && x.buyerAccountId === acc.id);
  if (!o) fail(404, 'Order not found');
  if (o.status !== 'PENDING_PAYMENT') fail(409, 'Only an unpaid order can be cancelled');
  Object.assign(o, { status: 'CANCELLED', cancelledAt: now(), cancelReason: c.body.reason || null, amountDuePaise: 0 });
  const p = products.find((x) => x.id === o.productId);
  if (p) p.availability = 'ACTIVE';
  return orderView(o);
});

// payments
on('POST', '/api/payments/simulate', (c) => {
  const acc = auth(c.req, 'CUSTOMER');
  const b = c.body;
  required(b, ['method', 'amountPaise']);
  if (!!b.orderId === !!b.repairQuoteId) fail(400, 'Send exactly one of orderId or repairQuoteId', { orderId: 'send exactly one of orderId or repairQuoteId' });
  const key = c.req.headers['idempotency-key'];
  if (key) { const prev = txns.find((t) => t.idempotencyKey === key && t.buyerAccountId === acc.id); if (prev) { c.headers = { 'Idempotent-Replay': 'true' }; return txView(prev); } }
  let base; let r; let ctx = {};
  if (b.orderId) {
    const o = orders.find((x) => x.id === Number(b.orderId) && x.buyerAccountId === acc.id);
    if (!o) fail(404, 'Order not found');
    if (o.status !== 'PENDING_PAYMENT') fail(409, 'This order is already paid or closed');
    base = o.subtotalPaise; r = RATE.MARKETPLACE_PURCHASE; ctx = { orderId: o.id, orderNumber: o.orderNumber, productId: o.productId, providerAccountId: o.sellerAccountId, providerType: 'SELLER', o };
  } else {
    const q = quotes.find((x) => x.id === Number(b.repairQuoteId));
    const rr = q && repairRequests.find((x) => x.id === q.repairRequestId && x.customerAccountId === acc.id);
    if (!rr) fail(404, 'Quote not found');
    if (rr.status !== 'AWAITING_PAYMENT' || q.status !== 'APPROVED') fail(409, 'Only an approved quote can be paid');
    base = q.base; r = RATE.REPAIR_BOOKING; ctx = { repairQuoteId: q.id, repairRequestId: rr.id, providerAccountId: repairers.find((x) => x.id === rr.repairerProfileId).ownerAccountId, providerType: 'REPAIRER', rr };
  }
  const cf = fee(base, r.c); const pf = fee(base, r.p);
  if (Number(b.amountPaise) !== base + cf) fail(422, `amountPaise must be ${base + cf} (base ${base} + customer fee ${cf})`, { amountPaise: 'must equal the amount owed: ' + (base + cf) });
  const outcome = b.outcome || 'SUCCESS';
  const t = { transactionId: 'TXN-' + crypto.randomBytes(5).toString('hex').toUpperCase(), idempotencyKey: key || null, buyerAccountId: acc.id, transactionType: b.orderId ? 'MARKETPLACE_PURCHASE' : 'REPAIR_BOOKING', status: outcome, method: b.method, simulated: true, orderId: ctx.orderId || null, orderNumber: ctx.orderNumber || null, productId: ctx.productId || null, repairQuoteId: ctx.repairQuoteId || null, repairRequestId: ctx.repairRequestId || null, baseAmountPaise: base, customerFeeRate: r.c, customerFeePaise: cf, totalChargedPaise: base + cf, providerCommissionRate: r.p, providerCommissionPaise: pf, providerNetPaise: base - pf, platformRevenuePaise: cf + pf, refundedAmountPaise: 0, failureReason: outcome === 'FAILED' ? 'Simulated decline: the bank did not approve this payment' : outcome === 'CANCELLED' ? 'Customer cancelled the simulated payment' : null, attemptedAt: now(), completedAt: now(), payoutStatus: outcome === 'SUCCESS' ? 'PENDING' : null, refunds: [], note: 'Simulated payment. No real money moved.' };
  txns.push(t);
  if (outcome === 'SUCCESS') {
    payouts.push({ payoutReference: 'PO-' + t.transactionId.slice(4), transactionId: t.transactionId, transactionType: t.transactionType, providerType: ctx.providerType, providerId: null, providerAccountId: ctx.providerAccountId, grossAmountPaise: base, commissionPaise: pf, netAmountPaise: base - pf, refundedGrossPaise: 0, reversedCommissionPaise: 0, reversedNetPaise: 0, currentNetPaise: base - pf, status: 'PENDING', currency: 'INR', createdAt: now(), paidAt: null });
    if (ctx.o) { Object.assign(ctx.o, { status: 'CONFIRMED', paidAt: now(), amountDuePaise: 0 }); const p = products.find((x) => x.id === ctx.o.productId); if (p) { p.availability = 'SOLD'; p.soldAt = now(); } }
    if (ctx.rr) { move(ctx.rr, 'PAID', 'SYSTEM', 'Payment ' + t.transactionId + ' received (simulated)'); ctx.rr.paymentTransactionId = txns.length; }
  } else if (ctx.o) { ctx.o.lastPaymentFailure = t.failureReason; }
  c.status = 201;
  return txView(t);
});
const findTx = (c, acc) => {
  const t = txns.find((x) => x.transactionId === c.params.id);
  const po = t && payouts.find((p) => p.transactionId === t.transactionId);
  if (!t || (t.buyerAccountId !== acc.id && !(po && po.providerAccountId === acc.id))) fail(404, 'Transaction not found');
  return t;
};
on('GET', '/api/payments/:id', (c) => txView(findTx(c, auth(c.req))));
on('GET', '/api/transactions/:id', (c) => txView(findTx(c, auth(c.req))));
on('GET', '/api/transactions/:id/receipt', (c) => {
  const t = findTx(c, auth(c.req));
  const m = (p) => '₹' + (p / 100).toFixed(2);
  return `<!doctype html><html><head><meta charset="utf-8"><title>Receipt ${t.transactionId}</title><style>body{font-family:system-ui;max-width:560px;margin:2rem auto;color:#1b2420}td{padding:4px 0}td:last-child{text-align:right}</style></head><body><h1>ReLoop receipt (MOCK)</h1><p>${t.transactionId} · ${t.status} · simulated payment</p><table style="width:100%"><tr><td>Base amount</td><td>${m(t.baseAmountPaise)}</td></tr><tr><td>ReLoop customer platform fee</td><td>${m(t.customerFeePaise)}</td></tr><tr><td><b>Total charged</b></td><td><b>${m(t.totalChargedPaise)}</b></td></tr><tr><td>Provider commission</td><td>${m(t.providerCommissionPaise)}</td></tr><tr><td>Provider net</td><td>${m(t.providerNetPaise)}</td></tr><tr><td>ReLoop total commission</td><td>${m(t.platformRevenuePaise)}</td></tr></table><p>No real money was transferred.</p></body></html>`;
});
on('POST', '/api/transactions/:id/refunds', (c) => {
  const acc = auth(c.req);
  const t = findTx(c, acc);
  if (t.buyerAccountId === acc.id) fail(403, 'Only the seller or repairer can refund this payment');
  required(c.body, ['reason']);
  const left = t.baseAmountPaise - t.refundedAmountPaise;
  const base = c.body.baseAmountPaise ? Number(c.body.baseAmountPaise) : left;
  if (base <= 0 || base > left) fail(422, 'You can refund at most ' + left + ' paise of the base amount', { baseAmountPaise: 'must be at most ' + left });
  const cf = Math.round(t.customerFeePaise * base / t.baseAmountPaise); const pf = Math.round(t.providerCommissionPaise * base / t.baseAmountPaise);
  t.refunds.push({ refundReference: 'RF-' + nextId(), transactionId: t.transactionId, status: 'COMPLETED', reason: c.body.reason, note: c.body.note, refundAmountPaise: base + cf, refundedBasePaise: base, refundedCustomerFeePaise: cf, reversedProviderCommissionPaise: pf, providerNetReversedPaise: base - pf, reversedCommissionPaise: cf + pf, currency: 'INR', completedAt: now() });
  t.refundedAmountPaise += base;
  t.status = t.refundedAmountPaise >= t.baseAmountPaise ? 'REFUNDED' : 'PARTIALLY_REFUNDED';
  const po = payouts.find((p) => p.transactionId === t.transactionId);
  if (po) { po.refundedGrossPaise += base; po.reversedCommissionPaise += pf; po.reversedNetPaise += base - pf; po.currentNetPaise = po.netAmountPaise - po.reversedNetPaise; po.status = t.status === 'REFUNDED' ? 'REVERSED' : 'PARTIALLY_REVERSED'; }
  const o = t.orderId && orders.find((x) => x.id === t.orderId);
  if (o) { o.status = t.status; o.refundedAmountPaise = t.refundedAmountPaise; }
  return txView(t);
});
on('GET', '/api/customers/:id/payments', (c) => {
  const acc = auth(c.req);
  if (acc.id !== Number(c.params.id)) fail(403, 'You can only view your own payments');
  const list = txns.filter((t) => t.buyerAccountId === acc.id).slice().reverse();
  const ok = list.filter((t) => ['SUCCESS', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(t.status));
  const paid = ok.reduce((s, t) => s + t.totalChargedPaise, 0);
  const refunded = ok.reduce((s, t) => s + t.refunds.reduce((a, r) => a + r.refundAmountPaise, 0), 0);
  return { count: list.length, successfulCount: ok.length, failedCount: list.filter((t) => t.status === 'FAILED').length, cancelledCount: list.filter((t) => t.status === 'CANCELLED').length, totalPaidPaise: paid, totalFeesPaise: ok.reduce((s, t) => s + t.customerFeePaise, 0), totalRefundedPaise: refunded, netSpentPaise: paid - refunded, payments: list.map(txView) };
});
on('GET', '/api/providers/:id/earnings', (c) => {
  const acc = auth(c.req);
  if (acc.id !== Number(c.params.id)) fail(403, 'You can only view your own earnings');
  const list = payouts.filter((p) => p.providerAccountId === acc.id).slice().reverse();
  const byType = {};
  ['MARKETPLACE_PURCHASE', 'REPAIR_BOOKING'].forEach((k) => { const l = list.filter((p) => p.transactionType === k); if (l.length) byType[k] = totals(l); });
  return { providerAccountId: acc.id, currency: 'INR', totals: totals(list), byTransactionType: byType, transactions: list };
});
on('GET', '/api/providers/:id/payouts', (c) => {
  const acc = auth(c.req);
  if (acc.id !== Number(c.params.id)) fail(403, 'You can only view your own payouts');
  const list = payouts.filter((p) => p.providerAccountId === acc.id).slice().reverse();
  const t = totals(list);
  return { providerAccountId: acc.id, count: list.length, pendingPayoutPaise: t.pendingPayoutPaise, paidOutPaise: t.paidOutPaise, payouts: list };
});

// repairers
on('GET', '/api/repairers', (c) => {
  const q = c.query;
  let list = repairers.filter((r) => r.status === 'ACTIVE' && !missing(r).length);
  if (q.get('q')) list = list.filter((r) => (r.businessName + ' ' + r.description).toLowerCase().includes(q.get('q').toLowerCase()));
  if (q.get('city')) list = list.filter((r) => r.city.toLowerCase() === q.get('city').toLowerCase());
  if (q.get('category')) list = list.filter((r) => r.categories.includes(q.get('category')));
  if (q.get('method')) list = list.filter((r) => r.repairMethods.includes(q.get('method')));
  if (q.get('availableOnly') === 'true') list = list.filter((r) => r.availability === 'AVAILABLE');
  const sort = q.get('sort') || 'rating';
  const v = list.map((r) => repairerSummary(r));
  v.sort((a, b) => sort === 'name' ? a.businessName.localeCompare(b.businessName) : sort === 'price_asc' ? (a.startingPrice || 1e9) - (b.startingPrice || 1e9) : sort === 'price_desc' ? (b.startingPrice || 0) - (a.startingPrice || 0) : (b.rating.average || 0) - (a.rating.average || 0));
  return page(v, q, 12);
});
on('GET', '/api/repairers/nearby', (c) => {
  const q = c.query;
  const center = [Number(q.get('lat')), Number(q.get('lng'))];
  if (Number.isNaN(center[0]) || Number.isNaN(center[1])) fail(400, 'lat and lng are required', { lat: 'required' });
  const radius = Number(q.get('radiusKm')) || 10;
  let list = repairers.filter((r) => !missing(r).length).map((r) => repairerSummary(r, center)).filter((r) => r.distanceKm <= radius);
  if (q.get('category')) list = list.filter((r) => r.categories.includes(q.get('category')));
  list.sort((a, b) => a.distanceKm - b.distanceKm);
  return { center: { latitude: center[0], longitude: center[1] }, radiusKm: radius, bounds: null, count: list.length, results: list };
});
on('GET', '/api/repairers/me', (c) => { const acc = auth(c.req, 'REPAIRER'); const r = myProfile(acc); if (!r) fail(404, 'You have no repairer profile yet'); return repairerDetail(r, acc); });
on('GET', '/api/repairers/:id', (c) => {
  const viewer = optionalAuth(c.req);
  const r = repairers.find((x) => x.id === Number(c.params.id));
  if (!r || (missing(r).length && (!viewer || viewer.id !== r.ownerAccountId))) fail(404, 'Repairer not found');
  return repairerDetail(r, viewer);
});
on('PUT', '/api/repairers/:id', (c) => {
  const acc = auth(c.req, 'REPAIRER');
  const r = repairers.find((x) => x.id === Number(c.params.id));
  if (!r) fail(404, 'Repairer not found');
  if (r.ownerAccountId !== acc.id) fail(403, 'You can only edit your own profile');
  const b = c.body;
  required(b, ['businessName', 'description', 'categories', 'services', 'addressLine', 'city', 'pincode', 'latitude', 'longitude', 'workingHours', 'repairMethods', 'availability']);
  if (String(b.description).length < 20) fail(400, 'Validation failed', { description: 'size must be between 20 and 2000' });
  b.services.forEach((s, i) => { if (!s.name || !(Number(s.price) >= 1)) fail(400, 'Validation failed', { [`services[${i}].price`]: 'must be at least 1.00' }); });
  Object.assign(r, { businessName: b.businessName, description: b.description, categories: b.categories, expertise: b.expertise || [], phone: b.phone, addressLine: b.addressLine, city: b.city, pincode: b.pincode, latitude: b.latitude, longitude: b.longitude, workingHours: b.workingHours, repairMethods: b.repairMethods, availability: b.availability, availabilityNote: b.availabilityNote || null });
  r.services = b.services.map((s) => Object.assign({}, s, { id: s.id || nextId(), price: Number(s.price) }));
  return repairerDetail(r, acc);
});

// repair requests
on('GET', '/api/repair-requests/demo-images', () => ['phone-cracked-screen', 'laptop-no-power', 'appliance-fault', 'bicycle-damage', 'generic-item'].map((n) => '/demo-images/repairs/' + n + '.svg'));
on('POST', '/api/repair-requests', (c) => {
  const acc = auth(c.req, 'CUSTOMER');
  const b = c.body;
  required(b, ['repairerProfileId', 'category', 'itemDescription', 'problemDescription', 'deliveryMethod']);
  if (String(b.problemDescription).length < 20) fail(400, 'Validation failed', { problemDescription: 'size must be between 20 and 2000' });
  const r = repairers.find((x) => x.id === Number(b.repairerProfileId));
  if (!r) fail(404, 'Repairer not found');
  if (!r.repairMethods.includes(b.deliveryMethod)) fail(400, 'Validation failed', { deliveryMethod: 'this workshop does not offer ' + b.deliveryMethod });
  const svc = b.repairServiceId ? r.services.find((s) => s.id === Number(b.repairServiceId)) : null;
  const rr = { id: nextId(), requestNumber: 'RR-2026-' + seq, status: 'REQUESTED', customerAccountId: acc.id, repairerProfileId: r.id, repairServiceId: svc ? svc.id : null, repairServiceName: svc ? svc.name : null, category: b.category, itemDescription: b.itemDescription, problemDescription: b.problemDescription, imageUrls: b.imageUrls || [], deliveryMethod: b.deliveryMethod, deliveryInstructions: null, customerNotes: b.customerNotes || null, inspectionNotes: null, paymentTransactionId: null, closeReason: null, createdAt: now(), updatedAt: now(), history: [{ fromStatus: null, toStatus: 'REQUESTED', actor: 'CUSTOMER', note: null, at: now() }] };
  repairRequests.push(rr);
  c.status = 201;
  return rrView(rr, acc);
});
function rrList(c, filter) {
  const st = c.query.get('status');
  let list = repairRequests.filter(filter);
  if (st) { const s = st.split(','); list = list.filter((r) => s.includes(r.status)); }
  return page(list.slice().reverse().map(rrSummary), c.query);
}
on('GET', '/api/repair-requests/mine', (c) => { const acc = auth(c.req, 'CUSTOMER'); return rrList(c, (r) => r.customerAccountId === acc.id); });
on('GET', '/api/repair-requests/incoming', (c) => { const acc = auth(c.req, 'REPAIRER'); const p = myProfile(acc); return rrList(c, (r) => p && r.repairerProfileId === p.id); });
function findRR(c) {
  const acc = auth(c.req);
  const rr = repairRequests.find((x) => x.id === Number(c.params.id));
  const p = acc.role === 'REPAIRER' ? myProfile(acc) : null;
  if (!rr || !(rr.customerAccountId === acc.id || (p && rr.repairerProfileId === p.id))) fail(404, 'Repair request not found');
  return { acc, rr };
}
on('GET', '/api/repair-requests/:id', (c) => { const { acc, rr } = findRR(c); return rrView(rr, acc); });
on('GET', '/api/repair-requests/:id/history', (c) => findRR(c).rr.history);
function act(c, name, fn) {
  const { acc, rr } = findRR(c);
  if (!(ACTIONS[acc.role][rr.status] || []).includes(name)) fail(409, `You cannot ${name.replace('-', ' ')} a request that is ${rr.status}`);
  fn(acc, rr, c.body || {});
  return rrView(rr, acc);
}
const note = (b, msg) => { if (!b.note || !String(b.note).trim()) fail(400, msg, { note: msg }); return b.note; };
on('POST', '/api/repair-requests/:id/accept', (c) => act(c, 'accept', (a, rr, b) => move(rr, 'ACCEPTED', 'REPAIRER', b.note)));
on('POST', '/api/repair-requests/:id/reject', (c) => act(c, 'reject', (a, rr, b) => move(rr, 'REJECTED', 'REPAIRER', note(b, 'a reason is required when rejecting a request'))));
on('POST', '/api/repair-requests/:id/inspect', (c) => act(c, 'inspect', (a, rr, b) => { if (!b.findings || b.findings.length < 10) fail(400, 'Validation failed', { findings: 'size must be between 10 and 2000' }); rr.inspectionNotes = b.findings; move(rr, 'INSPECTED', 'REPAIRER', b.findings); }));
function sendQuote(rr, b) {
  if (!(Number(b.amount) >= 1)) fail(400, 'Validation failed', { amount: 'must be at least 1.00' });
  if (!b.summary || b.summary.length < 10) fail(400, 'Validation failed', { summary: 'size must be between 10 and 2000' });
  quotes.filter((q) => q.repairRequestId === rr.id).forEach((q) => { q.status = 'SUPERSEDED'; });
  const base = paise(b.amount); const r = RATE.REPAIR_BOOKING;
  quotes.push({ id: nextId(), repairRequestId: rr.id, revision: quotes.filter((q) => q.repairRequestId === rr.id).length + 1, status: 'SENT', base, cFee: fee(base, r.c), pFee: fee(base, r.p), summary: b.summary, estimatedDays: b.estimatedDays || null, sentAt: now() });
  move(rr, 'QUOTED', 'REPAIRER', 'Quote ₹' + b.amount);
}
on('POST', '/api/repair-requests/:id/quote', (c) => act(c, 'quote', (a, rr, b) => sendQuote(rr, b)));
on('POST', '/api/repair-requests/:id/quote/revise', (c) => act(c, 'revise-quote', (a, rr, b) => sendQuote(rr, b)));
on('POST', '/api/repair-requests/:id/progress', (c) => act(c, 'progress', (a, rr, b) => move(rr, 'IN_PROGRESS', 'REPAIRER', note(b, 'describe the progress update'))));
on('POST', '/api/repair-requests/:id/complete', (c) => act(c, 'complete', (a, rr, b) => move(rr, 'COMPLETED', 'REPAIRER', b.note)));
on('POST', '/api/repair-requests/:id/cancel', (c) => act(c, 'cancel', (a, rr, b) => move(rr, 'CANCELLED', a.role, note(b, 'a reason is required when cancelling'))));
on('POST', '/api/repair-requests/:id/quote/approve', (c) => act(c, 'approve-quote', (a, rr) => { const q = quotes.filter((x) => x.repairRequestId === rr.id && x.status === 'SENT').pop(); q.status = 'APPROVED'; rr.history.push({ fromStatus: 'QUOTED', toStatus: 'QUOTE_APPROVED', actor: 'CUSTOMER', note: null, at: now() }); rr.status = 'QUOTE_APPROVED'; move(rr, 'AWAITING_PAYMENT', 'SYSTEM', 'Waiting for payment'); }));
on('POST', '/api/repair-requests/:id/quote/reject', (c) => act(c, 'reject-quote', (a, rr, b) => { const q = quotes.filter((x) => x.repairRequestId === rr.id && x.status === 'SENT').pop(); q.status = 'REJECTED'; move(rr, 'QUOTE_REJECTED', 'CUSTOMER', b.note); }));

// inventory
const myParts = (acc) => { const p = myProfile(acc); return parts.filter((x) => p && x.repairerId === p.id); };
const findPart = (c) => { const acc = auth(c.req, 'REPAIRER'); const p = myParts(acc).find((x) => x.id === Number(c.params.id)); if (!p) fail(404, 'Part not found'); return p; };
on('GET', '/api/inventory/parts', (c) => {
  const acc = auth(c.req, 'REPAIRER');
  const q = c.query;
  let list = myParts(acc);
  if (q.get('includeDiscontinued') !== 'true') list = list.filter((p) => p.status === 'ACTIVE');
  if (q.get('category')) list = list.filter((p) => p.category === q.get('category'));
  if (q.get('q')) list = list.filter((p) => (p.partName + ' ' + (p.sku || '')).toLowerCase().includes(q.get('q').toLowerCase()));
  let v = list.map(partView);
  if (q.get('lowStockOnly') === 'true') v = v.filter((p) => p.lowStock);
  return page(v, q);
});
on('POST', '/api/inventory/parts', (c) => {
  const acc = auth(c.req, 'REPAIRER');
  required(c.body, ['partName', 'category', 'unitCost']);
  const b = c.body;
  if (b.sku && myParts(acc).some((p) => p.sku === b.sku)) fail(409, 'You already have a part with SKU ' + b.sku);
  const p = { id: nextId(), repairerId: myProfile(acc).id, sku: b.sku || null, partName: b.partName, category: b.category, description: b.description || null, quantity: Number(b.quantity) || 0, unitCostPaise: paise(b.unitCost), lowStockThreshold: b.lowStockThreshold === undefined || b.lowStockThreshold === null ? 2 : Number(b.lowStockThreshold), status: 'ACTIVE', createdAt: now() };
  parts.push(p);
  if (p.quantity) addMovement(p, 'STOCK_IN', p.quantity, null, 'Opening stock');
  c.status = 201;
  return partView(p);
});
on('GET', '/api/inventory/parts/:id', (c) => partView(findPart(c)));
on('PATCH', '/api/inventory/parts/:id', (c) => { const p = findPart(c); const b = c.body; ['partName', 'category', 'sku', 'description', 'lowStockThreshold'].forEach((k) => { if (b[k] !== undefined && b[k] !== null) p[k] = b[k]; }); if (b.unitCost !== undefined && b.unitCost !== null) p.unitCostPaise = paise(b.unitCost); return partView(p); });
on('POST', '/api/inventory/parts/:id/discontinue', (c) => { const p = findPart(c); p.status = 'DISCONTINUED'; return partView(p); });
on('POST', '/api/inventory/parts/:id/reactivate', (c) => { const p = findPart(c); p.status = 'ACTIVE'; return partView(p); });
const stockResp = (p, m) => { const v = partView(p); return { part: v, movement: m, lowStockAlert: v.lowStock, alertMessage: v.lowStock ? `${p.partName} is ${v.stockStatus === 'OUT_OF_STOCK' ? 'out of stock' : 'running low (' + p.quantity + ' left)'}` : null }; };
on('POST', '/api/inventory/parts/:id/stock-in', (c) => { const p = findPart(c); const q = Number(c.body.quantity); if (!(q >= 1)) fail(400, 'Validation failed', { quantity: 'quantity must be at least 1' }); if (c.body.unitCost) p.unitCostPaise = paise(c.body.unitCost); p.quantity += q; return stockResp(p, addMovement(p, 'STOCK_IN', q, null, c.body.note)); });
on('POST', '/api/inventory/parts/:id/use', (c) => {
  const p = findPart(c); const q = Number(c.body.quantity);
  if (!(q >= 1)) fail(400, 'Validation failed', { quantity: 'quantity must be at least 1' });
  if (q > p.quantity) fail(409, `Only ${p.quantity} unit(s) of ${p.partName} in stock`);
  const rr = repairRequests.find((x) => x.id === Number(c.body.repairRequestId));
  if (!rr) fail(404, 'Repair request not found');
  p.quantity -= q;
  return stockResp(p, addMovement(p, 'USED_IN_REPAIR', -q, rr.id, c.body.note));
});
on('POST', '/api/inventory/parts/:id/adjust', (c) => {
  const p = findPart(c); const b = c.body;
  if ((b.newQuantity === undefined || b.newQuantity === null) === (b.quantityChange === undefined || b.quantityChange === null)) fail(400, 'Validation failed', { newQuantity: 'send exactly one of newQuantity or quantityChange' });
  if (!b.reason) fail(400, 'Validation failed', { reason: 'reason is required for a stock adjustment' });
  const target = b.newQuantity !== undefined && b.newQuantity !== null ? Number(b.newQuantity) : p.quantity + Number(b.quantityChange);
  if (target < 0) fail(409, 'Stock cannot go below zero');
  const change = target - p.quantity; p.quantity = target;
  return stockResp(p, addMovement(p, 'ADJUSTMENT', change, b.repairRequestId || null, b.reason));
});
on('GET', '/api/inventory/parts/:id/movements', (c) => { const p = findPart(c); return page(movements.filter((m) => m.sparePartId === p.id).slice().reverse(), c.query); });
on('GET', '/api/inventory/movements', (c) => { const acc = auth(c.req, 'REPAIRER'); const prof = myProfile(acc); let list = movements.filter((m) => prof && m.repairerId === prof.id); if (c.query.get('type')) list = list.filter((m) => m.movementType === c.query.get('type')); return page(list.slice().reverse(), c.query); });
on('GET', '/api/inventory/summary', (c) => {
  const acc = auth(c.req, 'REPAIRER'); const all = myParts(acc); const active = all.filter((p) => p.status === 'ACTIVE').map(partView);
  const value = active.reduce((s, p) => s + p.stockValuePaise, 0);
  const cats = {}; active.forEach((p) => { cats[p.category] = cats[p.category] || { category: p.category, parts: 0, units: 0, valuePaise: 0 }; cats[p.category].parts++; cats[p.category].units += p.quantity; cats[p.category].valuePaise += p.stockValuePaise; });
  const prof = myProfile(acc); const mv = movements.filter((m) => prof && m.repairerId === prof.id);
  return { repairerId: prof ? prof.id : null, totalParts: active.length, discontinuedParts: all.length - active.length, totalUnits: active.reduce((s, p) => s + p.quantity, 0), lowStockCount: active.filter((p) => p.stockStatus === 'LOW_STOCK').length, outOfStockCount: active.filter((p) => p.stockStatus === 'OUT_OF_STOCK').length, lowStockAlert: active.some((p) => p.lowStock), inventoryValuePaise: value, inventoryValue: rupees(value), inventoryValueDisplay: '₹' + rupees(value).toLocaleString('en-IN'), stockPurchasedCostPaise: mv.filter((m) => m.movementType === 'STOCK_IN').reduce((s, m) => s + m.totalCostPaise, 0), partsUsedInRepairsCostPaise: mv.filter((m) => m.movementType === 'USED_IN_REPAIR').reduce((s, m) => s + m.totalCostPaise, 0), includedInRepairEarnings: false, valuationNote: 'Inventory is valued at your recorded unit cost. Parts costs are not deducted from repair earnings.', byCategory: Object.values(cats).map((x) => Object.assign(x, { value: rupees(x.valuePaise) })) };
});
on('GET', '/api/inventory/low-stock', (c) => { const acc = auth(c.req, 'REPAIRER'); const items = myParts(acc).filter((p) => p.status === 'ACTIVE').map(partView).filter((p) => p.lowStock); return { lowStockAlert: items.length > 0, count: items.length, outOfStockCount: items.filter((p) => p.stockStatus === 'OUT_OF_STOCK').length, alertMessage: items.length ? items.length + ' part(s) need restocking' : null, items }; });
on('GET', '/api/inventory/repair-requests/:id/parts', (c) => { const acc = auth(c.req, 'REPAIRER'); const rr = repairRequests.find((x) => x.id === Number(c.params.id)); const prof = myProfile(acc); if (!rr || !prof || rr.repairerProfileId !== prof.id) fail(404, 'Repair request not found'); const mv = movements.filter((m) => m.repairRequestId === rr.id); return { repairRequestId: rr.id, requestNumber: rr.requestNumber, netUnitsUsed: -mv.reduce((s, m) => s + m.quantityChange, 0), partsCostPaise: mv.reduce((s, m) => s - m.quantityChange * m.unitCostPaise, 0), partsCost: 0, movements: mv }; });

// tracking
on('GET', '/api/tracking/demo-locations', () => LOCATIONS);
on('POST', '/api/tracking/deliveries', (c) => {
  const acc = auth(c.req); const b = c.body;
  required(b, ['deliveryType']);
  let customerAccountId; let partnerAccountId; let ref; let origin = b.originCode; let dest = b.destinationCode;
  if (b.deliveryType === 'STORE_TO_CUSTOMER') {
    const o = orders.find((x) => x.id === Number(b.orderId) && x.sellerAccountId === acc.id);
    if (!o) fail(404, 'Order not found');
    if (o.status !== 'CONFIRMED') fail(409, 'The order must be paid before a delivery can be booked');
    if (!origin || !dest) fail(400, 'Validation failed', { originCode: 'send both originCode and destinationCode' });
    customerAccountId = o.buyerAccountId; partnerAccountId = o.sellerAccountId; ref = o.orderNumber;
  } else {
    const rr = repairRequests.find((x) => x.id === Number(b.repairRequestId));
    const prof = rr && repairers.find((r) => r.id === rr.repairerProfileId);
    if (!rr || !(rr.customerAccountId === acc.id || prof.ownerAccountId === acc.id)) fail(404, 'Repair request not found');
    if (rr.deliveryMethod === 'IN_PERSON') fail(409, 'This repair is handed over in person');
    customerAccountId = rr.customerAccountId; partnerAccountId = prof.ownerAccountId; ref = rr.requestNumber;
    const nearest = LOCATIONS.slice().sort((x, y) => km([x.latitude, x.longitude], [prof.latitude, prof.longitude]) - km([y.latitude, y.longitude], [prof.latitude, prof.longitude]))[0].code;
    if (b.deliveryType === 'CUSTOMER_TO_REPAIRER') { if (rr.status !== 'ACCEPTED') fail(409, 'A pickup can be booked once the request is accepted'); if (!origin) fail(400, 'Validation failed', { originCode: 'choose where to pick the item up' }); dest = dest || nearest; } else {
      if (acc.role !== 'REPAIRER') fail(403, 'Only the workshop books the return');
      const inbound = deliveries.find((d) => d.repairRequestId === rr.id && d.deliveryType === 'CUSTOMER_TO_REPAIRER');
      origin = origin || nearest; dest = dest || (inbound && inbound.originCode);
      if (!dest) fail(400, 'Validation failed', { destinationCode: 'choose where to return the item' });
    }
  }
  if (origin === dest) fail(400, 'Validation failed', { destinationCode: 'origin and destination must differ' });
  if (deliveries.some((d) => d.deliveryType === b.deliveryType && ((b.orderId && d.orderId === Number(b.orderId)) || (b.repairRequestId && d.repairRequestId === Number(b.repairRequestId))))) fail(409, 'A delivery of this type is already booked');
  const o = LOCATIONS.find((l) => l.code === origin);
  const d = { trackingNumber: 'RLT' + crypto.randomBytes(4).toString('hex').toUpperCase(), deliveryType: b.deliveryType, orderId: b.orderId || null, repairRequestId: b.repairRequestId || null, referenceNumber: ref, customerAccountId, partnerAccountId, originCode: origin, destinationCode: dest, step: 0, bookedAt: now(), pickedUpAt: null, deliveredAt: null, lastUpdatedAt: now(), history: [{ sequence: 1, status: 'BOOKED', latitude: o.latitude, longitude: o.longitude, progressPercent: 0, remainingKm: 0, note: 'Simulated booking created', recordedAt: now() }] };
  deliveries.push(d);
  c.status = 201;
  return deliveryView(d);
});
on('GET', '/api/tracking/deliveries/mine', (c) => { const acc = auth(c.req); return deliveries.filter((d) => partyOf(d, acc)).slice().reverse().map(deliverySummary); });
const findDel = (c) => { const acc = auth(c.req); const d = deliveries.find((x) => x.trackingNumber === c.params.tn); if (!d || !partyOf(d, acc)) fail(404, 'Delivery not found'); return d; };
on('GET', '/api/tracking/deliveries/:tn', (c) => deliveryView(findDel(c)));
on('GET', '/api/tracking/deliveries/:tn/history', (c) => findDel(c).history);
on('POST', '/api/tracking/deliveries/:tn/advance', (c) => {
  const d = findDel(c);
  if (d.step >= FLOW.length - 1) fail(409, 'This simulated delivery is already delivered');
  d.step++; d.lastUpdatedAt = now();
  if (d.step === 1) d.pickedUpAt = now();
  if (d.step === FLOW.length - 1) d.deliveredAt = now();
  const v = deliveryView(d);
  d.history.push({ sequence: d.history.length + 1, status: v.status, latitude: v.currentPosition.latitude, longitude: v.currentPosition.longitude, progressPercent: v.progressPercent, remainingKm: v.remainingDistanceKm, note: 'Simulated checkpoint', recordedAt: now() });
  return deliveryView(d);
});
on('GET', '/api/tracking/repair-requests/:id/deliveries', (c) => { const acc = auth(c.req); return deliveries.filter((d) => d.repairRequestId === Number(c.params.id) && partyOf(d, acc)).map(deliverySummary); });
on('GET', '/api/tracking/orders/:id/deliveries', (c) => { const acc = auth(c.req); return deliveries.filter((d) => d.orderId === Number(c.params.id) && partyOf(d, acc)).map(deliverySummary); });

// placeholder images (served by the real services from their static folders)
const fs = require('fs');
const path = require('path');
function staticImage(res, dir, name) {
  const base = path.join(__dirname, '..', '..', dir);
  const file = path.normalize(path.join(base, name));
  if (!file.startsWith(base) || !/^[\w.-]+\.svg$/.test(name)) { send(res, 404, { status: 404, message: 'Not found' }); return; }
  fs.readFile(file, (err, data) => {
    if (err) { send(res, 404, { status: 404, message: 'Not found' }); return; }
    res.writeHead(200, { 'Content-Type': 'image/svg+xml', 'X-ReLoop-Mock': 'true' });
    res.end(data);
  });
}

async function handle(req, res, url) {
  let m;
  if ((m = url.pathname.match(/^\/demo-images\/placeholders\/([^/]+)$/))) { staticImage(res, 'product-service/src/main/resources/static/demo-images/placeholders', m[1]); return; }
  if ((m = url.pathname.match(/^\/demo-images\/repairs\/([^/]+)$/))) { staticImage(res, 'repair-service/src/main/resources/static/demo-images/repairs', m[1]); return; }
  for (const r of routes) {
    if (r.method !== req.method) continue;
    const match = url.pathname.match(r.re);
    if (!match) continue;
    const body = ['POST', 'PUT', 'PATCH'].includes(req.method) ? await readBody(req) : {};
    const c = { req, query: url.searchParams, params: match.groups || {}, body: body || {}, status: 200, headers: null };
    try {
      if (body === null) fail(400, 'Malformed JSON request body');
      const out = r.fn(c);
      send(res, c.status, out, c.headers);
    } catch (e) {
      if (!(e instanceof HttpError)) { console.error(e); e = new HttpError(500, 'Mock error: ' + e.message); } // eslint-disable-line no-console, no-ex-assign
      send(res, e.status, { timestamp: new Date().toISOString(), status: e.status, error: ERR[e.status] || 'Error', message: e.message, path: url.pathname, fieldErrors: e.fieldErrors });
    }
    return;
  }
  send(res, 404, { timestamp: new Date().toISOString(), status: 404, error: 'Not Found', message: 'Mock has no route for ' + req.method + ' ' + url.pathname, path: url.pathname });
}

module.exports = { handle };
