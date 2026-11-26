# Code Canvas web deployment

The production hostname is `canvas.zhuquan.xyz`. It is served from the separate Cloudflare Pages project `code-canvas-zhuquan`. Do not point it back to the GitHub account root hostname. Its `dsh.zhuquan.xyz` domain belongs to a different project.

Assemble a static site root from the contents of `web/site/` and copy `web/app/` as the resulting site's `app/` directory. The web edition release ZIP is only the app directory, which can also be served independently. Use `wrangler pages deploy <assembled-directory> --project-name code-canvas-zhuquan --branch main` from an authorized account. Keep credentials out of the repository. Confirm the homepage, `/en/`, `/app/`, app shell and worker, and both localized media before updating the Android download and update manifests. The GitHub Pages hold workflow preserves the old account-level redirect only; production app files are not intended for that deployment.

The app is local-first: only the static shell is precached. Browser drafts use the hosting origin; never swap domains without warning users. The production custom domain remains HTTPS-only for use and installation.
