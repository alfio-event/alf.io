# Alf.io website

This website uses 

- [Hugo](https://gohugo.io) for generating the html
- [Docsy](https://github.com/google/docsy) theme for Hugo, released under [Apache v2](https://github.com/google/docsy/blob/master/LICENSE).

## Getting started

**Please follow Docsy's [Getting Started](https://www.docsy.dev/docs/getting-started/) guide in order to prepare your environment.**



Since Docsy is included as a submodule, you have to make sure to clone also submodules. (don't forget to use `--recurse-submodules` or you won't pull down some of the code you need to generate a working site). 

The `hugo server` command builds and serves the site. If you just want to build the site, run `hugo` instead.

```bash
git clone --recurse-submodules --depth 1 https://github.com/alfio-event/alf.io.git
cd alf.io/website
hugo server
```

The Docsy theme is included as a Git submodule:

```bash
▶ git submodule
 a053131a4ebf6a59e4e8834a42368e248d98c01d themes/docsy (heads/master)
```

If you want to do SCSS edits and want to publish these, you need to install `PostCSS` (not needed for `hugo server`):

```bash
npm install
```

<!--### Cloning the Example from the Theme Project


```bash
git clone --recurse-submodules --depth 1 https://github.com/docsy.git
cd tech-doc-hugo-theme/exampleSite
HUGO_THEMESDIR="../.." hugo server
```


Note that the Hugo Theme Site requires the `exampleSite` to live in a subfolder of the theme itself. To avoid recursive duplication, the example site is added as a Git subtree:

```bash
git subtree add --prefix exampleSite https://github.com/google/docsy.git  master --squash
```

To pull in changes, see `pull-deps.sh` script in the theme.-->

## Running the website locally

Once you've cloned the site repo, move to the `website` folder and run:

```
hugo server
```



-------------------------------

This website is is a modified version of the great [Docsy website example](https://github.com/google/docsy-example)
