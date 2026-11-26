/* Services and prices: section editor over PUT /api/repairers/{id}. */
RL.page(async () => {
  RL.repairerProfile.mountEditor(RL.ui.$('#editor'), ['services']);
});
