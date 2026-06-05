<!--
  DataSourceList — 数据源管理页

  功能：
  1. 查看所有已注册的数据源列表（表格）
  2. 注册新数据源（弹窗表单）
  3. 删除数据源（confirm 确认后删除）

  数据来源：
  - listDatasources() → POST /api-datasource-list → 数据源列表
  - registerDatasource(config) → POST /api-datasource-register → 注册
  - removeDatasource(key) → POST /api-datasource-remove → 删除

  操作流程：
  - 页面加载 → onMounted → fetchList() → 显示数据源表格
  - 点击"注册数据源" → 弹窗 → 填表单 → handleRegister() → 刷新列表
  - 点击"删除" → confirm() → handleRemove(key) → 刷新列表
-->
<script setup>
import { ref, onMounted } from 'vue'
import { listDatasources, registerDatasource, removeDatasource } from '@/api/index.js'

// ----- 列表状态 -----
// dsList：数据源对象数组，每个元素包含 key/name/datasourceType/url/online 等
// loading：加载中标志（用于显示加载提示）
// listError：操作失败时的错误信息
const dsList = ref([])
const loading = ref(false)
const listError = ref('')

// ----- 弹窗状态 -----
// showForm：控制注册弹窗的显示/隐藏
// submitting：提交中标志（防止重复提交）
const showForm = ref(false)
const submitting = ref(false)

// 注册表单数据，用户填写后提交到后端
const form = ref({
  key: '',              // 数据源唯一标识，如 trade_oms
  name: '',             // 展示名称，如 交易系统A库
  datasourceType: 'TRADE_OMS',  // 数据源类型：TRADE_OMS / TRADE_BROKER
  url: '',              // JDBC 连接地址
  username: 'root',     // 数据库用户名
  password: '123456'    // 数据库密码
})

// ============================================================
// 生命周期：组件挂载后立即加载数据源列表
// ============================================================
onMounted(() => fetchList())

// ============================================================
// 获取数据源列表
// 从后端拉取所有已注册的数据源，更新 dsList
// ============================================================
async function fetchList() {
  loading.value = true
  listError.value = ''
  try {
    const res = await listDatasources()
    dsList.value = res.data || []
  } catch (e) {
    listError.value = e.message
  } finally {
    loading.value = false
  }
}

// ============================================================
// 打开注册弹窗
// 每次打开时重置表单数据到初始值
// ============================================================
function openForm() {
  form.value = { key: '', name: '', datasourceType: 'TRADE_OMS', url: '', username: 'root', password: '123456' }
  showForm.value = true
}

// ============================================================
// 提交注册新数据源
// 调用后端接口注册 → 关闭弹窗 → 刷新列表
// ============================================================
async function handleRegister() {
  // 按钮已经用 disabled 防呆了，这里保留函数体完整性
  if (!form.value.key || !form.value.name) {
    return
  }

  submitting.value = true
  try {
    await registerDatasource(form.value)
    showForm.value = false    // 关闭弹窗
    await fetchList()          // 刷新列表显示新数据源
  } catch (e) {
    listError.value = '注册失败: ' + e.message
  } finally {
    submitting.value = false
  }
}

// ============================================================
// 删除数据源
// 先用 confirm() 让用户确认，确认后再调后端接口删除
// ============================================================
async function handleRemove(key, name) {
  if (!confirm(`确定删除数据源「${name}」( ${key} ) ？`)) {
    return
  }
  try {
    await removeDatasource(key)
    await fetchList()  // 删除后刷新列表
  } catch (e) {
    listError.value = '删除失败: ' + e.message
  }
}

// ============================================================
// 辅助函数：数据源类型对应的样式和中文标签
// ============================================================

// 根据类型返回 Tailwind 样式类（不同颜色区分类型）
function typeStyle(type) {
  const map = {
    TRADE_OMS: 'bg-indigo-50 text-indigo-700',
    TRADE_BROKER: 'bg-emerald-50 text-emerald-700',
    HUB: 'bg-slate-50 text-slate-600'
  }
  return map[type] || 'bg-slate-50 text-slate-600'
}

// 根据类型返回中文标签
function typeLabel(type) {
  const map = {
    TRADE_OMS: 'OMS',
    TRADE_BROKER: 'Broker',
    HUB: '中台'
  }
  return map[type] || type
}
</script>

<template>
  <div class="max-w-6xl mx-auto animate-fade-in">

    <!-- ============================================================
         页面标题 + 操作按钮
         ============================================================ -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h2 class="text-xl font-semibold text-slate-800">数据源管理</h2>
        <p class="text-sm text-slate-400 mt-1">共 {{ dsList.length }} 个数据源</p>
      </div>
      <button
        @click="openForm"
        class="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors"
      >
        + 注册数据源
      </button>
    </div>

    <!-- ============================================================
         错误提示
         注册/删除操作失败时显示，可手动关闭
         ============================================================ -->
    <div
      v-if="listError"
      class="mb-4 bg-red-50 border border-red-200 text-red-700 rounded-xl px-5 py-3 text-sm"
    >
      {{ listError }}
      <button @click="listError = ''" class="ml-3 underline">关闭</button>
    </div>

    <!-- ============================================================
         数据源列表（卡片容器）
         三种状态：空状态/加载中/表格
         ============================================================ -->
    <div class="bg-white rounded-xl border border-slate-200 overflow-hidden">
      <!-- 空状态：没有数据源时显示 -->
      <div v-if="!loading && dsList.length === 0" class="py-16 text-center text-slate-400 text-sm">
        暂无数据源，点击右上角「注册数据源」添加
      </div>

      <!-- 加载中状态 -->
      <div v-if="loading" class="py-16 text-center text-slate-400 text-sm">加载中...</div>

      <!-- 数据源表格 -->
      <table v-if="!loading && dsList.length > 0" class="w-full text-sm">
        <!-- 表格头 -->
        <thead>
          <tr class="border-b border-slate-200 bg-slate-50 text-slate-400 text-xs uppercase tracking-wider">
            <th class="text-left py-3.5 px-5 font-medium">标识</th>
            <th class="text-left py-3.5 px-5 font-medium">名称</th>
            <th class="text-left py-3.5 px-5 font-medium">类型</th>
            <th class="text-left py-3.5 px-5 font-medium">连接地址</th>
            <th class="text-left py-3.5 px-5 font-medium">状态</th>
            <th class="text-right py-3.5 px-5 font-medium">操作</th>
          </tr>
        </thead>
        <!-- 表格体：遍历 dsList 渲染每一行 -->
        <tbody>
          <tr
            v-for="ds in dsList"
            :key="ds.key"
            class="border-b border-slate-100 hover:bg-slate-50 transition-colors"
          >
            <!-- 标识 -->
            <td class="py-4 px-5 font-mono text-xs text-slate-800 font-medium">{{ ds.key }}</td>
            <!-- 名称 -->
            <td class="py-4 px-5 text-slate-700">{{ ds.name }}</td>
            <!-- 类型（带颜色标签） -->
            <td class="py-4 px-5">
              <span class="px-2.5 py-1 rounded-md text-xs font-medium" :class="typeStyle(ds.datasourceType)">
                {{ typeLabel(ds.datasourceType) }}
              </span>
            </td>
            <!-- 连接地址（截断显示，鼠标可查看完整） -->
            <td class="py-4 px-5 text-xs text-slate-400 font-mono max-w-xs truncate">{{ ds.url }}</td>
            <!-- 在线状态：绿色圆点=在线，红色=离线 -->
            <td class="py-4 px-5">
              <span
                class="inline-block w-2 h-2 rounded-full"
                :class="ds.online ? 'bg-emerald-500' : 'bg-red-400'"
              ></span>
              <span class="ml-1.5 text-xs text-slate-500">{{ ds.online ? '在线' : '离线' }}</span>
            </td>
            <!-- 操作：删除按钮 -->
            <td class="py-4 px-5 text-right">
              <button
                @click="handleRemove(ds.key, ds.name)"
                class="text-xs text-red-500 hover:text-red-700 font-medium"
              >
                删除
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- ============================================================
         注册弹窗
         用 CSS 遮罩层模拟弹窗（不是 Element UI 的 dialog，手写的）
         点击遮罩层可关闭，点击弹窗内部不会关闭
         ============================================================ -->
    <div
      v-if="showForm"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="showForm = false"
    >
      <div class="bg-white rounded-2xl w-full max-w-lg mx-4 p-6 shadow-2xl">
        <h3 class="text-lg font-semibold text-slate-800 mb-5">注册数据源</h3>

        <!-- 表单字段 -->
        <div class="space-y-4">
          <!-- 标识（必填） -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">标识 *</label>
            <input
              v-model="form.key"
              placeholder="唯一标识，如 trade_oms"
              class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>

          <!-- 名称（必填） -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">名称 *</label>
            <input
              v-model="form.name"
              placeholder="显示名称，如 交易系统A库"
              class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>

          <!-- 类型（下拉选择） -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">类型</label>
            <select
              v-model="form.datasourceType"
              class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            >
              <option value="TRADE_OMS">OMS 交易系统</option>
              <option value="TRADE_BROKER">Broker 券商系统</option>
            </select>
          </div>

          <!-- 连接地址 -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">连接地址</label>
            <input
              v-model="form.url"
              placeholder="jdbc:mysql://host:3306/db"
              class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent font-mono"
            />
          </div>

          <!-- 用户名 / 密码（同行两列） -->
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-slate-700 mb-1">用户名</label>
              <input
                v-model="form.username"
                class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-slate-700 mb-1">密码</label>
              <input
                v-model="form.password"
                type="password"
                class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
              />
            </div>
          </div>
        </div>

        <!-- 底部按钮：取消 / 注册 -->
        <div class="flex justify-end gap-3 mt-6 pt-4 border-t border-slate-100">
          <button
            @click="showForm = false"
            class="px-5 py-2 text-sm text-slate-600 hover:text-slate-800 font-medium"
          >
            取消
          </button>
          <button
            @click="handleRegister"
            :disabled="!form.key || !form.name || submitting"
            class="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            {{ submitting ? '提交中...' : '注册' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
