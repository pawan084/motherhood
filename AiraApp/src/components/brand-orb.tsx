import { View } from 'react-native';

/**
 * Aira's mark: concentric translucent rings around a solid core.
 *
 * Drawn from plain Views because React Native has no radial gradient, and three
 * stacked circles read as a soft halo without an image asset that would need
 * its own light and dark variants.
 *
 * Colours come from global.css (`--halo-soft`, `--halo`, `--brand`); only the
 * geometry is computed here, so the splash and the welcome screen scale one mark
 * rather than keeping two hand-tuned copies.
 */
export function BrandOrb({ size = 92 }: { size?: number }) {
  const ring = size * 0.67;
  const core = size * 0.28;

  return (
    <View className="items-center justify-center" style={{ width: size, height: size }}>
      <View
        className="absolute inset-0 bg-halo-soft"
        style={{ borderRadius: size / 2 }}
      />
      <View
        className="absolute bg-halo"
        style={{ width: ring, height: ring, borderRadius: ring / 2 }}
      />
      <View
        className="bg-brand"
        style={{ width: core, height: core, borderRadius: core / 2 }}
      />
    </View>
  );
}
