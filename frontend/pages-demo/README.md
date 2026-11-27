# GitHub Pages demo

A static, click-through demo of the ReLoop frontend for GitHub Pages:
<https://darshankalburgi19.github.io/reloop/>

GitHub Pages hosts static files only, so the Java services cannot run there. The demo uses the same in-memory mock API as `node server.js --mock`, running in the browser. It is not the real backend: data lives in your browser's `localStorage`, and payments and tracking are simulated, as everywhere else in ReLoop.

## How it works

`build.js` creates the site in a separate output folder. **It never modifies `frontend/public` or any other source file.**

1. **Copies the site.** `frontend/public` is copied, and root paths such as `/assets/...`, `/customer/...` and `/product.html` are prefixed with the base path (`/reloop`). This happens in HTML attributes and in JavaScript path strings, because a project site is served from `https://<user>.github.io/<repo>/`.
2. **Copies the demo images.** The placeholder SVGs from product-service and repair-service go to `demo-images/`.
3. **Bundles the mock.** `frontend/dev/mock-api.js` and `browser-mock.js` are combined into `assets/js/pages-demo/mock.js`, which is added to every page. It answers `/api/**` requests in the browser and saves the demo data to `localStorage` after each call, so logins, listings, orders and repairs survive page changes.

The UI shows its usual "mock backend" banner. To start over, open any page with `?reset-demo`, for example <https://darshankalburgi19.github.io/reloop/?reset-demo>.

## Deployment

The workflow [`.github/workflows/pages.yml`](../../.github/workflows/pages.yml) builds and deploys the site on every push that changes the frontend. It can also be started by hand from the Actions tab.

One-time setting: **Settings → Pages → Build and deployment → Source: GitHub Actions**. With "Deploy from a branch", GitHub Pages renders `README.md` instead of the website.

## Build locally

```bash
node frontend/pages-demo/build.js --out /tmp/reloop-site --base /reloop
```

To preview the result, serve the parent folder so the site is reachable under `/reloop/`. For example, build with `--out /tmp/pages/reloop`, then run `python -m http.server 8000 -d /tmp/pages` and open <http://localhost:8000/reloop/>. For a custom domain or a `<user>.github.io` repository, build with `--base ""`.

## Demo accounts

Use the demo logins listed in [docs/AUTH.md](../../docs/AUTH.md): a customer (Asha) and a repairer (Ravi). In the Pages demo they exist only in the browser mock.
