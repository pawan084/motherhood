import { useEffect } from 'react';

import { useAppDispatch } from '@/store/hooks';
import { bootstrapSession } from '@/store/thunks';

/**
 * Makes sure there is an identity, once, at launch.
 *
 * A separate component because the dispatch has to happen INSIDE the Provider,
 * and the layout that renders the Provider cannot use the store it is creating.
 *
 * Renders nothing and blocks nothing. Aira is anonymous-first: `/device/register`
 * mints a device user when there is no token, so the app is usable from first
 * launch with no account and nothing asked of anybody. A failure here — backend
 * down, no network — must not stop the UI, so the rejection is swallowed and
 * left in `session.status` for a screen to read if it cares.
 */
export function SessionBootstrap() {
  const dispatch = useAppDispatch();

  useEffect(() => {
    void dispatch(bootstrapSession());
  }, [dispatch]);

  return null;
}
