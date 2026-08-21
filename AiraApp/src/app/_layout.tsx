import {
  Rubik_300Light,
  Rubik_400Regular,
  Rubik_500Medium,
  Rubik_600SemiBold,
  Rubik_700Bold,
  Rubik_800ExtraBold,
  Rubik_900Black,
  useFonts,
} from '@expo-google-fonts/rubik';
import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import * as ExpoSplashScreen from 'expo-splash-screen';
import { useCallback, useEffect, useState } from 'react';
import { useColorScheme } from 'react-native';
import { Provider } from 'react-redux';

import '@/global.css';

import { QuickLogPopup, useMoodPrompt } from '@/components/quick-log';
import { AiraSplash } from '@/components/splash';
import { store } from '@/store';

ExpoSplashScreen.preventAutoHideAsync();

export default function TabLayout() {
  const colorScheme = useColorScheme();

  // Every weight is registered under its own family name, because React Native
  // will not synthesise weights for a custom font — see tailwind.config.js.
  const [fontsLoaded, fontError] = useFonts({
    Rubik_300Light,
    Rubik_400Regular,
    Rubik_500Medium,
    Rubik_600SemiBold,
    Rubik_700Bold,
    Rubik_800ExtraBold,
    Rubik_900Black,
  });

  const [splashDone, setSplashDone] = useState(false);

  // Mounted here, above the navigation stack, so tapping a mood reminder opens
  // the popup over WHATEVER screen was showing rather than navigating away from
  // it. That is the difference between a quick log and losing your place.
  const moodPrompt = useMoodPrompt();
  const fontsSettled = fontsLoaded || fontError !== null;

  // Hand off from the NATIVE splash to ours, and only once the fonts are in.
  //
  // The order matters and is the whole reason this effect exists. Our splash
  // draws the wordmark in Rubik, so hiding the native splash before the font
  // has loaded would show one frame of system type and then reflow. Waiting
  // means the two splashes are indistinguishable at the seam.
  //
  // `fontError` releases it too: a missing font is a degraded app, but a
  // permanently frozen native splash is a broken one.
  useEffect(() => {
    if (fontsSettled) ExpoSplashScreen.hideAsync();
  }, [fontsSettled]);

  const handleSplashFinish = useCallback(() => setSplashDone(true), []);

  if (!fontsSettled) return null;

  return (
    <Provider store={store}>
      <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
        {/* A plain stack, no tab bar: there is one screen, and Aira's real
          navigation is still an open question — the reference prototype uses
          three tabs, the shipped web client uses seven flat screens. Deciding
          that by leaving the Expo starter's Home/Explore tabs in place would be
          deciding it by accident.

          The screen mounts underneath immediately, so it has already laid out
          and settled by the time the splash fades off it. */}
        <Stack screenOptions={{ headerShown: false }} />
        <QuickLogPopup label={moodPrompt.label} onDismiss={moodPrompt.dismiss} />
        {!splashDone && <AiraSplash onFinish={handleSplashFinish} />}
      </ThemeProvider>
    </Provider>
  );
}
