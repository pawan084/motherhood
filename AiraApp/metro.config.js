const { getDefaultConfig } = require('expo/metro-config');
const { withNativeWind } = require('nativewind/metro');

const config = getDefaultConfig(__dirname);

// `input` must point at the stylesheet that owns the @tailwind directives.
// This project keeps it in src/, not at the root.
module.exports = withNativeWind(config, { input: './src/global.css' });
