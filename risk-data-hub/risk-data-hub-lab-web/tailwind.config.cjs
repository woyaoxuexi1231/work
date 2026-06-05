/**
 * Tailwind CSS 配置
 *
 * content 指定需要扫描类名的文件路径，
 * 未在 content 中出现的文件中使用 Tailwind 类不会生效。
 */
module.exports = {
  content: [
    './index.html',
    './src/**/*.{vue,js}'
  ],
  theme: {
    extend: {}
  },
  plugins: []
}
