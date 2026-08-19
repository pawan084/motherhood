import Feather from '@expo/vector-icons/Feather';
import { cssInterop } from 'nativewind';

/**
 * Feather, taught to read a className.
 *
 * Vector icons take their colour as a `color` PROP, not a style, so a plain
 * `className="text-tile-foreground"` would be ignored and the icon would fall
 * back to black. `nativeStyleToProp` forwards the resolved text colour into
 * that prop, which keeps icon colours in global.css with everything else
 * instead of hardcoded at each call site.
 */
export const Icon = cssInterop(Feather, {
  className: {
    target: 'style',
    nativeStyleToProp: { color: 'color' },
  },
});

export type IconName = React.ComponentProps<typeof Feather>['name'];
