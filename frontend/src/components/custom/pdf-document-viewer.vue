<template>
  <div class="pdf-viewer-shell">
    <div v-if="!embeddedHeader" class="pdf-viewer-toolbar">
      <div class="toolbar-copy">
        <span class="viewer-badge">{{ singlePagePreviewActive ? '单页定位' : 'PDF 预览' }}</span>
        <span class="viewer-kicker">{{ viewerKicker }}</span>
      </div>
      <div class="toolbar-actions">
        <span class="toolbar-chip">
          <template v-if="singlePagePreviewActive">第 {{ displayCurrentPage }} 页</template>
          <template v-else>第 {{ displayCurrentPage }} / {{ totalPages || 1 }} 页</template>
        </span>
        <span class="toolbar-chip">{{ Math.round(zoom * 100) }}%</span>
        <template v-if="!singlePagePreviewActive">
          <NButton size="tiny" quaternary :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">
            <template #icon>
              <icon-mdi-chevron-left />
            </template>
          </NButton>
          <NButton size="tiny" quaternary :disabled="currentPage >= totalPages" @click="goToPage(currentPage + 1)">
            <template #icon>
              <icon-mdi-chevron-right />
            </template>
          </NButton>
        </template>
        <NButton size="tiny" quaternary :disabled="zoom <= minZoom" @click="zoomOut">
          <template #icon>
            <icon-mdi-magnify-minus-outline />
          </template>
        </NButton>
        <NButton size="tiny" quaternary :disabled="zoom >= maxZoom" @click="zoomIn">
          <template #icon>
            <icon-mdi-magnify-plus-outline />
          </template>
        </NButton>
        <NButton size="tiny" secondary @click="resetZoom">适应宽度</NButton>
        <NButton size="tiny" secondary @click="openInNewTab">
          <template #icon>
            <icon-mdi-open-in-new />
          </template>
          新窗口
        </NButton>
      </div>
    </div>

    <div class="pdf-viewer-body" :class="{ 'is-single-page': singlePagePreviewActive }">
      <aside v-if="!singlePagePreviewActive" class="page-sidebar">
        <button
          v-for="page in pageSummaries"
          :key="page.pageNumber"
          type="button"
          class="page-nav-item"
          :class="{
            'is-active': page.pageNumber === currentPage,
            'is-target': page.pageNumber === targetPageNumber
          }"
          @click="goToPage(page.pageNumber)"
        >
          <span class="page-nav-number">P{{ displayPageNumber(page.pageNumber) }}</span>
          <span class="page-nav-summary">{{ page.summary || `第 ${page.pageNumber} 页` }}</span>
        </button>
      </aside>

      <div ref="stageRef" class="page-stage">
        <div v-if="documentLoading" class="stage-feedback">
          <NSpin size="large" />
          <span>正在加载 PDF 文档</span>
        </div>
        <div v-else-if="renderError" class="stage-feedback is-error">
          <icon-mdi-alert-circle class="text-24" />
          <span>{{ renderError }}</span>
        </div>
        <div v-else class="page-scroll-shell">
          <div v-if="!singlePagePreviewActive && !embeddedHeader" class="page-meta-row">
            <span>第 {{ displayCurrentPage }} 页</span>
            <span v-if="currentPage === targetPageNumber">引用定位页</span>
            <span v-else-if="highlightCount > 0">已匹配到相关文本</span>
            <span v-else>浏览当前页</span>
          </div>

          <div ref="pageShellRef" class="pdf-page-shell">
            <canvas ref="canvasRef" class="pdf-canvas" />
            <div v-if="highlightRects.length" class="pdf-highlight-overlay">
              <div
                v-for="(rect, index) in highlightRects"
                :key="`${index}-${rect.left}-${rect.top}`"
                class="pdf-highlight-rect"
                :style="{
                  left: `${rect.left}px`,
                  top: `${rect.top}px`,
                  width: `${rect.width}px`,
                  height: `${rect.height}px`
                }"
              />
            </div>
            <div ref="textLayerRef" class="pdf-text-layer textLayer" />
            <div v-if="pageRendering" class="page-loading-mask">
              <NSpin size="small" />
              <span>正在渲染页面</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch, watchEffect } from 'vue';
import { useResizeObserver } from '@vueuse/core';
import { GlobalWorkerOptions, TextLayer, getDocument } from 'pdfjs-dist';
import type { PDFDocumentLoadingTask, PDFDocumentProxy, RenderTask } from 'pdfjs-dist';
import type { TextItem } from 'pdfjs-dist/types/src/display/api';
import { NButton, NSpin } from 'naive-ui';
import { getAuthorization } from '@/service/request/shared';
import workerSrc from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

GlobalWorkerOptions.workerSrc = workerSrc;

interface Props {
  url: string;
  sourceUrl?: string;
  fileName?: string;
  pageNumber?: number;
  singlePageMode?: boolean;
  sourcePageNumber?: number;
  anchorText?: string;
  searchText?: string;
  visible?: boolean;
  embeddedHeader?: boolean;
}

interface Emits {
  (e: 'toolbar-change', payload: {
    modeLabel: string;
    helperText: string;
    pageLabel: string;
    zoomLabel: string;
    singlePage: boolean;
    canPrev: boolean;
    canNext: boolean;
  }): void;
}

interface PageSummary {
  pageNumber: number;
  summary: string;
}

interface HighlightRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

interface HighlightFragment {
  div: HTMLElement;
}

interface TextLine {
  rect: HighlightRect;
  rawText: string;
  text: string;
  compactText: string;
  elements: HTMLElement[];
  centerY: number;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const minZoom = 0.7;
const maxZoom = 2.2;
const pdfRangeChunkSize = 256 * 1024;
const eagerSummaryRadius = 1;

const pdfDocument = shallowRef<PDFDocumentProxy | null>(null);
const documentLoading = ref(false);
const pageRendering = ref(false);
const renderError = ref('');
const totalPages = ref(0);
const currentPage = ref(1);
const zoom = ref(1);
const highlightCount = ref(0);
const highlightRects = ref<HighlightRect[]>([]);
const pageSummaries = ref<PageSummary[]>([]);

const stageRef = ref<HTMLDivElement | null>(null);
const pageShellRef = ref<HTMLDivElement | null>(null);
const canvasRef = ref<HTMLCanvasElement | null>(null);
const textLayerRef = ref<HTMLDivElement | null>(null);

const targetPageNumber = computed(() => clampPage(props.pageNumber || 1, totalPages.value || 1));
const matchCandidates = computed(() => buildMatchCandidates(props.searchText || props.anchorText || ''));
const singlePagePreviewActive = computed(() => Boolean(props.singlePageMode && props.sourcePageNumber));
const viewerKicker = computed(() => {
  if (singlePagePreviewActive.value) {
    return '当前是定位页快照，支持缩放；整本文档请点“新窗口”查看。';
  }
  return '支持翻页、缩放和新窗口查看原文件。';
});
const displayCurrentPage = computed(() => {
  if (singlePagePreviewActive.value) {
    return props.sourcePageNumber || props.pageNumber || 1;
  }
  return currentPage.value;
});
const embeddedHeader = computed(() => Boolean(props.embeddedHeader));
const toolbarState = computed(() => ({
  modeLabel: singlePagePreviewActive.value ? '单页定位' : 'PDF 预览',
  helperText: viewerKicker.value,
  pageLabel: singlePagePreviewActive.value
    ? `第 ${displayCurrentPage.value} 页`
    : `第 ${displayCurrentPage.value} / ${totalPages.value || 1} 页`,
  zoomLabel: `${Math.round(zoom.value * 100)}%`,
  singlePage: singlePagePreviewActive.value,
  canPrev: currentPage.value > 1,
  canNext: currentPage.value < totalPages.value
}));

let loadingTask: PDFDocumentLoadingTask | null = null;
let renderTask: RenderTask | null = null;
let textLayerTask: TextLayer | null = null;
let lifecycleToken = 0;
let renderTimer: number | null = null;
let queuedRenderVersion = 0;
let activeRenderVersion = 0;
let activeRenderPromise: Promise<void> | null = null;
let rerenderAfterCurrent = false;
let lastObservedStageWidth = 0;
let lastSuccessfulRenderSignature = '';
let summaryLoadTimer: number | null = null;
let activeSummaryPromise: Promise<void> | null = null;
let summaryQueue: number[] = [];
let summaryLoadedPages = new Set<number>();
let summaryLoadingPages = new Set<number>();

watch(
  () => props.url,
  async url => {
    if (!url) return;
    await loadDocument(url);
  },
  { immediate: true }
);

watch(
  () => props.pageNumber,
  pageNumber => {
    if (!pageNumber || !totalPages.value) return;
    currentPage.value = clampPage(pageNumber, totalPages.value);
  }
);

watch(
  () => props.anchorText,
  () => {
    applyHighlight();
  }
);

watch(
  () => props.searchText,
  () => {
    applyHighlight();
  }
);

watch(
  () => props.visible,
  async visible => {
    if (!visible || !pdfDocument.value) return;
    await nextTick();
    void scheduleRender({ immediate: true });
  }
);

watch(currentPage, () => {
  if (!pdfDocument.value) return;
  void scheduleRender({ immediate: true });
  scheduleSummaryLoading(getPrioritySummaryPages(currentPage.value), { prioritize: true });
});

watch(zoom, () => {
  if (!pdfDocument.value) return;
  void scheduleRender({ immediate: true });
});

watchEffect(() => {
  emit('toolbar-change', toolbarState.value);
});

useResizeObserver(stageRef, entries => {
  if (!pdfDocument.value || documentLoading.value || props.visible === false) return;

  const observedWidth = Math.round(entries[0]?.contentRect.width || stageRef.value?.clientWidth || 0);
  if (observedWidth <= 0) return;
  if (Math.abs(observedWidth - lastObservedStageWidth) < 2) return;

  lastObservedStageWidth = observedWidth;
  void scheduleRender({ delay: 120 });
});

onBeforeUnmount(() => {
  lifecycleToken++;
  void cleanupPdfState();
});

function clampPage(page: number, maxPage: number) {
  return Math.min(Math.max(page, 1), Math.max(maxPage, 1));
}

function normalizeForMatch(value: string) {
  return value
    .normalize('NFKC')
    .replace(/[…]/g, '')
    .replace(/[“”"「」『』《》]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

function compactForMatch(value: string) {
  return normalizeForMatch(value).replace(/\s+/g, '');
}

function buildMatchCandidates(value: string | string[]) {
  const rawValues = Array.isArray(value) ? value : value.split(/\n+/);
  const values = rawValues.map(item => item.trim()).filter(Boolean);
  if (!values.length) return [];

  const candidates = new Set<string>();

  values.forEach(item => {
    const normalizedFull = normalizeForMatch(item);
    if (normalizedFull.length >= 6) {
      candidates.add(normalizedFull);
    }

    // 检测URL并完整保留
    const urlPattern = /(https?:\/\/[^\s，,。；;：:!？!?、\n\r]+)/g;
    for (const urlMatch of item.matchAll(urlPattern)) {
      const url = urlMatch[0]?.trim();
      if (url && url.length >= 8) {
        candidates.add(normalizeForMatch(url));
      }
    }

    const normalizedSource = item.normalize('NFKC').replace(/\s+/g, ' ').trim();
    const quotePattern = /[["「『《](.+?)[」」』》]/g;
    for (const match of normalizedSource.matchAll(quotePattern)) {
      const quoted = normalizeForMatch(match[1] || '');
      if (quoted.length >= 4) {
        candidates.add(quoted);
      }
    }

    // 移除引文标记后再分割，使用更智能的分段逻辑
    const withoutCitations = normalizedSource.replace(/(?:\(|（)?来源#\d+:[^)）;；。！？!?]*/g, ' ');
    // 按句号、问号、感叹号分割，保留冒号用于连接
    const segments = withoutCitations
      .split(/[；;，,。！？!?、\n\r]/)
      .map(segment => normalizeForMatch(segment))
      .filter(segment => segment.length >= 6);

    segments
      .sort((left, right) => right.length - left.length)
      .forEach(segment => candidates.add(segment));

    // 添加冒号分割的前后部分作为额外候选
    const colonSegments = item.split(/[：:]/);
    if (colonSegments.length > 1) {
      const beforeColon = normalizeForMatch(colonSegments[0].trim());
      const afterColon = normalizeForMatch(colonSegments.slice(1).join(':').trim());
      if (beforeColon.length >= 6) {
        candidates.add(beforeColon);
      }
      if (afterColon.length >= 6) {
        candidates.add(afterColon);
      }
    }
  });

  return [...candidates];
}

function summarizeText(value: string) {
  return value.replace(/\s+/g, ' ').trim();
}

function buildFallbackSummary(pageNumber: number) {
  return `第 ${pageNumber} 页`;
}

function buildSummaryFromItems(items: unknown[]) {
  return summarizeText(
    items
      .filter((item): item is TextItem => typeof item === 'object' && item !== null && 'str' in item)
      .map(item => item.str)
      .join(' ')
  ).slice(0, 80);
}

function updatePageSummary(pageNumber: number, items?: unknown[]) {
  const summary = items ? buildSummaryFromItems(items) : '';
  pageSummaries.value[pageNumber - 1] = {
    pageNumber,
    summary: summary || buildFallbackSummary(pageNumber)
  };
  summaryLoadedPages.add(pageNumber);
}

function getPrioritySummaryPages(centerPage: number) {
  const pages = new Set<number>([centerPage, targetPageNumber.value]);

  for (let offset = 1; offset <= eagerSummaryRadius; offset += 1) {
    pages.add(centerPage - offset);
    pages.add(centerPage + offset);
  }

  return [...pages].filter(pageNumber => pageNumber >= 1 && pageNumber <= totalPages.value);
}

function scheduleSummaryLoading(pageNumbers: number[], options?: { delay?: number; prioritize?: boolean }) {
  if (!pdfDocument.value) return;

  if (pageNumbers.length) {
    const nextPages = pageNumbers.filter(pageNumber => {
      return !summaryLoadedPages.has(pageNumber) && !summaryLoadingPages.has(pageNumber);
    });

    if (!nextPages.length && !summaryQueue.length) return;

    if (nextPages.length) {
      if (options?.prioritize) {
        summaryQueue = [...nextPages, ...summaryQueue.filter(pageNumber => !nextPages.includes(pageNumber))];
      } else {
        const existingPages = new Set(summaryQueue);
        nextPages.forEach(pageNumber => {
          if (!existingPages.has(pageNumber)) {
            summaryQueue.push(pageNumber);
          }
        });
      }
    }
  }

  if (summaryLoadTimer) {
    window.clearTimeout(summaryLoadTimer);
  }

  summaryLoadTimer = window.setTimeout(() => {
    summaryLoadTimer = null;
    void flushSummaryQueue();
  }, options?.delay ?? 0);
}

async function flushSummaryQueue() {
  if (!pdfDocument.value || activeSummaryPromise || !summaryQueue.length) return;

  const summaryPromise = loadQueuedSummaries();
  activeSummaryPromise = summaryPromise;

  try {
    await summaryPromise;
  } finally {
    if (activeSummaryPromise === summaryPromise) {
      activeSummaryPromise = null;
    }

    if (summaryQueue.length) {
      scheduleSummaryLoading([], { delay: 120 });
    }
  }
}

async function loadQueuedSummaries() {
  const documentProxy = pdfDocument.value;
  if (!documentProxy) return;

  while (summaryQueue.length && pdfDocument.value === documentProxy && props.visible !== false) {
    const pageNumber = summaryQueue.shift();
    if (!pageNumber || summaryLoadedPages.has(pageNumber) || summaryLoadingPages.has(pageNumber)) continue;

    summaryLoadingPages.add(pageNumber);

    try {
      const page = await documentProxy.getPage(pageNumber);
      if (pdfDocument.value !== documentProxy) return;

      const textContent = await page.getTextContent();
      if (pdfDocument.value !== documentProxy) return;

      updatePageSummary(pageNumber, textContent.items);
    } catch (error) {
      pageSummaries.value[pageNumber - 1] = {
        pageNumber,
        summary: buildFallbackSummary(pageNumber)
      };
      summaryLoadedPages.add(pageNumber);
    } finally {
      summaryLoadingPages.delete(pageNumber);
    }
  }
}

function goToPage(page: number) {
  currentPage.value = clampPage(page, totalPages.value || 1);
}

function displayPageNumber(pageNumber: number) {
  if (singlePagePreviewActive.value) {
    return props.sourcePageNumber || props.pageNumber || pageNumber;
  }
  return pageNumber;
}

function zoomIn() {
  zoom.value = Math.min(Number((zoom.value + 0.1).toFixed(2)), maxZoom);
}

function zoomOut() {
  zoom.value = Math.max(Number((zoom.value - 0.1).toFixed(2)), minZoom);
}

function resetZoom() {
  zoom.value = 1;
}

function openInNewTab() {
  const targetUrl = props.sourceUrl || props.url;
  if (!targetUrl) return;

  const page = singlePagePreviewActive.value ? (props.sourcePageNumber || props.pageNumber || 1) : currentPage.value;
  window.open(`${targetUrl}#page=${page}`, '_blank', 'noopener,noreferrer');
}

function goPrevPage() {
  if (currentPage.value <= 1) return;
  goToPage(currentPage.value - 1);
}

function goNextPage() {
  if (currentPage.value >= totalPages.value) return;
  goToPage(currentPage.value + 1);
}

defineExpose({
  zoomIn,
  zoomOut,
  resetZoom,
  goPrevPage,
  goNextPage,
  openInNewTab
});

async function loadDocument(url: string) {
  lifecycleToken += 1;
  const currentToken = lifecycleToken;

  documentLoading.value = true;
  renderError.value = '';
  totalPages.value = 0;
  currentPage.value = 1;
  highlightCount.value = 0;
  highlightRects.value = [];
  pageSummaries.value = [];
  lastObservedStageWidth = 0;
  lastSuccessfulRenderSignature = '';

  await cleanupPdfState();

  try {
    const shouldAttachAuthHeaders = !/^https?:\/\//i.test(url) || url.includes('/api/v1/documents/page-preview');
    const authorization = getAuthorization();
    loadingTask = getDocument({
      url,
      withCredentials: false,
      disableAutoFetch: true,
      disableStream: true,
      rangeChunkSize: pdfRangeChunkSize,
      httpHeaders: shouldAttachAuthHeaders && authorization ? { Authorization: authorization } : undefined
    });

    const documentProxy = await loadingTask.promise;
    if (currentToken !== lifecycleToken) {
      await documentProxy.destroy();
      return;
    }

    pdfDocument.value = documentProxy;
    totalPages.value = documentProxy.numPages;
    currentPage.value = targetPageNumber.value;
    pageSummaries.value = Array.from({ length: documentProxy.numPages }, (_, index) => ({
      pageNumber: index + 1,
      summary: buildFallbackSummary(index + 1)
    }));
    summaryLoadedPages = new Set();
    summaryLoadingPages = new Set();
    summaryQueue = [];
    scheduleSummaryLoading(getPrioritySummaryPages(currentPage.value), { prioritize: true });

    await nextTick();
    await waitForStageReady(currentToken);
    if (currentToken === lifecycleToken) {
      documentLoading.value = false;
    }
    await nextTick();
    await forceRender(currentToken);
  } catch (error) {
    if (currentToken !== lifecycleToken) return;
    console.error('[PDF 预览] 加载失败:', error);
    renderError.value = 'PDF 加载失败，请尝试新窗口打开或重新预览。';
  } finally {
    if (currentToken === lifecycleToken) {
      documentLoading.value = false;
    }
  }
}

async function scheduleRender(options?: { immediate?: boolean; delay?: number }) {
  if (!pdfDocument.value || documentLoading.value || props.visible === false) return;

  queuedRenderVersion += 1;
  const renderVersion = queuedRenderVersion;
  const delay = options?.immediate ? 0 : options?.delay ?? 0;

  if (renderTimer) {
    window.clearTimeout(renderTimer);
  }

  renderTimer = window.setTimeout(() => {
    renderTimer = null;
    void flushRenderQueue(renderVersion);
  }, delay);
}

async function forceRender(expectedToken = lifecycleToken) {
  queuedRenderVersion += 1;
  await flushRenderQueue(queuedRenderVersion, expectedToken);
}

async function flushRenderQueue(renderVersion: number, expectedToken = lifecycleToken) {
  if (!pdfDocument.value || documentLoading.value || props.visible === false) return;
  if (expectedToken !== lifecycleToken) return;

  if (activeRenderPromise) {
    rerenderAfterCurrent = true;
    renderTask?.cancel();
    textLayerTask?.cancel();
    return;
  }

  activeRenderVersion = renderVersion;
  pageRendering.value = true;
  renderError.value = '';

  const renderPromise = renderCurrentPage(expectedToken, renderVersion);
  activeRenderPromise = renderPromise;

  try {
    await renderPromise;
  } finally {
    if (activeRenderPromise === renderPromise) {
      activeRenderPromise = null;
    }

    const shouldRenderAgain =
      rerenderAfterCurrent || (queuedRenderVersion > activeRenderVersion && expectedToken === lifecycleToken);

    rerenderAfterCurrent = false;

    if (shouldRenderAgain && pdfDocument.value && !documentLoading.value) {
      await waitForAnimationFrame();
      await flushRenderQueue(queuedRenderVersion, lifecycleToken);
      return;
    }

    if (expectedToken === lifecycleToken) {
      pageRendering.value = false;
    }
  }
}

async function renderCurrentPage(expectedToken = lifecycleToken, renderVersion = queuedRenderVersion) {
  const documentProxy = pdfDocument.value;
  const canvas = canvasRef.value;
  const textLayer = textLayerRef.value;
  let stage = stageRef.value;

  if (!documentProxy || !canvas || !textLayer || !stage) return;

  highlightCount.value = 0;
  highlightRects.value = [];

  try {
    renderTask?.cancel();
    renderTask = null;
    textLayerTask?.cancel();
    textLayerTask = null;

    const page = await documentProxy.getPage(currentPage.value);
    if (expectedToken !== lifecycleToken) return;

    await waitForStageReady(expectedToken);
    stage = stageRef.value;
    if (!stage || expectedToken !== lifecycleToken) return;

    const baseViewport = page.getViewport({ scale: 1 });
    const availableWidth = Math.max(stage.clientWidth - 72, 320);
    const renderSignature = `${expectedToken}:${currentPage.value}:${zoom.value}:${Math.round(availableWidth)}`;

    if (lastSuccessfulRenderSignature === renderSignature && canvas.width > 0 && canvas.height > 0) {
      highlightCount.value = applyHighlight();
      return;
    }

    const fitScale = availableWidth / baseViewport.width;
    const renderScale = fitScale * zoom.value;
    const viewport = page.getViewport({ scale: renderScale });

    const context = canvas.getContext('2d', { alpha: false });
    if (!context) {
      renderError.value = '无法初始化 PDF 画布。';
      return;
    }

    const devicePixelRatio = window.devicePixelRatio || 1;
    canvas.width = Math.floor(viewport.width * devicePixelRatio);
    canvas.height = Math.floor(viewport.height * devicePixelRatio);
    canvas.style.width = `${viewport.width}px`;
    canvas.style.height = `${viewport.height}px`;

    context.setTransform(1, 0, 0, 1, 0, 0);
    context.clearRect(0, 0, canvas.width, canvas.height);

    textLayer.innerHTML = '';
    textLayer.style.width = `${viewport.width}px`;
    textLayer.style.height = `${viewport.height}px`;

    renderTask = page.render({
      canvas,
      canvasContext: context,
      viewport,
      transform: devicePixelRatio === 1 ? undefined : [devicePixelRatio, 0, 0, devicePixelRatio, 0, 0]
    });

    await renderTask.promise;
    if (expectedToken !== lifecycleToken || renderVersion !== activeRenderVersion) return;

    const textContent = await page.getTextContent({
      includeMarkedContent: true
    });
    updatePageSummary(currentPage.value, textContent.items);

    textLayerTask = new TextLayer({
      textContentSource: textContent,
      container: textLayer,
      viewport
    });

    await textLayerTask.render();
    await nextTick();
    if (expectedToken !== lifecycleToken || renderVersion !== activeRenderVersion) return;
    lastSuccessfulRenderSignature = renderSignature;
    highlightCount.value = applyHighlight();
  } catch (error: any) {
    if (isBenignRenderError(error) || expectedToken !== lifecycleToken || renderVersion !== activeRenderVersion) {
      return;
    }
    console.error('[PDF 预览] 页面渲染失败:', error);
    renderError.value = 'PDF 页面渲染失败，请稍后重试。';
  }
}

function applyHighlight() {
  const textLayer = textLayerRef.value;
  if (!textLayerTask || !textLayer) return 0;

  const textDivs = textLayerTask.textDivs;
  highlightRects.value = [];

  const candidates = matchCandidates.value;
  if (!candidates.length) return 0;

  const directSource = props.searchText || props.anchorText || '';
  const directMatch = resolveDirectClueHighlight(
    textLayer,
    textLayerTask.textDivs,
    textLayerTask.textContentItemsStr,
    directSource
  );
  if (directMatch) {
    highlightRects.value = [directMatch.rect];
    directMatch.firstElement?.scrollIntoView({
      block: 'center',
      behavior: 'smooth'
    });
    return 1;
  }

  const paragraphMatch = resolveParagraphHighlight(textLayer, textLayerTask.textDivs, textLayerTask.textContentItemsStr, candidates);
  if (paragraphMatch) {
    highlightRects.value = [paragraphMatch.rect];
    paragraphMatch.firstElement?.scrollIntoView({
      block: 'center',
      behavior: 'smooth'
    });
    return 1;
  }

  const itemRanges: Array<{ index: number; start: number; end: number }> = [];
  let mergedText = '';

  textLayerTask.textContentItemsStr.forEach((item, index) => {
    const normalized = normalizeForMatch(item);
    if (!normalized) return;

    if (mergedText) {
      mergedText += ' ';
    }

    const start = mergedText.length;
    mergedText += normalized;
    itemRanges.push({
      index,
      start,
      end: mergedText.length
    });
  });

  const matchRange = resolveMatchRange(mergedText, candidates);
  if (!matchRange) return 0;

  const [matchStart, matchEnd] = matchRange;
  let firstMatch: HTMLElement | undefined;
  const matchedFragments: HighlightFragment[] = [];

  itemRanges.forEach(({ index, start, end }) => {
    if (end <= matchStart || start >= matchEnd) return;
    const div = textDivs[index];
    if (!div) return;

    firstMatch ??= div;
    matchedFragments.push({
      div
    });
  });

  highlightRects.value = buildHighlightRects(textLayer, matchedFragments);

  if (firstMatch) {
    firstMatch.scrollIntoView({
      block: 'center',
      behavior: 'smooth'
    });
  }

  return highlightRects.value.length;
}

function resolveDirectClueHighlight(
  container: HTMLElement,
  textDivs: HTMLElement[],
  textItems: string[],
  clueText: string
) {
  const compactClue = compactForMatch(clueText);
  if (compactClue.length < 8) return null;

  const itemRanges: Array<{ index: number; start: number; end: number }> = [];
  let mergedCompactText = '';

  textItems.forEach((item, index) => {
    const compactItem = compactForMatch(item);
    if (!compactItem) return;

    const start = mergedCompactText.length;
    mergedCompactText += compactItem;
    itemRanges.push({
      index,
      start,
      end: mergedCompactText.length
    });
  });

  if (!itemRanges.length || !mergedCompactText) return null;

  let matchStart = mergedCompactText.indexOf(compactClue);
  let matchEnd = matchStart >= 0 ? matchStart + compactClue.length : -1;

  if (matchStart < 0 && compactClue.length >= 20) {
    const prefixLength = Math.min(28, Math.floor(compactClue.length * 0.4));
    const suffixLength = Math.min(28, Math.floor(compactClue.length * 0.4));
    const prefix = compactClue.slice(0, prefixLength);
    const suffix = compactClue.slice(-suffixLength);
    const prefixIndex = mergedCompactText.indexOf(prefix);
    if (prefixIndex >= 0) {
      const suffixIndex = mergedCompactText.indexOf(suffix, prefixIndex + prefix.length);
      if (suffixIndex >= 0) {
        matchStart = prefixIndex;
        matchEnd = suffixIndex + suffix.length;
      }
    }
  }

  if (matchStart < 0 || matchEnd <= matchStart) return null;

  let firstElement: HTMLElement | undefined;
  const matchedFragments: HighlightFragment[] = [];

  itemRanges.forEach(({ index, start, end }) => {
    if (end <= matchStart || start >= matchEnd) return;
    const div = textDivs[index];
    if (!div) return;
    firstElement ??= div;
    matchedFragments.push({ div });
  });

  if (!matchedFragments.length) return null;

  const [rect] = buildHighlightRects(container, matchedFragments);
  if (!rect) return null;

  return {
    rect,
    firstElement
  };
}

function resolveMatchRange(target: string, anchors: string[]): [number, number] | null {
  if (!target || !anchors.length) return null;

  for (const anchor of anchors) {
    if (!anchor) continue;

    const exactMatchIndex = target.indexOf(anchor);
    if (exactMatchIndex >= 0) {
      return [exactMatchIndex, exactMatchIndex + anchor.length];
    }

    const partialAnchor = anchor.slice(0, Math.min(anchor.length, 48)).trim();
    if (partialAnchor.length < 8) continue;

    const partialMatchIndex = target.indexOf(partialAnchor);
    if (partialMatchIndex >= 0) {
      return [partialMatchIndex, partialMatchIndex + partialAnchor.length];
    }
  }

  return resolveFuzzyMatchRange(target, anchors);
}

function resolveParagraphHighlight(
  container: HTMLElement,
  textDivs: HTMLElement[],
  textItems: string[],
  anchors: string[]
) {
  const lines = buildTextLines(container, textDivs, textItems);
  if (!lines.length) return null;

  const normalizedAnchors = anchors
    .map(anchor => ({
      normalized: normalizeForMatch(anchor),
      compact: compactForMatch(anchor)
    }))
    .filter(anchor => anchor.normalized.length >= 6 && anchor.compact.length >= 6)
    .sort((left, right) => right.compact.length - left.compact.length);

  if (!normalizedAnchors.length) return null;
  const primaryAnchor = normalizedAnchors[0];
  const secondaryAnchors = normalizedAnchors.slice(1);

  const maxWindowSize = Math.min(6, lines.length);
  let bestPrimaryMatch: { rect: HighlightRect; firstElement?: HTMLElement; score: number } | null = null;
  let bestFallbackMatch: { rect: HighlightRect; firstElement?: HTMLElement; score: number } | null = null;

  for (let start = 0; start < lines.length; start += 1) {
    let mergedText = '';
    let mergedCompactText = '';
    let left = Number.POSITIVE_INFINITY;
    let right = 0;
    let top = Number.POSITIVE_INFINITY;
    let bottom = 0;
    let firstElement: HTMLElement | undefined;

    for (let end = start; end < Math.min(lines.length, start + maxWindowSize); end += 1) {
      const line = lines[end];
      const previousLine = end > start ? lines[end - 1] : null;
      if (previousLine) {
        const previousBottom = previousLine.rect.top + previousLine.rect.height;
        const verticalGap = line.rect.top - previousBottom;
        const continuityTolerance = Math.max(10, Math.min(previousLine.rect.height, line.rect.height) * 1.2);
        if (verticalGap > continuityTolerance) {
          break;
        }
      }

      if (previousLine && isLikelyListStart(line.rawText) && /[。！？!?]$/.test(previousLine.rawText.trim())) {
        break;
      }

      if (mergedText) {
        mergedText += ' ';
      }
      mergedText += line.text;
      mergedCompactText += line.compactText;

      left = Math.min(left, line.rect.left);
      top = Math.min(top, line.rect.top);
      right = Math.max(right, line.rect.left + line.rect.width);
      bottom = Math.max(bottom, line.rect.top + line.rect.height);
      firstElement ??= line.elements[0];

      if (mergedCompactText.length < 6) continue;

      const primaryScore = scoreAgainstAnchor(mergedText, mergedCompactText, primaryAnchor);
      const secondaryScore = secondaryAnchors.length
        ? scoreParagraphMatch(mergedText, mergedCompactText, secondaryAnchors)
        : 0;
      const score = Math.max(primaryScore * 1.12, secondaryScore);
      if (score <= 0) continue;

      const paddedRect = clampRectToContainer(
        {
          left: left - 6,
          top: top - 5,
          width: right - left + 12,
          height: bottom - top + 24
        },
        container.clientWidth,
        container.clientHeight
      );

      const primaryMinimum = primaryAnchor.compact.length >= 40 ? 0.3 : primaryAnchor.compact.length >= 20 ? 0.38 : 0.5;
      if (primaryScore >= primaryMinimum) {
        if (!bestPrimaryMatch || score > bestPrimaryMatch.score) {
          bestPrimaryMatch = {
            rect: paddedRect,
            firstElement,
            score
          };
        }
      } else if (secondaryScore >= 0.62 && (!bestFallbackMatch || secondaryScore > bestFallbackMatch.score)) {
        bestFallbackMatch = {
          rect: paddedRect,
          firstElement,
          score: secondaryScore
        };
      }

      // 当窗口文本已完整覆盖较长锚点时，避免继续扩大导致误框
      if (primaryAnchor && mergedCompactText.includes(primaryAnchor.compact)) {
        break;
      }

      if (/[。！？!?]$/.test(line.rawText.trim()) && primaryScore >= 0.52) {
        break;
      }
    }
  }

  return bestPrimaryMatch || bestFallbackMatch;
}

function buildTextLines(container: HTMLElement, textDivs: HTMLElement[], textItems: string[]) {
  const containerRect = container.getBoundingClientRect();
  const lines: TextLine[] = [];

  textItems.forEach((item, index) => {
    const div = textDivs[index];
    if (!div) return;

    const rawText = item.normalize('NFKC').replace(/\s+/g, ' ').trim();
    const text = normalizeForMatch(rawText);
    const compactText = text.replace(/\s+/g, '');
    if (!rawText || !text || compactText.length < 2) return;

    const rect = div.getBoundingClientRect();
    if (!rect || rect.width < 2 || rect.height < 2) return;

    const relativeRect: HighlightRect = {
      left: Math.max(0, rect.left - containerRect.left),
      top: Math.max(0, rect.top - containerRect.top),
      width: rect.width,
      height: rect.height
    };
    const centerY = relativeRect.top + relativeRect.height / 2;

    const targetLine = lines.find(line => {
      const tolerance = Math.max(4, Math.min(line.rect.height, relativeRect.height) * 0.65);
      return Math.abs(line.centerY - centerY) <= tolerance;
    });

    if (!targetLine) {
      lines.push({
        rect: { ...relativeRect },
        rawText,
        text,
        compactText,
        elements: [div],
        centerY
      });
      return;
    }

    targetLine.rect.left = Math.min(targetLine.rect.left, relativeRect.left);
    targetLine.rect.top = Math.min(targetLine.rect.top, relativeRect.top);
    targetLine.rect.width = Math.max(
      targetLine.rect.left + targetLine.rect.width,
      relativeRect.left + relativeRect.width
    ) - targetLine.rect.left;
    targetLine.rect.height = Math.max(
      targetLine.rect.top + targetLine.rect.height,
      relativeRect.top + relativeRect.height
    ) - targetLine.rect.top;
    targetLine.centerY = (targetLine.centerY * targetLine.elements.length + centerY) / (targetLine.elements.length + 1);
    targetLine.rawText = `${targetLine.rawText} ${rawText}`;
    targetLine.text = `${targetLine.text} ${text}`;
    targetLine.compactText += compactText;
    targetLine.elements.push(div);
  });

  return lines.sort((left, right) => {
    if (Math.abs(left.rect.top - right.rect.top) > 2) {
      return left.rect.top - right.rect.top;
    }
    return left.rect.left - right.rect.left;
  });
}

function isLikelyListStart(text: string) {
  const value = text.trim();
  if (!value) return false;
  return /^[•·●○▪▸\-–—\d]+[\.\)、\s]?/.test(value);
}

function scoreParagraphMatch(text: string, compactText: string, anchors: Array<{ normalized: string; compact: string }>) {
  let bestScore = 0;
  for (const anchor of anchors) {
    bestScore = Math.max(bestScore, scoreAgainstAnchor(text, compactText, anchor));
  }

  return bestScore;
}

function scoreAgainstAnchor(text: string, compactText: string, anchor: { normalized: string; compact: string }) {
  if (!anchor?.normalized || !anchor.compact || !text || !compactText) return 0;

  const compactAnchor = anchor.compact;
  const normalizedText = normalizeForMatch(text);
  let score = 0;

  if (compactText.includes(compactAnchor)) {
    score = 1;
  } else if (compactAnchor.includes(compactText)) {
    score = Math.max(0.68, compactText.length / compactAnchor.length);
  } else {
    score = calculateDiceCoefficient(compactText, compactAnchor);
  }

  if (compactAnchor.length >= 18) {
    const prefix = compactAnchor.slice(0, Math.min(14, compactAnchor.length));
    if (!compactText.includes(prefix)) {
      score *= 0.7;
    } else {
      score = Math.max(score, 0.62);
    }
  }

  if (/https?:\/\//.test(anchor.normalized) && normalizedText.includes('http')) {
    score = Math.max(score, 0.36);
  }

  return score;
}

function clampRectToContainer(rect: HighlightRect, maxWidth: number, maxHeight: number): HighlightRect {
  const left = Math.max(0, Math.min(rect.left, Math.max(0, maxWidth - 1)));
  const top = Math.max(0, Math.min(rect.top, Math.max(0, maxHeight - 1)));
  const right = Math.max(left + 1, Math.min(rect.left + rect.width, maxWidth));
  const bottom = Math.max(top + 1, Math.min(rect.top + rect.height, maxHeight));

  return {
    left,
    top,
    width: Math.max(1, right - left),
    height: Math.max(1, bottom - top)
  };
}

function resolveFuzzyMatchRange(target: string, anchors: string[]): [number, number] | null {
  const ranges = buildPhraseRanges(target);
  if (!ranges.length) return null;

  interface FuzzyMatchCandidate {
    start: number;
    end: number;
    score: number;
    weightedScore: number;
  }

  const sortedAnchors = [...anchors]
    .filter(anchor => anchor && anchor.length >= 6)
    .sort((left, right) => right.length - left.length);

  let bestMatch: FuzzyMatchCandidate | null = null;

  for (const anchor of sortedAnchors) {
    let bestRangeForAnchor: FuzzyMatchCandidate | null = null;

    for (const range of ranges) {
      const score = calculateDiceCoefficient(anchor, range.text);
      // 降低阈值，提高匹配的容错性
      if (score < 0.12) continue;

      const weightedScore = score * Math.min(anchor.length, range.text.length);
      if (
        !bestRangeForAnchor ||
        weightedScore > bestRangeForAnchor.weightedScore ||
        (weightedScore === bestRangeForAnchor.weightedScore && score > bestRangeForAnchor.score)
      ) {
        bestRangeForAnchor = {
          start: range.start,
          end: range.end,
          score,
          weightedScore
        };
      }
    }

    if (!bestRangeForAnchor) continue;

    // 对于较长的文本，降低匹配分数要求
    if (
      (anchor.length >= 18 && bestRangeForAnchor.score >= 0.15) ||
      (anchor.length >= 12 && bestRangeForAnchor.score >= 0.2) ||
      (anchor.length >= 8 && bestRangeForAnchor.score >= 0.25)
    ) {
      return [bestRangeForAnchor.start, bestRangeForAnchor.end];
    }

    if (
      !bestMatch ||
      bestRangeForAnchor.weightedScore > bestMatch.weightedScore ||
      (bestRangeForAnchor.weightedScore === bestMatch.weightedScore && bestRangeForAnchor.score > bestMatch.score)
    ) {
      bestMatch = bestRangeForAnchor;
    }
  }

  // 返回最佳匹配，即使分数不高（只要有匹配就返回）
  return bestMatch && bestMatch.score >= 0.15
    ? [bestMatch.start, bestMatch.end]
    : null;
}

function buildPhraseRanges(target: string) {
  const ranges: Array<{ start: number; end: number; text: string }> = [];

  // 先提取URL，保留完整URL作为一个range
  const urlPattern = /(https?:\/\/[^\s，,。；;：:!？!?、\n\r]+)/g;
  for (const match of target.matchAll(urlPattern)) {
    const url = match[0]?.trim();
    const start = match.index ?? -1;
    if (!url || start < 0) continue;

    const normalized = normalizeForMatch(url);
    if (normalized.length < 8) continue;

    ranges.push({
      start,
      end: start + url.length,
      text: normalized
    });
  }

  // 使用URL占位符替换，避免被分割
  const urlPlaceholders = target.replace(urlPattern, '__URL__');

  // 然后按标点分割其他文本
  const phrasePattern = /[^，,；;。！？!?]+[，,；;。！？!?]?/g;

  for (const match of urlPlaceholders.matchAll(phrasePattern)) {
    const text = match[0]?.trim();
    const start = match.index ?? -1;
    if (!text || start < 0 || text.includes('__URL__')) continue;

    const normalized = normalizeForMatch(text);
    if (normalized.length < 6) continue;

    ranges.push({
      start,
      end: start + match[0].length,
      text: normalized
    });
  }

  return ranges;
}

function calculateDiceCoefficient(left: string, right: string) {
  const leftBigrams = buildBigrams(left);
  const rightBigrams = buildBigrams(right);
  if (!leftBigrams.size || !rightBigrams.size) return 0;

  let overlap = 0;
  leftBigrams.forEach((count, bigram) => {
    const rightCount = rightBigrams.get(bigram) || 0;
    overlap += Math.min(count, rightCount);
  });

  const leftSize = [...leftBigrams.values()].reduce((sum, count) => sum + count, 0);
  const rightSize = [...rightBigrams.values()].reduce((sum, count) => sum + count, 0);
  return (2 * overlap) / (leftSize + rightSize);
}

function buildBigrams(value: string) {
  const compact = value.replace(/\s+/g, '');
  const bigrams = new Map<string, number>();

  if (compact.length < 2) {
    if (compact) {
      bigrams.set(compact, 1);
    }
    return bigrams;
  }

  for (let index = 0; index < compact.length - 1; index += 1) {
    const bigram = compact.slice(index, index + 2);
    bigrams.set(bigram, (bigrams.get(bigram) || 0) + 1);
  }

  return bigrams;
}

async function waitForStageReady(expectedToken = lifecycleToken) {
  for (let attempt = 0; attempt < 24; attempt += 1) {
    if (expectedToken !== lifecycleToken) return;

    const stage = stageRef.value;
    const shell = pageShellRef.value;
    const hasStableStage =
      Boolean(stage) &&
      Boolean(shell) &&
      stage!.clientWidth > 240 &&
      stage!.clientHeight > 120 &&
      stage!.getClientRects().length > 0;

    if (hasStableStage) {
      return;
    }

    await waitForAnimationFrame();
  }
}

function waitForAnimationFrame() {
  return new Promise<void>(resolve => {
    requestAnimationFrame(() => resolve());
  });
}

function isBenignRenderError(error: unknown) {
  if (!error || typeof error !== 'object') return false;

  const errorName = 'name' in error ? String(error.name) : '';
  const errorMessage = 'message' in error ? String(error.message) : '';
  const benignNames = new Set(['RenderingCancelledException', 'AbortException', 'InvalidStateError']);

  if (benignNames.has(errorName)) {
    return true;
  }

  return /cancelled|canceled|abort/i.test(errorMessage);
}

function buildHighlightRects(container: HTMLElement, matchedFragments: HighlightFragment[]) {
  if (!matchedFragments.length) return [];

  const containerRect = container.getBoundingClientRect();
  const rects = matchedFragments
    .map(({ div }) => {
      const rect = div.getBoundingClientRect();
      if (rect.width < 2 || rect.height < 2) return null;

      const paddingX = Math.max(2, Math.min(8, rect.width * 0.04));
      const paddingY = Math.max(2, Math.min(8, rect.height * 0.22));

      return {
        left: Math.max(0, rect.left - containerRect.left - paddingX),
        top: Math.max(0, rect.top - containerRect.top - paddingY),
        width: rect.width + paddingX * 2,
        height: rect.height + paddingY * 2
      };
    })
    .filter((rect): rect is HighlightRect => Boolean(rect));
  if (!rects.length) return [];

  const left = Math.min(...rects.map(rect => rect.left));
  const top = Math.min(...rects.map(rect => rect.top));
  const right = Math.max(...rects.map(rect => rect.left + rect.width));
  const bottom = Math.max(...rects.map(rect => rect.top + rect.height));

  return [
    clampRectToContainer(
      {
        left,
        top,
        width: right - left,
        height: bottom - top
      },
      container.clientWidth,
      container.clientHeight
    )
  ];
}

async function cleanupPdfState() {
  if (summaryLoadTimer) {
    window.clearTimeout(summaryLoadTimer);
    summaryLoadTimer = null;
  }

  if (renderTimer) {
    window.clearTimeout(renderTimer);
    renderTimer = null;
  }

  queuedRenderVersion = 0;
  activeRenderVersion = 0;
  activeRenderPromise = null;
  rerenderAfterCurrent = false;
  lastObservedStageWidth = 0;
  lastSuccessfulRenderSignature = '';
  activeSummaryPromise = null;
  summaryQueue = [];
  summaryLoadedPages = new Set();
  summaryLoadingPages = new Set();

  renderTask?.cancel();
  renderTask = null;

  textLayerTask?.cancel();
  textLayerTask = null;

  if (loadingTask) {
    await loadingTask.destroy();
    loadingTask = null;
  }

  if (pdfDocument.value) {
    await pdfDocument.value.destroy();
    pdfDocument.value = null;
  }

  if (canvasRef.value) {
    const context = canvasRef.value.getContext('2d');
    context?.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height);
  }

  if (textLayerRef.value) {
    textLayerRef.value.innerHTML = '';
  }

  highlightRects.value = [];
}
</script>

<style scoped lang="scss">
.pdf-viewer-shell {
  @apply flex h-full min-h-0 flex-col bg-white;
}

.pdf-viewer-toolbar {
  @apply flex items-start justify-between gap-3 border-b border-stone-200 bg-white px-3 py-2;
}

.toolbar-copy {
  @apply flex min-w-0 flex-col gap-1;
}

.viewer-kicker {
  @apply text-xs leading-5 text-stone-500;
}

.viewer-badge {
  @apply inline-flex w-fit text-xs font-semibold text-primary;
}

.toolbar-actions {
  @apply flex flex-wrap items-center justify-end gap-2;
}

.toolbar-chip {
  @apply inline-flex items-center text-xs text-stone-500;
}

.pdf-viewer-body {
  @apply grid min-h-0 flex-1 overflow-hidden grid-cols-[230px_minmax(0,1fr)];
}

.pdf-viewer-body.is-single-page {
  @apply block h-full min-h-0;
}

.page-sidebar {
  @apply min-h-0 overflow-y-auto border-r border-stone-200 bg-white p-2;
}

.page-nav-item {
  @apply mb-1 flex w-full flex-col items-start gap-1 rounded-8px px-3 py-3 text-left text-stone-700 transition;
}

.page-nav-item:hover {
  @apply bg-stone-50;
}

.page-nav-item.is-active {
  @apply bg-primary/8 text-primary;
}

.page-nav-item.is-target {
  box-shadow: inset 2px 0 0 rgba(24, 144, 255, 0.75);
}

.page-nav-number {
  @apply text-xs font-semibold opacity-80;
}

.page-nav-summary {
  @apply line-clamp-3 text-xs leading-5;
}

.page-stage {
  @apply relative min-h-0 overflow-auto bg-white p-3;
}

.stage-feedback {
  @apply flex h-full min-h-320px flex-col items-center justify-center gap-3 rounded-12px bg-stone-50 text-stone-500;
}

.stage-feedback.is-error {
  @apply text-red-500;
}

.page-scroll-shell {
  @apply flex min-h-full flex-col items-center gap-3;
}

.page-meta-row {
  @apply mb-2 flex w-full max-w-960px items-center justify-between px-1 text-xs text-stone-500;
}

.pdf-page-shell {
  @apply relative bg-white p-2;
}

.pdf-viewer-body.is-single-page .page-stage {
  @apply h-full min-h-0;
  padding: 10px 12px 12px;
  overflow-x: hidden;
  overflow-y: auto;
}

.pdf-viewer-body.is-single-page .page-scroll-shell {
  @apply min-h-max justify-start gap-0;
}

.pdf-viewer-body.is-single-page .pdf-page-shell {
  @apply p-1;
}

.pdf-canvas {
  @apply relative z-0 block rounded-2xl;
}

.pdf-highlight-overlay {
  @apply pointer-events-none absolute inset-6 z-[5] overflow-hidden rounded-2xl;
}

.pdf-highlight-rect {
  @apply absolute;
  border-radius: 6px;
  background: linear-gradient(
    180deg,
    rgba(64, 169, 255, 0.12) 0%,
    rgba(24, 144, 255, 0.28) 100%
  );
  box-shadow: 0 0 0 1px rgba(24, 144, 255, 0.12);
  opacity: 0.92;
}

.pdf-text-layer {
  @apply absolute inset-6 z-10 overflow-hidden;
}

.pdf-text-layer :deep(span),
.pdf-text-layer :deep(br) {
  color: transparent;
  position: absolute;
  transform-origin: 0 0;
  white-space: pre;
  cursor: text;
  line-height: 1;
  margin: 0;
  padding: 0;
}

.pdf-text-layer :deep(.endOfContent) {
  @apply absolute left-0 top-full block h-px w-px opacity-0;
}

.page-loading-mask {
  @apply absolute inset-2 z-20 flex items-center justify-center gap-2 bg-white/75 text-sm text-stone-500 backdrop-blur-sm;
}

@media (max-width: 960px) {
  .pdf-viewer-toolbar {
    @apply flex-col;
  }

  .toolbar-actions {
    @apply w-full justify-start;
  }

  .pdf-viewer-body {
    @apply grid-cols-1;
  }

  .page-sidebar {
    @apply max-h-42 border-b border-r-0;
  }
}
</style>
