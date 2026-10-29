# Alf.io website

This website uses

- [Hugo](https://gohugo.io) for generating the html
- [Docsy](https://www.docsy.dev) theme for Hugo, released under [Apache v2](https://github.com/docsy/docsy/blob/main/LICENSE).

## Prerequisites

- [Hugo **extended**](https://gohugo.io/installation/), version 0.160.1 or later. Netlify builds the site with the version set in [`netlify.toml`](netlify.toml).
- [Dart Sass](https://gohugo.io/functions/css/sass/#dart-sass), available on the `PATH` as `sass`.
- [Go](https://go.dev/dl/): Docsy is imported as a [Hugo module](https://gohugo.io/hugo-modules/), and Hugo needs Go to download it.
- [Node.js](https://nodejs.org/): Bootstrap, Font Awesome and PostCSS are installed with npm.

## Running the website locally

Move to the `website` folder, install the npm dependencies and start the Hugo server:

```bash
cd website
npm install
hugo server
```

If you just want to build the site, run `hugo` instead.

## Updating Docsy

Docsy is declared in [`go.mod`](go.mod). To update it, run:

```bash
hugo mod get github.com/docsy/docsy/theme@vX.Y.Z
hugo mod tidy
```

Then align the `bootstrap` and `@fortawesome/fontawesome-free` versions in [`package.json`](package.json) with the ones declared by the theme's own `package.json`, and follow the upgrade notes on the [Docsy blog](https://www.docsy.dev/blog/).

-------------------------------

This website is is a modified version of the great [Docsy website example](https://github.com/docsy/docsy-example)
