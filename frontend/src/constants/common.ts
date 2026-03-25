import { transformRecordToOption } from '@/utils/common';

export const yesOrNoRecord: Record<CommonType.YesOrNo, App.I18n.I18nKey> = {
  Y: 'common.yesOrNo.yes',
  N: 'common.yesOrNo.no'
};

export const yesOrNoOptions = transformRecordToOption(yesOrNoRecord);

export const enableStatusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
];

export const chunkSize = 5 * 1024 * 1024;

export const uploadAccept = '.pdf,.doc,.docx,.txt';
