<!--
  DataSourceList — 数据源管理页
  功能：查看数据源列表、注册新数据源、删除数据源
-->
<script setup>
import { ref, onMounted } from 'vue'
import { listDatasources, registerDatasource, removeDatasource } from '@/api/index.js'

// ===== 列表状态 =====
const dsList = ref([])
const loading = ref(false)
const listError = ref('')

// ===== 弹窗状态 =====
const showForm = ref(false)
const submitting = ref(false)

// 注册表单数据
const form = ref({
  key: '',
  name: '',
  datasourceType: 'TRADE_OMS',
  url: '',
  username: 'root',
  password: '123456'
})

// ===== 初始化 =====
onMounted(() => fetchList())

// ===== 获取数据源列表 =====
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

// ===== 打开注册弹窗 =====
function openForm() {
  form.value = { key: '', name: '', datasourceType: 'TRADE_OMS', url: '', username: 'root', password: '123456' }
  showForm.value = true
}

// ===== 提交注册 =====
async function handleRegister() {
  // 简单校验
  if (!form.value.key || !form.value.name) {
    return // 按钮 disabled 已经防呆，这里留空
  }

  submitting.value = true
  try {
    await registerDatasource(form.value)
    showForm.value = false
    await fetchList()
  } catch (e) {
    listError.value = '注册失败: ' + e.message
  } finally {
    submitting.value = false
  }
}

// ===== 删除数据源 =====
async function handleRemove(key, name) {
  if (!confirm(`确定删除数据源「${name}」( ${key} ) ？`)) {
    return
  }
  try {
    await removeDatasource(key)
    await fetchList()
  } catch (e) {
    listError.value = '删除失败: ' + e.message
  }
}

// ===== 数据源类型标签样式 =====
function typeStyle(type) {
  const map = {
    TRADE_OMS: 'bg-indigo-50 text-indigo-700',
    TRADE_BROKER: 'bg-emerald-50 text-emerald-700',
    HUB: 'bg-slate-50 text-slate-600'
  }
  return map[type] || 'bg-slate-50 text-slate-600'
}

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
    <!-- ===== 页面标题 + 操作按钮 ===== -->
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

    <!-- ===== 错误提示 ===== -->
    <div
      v-if="listError"
      class="mb-4 bg-red-50 border border-red-200 text-red-700 rounded-xl px-5 py-3 text-sm"
    >
      {{ listError }}
      <button @click="listError = ''" class="ml-3 underline">关闭</button>
    </div>

    <!-- ===== 数据源列表 ===== -->
    <div class="bg-white rounded-xl border border-slate-200 overflow-hidden">
      <!-- 空状态 -->
      <div v-if="!loading && dsList.length === 0" class="py-16 text-center text-slate-400 text-sm">
        暂无数据源，点击右上角「注册数据源」添加
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="py-16 text-center text-slate-400 text-sm">加载中...</div>

      <!-- 表格 -->
      <table v-if="!loading && dsList.length > 0" class="w-full text-sm">
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
        <tbody>
          <tr
            v-for="ds in dsList"
            :key="ds.key"
            class="border-b border-slate-100 hover:bg-slate-50 transition-colors"
          >
            <td class="py-4 px-5 font-mono text-xs text-slate-800 font-medium">{{ ds.key }}</td>
            <td class="py-4 px-5 text-slate-700">{{ ds.name }}</td>
            <td class="py-4 px-5">
              <span class="px-2.5 py-1 rounded-md text-xs font-medium" :class="typeStyle(ds.datasourceType)">
                {{ typeLabel(ds.datasourceType) }}
              </span>
            </td>
            <td class="py-4 px-5 text-xs text-slate-400 font-mono max-w-xs truncate">{{ ds.url }}</td>
            <td class="py-4 px-5">
              <span
                class="inline-block w-2 h-2 rounded-full"
                :class="ds.online ? 'bg-emerald-500' : 'bg-red-400'"
              ></span>
              <span class="ml-1.5 text-xs text-slate-500">{{ ds.online ? '在线' : '离线' }}</span>
            </td>
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

    <!-- ===== 注册弹窗（用遮罩层模拟） ===== -->
    <div
      v-if="showForm"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="showForm = false"
    >
      <div class="bg-white rounded-2xl w-full max-w-lg mx-4 p-6 shadow-2xl">
        <h3 class="text-lg font-semibold text-slate-800 mb-5">注册数据源</h3>

        <!-- 表单 -->
        <div class="space-y-4">
          <!-- 标识 -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">标识 *</label>
            <input
              v-model="form.key"
              placeholder="唯一标识，如 trade_oms"
              class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>

          <!-- 名称 -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">名称 *</label>
            <input
              v-model="form.name"
              placeholder="显示名称，如 交易系统A库"
              class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>

          <!-- 类型 -->
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

          <!-- 用户名 / 密码 -->
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

        <!-- 按钮 -->
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
