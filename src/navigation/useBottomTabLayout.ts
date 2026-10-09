import { useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { bottomTabLayout } from '../theme/liquidGlass';
import { YanjiSpacing } from '../theme/tokens';

export function useBottomTabLayout() {
  const { fontScale } = useWindowDimensions();
  const { bottom } = useSafeAreaInsets();
  return bottomTabLayout(fontScale, bottom, YanjiSpacing.md);
}
