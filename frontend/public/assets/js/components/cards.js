/*
 * Reusable cards: product card, repairer card, stat card and the shared HTML <head>-less helpers used by listing
 * pages. Every API string is escaped; image URLs pass RL.ui.safeUrl.
 */
(function (window) {
  'use strict';

  const RL = window.RL = window.RL || {};
  const { esc, safeUrl, rupees, label, badge, initials } = RL.ui;

  function placeholderFor(category) {
    const slug = String(category || 'OTHER').toLowerCase().replace(/_/g, '-');
    return '/demo-images/placeholders/' + slug + '.svg';
  }

  function productImage(p) {
    return safeUrl(p.thumbnailUrl) || safeUrl(p.images && p.images[0] && p.images[0].url) || placeholderFor(p.category);
  }

  /** ProductSummaryResponse -> card. opts.href overrides the link (default /product.html?id=). */
  function productCard(p, opts) {
    const o = opts || {};
    const href = o.href || '/product.html?id=' + encodeURIComponent(p.id);
    const status = p.availability && p.availability !== 'ACTIVE' ? `<span class="position-absolute top-0 start-0 m-2">${badge(p.availability)}</span>` : '';
    return `<article class="card product-card position-relative">
      <div class="thumb position-relative">${status}<img src="${esc(productImage(p))}" alt="" loading="lazy" data-fallback="${esc(placeholderFor(p.category))}"></div>
      <div class="card-body d-flex flex-column gap-1">
        <div class="d-flex justify-content-between align-items-start gap-2">
          <h3 class="h6 mb-0 lh-sm"><a class="stretched-link text-decoration-none text-reset" href="${esc(href)}">${esc(p.title)}</a></h3>
        </div>
        <div class="price">${rupees(p.price)}</div>
        <div class="d-flex flex-wrap gap-1 mt-1"><span class="chip">${esc(label(p.condition))}</span><span class="chip">${esc(label(p.category))}</span></div>
        <div class="meta mt-auto pt-2"><i class="bi bi-geo-alt" aria-hidden="true"></i> ${esc(p.city || '—')}${p.sellerName ? ` · ${esc(p.sellerName)}` : ''}</div>
      </div></article>`;
  }

  function stars(avg) {
    if (avg === null || avg === undefined) return '<span class="text-muted small">No reviews yet</span>';
    return `<span class="rating"><i class="bi bi-star-fill" aria-hidden="true"></i> ${Number(avg).toFixed(1)}</span>`;
  }

  /** RepairerSummaryResponse -> card. */
  function repairerCard(r) {
    const href = '/repairer.html?id=' + encodeURIComponent(r.id);
    const cats = (r.categories || []).slice(0, 4).map((c) => `<span class="chip">${esc(label(c))}</span>`).join('');
    const rating = r.rating || {};
    return `<article class="card repairer-card position-relative"><div class="card-body d-flex flex-column gap-2">
      <div class="d-flex gap-3 align-items-start">
        <div class="repairer-initial" aria-hidden="true">${esc(initials(r.businessName))}</div>
        <div class="min-w-0 flex-grow-1">
          <h3 class="h6 mb-1 lh-sm"><a class="stretched-link text-decoration-none text-reset" href="${esc(href)}">${esc(r.businessName)}</a></h3>
          <div class="small text-muted"><i class="bi bi-geo-alt" aria-hidden="true"></i> ${esc(r.city || '—')}${r.distanceKm !== null && r.distanceKm !== undefined ? ` · ${Number(r.distanceKm).toFixed(1)} km away` : ''}</div>
        </div>
        ${badge(r.availability)}
      </div>
      <div class="d-flex flex-wrap gap-1">${cats}</div>
      <div class="d-flex justify-content-between align-items-center mt-auto pt-1 small">
        <span>${stars(rating.average)}${rating.count ? ` <span class="text-muted">(${esc(rating.count)})</span>` : ''}</span>
        <span class="fw-semibold">${esc(r.startingPriceLabel || (r.startingPrice ? 'From ' + rupees(r.startingPrice, true) : 'Price on request'))}</span>
      </div>
      ${r.verificationStatus === 'VERIFIED' ? '<div class="small text-success"><i class="bi bi-patch-check-fill" aria-hidden="true"></i> Verified by ReLoop</div>' : ''}
      ${r.demo ? '<div class="small text-muted"><i class="bi bi-info-circle" aria-hidden="true"></i> Demo listing</div>' : ''}
    </div></article>`;
  }

  /** Stat card: {label, value, icon, sub} (value is trusted text, escaped here). */
  function statCard(s) {
    return `<div class="card stat-card"><div class="stat-label">${s.icon ? `<span class="stat-icon"><i class="bi ${esc(s.icon)}" aria-hidden="true"></i></span>` : ''}${esc(s.label)}</div>
      <div class="stat-value">${esc(s.value)}</div>${s.sub ? `<div class="stat-sub">${esc(s.sub)}</div>` : ''}</div>`;
  }

  RL.cards = { productCard, repairerCard, statCard, stars, placeholderFor, productImage };
})(window);
