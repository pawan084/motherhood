import { cssInterop } from 'nativewind';
import { Circle, Path, Polyline } from 'react-native-svg';

/**
 * react-native-svg primitives, taught to read a className.
 *
 * SVG takes its colours as `fill` and `stroke` PROPS, so a plain
 * `className="stroke-brand"` would be dropped and the shape would come out
 * black. `nativeStyleToProp` forwards the resolved values into those props,
 * which keeps the ring and the sparkline on tokens from global.css instead of
 * hexes hardcoded at the call site — the same trick `components/icon.tsx` uses.
 */
// `target: false` means "do not apply this to a style prop, only map it onto
// the named props" — which is the whole point here, since SVG has no style
// route for fill/stroke.
const interop = {
  className: {
    target: false,
    nativeStyleToProp: { fill: true, stroke: true },
  },
} as const;

export const SCircle = cssInterop(Circle, interop);
export const SPath = cssInterop(Path, interop);
export const SPolyline = cssInterop(Polyline, interop);

export { default as Svg } from 'react-native-svg';
