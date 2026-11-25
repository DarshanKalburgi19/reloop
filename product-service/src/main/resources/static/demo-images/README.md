# Demo images (no real image hosting yet)

Real image upload/hosting is out of scope for the marketplace phase. These files are bundled
with product-service and served from `/demo-images/**` (public, GET only).

- `placeholders/<category>.svg` - one placeholder per `ProductCategory`
  (`electronics`, `furniture`, `fashion`, `books`, `home-kitchen`, `sports-toys`, `appliances`, `other`).
  A listing created without `imageUrls` gets the placeholder for its category automatically.

Listings accept `imageUrls` that are either a `/demo-images/...` path or an `https://` URL.
