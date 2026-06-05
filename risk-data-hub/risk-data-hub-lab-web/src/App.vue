<template>
  <!--
  根组件
  Vue 3 应用的入口组件。所有页面都通过 <router-view> 显示。
  MainLayout.vue 是布局容器（侧边栏 + 内容区），
  各个页面在 MainLayout 内部通过子路由切换。

  逻辑链路：App.vue → router-view → MainLayout.vue → router-view → 各个页面
  -->
  <router-view />
</template>
