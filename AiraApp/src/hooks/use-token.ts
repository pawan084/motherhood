import { useUnstableNativeVariable } from 'nativewind';

/**
 * Read one colour token from global.css as a value React Native can use.
 *
 * For the handful of props that take a colour STRING rather than a style, so a
 * className cannot reach them — `placeholderTextColor`, gradient stops,
 * `selectionColor`. Everything else should use a utility (`text-foreground`,
 * `bg-tile`) instead of this.
 *
 * NATIVE ONLY, deliberately: NativeWind types this hook as `() => undefined` on
 * web, because there the variables live in real CSS and the browser resolves
 * them itself. So this returns undefined on web and the prop falls back to the
 * platform default — for a placeholder that is an ordinary grey, which is the
 * right answer anyway.
 *
 * It returns undefined rather than a literal fallback for the same reason:
 * hardcoding a hex here would put a second copy of the palette in the codebase,
 * which is precisely what moving the colours into global.css was for.
 */
export function useToken(name: `--${string}`): string | undefined {
  const raw: unknown = useUnstableNativeVariable(name);
  if (typeof raw !== 'string') return undefined;
  const triplet = raw.trim();
  return triplet ? `hsl(${triplet})` : undefined;
}
