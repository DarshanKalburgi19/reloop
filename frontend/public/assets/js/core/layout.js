/*
 * ReLoop layout: navbar, dashboard sidebar (grouped), footer, role guard and page bootstrap.
 *
 * Page contract (set on <body>):
 *   data-layout="public" | "dashboard" | "auth"
 *   data-role="CUSTOMER" | "REPAIRER"       (dashboard pages: required role, enforced before the page script runs)
 *   data-nav="<sidebar item id>"            (highlights the active sidebar item / navbar link)
 * Containers: #rl-navbar, #rl-sidebar (dashboard only), #rl-footer.
 * Page scripts call RL.page(async (ctx) => { ... }) and receive {account, role}.
 */
(function (window, document) {
  'use strict';

  const RL = window.RL = window.RL || {};
  const { esc } = RL.ui;

  const HOME = { CUSTOMER: '/customer/', REPAIRER: '/repairer/' };

  const NAV = {
    CUSTOMER: [
      { heading: 'Overview', items: [{ id: 'customer-overview', label: 'Overview', icon: 'bi-grid-1x2', href: '/customer/' }] },
      {
        heading: 'Shop', items: [
          { id: 'customer-buy', label: 'Buy products', icon: 'bi-bag', href: '/customer/buy.html' },
          { id: 'customer-purchases', label: 'My purchases', icon: 'bi-receipt', href: '/customer/purchases.html' },
          { id: 'customer-tracking', label: 'Tracking', icon: 'bi-truck', href: '/customer/tracking.html' },
        ],
      },
      {
        heading: 'Sell', items: [
          { id: 'customer-sell', label: 'Sell products', icon: 'bi-plus-square', href: '/customer/sell.html' },
          { id: 'customer-listings', label: 'My listings', icon: 'bi-tags', href: '/customer/listings.html' },
          { id: 'customer-sales', label: 'My sales', icon: 'bi-graph-up-arrow', href: '/customer/sales.html' },
        ],
      },
      {
        heading: 'Repairs', items: [
          { id: 'customer-repairs', label: 'My repair requests', icon: 'bi-tools', href: '/customer/repairs.html' },
          { id: 'find-repairers', label: 'Find a repairer', icon: 'bi-geo-alt', href: '/repairers.html' },
        ],
      },
      {
        heading: 'Account', items: [
          { id: 'customer-payments', label: 'Payment history', icon: 'bi-credit-card-2-front', href: '/customer/payments.html' },
          { id: 'customer-profile', label: 'Profile', icon: 'bi-person', href: '/customer/profile.html' },
        ],
      },
    ],
    REPAIRER: [
      { heading: 'Business', items: [{ id: 'repairer-overview', label: 'Business overview', icon: 'bi-speedometer2', href: '/repairer/' }] },
      {
        heading: 'Repair jobs', items: [
          { id: 'repairer-requests', label: 'New requests', icon: 'bi-inbox', href: '/repairer/requests.html' },
          { id: 'repairer-active', label: 'Active repairs', icon: 'bi-wrench-adjustable', href: '/repairer/active.html' },
          { id: 'repairer-completed', label: 'Completed repairs', icon: 'bi-check2-square', href: '/repairer/completed.html' },
        ],
      },
      {
        heading: 'Workshop', items: [
          { id: 'repairer-services', label: 'Services', icon: 'bi-list-check', href: '/repairer/services.html' },
          { id: 'repairer-inventory', label: 'Inventory', icon: 'bi-box-seam', href: '/repairer/inventory.html' },
          { id: 'repairer-location', label: 'Location', icon: 'bi-geo-alt', href: '/repairer/location.html' },
        ],
      },
      {
        heading: 'Money', items: [
          { id: 'repairer-earnings', label: 'Earnings', icon: 'bi-cash-stack', href: '/repairer/earnings.html' },
          { id: 'repairer-payments', label: 'Payment history', icon: 'bi-credit-card-2-front', href: '/repairer/payments.html' },
        ],
      },
      { heading: 'Account', items: [{ id: 'repairer-profile', label: 'Profile', icon: 'bi-person-badge', href: '/repairer/profile.html' }] },
    ],
  };

  const PUBLIC_LINKS = [
    { id: 'marketplace', label: 'Marketplace', href: '/marketplace.html' },
    { id: 'find-repairers', label: 'Find repairers', href: '/repairers.html' },
    { id: 'fees', label: 'How fees work', href: '/#fees' },
  ];

  function currentPath() { return window.location.pathname + window.location.search; }
  function loginUrl(next) { return '/login.html?next=' + encodeURIComponent(next || currentPath()); }

  /** Only allow same-site relative redirects (prevents open redirects via ?next=). */
  function safeNext(next, fallback) {
    if (typeof next === 'string' && /^\/(?!\/)[^\s\\]*$/.test(next)) return next;
    return fallback;
  }

  function renderNavbar() {
    const host = document.getElementById('rl-navbar');
    if (!host) return;
    const body = document.body;
    const active = body.dataset.nav || '';
    const isDashboard = body.dataset.layout === 'dashboard';
    const account = RL.session.account();

    const links = PUBLIC_LINKS.map((l) => `<li class="nav-item"><a class="nav-link${l.id === active ? ' active' : ''}" href="${l.href}"${l.id === active ? ' aria-current="page"' : ''}>${esc(l.label)}</a></li>`).join('');

    let right;
    if (account) {
      const home = HOME[account.role] || '/';
      const roleLabel = account.role === 'REPAIRER' ? 'Repairer' : 'Customer';
      right = `<a class="btn btn-soft btn-sm me-2 d-none d-lg-inline-flex align-items-center gap-1" href="${home}"><i class="bi bi-grid-1x2"></i> Dashboard</a>
        <div class="dropdown">
          <button class="btn btn-link text-decoration-none d-flex align-items-center gap-2 p-1" type="button" data-bs-toggle="dropdown" aria-expanded="false" aria-label="Account menu">
            <span class="rl-avatar">${esc(RL.ui.initials(account.name))}</span>
            <span class="d-none d-md-inline text-start lh-sm"><span class="d-block fw-semibold small text-dark">${esc(account.name)}</span><span class="d-block text-muted" style="font-size:.72rem">${roleLabel}</span></span>
            <i class="bi bi-chevron-down small text-muted"></i>
          </button>
          <ul class="dropdown-menu dropdown-menu-end shadow-sm">
            <li><a class="dropdown-item" href="${home}"><i class="bi bi-grid-1x2 me-2"></i>Dashboard</a></li>
            <li><a class="dropdown-item" href="${home}profile.html"><i class="bi bi-person me-2"></i>Profile</a></li>
            <li><hr class="dropdown-divider"></li>
            <li><button class="dropdown-item text-danger" type="button" data-signout><i class="bi bi-box-arrow-right me-2"></i>Sign out</button></li>
          </ul>
        </div>`;
    } else {
      right = `<a class="btn btn-link text-decoration-none fw-semibold me-1" href="${loginUrl(body.dataset.layout === 'auth' ? '/' : currentPath())}">Sign in</a>
        <div class="dropdown">
          <button class="btn btn-primary btn-sm dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false">Join ReLoop</button>
          <ul class="dropdown-menu dropdown-menu-end shadow-sm">
            <li><a class="dropdown-item" href="/register-customer.html"><i class="bi bi-bag me-2"></i>As a customer<div class="small text-muted ms-4">Buy, sell and book repairs</div></a></li>
            <li><a class="dropdown-item" href="/register-repairer.html"><i class="bi bi-tools me-2"></i>As a repairer<div class="small text-muted ms-4">List your workshop</div></a></li>
          </ul>
        </div>`;
    }

    host.innerHTML = `<div id="rl-mock-bar"></div>
      <nav class="navbar navbar-expand-lg rl-navbar sticky-top" aria-label="Main">
        <div class="container-fluid px-3 px-lg-4">
          ${isDashboard ? `<button class="btn btn-light btn-sm d-lg-none me-2" type="button" data-bs-toggle="offcanvas" data-bs-target="#rl-offcanvas" aria-controls="rl-offcanvas" aria-label="Open dashboard menu"><i class="bi bi-list fs-5"></i></button>` : ''}
          <a class="rl-brand" href="/"><span class="rl-brand-mark"><i class="bi bi-arrow-repeat" aria-hidden="true"></i></span>ReLoop</a>
          <button class="navbar-toggler border-0" type="button" data-bs-toggle="collapse" data-bs-target="#rl-nav-collapse" aria-controls="rl-nav-collapse" aria-expanded="false" aria-label="Toggle navigation"><span class="navbar-toggler-icon"></span></button>
          <div class="collapse navbar-collapse" id="rl-nav-collapse">
            <ul class="navbar-nav mx-lg-auto my-2 my-lg-0 gap-lg-1">${links}</ul>
            <div class="d-flex align-items-center">${right}</div>
          </div>
        </div>
      </nav>`;

    const so = host.querySelector('[data-signout]');
    if (so) so.addEventListener('click', signOut);
    if (RL.api.isMock()) showMockBar();
  }

  function sidebarHtml(role, active) {
    const account = RL.session.account();
    const groups = NAV[role] || [];
    const acct = account ? `<div class="rl-sidebar-account d-flex align-items-center gap-2">
        <span class="rl-avatar">${esc(RL.ui.initials(account.name))}</span>
        <div class="min-w-0"><div class="fw-semibold small text-truncate">${esc(account.name)}</div>
        <div class="text-muted text-truncate" style="font-size:.74rem">${esc(account.email || '')}</div></div></div>` : '';
    return acct + groups.map((g) => `<div class="rl-sidebar-group"><div class="rl-sidebar-heading">${esc(g.heading)}</div>
      <ul class="nav flex-column">${g.items.map((i) => `<li class="nav-item"><a class="nav-link${i.id === active ? ' active' : ''}" href="${i.href}"${i.id === active ? ' aria-current="page"' : ''}><i class="bi ${i.icon}" aria-hidden="true"></i>${esc(i.label)}</a></li>`).join('')}</ul></div>`).join('')
      + `<div class="rl-sidebar-group"><ul class="nav flex-column"><li class="nav-item"><button type="button" class="nav-link w-100 text-start border-0 bg-transparent" data-signout><i class="bi bi-box-arrow-right" aria-hidden="true"></i>Sign out</button></li></ul></div>`;
  }

  function renderSidebar() {
    const host = document.getElementById('rl-sidebar');
    if (!host) return;
    const role = document.body.dataset.role;
    const active = document.body.dataset.nav;
    host.setAttribute('aria-label', 'Dashboard');
    host.innerHTML = sidebarHtml(role, active);
    const off = document.createElement('div');
    off.className = 'offcanvas offcanvas-start';
    off.id = 'rl-offcanvas';
    off.tabIndex = -1;
    off.setAttribute('aria-labelledby', 'rl-offcanvas-title');
    off.innerHTML = `<div class="offcanvas-header"><h2 class="offcanvas-title h6" id="rl-offcanvas-title">Dashboard menu</h2>
      <button type="button" class="btn-close" data-bs-dismiss="offcanvas" aria-label="Close"></button></div>
      <div class="offcanvas-body"><nav class="rl-sidebar">${sidebarHtml(role, active)}</nav></div>`;
    document.body.appendChild(off);
    document.querySelectorAll('[data-signout]').forEach((b) => b.addEventListener('click', signOut));
  }

  function renderFooter() {
    const host = document.getElementById('rl-footer');
    if (!host) return;
    host.innerHTML = `<footer class="rl-footer"><div class="container">
      <div class="row g-4">
        <div class="col-md-5"><a class="rl-brand mb-2" href="/"><span class="rl-brand-mark"><i class="bi bi-arrow-repeat" aria-hidden="true"></i></span>ReLoop</a>
          <p class="mb-0">Buy pre-loved, sell what you no longer need, and repair before you replace. Every fee is shown before you pay.</p></div>
        <div class="col-6 col-md-2"><div class="small-caps mb-2">Shop</div><ul class="list-plain d-grid gap-1"><li><a href="/marketplace.html">Marketplace</a></li><li><a href="/customer/sell.html">Sell an item</a></li></ul></div>
        <div class="col-6 col-md-2"><div class="small-caps mb-2">Repair</div><ul class="list-plain d-grid gap-1"><li><a href="/repairers.html">Find repairers</a></li><li><a href="/register-repairer.html">List your workshop</a></li></ul></div>
        <div class="col-md-3"><div class="small-caps mb-2">Transparency</div><ul class="list-plain d-grid gap-1"><li><a href="/#fees">How fees work</a></li><li>Payments are simulated in this build. No real money moves.</li></ul></div>
      </div>
      <div class="divider"></div>
      <div class="d-flex flex-wrap justify-content-between gap-2"><span>© ${new Date().getFullYear()} ReLoop</span><span>Delivery tracking is a simulation using demo locations in Gujarat.</span></div>
    </div></footer>`;
  }

  function showMockBar() {
    const bar = document.getElementById('rl-mock-bar');
    if (!bar || bar.childElementCount) return;
    bar.innerHTML = `<div class="rl-notice-bar mock" role="status"><i class="bi bi-cone-striped me-1"></i>
      <strong>Mock backend:</strong> this preview is answered by the frontend's in-memory dev mock, not the real ReLoop services.</div>`;
  }

  function signOut() {
    RL.session.clear();
    RL.ui.flash('You have signed out.', 'info');
    window.location.href = '/';
  }

  /** Returns true when the page may run; otherwise redirects. */
  function guard() {
    const required = document.body.dataset.role;
    if (!required) return true;
    const account = RL.session.account();
    if (!account) {
      RL.ui.flash('Please sign in to open your dashboard.', 'info');
      window.location.replace(loginUrl());
      return false;
    }
    if (account.role !== required) {
      RL.ui.flash(`That page is for ${required === 'REPAIRER' ? 'repairer' : 'customer'} accounts. You are signed in as a ${account.role === 'REPAIRER' ? 'repairer' : 'customer'}.`, 'warning');
      window.location.replace(HOME[account.role] || '/');
      return false;
    }
    return true;
  }

  function init() {
    if (!guard()) return false;
    renderNavbar();
    renderSidebar();
    renderFooter();
    RL.ui.consumeFlash();
    window.addEventListener('reloop:mock-backend', showMockBar);
    window.addEventListener('reloop:session-expired', () => {
      if (document.body.dataset.role) {
        RL.ui.flash('Your session has expired. Please sign in again.', 'warning');
        window.location.replace(loginUrl());
      } else {
        RL.ui.toast('Your session has expired. Please sign in again.', 'warning');
        renderNavbar();
      }
    });
    return true;
  }

  /** Page bootstrap: run the layout + guard, then the page's init with {account, role}. */
  RL.page = function page(fn) {
    const run = async () => {
      if (!init()) return;
      const account = RL.session.account();
      try {
        await fn({ account, role: account ? account.role : null });
      } catch (err) {
        // Last-resort handler: report the failure instead of leaving a half-rendered page.
        console.error(err); // eslint-disable-line no-console
        RL.ui.toastError(err, 'This page failed to load');
      }
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run);
    else run();
  };

  RL.layout = { HOME, NAV, loginUrl, safeNext, signOut, renderNavbar };
})(window, document);
