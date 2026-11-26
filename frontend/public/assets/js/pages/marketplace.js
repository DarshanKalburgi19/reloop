/* Marketplace (public /marketplace.html and the customer's /customer/buy.html): mounts the shared product browser. */
RL.page(() => {
  const { $ } = RL.ui;
  RL.productBrowser.mount({
    filters: $('#filters'),
    results: $('#results'),
    pager: $('#pager'),
    count: $('#result-count'),
    sortSelect: $('#sort'),
    wide: document.body.dataset.layout !== 'dashboard',
    size: 12,
  });
});
