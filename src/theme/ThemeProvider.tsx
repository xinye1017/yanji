/**
 * Yanji theme provider — resolves light/dark from the native theme preference
 * (user_settings.themeMode) and exposes semantic colors to the component tree.
 *
 * The system-level light/dark setting is NEVER modified (AGENTS.md §二.6):
 * this provider only reads the app preference and the system-wide color scheme.
 *
 * SYSTEM follows the **system-wide** scheme, not the app-effective one. ColorOS
 * and similar OEM builds expose a per-app "force dark" override
 * (`ui_night_mode_override_on`), which makes `useColorScheme()` — and
 * `context.resources` — report dark while the phone itself is light. Choosing
 * 「跟随系统」 means following the phone, so the native side reads
 * `Resources.getSystem()` and pushes it through `onThemeChanged`.
 *
 * `useColorScheme()` is kept only as the pre-hydration fallback: it is correct
 * on platforms without an app-level override and avoids a light flash while the
 * native promise is in flight.
 *
 * The 素笺与寸阴 paper domain is the single colour and type vocabulary, for the
 * navigation dock as much as for the body. The remaining scales provide
 * neutral geometry, touch targets, motion and shared elevation styles.
 */

import React, { createContext, useContext, useEffect, useState } from 'react';
import { useColorScheme } from 'react-native';
import { YanjiThemeNative, onThemeChanged } from '../bridge';
import {
  YANJI_FONT_AVAILABILITY_FALLBACK,
  resolveYanjiFontFamily,
  YanjiMotion,
  YanjiPaperColors,
  YanjiPaperImmersive,
  YanjiPaperLayout,
  YanjiPaperMotion,
  YanjiPaperRadius,
  YanjiPaperTypography,
  YanjiRadius,
  YanjiShadow,
  YanjiSpacing,
  YanjiTouch,
} from './tokens';
import type { YanjiFontAvailability } from './tokens';
import type { ThemeMode } from '../bridge';

type YanjiPaperTypographyScale = typeof YanjiPaperTypography;
type YanjiResolvedTypography = {
  [K in keyof YanjiPaperTypographyScale]:
    Omit<YanjiPaperTypographyScale[K], 'fontRole'> & { fontFamily?: string };
};

/**
 * Replace each type role with a family this device can actually render.
 *
 * A role that resolves to no family contributes no `fontFamily` at all, so the
 * Text keeps React Native's exact-weight system path — that is what preserves
 * the 300 and 900 cuts `displayTimer` and `titleSection` depend on.
 */
function resolveTypography(availability: YanjiFontAvailability): YanjiResolvedTypography {
  const resolved: Record<string, unknown> = {};
  for (const [name, style] of Object.entries(YanjiPaperTypography)) {
    const { fontRole, ...rest } = style as typeof style & { fontRole?: 'serif' | 'sans' };
    const family = fontRole ? resolveYanjiFontFamily(fontRole, availability) : undefined;
    resolved[name] = family ? { ...rest, fontFamily: family } : rest;
  }
  return resolved as YanjiResolvedTypography;
}

export interface YanjiTheme {
  isDark: boolean;
  mode: ThemeMode;
  radius: typeof YanjiRadius;
  spacing: typeof YanjiSpacing;
  touch: typeof YanjiTouch;
  motion: typeof YanjiMotion;
  shadow: typeof YanjiShadow;
  /**
   * 素笺与寸阴 domain. `colors` resolves with the theme; `immersive` is always
   * the full pair because a focus screen picks running vs paused itself.
   */
  paper: {
    colors: (typeof YanjiPaperColors)['light'] | (typeof YanjiPaperColors)['dark'];
    immersive: typeof YanjiPaperImmersive;
    /**
     * The scale with every `fontRole` resolved to this device's `fontFamily`, so
     * a consumer spreads the style and gets the right face without knowing
     * whether the phone has a serif CJK cut at all.
     */
    typography: YanjiResolvedTypography;
    layout: typeof YanjiPaperLayout;
    radius: typeof YanjiPaperRadius;
    motion: typeof YanjiPaperMotion;
  };
}

const ThemeContext = createContext<YanjiTheme | null>(null);

export function YanjiThemeProvider({ children }: { children: React.ReactNode }): React.JSX.Element {
  const rnSystemDark = useColorScheme() === 'dark';
  const [mode, setMode] = useState<ThemeMode>('SYSTEM');
  const [systemDark, setSystemDark] = useState<boolean>(rnSystemDark);

  useEffect(() => {
    let cancelled = false;
    YanjiThemeNative.getThemePreference()
      .then(pref => {
        if (cancelled) return;
        setMode(pref.mode);
        setSystemDark(pref.isDark);
      })
      .catch(() => {
        // Native module unavailable (web/tests): fall back to the RN scheme.
        setSystemDark(rnSystemDark);
      });
    const unsubscribe = onThemeChanged(pref => {
      if (cancelled) return;
      setMode(pref.mode);
      setSystemDark(pref.isDark);
    });
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [rnSystemDark]);

  const isDark = mode === 'DARK' || (mode === 'SYSTEM' && systemDark);

  // Which families this device can actually draw. ColorOS ships no serif Han
  // face, so the default is "none" and the serif role resolves to the system
  // sans; a device that does have one (or a future ColorOS that adds it) only
  // changes this probe, not every screen.
  const [fonts, setFonts] = useState<YanjiFontAvailability>(YANJI_FONT_AVAILABILITY_FALLBACK);
  useEffect(() => {
    let cancelled = false;
    YanjiThemeNative.getFontAvailability()
      .then(availability => {
        if (cancelled) return;
        setFonts(availability);
      })
      .catch(() => {
        // Bridge unavailable (web/tests): the conservative answer is already set.
      });
    return () => { cancelled = true; };
  }, []);

  const typography = resolveTypography(fonts);

  const theme: YanjiTheme = {
    isDark,
    mode,
    radius: YanjiRadius,
    spacing: YanjiSpacing,
    touch: YanjiTouch,
    motion: YanjiMotion,
    shadow: YanjiShadow,
    paper: {
      colors: isDark ? YanjiPaperColors.dark : YanjiPaperColors.light,
      immersive: YanjiPaperImmersive,
      typography,
      layout: YanjiPaperLayout,
      radius: YanjiPaperRadius,
      motion: YanjiPaperMotion,
    },
  };

  return <ThemeContext.Provider value={theme}>{children}</ThemeContext.Provider>;
}

export function useYanjiTheme(): YanjiTheme {
  const theme = useContext(ThemeContext);
  if (!theme) {
    throw new Error('useYanjiTheme must be used inside YanjiThemeProvider');
  }
  return theme;
}
