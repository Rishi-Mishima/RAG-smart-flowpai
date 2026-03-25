import SparkMD5 from 'spark-md5';
import { $t } from '@/locales';

/**
 * Transform record to option
 *
 * @example
 *   ```ts
 *   const record = {
 *     key1: 'label1',
 *     key2: 'label2'
 *   };
 *   const options = transformRecordToOption(record);
 *   // [
 *   //   { value: 'key1', label: 'label1' },
 *   //   { value: 'key2', label: 'label2' }
 *   // ]
 *   ```;
 *
 * @param record
 */
export function transformRecordToOption<T extends Record<string, string>>(record: T) {
  return Object.entries(record).map(([value, label]) => ({
    value,
    label
  })) as CommonType.Option<keyof T, T[keyof T]>[];
}

/**
 * Translate options
 *
 * @param options
 */
export function translateOptions(options: CommonType.Option<string, App.I18n.I18nKey>[]) {
  return options.map(option => ({
    ...option,
    label: $t(option.label)
  }));
}

/**
 * Toggle html class
 *
 * @param className
 */
export function toggleHtmlClass(className: string) {
  function add() {
    document.documentElement.classList.add(className);
  }

  function remove() {
    document.documentElement.classList.remove(className);
  }

  return {
    add,
    remove
  };
}

// 文件大小转换，根据文件大小转换为K、M、G
export function fileSize(size: number) {
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(2)}K`;
  }
  if (size < 1024 * 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(2)}M`;
  }
  return `${(size / 1024 / 1024 / 1024).toFixed(2)}G`;
}

export async function calculateMD5(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunkSize = 5 * 1024 * 1024; // 5MB
    const spark = new SparkMD5.ArrayBuffer();
    const reader = new FileReader();

    let currentChunk = 0;

    const loadNext = () => {
      const start = currentChunk * chunkSize;
      const end = Math.min(start + chunkSize, file.size);

      if (start >= file.size) {
        resolve(spark.end());
        return;
      }

      const blob = file.slice(start, end);
      reader.readAsArrayBuffer(blob);
    };

    reader.onload = e => {
      spark.append(e.target?.result as ArrayBuffer);
      currentChunk += 1;
      loadNext();
    };

    reader.onerror = () => reject(new Error('文件读取失败'));
    loadNext();
  });
}

export function formatDate(date: string | number | null | undefined, format = 'YYYY-MM-DD HH:mm:ss') {
  if (!date) return '';
  return dayjs(date).format(format);
}

// 获取文件扩展名
export function getFileExt(fileName: string) {
  if (!fileName) return '';
  return fileName.split('.').pop();
}
