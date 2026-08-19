// Expo's web dev server hands .css files to lightningcss. Without a PostCSS
// config, Tailwind never runs first, so lightningcss parses the raw file and
// warns "Unknown at rule: @tailwind / @apply" — the directives reach it
// uncompiled, and on web the utilities never materialise.
//
// `expo export` did not show this: NativeWind's Metro transformer compiles the
// stylesheet on that path, which is why the production bundle came out with
// `font-family: Rubik_400Regular` while the dev server complained.
//
// No autoprefixer: it is not a dependency here, and lightningcss already
// handles vendor prefixing from the browserslist target.
module.exports = {
  plugins: {
    tailwindcss: {},
  },
};
