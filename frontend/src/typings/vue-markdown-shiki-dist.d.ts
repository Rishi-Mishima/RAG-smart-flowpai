declare module 'vue-markdown-shiki/dist/index.mjs' {
  import type { App, DefineComponent, Plugin } from 'vue';

  const VueMarkdownIt: DefineComponent<Record<string, unknown>, {}, any>;
  const VueMarkdownItProvider: DefineComponent<Record<string, unknown>, {}, any>;

  const markdownPlugin: Plugin & {
    install: (app: App) => void;
  };

  export { VueMarkdownIt, VueMarkdownItProvider };
  export default markdownPlugin;
}
