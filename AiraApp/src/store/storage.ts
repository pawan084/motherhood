import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * Storage for redux-persist, safe on every target.
 *
 * Expo's web build STATIC-RENDERS each route in Node, where there is no
 * `window` — and AsyncStorage on web is localStorage, so rehydrating or
 * flushing during that pass throws `ReferenceError: window is not defined`.
 *
 * A no-op on the server is the correct behaviour, not just a silencer: there is
 * no session to restore for a render that belongs to nobody, and anything
 * written there would be thrown away with the process.
 */
const isServer = typeof window === 'undefined';

const noopStorage = {
  getItem: async (_key: string): Promise<string | null> => null,
  setItem: async (_key: string, _value: string): Promise<void> => {},
  removeItem: async (_key: string): Promise<void> => {},
};

export const persistStorage = isServer ? noopStorage : AsyncStorage;
