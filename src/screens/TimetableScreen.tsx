import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { onDataChanged, YanjiDataNative } from '../bridge';
import type { ScheduleBlock, ScheduleTemplate, Subject } from '../bridge';
import { ScheduleTimeline } from '../components/ScheduleTimeline';
import { ScheduleTemplateEditor, ScheduleTemplateManagerSheet, copiedTemplateName } from '../components/ScheduleTemplateSheets';
import type { ScheduleTemplateDraft } from '../components/ScheduleTemplateSheets';
import { YanjiChip, YanjiEditorialHeader, YanjiIconButton, YanjiTextAction } from '../components/YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { uniqueTemplateName, validateBlockDraft } from './timetableViewLogic';

function toDraft(template: ScheduleTemplate): ScheduleTemplateDraft {
  return { id: template.id, name: template.name, blocks: template.blocks.map(block => ({ ...block, subjectIds: [...block.subjectIds] })) };
}

export function TimetableScreen({ onClose }: { onClose: () => void }): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout } = paper;
  const insets = useSafeAreaInsets();
  const [templates, setTemplates] = useState<ScheduleTemplate[]>([]);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [managerOpen, setManagerOpen] = useState(false);
  const [draft, setDraft] = useState<ScheduleTemplateDraft | null>(null);
  const [initialBlock, setInitialBlock] = useState<{ index?: number; startMinute?: number } | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editorError, setEditorError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const sequence = useRef(0);
  const scroll = useRef<React.ComponentRef<typeof ScrollView>>(null);

  const load = useCallback(async () => {
    const current = ++sequence.current;
    try {
      const [nextTemplates, nextSubjects] = await Promise.all([YanjiDataNative.getScheduleTemplates(), YanjiDataNative.getSubjects()]);
      if (current !== sequence.current) return;
      setTemplates(nextTemplates);
      setSubjects(nextSubjects);
      setSelectedId(previous => nextTemplates.some(template => template.id === previous) ? previous : nextTemplates[0]?.id ?? null);
      setError(null);
    } catch {
      if (current === sequence.current) setError('时间表读取失败，请重试');
    } finally {
      if (current === sequence.current) setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); return onDataChanged(event => { if (event.type === 'schedule' || event.type === 'subjects') void load(); }); }, [load]);
  const template = templates.find(item => item.id === selectedId) ?? null;

  const save = useCallback(async (next: ScheduleTemplateDraft) => {
    if (saving) return;
    setSaving(true);
    setEditorError(null);
    setError(null);
    try {
      const saved = next.id
        ? await YanjiDataNative.updateScheduleTemplate(next.id, next.name, next.blocks)
        : await YanjiDataNative.createScheduleTemplate(next.name, next.blocks);
      setSelectedId(saved.id);
      setDraft(null);
      setInitialBlock(null);
      await load();
    } catch {
      setEditorError('保存失败，请检查时间段后重试');
    } finally {
      setSaving(false);
    }
  }, [saving, load]);

  const changeBlock = useCallback(async (index: number, block: ScheduleBlock) => {
    if (!template || saving) return;
    const others = template.blocks.filter((_, i) => i !== index);
    const message = validateBlockDraft(block, others);
    if (message) { setError(message); return; }
    setSaving(true);
    setError(null);
    try {
      await YanjiDataNative.updateScheduleTemplate(template.id, template.name, template.blocks.map((item, i) => i === index ? block : item));
      await load();
    } catch {
      setError('时间未保存，请重试');
    } finally { setSaving(false); }
  }, [template, saving, load]);

  const editBlock = (index: number) => {
    if (!template) return;
    setEditorError(null);
    setInitialBlock({ index });
    setDraft(toDraft(template));
  };
  const addBlock = (startMinute: number) => {
    if (!template) return;
    setEditorError(null);
    setInitialBlock({ startMinute });
    setDraft(toDraft(template));
  };
  const editTemplate = (item: ScheduleTemplate) => {
    setManagerOpen(false);
    setSelectedId(item.id);
    setInitialBlock(null);
    setEditorError(null);
    setDraft(toDraft(item));
  };
  const create = () => {
    setManagerOpen(false);
    setInitialBlock(null);
    setEditorError(null);
    setDraft({ id: null, name: uniqueTemplateName(templates.map(item => item.name), '新模板'), blocks: [] });
  };
  const copy = (item: ScheduleTemplate) => {
    setManagerOpen(false);
    setInitialBlock(null);
    setEditorError(null);
    setDraft({ id: null, name: copiedTemplateName(templates, item.name), blocks: item.blocks.map(block => ({ ...block, id: null })) });
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.canvas }}>
      <View style={{ paddingHorizontal: layout.pageGutter, paddingTop: YanjiSpacing.lg }}>
        <YanjiEditorialHeader title="时间表设置" subtitle="安排每天的作息" trailing={<YanjiIconButton icon="close" accessibilityLabel="返回设置" onPress={onClose} tone="filled" />} />
        <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: YanjiSpacing.md }}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: YanjiSpacing.xs }} style={{ flex: 1 }}>
            {templates.map(item => <YanjiChip key={item.id} label={item.name} selected={item.id === selectedId} onPress={() => setSelectedId(item.id)} />)}
          </ScrollView>
          <YanjiTextAction label="管理" onPress={() => setManagerOpen(true)} />
        </View>
        <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.sm, marginBottom: YanjiSpacing.sm }]}>点击空白时间添加，拖动时段或边缘调整时间</Text>
        {error ? <Text accessibilityRole="alert" style={[typography.captionMeta, { color: colors.danger, marginBottom: YanjiSpacing.sm }]}>{error} <Text onPress={() => void load()}>重试</Text></Text> : null}
      </View>
      {template ? (
        <ScrollView ref={scroll} showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: layout.pageGutter, paddingTop: YanjiSpacing.sm, paddingBottom: insets.bottom + YanjiSpacing.xl }} onLayout={() => scroll.current?.scrollTo({ y: 8 * 80, animated: false })}>
          <ScheduleTimeline blocks={template.blocks} disabled={saving} onAdd={addBlock} onEdit={editBlock} onChange={(index, next) => void changeBlock(index, next)} />
        </ScrollView>
      ) : <View style={{ paddingHorizontal: layout.pageGutter, paddingVertical: YanjiSpacing.lg }}><Text style={[typography.bodyPrimary, { color: colors.secondary }]}>{loading ? '正在读取时间表…' : '还没有作息模板，先新建一套。'}</Text>{!loading ? <YanjiTextAction label="新建模板" onPress={create} /> : null}</View>}
      <ScheduleTemplateManagerSheet visible={managerOpen} templates={templates} activeTemplateId={null} onClose={() => setManagerOpen(false)} onChanged={() => void load()} onCreate={create} onCopy={copy} onEdit={editTemplate} />
      <ScheduleTemplateEditor draft={draft} subjects={subjects} saving={saving} error={editorError} initialBlock={initialBlock} onClose={() => { if (!saving) { setDraft(null); setInitialBlock(null); } }} onSave={next => void save(next)} />
    </View>
  );
}
