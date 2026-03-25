import process from 'node:process';
import path from 'node:path';
import type { PluginOption } from 'vite';
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons';
import Icons from 'unplugin-icons/vite';
import IconsResolver from 'unplugin-icons/resolver';
import Components from 'unplugin-vue-components/vite';
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers';
import { FileSystemIconLoader } from 'unplugin-icons/loaders';
import AutoImport from 'unplugin-auto-import/vite';

export function setupUnplugin(viteEnv: Env.ImportMeta) {
  const { VITE_ICON_PREFIX, VITE_ICON_LOCAL_PREFIX } = viteEnv;

  const localIconPath = path.join(process.cwd(), 'src/assets/svg-icon');

  /** The name of the local icon collection */
  const collectionName = VITE_ICON_LOCAL_PREFIX.replace(`${VITE_ICON_PREFIX}-`, '');

  const plugins: PluginOption[] = [
    Icons({
      compiler: 'vue3',
      customCollections: {
        [collectionName]: FileSystemIconLoader(localIconPath, svg =>
          svg.replace(/^<svg\s/, '<svg width="1em" height="1em" ')
        )
      },
      scale: 1,
      defaultClass: 'inline-block'
    }),
    // https://github.com/unplugin/unplugin-auto-import
    AutoImport({
      dts: 'src/typings/auto-imports.d.ts',
      imports: [
        'vue',
        'pinia',
        'vue-router',
        {
          dayjs: [['default', 'dayjs']]
        },
        {
          from: 'naive-ui',
          imports: ['TreeOption', 'FormRules'],
          type: true
        },
        {
          from: '~/packages/axios/src',
          imports: ['FlatResponseData'],
          type: true
        },
        {
          from: '@/service/request',
          imports: ['request']
        }
      ],
      dirs: [
        'src/service/api/**/*.ts',
        'src/store/modules/**/*.ts',
        'src/hooks/**/*.ts',
        'src/enum/**/*.ts',
        'src/utils/*.ts',
        'src/constants/*.ts'
      ]
    }),
    Components({
      dts: 'src/typings/components.d.ts',
      types: [{ from: 'vue-router', names: ['RouterLink', 'RouterView'] }],
      resolvers: [
        NaiveUiResolver(),
        IconsResolver({ customCollections: [collectionName], componentPrefix: VITE_ICON_PREFIX })
      ],
      dirs: ['src/components']
    }),
    createSvgIconsPlugin({
      iconDirs: [localIconPath],
      symbolId: `${VITE_ICON_LOCAL_PREFIX}-[dir]-[name]`,
      inject: 'body-last',
      customDomId: '__SVG_ICON_LOCAL__'
    })
  ];

  return plugins;
}
