import { Platform, StyleSheet, Text, type TextProps } from 'react-native';

import { Fonts, Rubik, ThemeColor } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type ThemedTextProps = TextProps & {
  type?: 'default' | 'title' | 'small' | 'smallBold' | 'subtitle' | 'link' | 'linkPrimary' | 'code';
  themeColor?: ThemeColor;
};

export function ThemedText({ style, type = 'default', themeColor, ...rest }: ThemedTextProps) {
  const theme = useTheme();

  return (
    <Text
      style={[
        { color: theme[themeColor ?? 'text'] },
        type === 'default' && styles.default,
        type === 'title' && styles.title,
        type === 'small' && styles.small,
        type === 'smallBold' && styles.smallBold,
        type === 'subtitle' && styles.subtitle,
        type === 'link' && styles.link,
        type === 'linkPrimary' && styles.linkPrimary,
        type === 'code' && styles.code,
        style,
      ]}
      {...rest}
    />
  );
}

// Weight is expressed as a Rubik FAMILY, never as `fontWeight`.
//
// This is not a style preference. React Native does not synthesise weights for
// a custom font, so the previous `fontWeight: 600` on `title` would have
// rendered as plain Rubik Regular on iOS and a smeared faux-bold on Android —
// the type scale would have looked flat with no error anywhere to explain it.
// `Rubik.semibold` is a real cut of the typeface.
const styles = StyleSheet.create({
  small: {
    fontFamily: Rubik.medium,
    fontSize: 14,
    lineHeight: 20,
  },
  smallBold: {
    fontFamily: Rubik.bold,
    fontSize: 14,
    lineHeight: 20,
  },
  default: {
    fontFamily: Rubik.medium,
    fontSize: 16,
    lineHeight: 24,
  },
  title: {
    fontFamily: Rubik.semibold,
    fontSize: 48,
    lineHeight: 52,
  },
  subtitle: {
    fontFamily: Rubik.semibold,
    fontSize: 32,
    lineHeight: 44,
  },
  link: {
    fontFamily: Rubik.regular,
    lineHeight: 30,
    fontSize: 14,
  },
  linkPrimary: {
    fontFamily: Rubik.regular,
    lineHeight: 30,
    fontSize: 14,
    color: '#3c87f7',
  },
  code: {
    // The one exception: `mono` is a system family, and system families DO
    // honour fontWeight — so this keeps its original treatment.
    fontFamily: Fonts.mono,
    fontWeight: Platform.select({ android: '700' as const }) ?? ('500' as const),
    fontSize: 12,
  },
});
