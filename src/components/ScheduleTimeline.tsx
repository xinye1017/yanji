import React, { useMemo, useRef, useState } from 'react';
import { PanResponder, Pressable, Text, View } from 'react-native';
import type { ScheduleBlock } from '../bridge';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { adjustScheduleTime, formatMinuteOfDay } from '../screens/timetableViewLogic';

const HOUR_HEIGHT = 80;
const TIME_WIDTH = 54;
type DragMode = 'move' | 'start' | 'end';

/** Pure geometry: existing off-grid values remain untouched until a drag is committed. */

function TimelineBlock({ block, disabled, onEdit, onChange }: {
  block: ScheduleBlock;
  disabled: boolean;
  onEdit: () => void;
  onChange: (next: ScheduleBlock) => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const [preview, setPreview] = useState<ScheduleBlock | null>(null);
  const latest = useRef({ block, disabled, onChange, onEdit });
  latest.current = { block, disabled, onChange, onEdit };
  const candidate = useRef<ScheduleBlock | null>(null);
  const responders = useMemo(() => {
    const create = (mode: DragMode) => PanResponder.create({
      onStartShouldSetPanResponder: () => !latest.current.disabled,
      onPanResponderTerminationRequest: () => false,
      onPanResponderGrant: () => { candidate.current = null; },
      onPanResponderMove: (_, gesture) => {
        if (Math.abs(gesture.dy) <= 6) return;
        const next = adjustScheduleTime(latest.current.block, mode, gesture.dy * 60 / HOUR_HEIGHT);
        candidate.current = next;
        setPreview(next);
      },
      onPanResponderRelease: () => {
        const next = candidate.current;
        setPreview(null);
        candidate.current = null;
        if (next) latest.current.onChange(next);
        else latest.current.onEdit();
      },
      onPanResponderTerminate: () => { setPreview(null); candidate.current = null; },
    });
    return { move: create('move'), start: create('start'), end: create('end') };
  }, []);
  const shown = preview ?? block;
  const height = (shown.endMinute - shown.startMinute) / 60 * HOUR_HEIGHT;
  return (
    <View style={{ position: 'absolute', left: TIME_WIDTH + 5, right: 4, top: shown.startMinute / 60 * HOUR_HEIGHT, height, zIndex: preview ? 5 : 2 }}>
      <View
        {...responders.move.panHandlers}
        accessible
        accessibilityRole="button"
        accessibilityState={{ disabled }}
        accessibilityLabel={`${formatMinuteOfDay(block.startMinute)}至${formatMinuteOfDay(block.endMinute)}，${block.subjectLabel} ${block.title}，点击编辑`}
        accessibilityActions={[{ name: 'activate', label: '编辑时间段' }]}
        onAccessibilityAction={event => { if (event.nativeEvent.actionName === 'activate') onEdit(); }}
        style={{ flex: 1, overflow: 'hidden', borderRadius: paper.radius.tag, backgroundColor: paper.colors.brandSubtle, borderLeftWidth: 3, borderLeftColor: paper.colors.brand, paddingHorizontal: 8, justifyContent: 'center' }}
      >
        <Text numberOfLines={1} style={[paper.typography.captionMeta, { color: paper.colors.brandInk, fontWeight: '700' }]}>{shown.title}{shown.subjectLabel ? ` · ${shown.subjectLabel}` : ''}</Text>
        {height >= 45 ? <Text numberOfLines={1} style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>{formatMinuteOfDay(shown.startMinute)}–{formatMinuteOfDay(shown.endMinute)}</Text> : null}
      </View>
      <View {...responders.start.panHandlers} style={{ position: 'absolute', top: 0, left: 0, right: 0, height: Math.min(12, height / 3) }} />
      <View {...responders.end.panHandlers} style={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: Math.min(12, height / 3) }} />
    </View>
  );
}

export function ScheduleTimeline({ blocks, disabled, onAdd, onEdit, onChange }: {
  blocks: ScheduleBlock[];
  disabled: boolean;
  onAdd: (minute: number) => void;
  onEdit: (index: number) => void;
  onChange: (index: number, block: ScheduleBlock) => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  return (
    <View style={{ height: HOUR_HEIGHT * 24 + 20 }}>
      {Array.from({ length: 24 }, (_, hour) => (
        <View key={hour} style={{ position: 'absolute', top: hour * HOUR_HEIGHT, left: 0, right: 0, height: HOUR_HEIGHT, flexDirection: 'row' }}>
          <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, width: TIME_WIDTH, fontVariant: ['tabular-nums'] }]}>{String(hour).padStart(2, '0')}:00</Text>
          <Pressable
            disabled={disabled}
            accessibilityRole="button"
            accessibilityLabel={`${hour}点，添加时间段`}
            onPress={event => onAdd(Math.min(1425, hour * 60 + Math.floor(event.nativeEvent.locationY / HOUR_HEIGHT * 4) * 15))}
            style={{ flex: 1, borderTopWidth: paper.layout.hairlineWidth, borderColor: paper.colors.hairline }}
          />
        </View>
      ))}
      {blocks.map((block, index) => <TimelineBlock key={block.id} block={block} disabled={disabled} onEdit={() => onEdit(index)} onChange={next => onChange(index, next)} />)}
      <Text style={[paper.typography.captionMeta, { position: 'absolute', top: 24 * HOUR_HEIGHT, color: paper.colors.secondary }]}>24:00</Text>
    </View>
  );
}
