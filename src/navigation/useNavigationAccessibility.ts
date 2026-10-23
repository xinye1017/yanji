import { useEffect, useState } from 'react';
import { AccessibilityInfo, Platform } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';

/** Read system preferences without changing them, including changes while open. */
export function useNavigationAccessibility() {
  const launchReduceMotion = useReducedMotion();
  const [reduceMotion, setReduceMotion] = useState(launchReduceMotion);
  const [highContrast, setHighContrast] = useState(false);

  useEffect(() => {
    let mounted = true;
    let motionChanged = false;
    let contrastChanged = false;
    const motion = AccessibilityInfo.addEventListener('reduceMotionChanged', enabled => {
      motionChanged = true;
      if (mounted) setReduceMotion(enabled);
    });
    const contrast = Platform.OS === 'android'
      ? AccessibilityInfo.addEventListener('highTextContrastChanged', enabled => {
        contrastChanged = true;
        if (mounted) setHighContrast(enabled);
      })
      : null;

    // An initial read must never overwrite a newer accessibility event.
    void AccessibilityInfo.isReduceMotionEnabled().then(enabled => {
      if (mounted && !motionChanged) setReduceMotion(enabled);
    }).catch(() => {});
    if (Platform.OS === 'android') {
      void AccessibilityInfo.isHighTextContrastEnabled().then(enabled => {
        if (mounted && !contrastChanged) setHighContrast(enabled);
      }).catch(() => {});
    }
    return () => {
      mounted = false;
      motion.remove();
      contrast?.remove();
    };
  }, []);

  return { reduceMotion, highContrast };
}
