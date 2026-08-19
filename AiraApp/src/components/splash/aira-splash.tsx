import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

import { BrandOrb } from '@/components/brand-orb';

/**
 * The Aira launch screen: brand orb, wordmark, tagline, quiet loading dots.
 *
 * Drawn rather than shipped as an image so it inherits the type scale and both
 * colour schemes from global.css. It renders OVER the app while first paint
 * settles, then fades itself out.
 *
 * Note the shape: the animated views carry ONLY the animated style, and a plain
 * View inside them carries the className. NativeWind does not apply className to
 * `Animated.View` out of the box, so putting the background there would silently
 * render nothing.
 */
export type AiraSplashProps = {
  /** Fired once the fade-out has finished, so the parent can unmount it. */
  onFinish: () => void;
  /** How long the brand moment holds before it starts fading. */
  holdFor?: number;
  /**
   * When the manual escape hatch appears. The copy says "if loading takes too
   * long", so showing the button immediately would contradict itself — it is
   * only honest once the wait has actually become long.
   */
  escapeHatchAfter?: number;
};

const FADE_MS = 320;

export function AiraSplash({
  onFinish,
  holdFor = 1600,
  escapeHatchAfter = 4000,
}: AiraSplashProps) {
  const reduceMotion = useReducedMotion();

  const [showEscapeHatch, setShowEscapeHatch] = useState(false);
  const opacity = useSharedValue(1);

  // One flag owns the exit, so the timer and the button cannot both run it.
  const [leaving, setLeaving] = useState(false);

  useEffect(() => {
    const hold = setTimeout(() => setLeaving(true), holdFor);
    const hatch = setTimeout(() => setShowEscapeHatch(true), escapeHatchAfter);
    return () => {
      clearTimeout(hold);
      clearTimeout(hatch);
    };
  }, [holdFor, escapeHatchAfter]);

  useEffect(() => {
    if (!leaving) return;
    opacity.value = withTiming(0, { duration: FADE_MS, easing: Easing.out(Easing.quad) });
    // Unmounting is driven from JS rather than an animation callback: it keeps
    // this component free of worklet-to-JS plumbing, and a splash that fails to
    // dismiss is the worst bug this file could have.
    const done = setTimeout(onFinish, FADE_MS + 20);
    return () => clearTimeout(done);
  }, [leaving, onFinish, opacity]);

  const sheet = useAnimatedStyle(() => ({ opacity: opacity.value }));

  return (
    <Animated.View
      style={[styles.overlay, sheet]}
      accessibilityRole="progressbar"
      accessibilityLabel="Aira is starting"
      pointerEvents={leaving ? 'none' : 'auto'}>
      <View className="flex-1 items-center justify-center bg-background">
        <View className="items-center gap-3.5">
          <BrandOrb size={92} />

          <Text className="mt-3 font-rubik-medium text-4xl tracking-wide text-brand">
            Aira
          </Text>

          <Text className="text-center font-rubik text-sm leading-relaxed text-muted-foreground">
            Your private AI companion{'\n'}for motherhood
          </Text>

          <View className="mt-5 flex-row gap-2">
            {[0, 1, 2].map((i) => (
              <Dot key={i} index={i} reduceMotion={reduceMotion} />
            ))}
          </View>
        </View>

        {/* Reserved whether or not it is filled, so the layout does not jump
            when the button appears. */}
        <View className="absolute bottom-24 h-6 justify-center">
          {showEscapeHatch && !leaving && (
            <Pressable
              onPress={() => setLeaving(true)}
              accessibilityRole="button"
              hitSlop={12}
              className="active:opacity-60">
              <Text className="font-rubik-semibold text-sm text-brand">
                Continue if loading takes too long
              </Text>
            </Pressable>
          )}
        </View>
      </View>
    </Animated.View>
  );
}

/** One loading dot. The three are offset so the row reads as a slow pulse. */
function Dot({ index, reduceMotion }: { index: number; reduceMotion: boolean }) {
  const v = useSharedValue(reduceMotion ? 0.55 : 0.25);

  useEffect(() => {
    if (reduceMotion) return;
    v.value = withDelay(
      index * 180,
      withRepeat(
        withSequence(
          withTiming(0.85, { duration: 420, easing: Easing.inOut(Easing.quad) }),
          withTiming(0.25, { duration: 420, easing: Easing.inOut(Easing.quad) }),
        ),
        -1,
      ),
    );
  }, [index, reduceMotion, v]);

  const style = useAnimatedStyle(() => ({ opacity: v.value }));

  return (
    <Animated.View style={style}>
      <View className="h-2 w-2 rounded-full bg-brand" />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    // In React Native 0.85 `absoluteFill` is a plain style object, so spreading
    // it is correct. (`absoluteFillObject` no longer exists.)
    ...StyleSheet.absoluteFill,
    zIndex: 1000,
  },
});
