/* Location, hand-over methods and opening hours: section editor over PUT /api/repairers/{id}. */
RL.page(async () => {
  RL.repairerProfile.mountEditor(RL.ui.$('#editor'), ['location', 'hours']);
});
