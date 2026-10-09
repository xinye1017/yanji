import React from 'react';
import { View, Text, ViewStyle } from 'react-native';
import Svg, { Circle, Path, G } from 'react-native-svg';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { computePieSlices } from '../screens/reviewViewLogic';
import type { ReviewSubjectSlice } from '../bridge';

export interface YanjiPieChartProps {
  slices: ReadonlyArray<ReviewSubjectSlice>;
  size?: number;
  innerRadiusRatio?: number;
  emptyLabel?: string;
  style?: ViewStyle;
}

/**
 * Yanji standard Pie / Donut Chart rendered with react-native-svg.
 * Fully responsive, no external dependencies, smooth rendering on Fabric.
 */
export function YanjiPieChart({
  slices,
  size = 140,
  innerRadiusRatio = 0.55,
  emptyLabel = '暂无数据',
  style,
}: YanjiPieChartProps): React.JSX.Element {
  const theme = useYanjiTheme();
  const radius = size / 2;
  const innerRadius = radius * innerRadiusRatio;
  const strokeWidth = radius - innerRadius;
  const midRadius = (radius + innerRadius) / 2;

  const validSlices = slices.filter(s => s.share > 0);
  const pieData = computePieSlices(validSlices, radius, innerRadius, radius, radius);

  if (pieData.length === 0) {
    return (
      <View
        style={[
          {
            width: size,
            height: size,
            alignItems: 'center',
            justifyContent: 'center',
            alignSelf: 'center',
          },
          style,
        ]}
      >
        <Svg width={size} height={size}>
          <Circle
            cx={radius}
            cy={radius}
            r={midRadius}
            stroke={theme.colors.bgElevated}
            strokeWidth={strokeWidth}
            fill="none"
          />
        </Svg>
        <View
          style={{
            position: 'absolute',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Text
            style={{
              color: theme.colors.textTertiary,
              ...theme.typography.caption,
            }}
          >
            {emptyLabel}
          </Text>
        </View>
      </View>
    );
  }

  return (
    <View
      style={[
        {
          width: size,
          height: size,
          alignItems: 'center',
          justifyContent: 'center',
          alignSelf: 'center',
        },
        style,
      ]}
      accessible
      accessibilityRole="image"
      accessibilityLabel={`科目分布图，共 ${validSlices.length} 个学科`}
    >
      <Svg width={size} height={size}>
        {pieData.length === 1 ? (
          <Circle
            cx={radius}
            cy={radius}
            r={midRadius}
            stroke={pieData[0].color}
            strokeWidth={strokeWidth}
            fill="none"
          />
        ) : (
          <G>
            {pieData.map(slice => (
              <Path key={slice.key} d={slice.path} fill={slice.color} />
            ))}
          </G>
        )}
      </Svg>
    </View>
  );
}
