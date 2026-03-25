<template>
  <div class="file-preview-container">
    <div class="preview-backdrop" />

    <div class="preview-content">
      <template v-if="loading">
        <div class="state-panel">
          <div class="state-orb">
            <NSpin size="large" />
          </div>
          <div class="state-copy">
            <strong>正在装载引用文档</strong>
            <span>整理线索、页码定位和可预览内容。</span>
          </div>
        </div>
      </template>
      <template v-else-if="error">
        <div class="state-panel state-panel--error">
          <div class="state-orb state-orb--error">
            <icon-mdi-alert-circle class="text-34" />
          </div>
          <div class="state-copy">
            <strong>这份文档暂时没能打开</strong>
            <span>{{ error }}</span>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="content-wrapper" :class="{ 'content-wrapper--immersive': previewType === 'pdf' && previewUrl }">
          <aside class="insight-rail">
            <section class="info-card source-card">
              <div class="source-card-top">
                <div class="file-badge-shell">
                  <div class="file-badge-icon">
                    <SvgIcon :local-icon="getFileIcon(fileName)" class="text-18" />
                  </div>
                  <div class="file-badge-copy">
                    <h2 class="preview-title">{{ fileName }}</h2>
                    <p v-if="headerMetaLine" class="preview-subtitle">{{ headerMetaLine }}</p>
                  </div>
                </div>
              </div>
              <div class="source-actions">
                <NButton
                  v-if="previewType !== 'pdf'"
                  size="small"
                  secondary
                  @click="openPreviewInNewTab"
                  :disabled="!canOpenInNewTab"
                >
                  <template #icon>
                    <icon-mdi-open-in-new />
                  </template>
                  新窗口
                </NButton>
                <NButton size="small" secondary @click="downloadFile" :loading="downloading">
                  <template #icon>
                    <icon-mdi-download />
                  </template>
                  下载
                </NButton>
                <NButton size="small" quaternary @click="closePreview">
                  <template #icon>
                    <icon-mdi-close />
                  </template>
                  关闭
                </NButton>
              </div>
            </section>

            <section class="info-card info-card--hero">
              <span class="info-label">概览</span>
              <strong class="info-title">{{ heroHeadline }}</strong>
              <p class="info-copy">{{ heroDescription }}</p>
              <div v-if="retrievalQuery" class="info-inline-block">
                <span class="info-label">检索问题</span>
                <p class="support-copy">{{ retrievalQuery }}</p>
              </div>
            </section>

            <section v-if="evidenceSnippet" class="info-card">
              <span class="info-label">线索</span>
              <p class="support-copy">{{ evidenceSnippet }}</p>
            </section>

            <section v-else-if="resolvedHighlightAnchor" class="info-card">
              <span class="info-label">定位线索</span>
              <p class="support-copy">{{ resolvedHighlightAnchor }}</p>
            </section>

          </aside>

          <section class="preview-stage">
            <div class="stage-body">
              <template v-if="previewType === 'pdf' && previewUrl">
                <div class="pdf-preview-stack">
                  <PdfDocumentViewer
                    :url="resolvedPreviewUrl"
                    :source-url="resolvedSourceUrl"
                    :file-name="fileName"
                    :page-number="pageNumber"
                    :single-page-mode="singlePageMode"
                    :source-page-number="sourcePageNumber"
                    :anchor-text="resolvedHighlightAnchor"
                    :search-text="resolvedHighlightSearchText"
                    :visible="visible"
                  />
                </div>
              </template>
              <template v-else-if="previewType === 'image' && resolvedPreviewUrl">
                <div class="image-preview-shell">
                  <img :src="resolvedPreviewUrl" :alt="fileName" class="preview-image" />
                </div>
              </template>
              <template v-else-if="previewType === 'text'">
                <div class="text-preview-shell">
                  <pre class="preview-text">{{ content }}</pre>
                </div>
              </template>
              <template v-else>
                <div class="download-placeholder">
                  <div class="placeholder-icon">
                    <SvgIcon :local-icon="getFileIcon(fileName)" class="text-28" />
                  </div>
                  <div class="state-copy">
                    <strong>当前格式暂不支持在线预览</strong>
                    <span>你可以先下载文件，或在新窗口中尝试打开原始资源。</span>
                  </div>
                  <div class="placeholder-actions">
                    <NButton secondary @click="openPreviewInNewTab" :disabled="!canOpenInNewTab">
                      <template #icon>
                        <icon-mdi-open-in-new />
                      </template>
                      新窗口打开
                    </NButton>
                    <NButton type="primary" @click="downloadFile">
                      <template #icon>
                        <icon-mdi-download />
                      </template>
                      下载后查看
                    </NButton>
                  </div>
                </div>
              </template>
            </div>
          </section>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { NButton, NSpin } from 'naive-ui';
import { request } from '@/service/request';
import { getFileExt } from '@/utils/common';
import { getServiceBaseURL } from '@/utils/service';
import PdfDocumentViewer from '@/components/custom/pdf-document-viewer.vue';
import SvgIcon from '@/components/custom/svg-icon.vue';

interface Props {
  fileName: string;
  fileMd5?: string;
  pageNumber?: number;
  anchorText?: string;
  searchText?: string;
  retrievalMode?: Api.Chat.ReferenceEvidence['retrievalMode'];
  retrievalLabel?: string;
  retrievalQuery?: string;
  evidenceSnippet?: string;
  matchedChunkText?: string;
  score?: number | null;
  chunkId?: number | null;
  visible: boolean;
}

interface Emits {
  (e: 'close'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const loading = ref(false);
const downloading = ref(false);
const content = ref('');
const error = ref('');
const previewType = ref<'pdf' | 'image' | 'text' | 'download'>('text');
const previewUrl = ref('');
const sourceUrl = ref('');
const singlePageMode = ref(false);
const sourcePageNumber = ref<number | undefined>(undefined);
const isHttpProxy = import.meta.env.DEV && import.meta.env.VITE_HTTP_PROXY === 'Y';
const { baseURL: serviceBaseUrl } = getServiceBaseURL(import.meta.env, isHttpProxy);

const resolvedPreviewUrl = computed(() => resolveFileAccessUrl(previewUrl.value));
const resolvedSourceUrl = computed(() => resolveFileAccessUrl(sourceUrl.value));
const fileExtensionLabel = computed(() => {
  const extension = getFileExt(props.fileName)?.toUpperCase();
  return extension || 'FILE';
});
const fallbackRetrievalLabel = computed(() => {
  if (props.retrievalMode === 'TEXT_ONLY') {
    return '关键词召回';
  }
  if (props.retrievalMode === 'HYBRID') {
    return '混合召回（语义相关 + 关键词）';
  }
  return '';
});
const resolvedHighlightAnchor = computed(() => props.anchorText || '');
const resolvedHighlightSearchText = computed(() => {
  return [props.matchedChunkText, props.searchText, props.anchorText]
    .map(text => text?.trim())
    .filter((text, index, values): text is string => Boolean(text) && values.indexOf(text) === index)
    .join('\n');
});
const displayScore = computed(() => {
  if (typeof props.score !== 'number' || Number.isNaN(props.score)) {
    return '';
  }
  return props.score.toFixed(3);
});
const displayPage = computed(() => sourcePageNumber.value || props.pageNumber || undefined);
const displayPageLabel = computed(() => (displayPage.value ? `第 ${displayPage.value} 页` : ''));
const displayScoreLabel = computed(() => (displayScore.value ? `相关分数 ${displayScore.value}` : ''));
const headerMetaLine = computed(() => {
  if (previewType.value === 'pdf') {
    return [displayPageLabel.value, displayScore.value ? `分数 ${displayScore.value}` : ''].filter(Boolean).join(' / ');
  }
  if (previewType.value === 'image') {
    return [fileExtensionLabel.value, displayScore.value ? `分数 ${displayScore.value}` : ''].filter(Boolean).join(' / ');
  }
  if (previewType.value === 'text') {
    return [fileExtensionLabel.value, displayScore.value ? `分数 ${displayScore.value}` : ''].filter(Boolean).join(' / ');
  }
  return [fileExtensionLabel.value, displayScore.value ? `分数 ${displayScore.value}` : ''].filter(Boolean).join(' / ');
});
const heroHeadline = computed(() => {
  if (props.retrievalLabel || fallbackRetrievalLabel.value) {
    return props.retrievalLabel || fallbackRetrievalLabel.value;
  }
  if (previewType.value === 'pdf') {
    return '文档已定位到可阅读页';
  }
  return '已就绪的引用文档';
});
const heroDescription = computed(() => {
  if (props.retrievalQuery) {
    return '左侧展示的是本次 RAG 检索的问题与定位线索，右侧则直接打开原始文档，方便核对答案依据。';
  }
  if (props.evidenceSnippet) {
    return '左侧展示的是这次检索的定位线索，右侧则直接打开原始文档，方便核对答案依据。';
  }
  if (resolvedHighlightAnchor.value) {
    return '当前预览会优先围绕这条上下文线索定位，方便你核对答案和原文是否一致。';
  }
  return '这里展示的是引用来源的原始文档内容，你可以直接浏览、下载或在新窗口中打开。';
});
const canOpenInNewTab = computed(() => Boolean(resolvedSourceUrl.value || resolvedPreviewUrl.value));

function resolveFileAccessUrl(url: string) {
  if (!url) return '';
  if (/^(https?:)?\/\//i.test(url) || /^(blob:|data:)/i.test(url)) {
    return url;
  }

  if (url.startsWith('/api/')) {
    if (serviceBaseUrl.startsWith('/proxy-')) {
      return `${serviceBaseUrl}${url.replace(/^\/api\/v\d+/, '')}`;
    }

    if (/^https?:\/\//i.test(serviceBaseUrl)) {
      return `${new URL(serviceBaseUrl).origin}${url}`;
    }

    const serviceOrigin = serviceBaseUrl.replace(/\/api(?:\/v\d+)?\/?$/, '');
    return `${serviceOrigin}${url}`;
  }

  if (url.startsWith('/')) {
    return url;
  }

  return `${serviceBaseUrl.replace(/\/$/, '')}/${url.replace(/^\//, '')}`;
}

// 获取文件图标
function getFileIcon(fileName: string) {
  const ext = getFileExt(fileName);
  if (ext) {
    const supportedIcons = ['pdf', 'doc', 'docx', 'txt', 'md', 'jpg', 'jpeg', 'png', 'gif'];
    return supportedIcons.includes(ext.toLowerCase()) ? ext : 'dflt';
  }
  return 'dflt';
}

// 监听文件名变化，加载预览内容
watch(() => props.fileName, async (newFileName) => {
  if (newFileName && props.visible) {
    await loadPreviewContent();
  }
}, { immediate: true });

// 监听可见性变化
watch(() => props.visible, async (visible) => {
  if (visible && props.fileName) {
    await loadPreviewContent();
  }
});

// 加载预览内容
async function loadPreviewContent() {
  if (!props.fileName) return;

  console.log('[文件预览] 开始加载预览内容:', {
    fileName: props.fileName,
    fileMd5: props.fileMd5,
    visible: props.visible
  });

  loading.value = true;
  error.value = '';
  content.value = '';
  previewUrl.value = '';
  sourceUrl.value = '';
  singlePageMode.value = false;
  sourcePageNumber.value = undefined;
  previewType.value = 'text';

  try {
    // 优先使用 MD5 预览（如果存在）
    if (props.fileMd5) {
      console.log('[文件预览] 使用MD5模式预览，请求参数:', {
        fileName: props.fileName,
        fileMd5: props.fileMd5,
        pageNumber: props.pageNumber
      });

      const { error: requestError, data } = await request<{
        fileName: string;
        fileSize: number;
        fileMd5?: string;
        content?: string;
        previewUrl?: string;
        sourceUrl?: string;
        singlePageMode?: boolean;
        sourcePageNumber?: number;
        previewType?: 'pdf' | 'image' | 'text' | 'download';
      }>({
        url: '/documents/preview',
        params: {
          fileName: props.fileName,
          fileMd5: props.fileMd5,
          pageNumber: props.pageNumber
        }
      });

      console.log('[文件预览] MD5模式API响应:', {
        hasError: !!requestError,
        error: requestError,
        hasData: !!data,
        contentLength: data?.content?.length || 0,
        contentPreview: data?.content?.substring(0, 100) || ''
      });

      if (requestError) {
        error.value = '预览失败：' + (requestError.message || '未知错误');
      } else if (data) {
        previewType.value = data.previewType || 'download';
        content.value = data.content || '';
        previewUrl.value = data.previewUrl || '';
        sourceUrl.value = data.sourceUrl || data.previewUrl || '';
        singlePageMode.value = Boolean(data.singlePageMode);
        sourcePageNumber.value = data.sourcePageNumber || props.pageNumber;
      }
    } else {
      // 降级：使用文件名预览（向后兼容）
      console.log('[文件预览] 使用文件名模式预览（降级）, 请求参数:', {
        fileName: props.fileName,
        pageNumber: props.pageNumber
      });

      const { error: requestError, data } = await request<{
        fileName: string;
        fileSize: number;
        fileMd5?: string;
        content?: string;
        previewUrl?: string;
        sourceUrl?: string;
        singlePageMode?: boolean;
        sourcePageNumber?: number;
        previewType?: 'pdf' | 'image' | 'text' | 'download';
      }>({
        url: '/documents/preview',
        params: {
          fileName: props.fileName,
          pageNumber: props.pageNumber
        }
      });

      console.log('[文件预览] 文件名模式API响应:', {
        hasError: !!requestError,
        error: requestError,
        hasData: !!data,
        contentLength: data?.content?.length || 0,
        contentPreview: data?.content?.substring(0, 100) || ''
      });

      if (requestError) {
        error.value = '预览失败：' + (requestError.message || '未知错误');
      } else if (data) {
        previewType.value = data.previewType || 'download';
        content.value = data.content || '';
        previewUrl.value = data.previewUrl || '';
        sourceUrl.value = data.sourceUrl || data.previewUrl || '';
        singlePageMode.value = Boolean(data.singlePageMode);
        sourcePageNumber.value = data.sourcePageNumber || props.pageNumber;
      }
    }
  } catch (err: any) {
    error.value = '预览失败：' + (err.message || '网络错误');
  } finally {
    loading.value = false;
  }
}

// 下载文件
async function downloadFile() {
  if (!props.fileName) return;

  downloading.value = true;

  try {
    // 优先使用 MD5 下载（如果存在）
    if (props.fileMd5) {
      const { error: requestError, data } = await request<{
        fileName: string;
        downloadUrl: string;
        fileSize: number;
        fileMd5?: string;
      }>({
        url: '/documents/download-by-md5',
        params: {
          fileMd5: props.fileMd5
        }
      });

      if (requestError) {
        window.$message?.error('下载失败：' + (requestError.message || '未知错误'));
      } else if (data) {
        // 使用预签名URL下载文件
        const link = document.createElement('a');
        link.href = data.downloadUrl;
        link.download = data.fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.$message?.success('开始下载文件');
      }
    } else {
      // 降级：使用文件名下载（向后兼容）
      const { error: requestError, data } = await request<{
        fileName: string;
        downloadUrl: string;
        fileSize: number;
      }>({
        url: '/documents/download',
        params: {
          fileName: props.fileName
        }
      });

      if (requestError) {
        window.$message?.error('下载失败：' + (requestError.message || '未知错误'));
      } else if (data) {
        // 使用预签名URL下载文件
        const link = document.createElement('a');
        link.href = data.downloadUrl;
        link.download = data.fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.$message?.success('开始下载文件');
      }
    }
  } catch (err: any) {
    window.$message?.error('下载失败：' + (err.message || '网络错误'));
  } finally {
    downloading.value = false;
  }
}

function openPreviewInNewTab() {
  const targetUrl = resolvedSourceUrl.value || resolvedPreviewUrl.value;
  if (!targetUrl) return;

  if (previewType.value === 'pdf' && displayPage.value) {
    window.open(`${targetUrl}#page=${displayPage.value}`, '_blank', 'noopener,noreferrer');
    return;
  }

  window.open(targetUrl, '_blank', 'noopener,noreferrer');
}

// 关闭预览
function closePreview() {
  emit('close');
}

</script>

<style scoped lang="scss">
.file-preview-container {
  @apply relative flex h-full min-h-0 flex-col overflow-hidden bg-white;
  height: min(92vh, calc(100vh - 20px));
  min-height: min(760px, calc(100vh - 20px));

  .preview-backdrop {
    display: none;
  }

  .file-badge-shell {
    @apply flex items-start gap-4;
  }

  .file-badge-icon {
    @apply flex h-13 w-13 shrink-0 items-center justify-center rounded-14px border border-stone-200 bg-stone-50 text-primary shadow-sm;
  }

  .file-badge-copy {
    @apply min-w-0;
  }

  .preview-title {
    @apply m-0 truncate text-[17px] font-700 leading-tight text-stone-800;
  }

  .preview-subtitle {
    @apply mt-1 text-sm text-stone-500;
  }

  .preview-content {
    @apply relative z-1 min-h-0 flex-1 overflow-hidden bg-white px-3 py-3;

    .content-wrapper {
      @apply grid h-full min-h-0 grid-cols-[240px_minmax(0,1fr)] gap-3 overflow-hidden;
    }

    .content-wrapper--immersive {
      grid-template-columns: 240px minmax(0, 1fr);
    }

    .state-panel {
      @apply flex h-full min-h-[420px] flex-col items-center justify-center gap-5 rounded-16px border border-stone-200 bg-white px-10 text-center shadow-sm;
    }

    .state-panel--error {
      @apply border-rose-200/60 bg-rose-50/72;
    }

    .state-orb {
      @apply flex h-16 w-16 items-center justify-center rounded-full border border-stone-200 bg-stone-50 text-stone-700;
    }

    .state-orb--error {
      @apply border-rose-200 bg-rose-100 text-rose-600;
    }

    .state-copy {
      @apply flex max-w-520px flex-col gap-2 text-stone-600;
    }

    .state-copy strong {
      @apply text-lg text-stone-800;
      font-family: 'Avenir Next', 'Segoe UI', sans-serif;
    }

    .insight-rail {
      @apply flex min-h-0 min-w-0 flex-col gap-4 overflow-y-auto overflow-x-hidden pr-1;
    }

    .info-card {
      @apply rounded-12px bg-stone-50 p-4 text-stone-700;
    }

    .info-card--hero {
      @apply bg-transparent p-0;
    }

    .source-card {
      @apply gap-0 rounded-16px border border-stone-200 bg-white p-4 shadow-sm;
    }

    .source-card-top {
      @apply min-w-0 overflow-hidden;
    }

    .source-actions {
      @apply mt-4 flex flex-wrap gap-2;
    }

    .info-card--quiet {
      @apply bg-stone-50;
    }

    .info-row {
      @apply mb-3 flex items-center justify-between gap-3;
    }

    .info-label {
      @apply text-[11px] uppercase tracking-[0.16em] text-stone-500;
    }

    .info-title {
      @apply mt-3 block whitespace-nowrap text-sm font-700 leading-tight text-stone-900;
    }

    .info-copy,
    .support-copy,
    .spotlight-copy {
      @apply mb-0 mt-3 text-sm leading-7 break-words;
      overflow-wrap: anywhere;
    }

    .info-inline-block {
      @apply mt-4 rounded-12px bg-primary/4 px-4 py-3;
    }

    .spotlight-copy {
      color: inherit;
    }

    .preview-stage {
      @apply min-h-0 overflow-hidden bg-white;
    }

    .stage-body {
      @apply h-full min-h-0 overflow-hidden rounded-16px border border-stone-200 bg-white;
    }

    .pdf-preview-stack {
      @apply flex h-full min-h-0 flex-col;
    }

    .text-preview-shell {
      @apply h-full bg-white p-4;
    }

    .preview-text {
      @apply m-0 h-full overflow-auto text-[14px] whitespace-pre-wrap break-words text-stone-700;
      font-family: 'SFMono-Regular', 'Menlo', 'Monaco', monospace;
      line-height: 1.68;
    }

    .image-preview-shell {
      @apply flex h-full min-h-0 items-center justify-center overflow-auto bg-white p-4;
    }

    .preview-image {
      @apply max-h-full max-w-full rounded-12px object-contain shadow-sm;
    }

    .download-placeholder {
      @apply flex h-full min-h-[320px] flex-col items-center justify-center gap-5 rounded-12px bg-stone-50 px-8 text-center text-stone-500;
    }

    .placeholder-icon {
      @apply flex h-16 w-16 items-center justify-center rounded-full bg-stone-100 text-stone-700;
    }

    .placeholder-actions {
      @apply flex flex-wrap items-center justify-center gap-3;
    }
  }

  @media (max-width: 960px) {
    height: min(92vh, calc(100vh - 24px));
    min-height: auto;

    .preview-content {
      @apply px-4 pb-4;
    }

    .preview-content .content-wrapper,
    .preview-content .content-wrapper--immersive {
      @apply grid-cols-1;
    }

    .preview-content .insight-rail {
      @apply max-h-[30vh] pr-0;
    }

    .preview-content .preview-stage {
      min-height: 58vh;
    }
  }
}
</style>
