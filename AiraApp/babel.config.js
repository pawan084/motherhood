// NativeWind needs both halves: `jsxImportSource` so every JSX element accepts
// `className`, and its own preset to compile the CSS.
module.exports = function (api) {
  api.cache(true);
  return {
    presets: [
      ['babel-preset-expo', { jsxImportSource: 'nativewind' }],
      'nativewind/babel',
    ],
  };
};
