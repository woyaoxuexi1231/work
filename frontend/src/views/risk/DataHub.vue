<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { post } from '../../api/request.js'
import { ElMessage } from 'element-plus'

const activeTab = ref('overview')
const overview = ref(null)
const loading = ref(false)
let fetching = false
let refreshTimer = null

// 数据源相关
const datasourceList = ref([])
const datasourceLoading = ref(false)
const datasourceDialogVisible = ref(false)
const datasourceForm = ref({
  key: '',
  name: '',
  datasourceType: 'MYSQL',
  upstreamType: '',
  url: '',
  username: '',
  password: '',
  poolSize: 10
})

// 同步相关
const syncDialogVisible = ref(false)
const syncLoading = ref(false)
const syncForm = ref({
  dataSourceKey: '',
  pageSize: 100
})
const syncTask = ref(null)
const initTask = ref(null)
const syncTaskLoading = ref(false)
const initTaskLoading = ref(false)

onMounted(async () => {
  await fetchOverview()
  await fetchDatasources()
  await fetchSyncTask()
  await fetchInitTask()
  
  // 启动定时刷新 - 任务状态每 3 秒刷新一次
  startTaskRefresh()
})

onUnmounted(() => {
  stopTaskRefresh()
})

// 定时刷新任务状态
function startTaskRefresh() {
  if (refreshTimer) return
  refreshTimer = setInterval(async () => {
    if (activeTab.value === 'sync') {
      await fetchSyncTask(true)
    } else if (activeTab.value === 'init') {
      await fetchInitTask(true)
    }
  }, 3000)  // 每 3 秒刷新
}

function stopTaskRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

async function fetchOverview() {
  if (fetching) return
  fetching = true
  loading.value = true
  try {
    const res = await post('/risk-api/api/hub/overview')
    if (res._httpError) {
      ElMessage.error(res.message || '网关错误')
      return
    }
    if (res.code === 200) {
      overview.value = res.data
      // 显示详细状态
      if (res.status) {
        console.log('[Risk Hub] 总览已刷新:', res.status)
      }
    } else {
      ElMessage.error((res.message || '获取概览失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    loading.value = false
    fetching = false
  }
}

async function fetchDatasources() {
  datasourceLoading.value = true
  try {
    const res = await post('/risk-api/api/datasource/list')
    if (res._httpError) {
      ElMessage.error(res.message || '网关错误')
      return
    }
    if (res.code === 200) {
      datasourceList.value = res.data || []
    } else {
      ElMessage.error((res.message || '获取数据源失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    datasourceLoading.value = false
  }
}

async function fetchSyncTask(silent = false) {
  syncTaskLoading.value = true
  try {
    const res = await post('/risk-api/api/hub/sync-task')
    if (res._httpError) {
      if (!silent) ElMessage.error(res.message || '网关错误')
      return
    }
    if (res.code === 200) {
      const oldStatus = syncTask.value?.status
      syncTask.value = res.data
      // 状态变化时提示
      if (!silent && oldStatus && oldStatus !== res.data?.status) {
        ElMessage.info('同步任务状态: ' + (res.data?.status || '未知'))
      }
    } else {
      if (!silent) ElMessage.error((res.message || '获取同步任务失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    // 静默失败
  } finally {
    syncTaskLoading.value = false
  }
}

async function fetchInitTask(silent = false) {
  initTaskLoading.value = true
  try {
    const res = await post('/risk-api/api/hub/init-task')
    if (res._httpError) {
      if (!silent) ElMessage.error(res.message || '网关错误')
      return
    }
    if (res.code === 200) {
      const oldStatus = initTask.value?.status
      const oldProgress = initTask.value?.progress
      initTask.value = res.data
      // 进度变化时静默更新，但完成后提示
      if (!silent && oldStatus && oldStatus !== res.data?.status) {
        ElMessage.info('初始化任务状态: ' + (res.data?.status || '未知'))
      }
      // 进度变化提示
      if (!silent && oldProgress !== undefined && res.data?.progress !== oldProgress) {
        console.log('[Risk Hub] 初始化进度:', res.data?.progress + '%')
      }
    } else {
      if (!silent) ElMessage.error((res.message || '获取初始化任务失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    // 静默失败
  } finally {
    initTaskLoading.value = false
  }
}

async function registerDatasource() {
  if (!datasourceForm.value.key || !datasourceForm.value.name || !datasourceForm.value.upstreamType) {
    ElMessage.warning('请填写完整信息（数据源标识、名称、上游类型为必填）')
    return
  }
  syncLoading.value = true
  try {
    const res = await post('/risk-api/api/datasource/register', datasourceForm.value)
    if (res.code === 200) {
      ElMessage.success((res.message || '数据源注册成功') + ' [' + res.status + ']')
      datasourceDialogVisible.value = false
      await fetchDatasources()
    } else {
      ElMessage.error((res.message || '注册失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    syncLoading.value = false
  }
}

async function removeDatasource(key) {
  try {
    const res = await post('/risk-api/api/datasource/remove', { key })
    if (res.code === 200) {
      ElMessage.success((res.message || '删除成功') + ' [' + res.status + ']')
      await fetchDatasources()
    } else {
      ElMessage.error((res.message || '删除失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

async function startSync() {
  if (!syncForm.value.dataSourceKey) {
    ElMessage.warning('请选择数据源')
    return
  }
  syncLoading.value = true
  try {
    const res = await post('/risk-api/api/hub/sync', {
      dataSourceKey: syncForm.value.dataSourceKey,
      pageSize: syncForm.value.pageSize
    })
    if (res.code === 200) {
      ElMessage.success((res.message || '同步任务已启动') + ' [' + res.status + ']')
      syncDialogVisible.value = false
      await fetchSyncTask()
    } else {
      ElMessage.error((res.message || '启动失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    syncLoading.value = false
  }
}

async function startInitData() {
  syncLoading.value = true
  try {
    const res = await post('/risk-api/api/hub/init-data')
    if (res.code === 200) {
      ElMessage.success((res.message || '初始化任务已启动') + ' [' + res.status + ']')
      await fetchInitTask()
    } else {
      ElMessage.error((res.message || '启动失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    syncLoading.value = false
  }
}

const businessTableCount = computed(() => {
  if (!overview.value) return 0
  const business = overview.value.businessTableStats || {}
  return Object.keys(business.trade_oms || {}).length + 
         Object.keys(business.trade_broker || {}).length
})

const datasourceTypeMap = {
  'MYSQL': { text: 'MySQL', type: 'primary' },
  'POSTGRESQL': { text: 'PostgreSQL', type: 'success' },
  'ORACLE': { text: 'Oracle', type: 'warning' },
  'SQLSERVER': { text: 'SQL Server', type: 'danger' }
}

const upstreamTypeMap = {
  'TRADE_OMS': { text: '交易 OMS 系统', type: 'primary' },
  'TRADE_BROKER': { text: '券商 Broker 系统', type: 'success' },
  'CUSTOM': { text: '自定义上游', type: 'info' }
}

const taskStatusType = (status) => {
  const map = {
    'PENDING': 'info',
    'RUNNING': 'primary',
    'COMPLETED': 'success',
    'FAILED': 'danger'
  }
  return map[status] || 'info'
}

const taskStatusText = (status) => {
  const map = {
    'PENDING': '等待中',
    'RUNNING': '运行中',
    'COMPLETED': '已完成',
    'FAILED': '失败'
  }
  return map[status] || status || '未知'
}
</script>

<template>
  <div class="data-hub">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>Risk Hub</el-breadcrumb-item>
      <el-breadcrumb-item>数据中台</el-breadcrumb-item>
    </el-breadcrumb>

    <el-tabs v-model="activeTab" class="hub-tabs">
      <!-- 总览 -->
      <el-tab-pane label="数据总览" name="overview">
        <el-card class="content-card">
          <template #header>
            <div class="card-header">
              <span>数据中台同步实验室</span>
              <el-button type="primary" @click="fetchOverview">刷新</el-button>
            </div>
          </template>

          <el-row :gutter="20" class="stats-row">
            <el-col :xs="12" :sm="6">
              <el-statistic title="数据源数量" :value="overview?.datasourceCount || 0">
                <template #prefix>
                  <el-icon color="#409EFF"><Connection /></el-icon>
                </template>
              </el-statistic>
            </el-col>
            <el-col :xs="12" :sm="6">
              <el-statistic title="上游库数量" :value="(overview?.topology?.upstreams || []).length">
                <template #prefix>
                  <el-icon color="#67c23a"><Database /></el-icon>
                </template>
              </el-statistic>
            </el-col>
            <el-col :xs="12" :sm="6">
              <el-statistic title="业务表数量" :value="businessTableCount">
                <template #prefix>
                  <el-icon color="#f56c6c"><Grid /></el-icon>
                </template>
              </el-statistic>
            </el-col>
          </el-row>

          <el-divider />

          <!-- 上游拓扑 -->
          <div class="detail-section">
            <h4>上游拓扑</h4>
            <el-table 
              v-if="overview?.topology?.upstreams?.length" 
              :data="overview.topology.upstreams" 
              stripe
              style="width: 100%; margin-top: 12px"
            >
              <el-table-column prop="key" label="标识" width="180" />
              <el-table-column label="类型" width="180">
                <template #default="{ row }">
                  <el-tag :type="upstreamTypeMap[row.type]?.type || 'info'" size="small">
                    {{ upstreamTypeMap[row.type]?.text || row.type }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="同步表" min-width="300">
                <template #default="{ row }">
                  <el-tag 
                    v-for="table in (row.syncTables || [])" 
                    :key="table" 
                    size="small" 
                    style="margin-right: 4px; margin-bottom: 2px;"
                  >
                    {{ table }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无上游拓扑数据" />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 数据源管理 -->
      <el-tab-pane label="数据源管理" name="datasource">
        <el-card class="content-card">
          <template #header>
            <div class="card-header">
              <span>数据源配置</span>
              <el-button type="primary" @click="datasourceDialogVisible = true">注册数据源</el-button>
            </div>
          </template>

          <el-table v-loading="datasourceLoading" :data="datasourceList" stripe>
            <el-table-column prop="key" label="标识" width="150" />
            <el-table-column prop="name" label="名称" min-width="150" />
            <el-table-column label="类型" width="120">
              <template #default="{ row }">
                <el-tag :type="datasourceTypeMap[row.datasourceType]?.type || 'info'" size="small">
                  {{ datasourceTypeMap[row.datasourceType]?.text || row.datasourceType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="url" label="连接地址" min-width="300" show-overflow-tooltip />
            <el-table-column label="连接池" width="100">
              <template #default="{ row }">
                <span>{{ row.poolSize || 10 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.active ? 'success' : 'info'" size="small">
                  {{ row.active ? '活跃' : '空闲' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="syncForm.dataSourceKey = row.key; syncDialogVisible = true">同步</el-button>
                <el-button link type="danger" size="small" @click="removeDatasource(row.key)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!datasourceLoading && datasourceList.length === 0" description="暂无数据源，点击右上角注册" />
        </el-card>
      </el-tab-pane>

      <!-- 同步任务 (带自动刷新) -->
      <el-tab-pane label="同步任务" name="sync">
        <el-card class="content-card">
          <template #header>
            <div class="card-header">
              <span>同步任务管理</span>
              <div>
                <el-button @click="fetchSyncTask()" :loading="syncTaskLoading">刷新状态</el-button>
                <el-button type="primary" @click="syncDialogVisible = true">发起同步</el-button>
              </div>
            </div>
          </template>

          <!-- 实时状态提示 -->
          <el-alert
            v-if="syncTask?.status === 'RUNNING'"
            title="同步任务运行中..."
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 16px"
          >
            <template #default>
              <span>状态每 3 秒自动刷新</span>
            </template>
          </el-alert>

          <!-- 当前同步任务 -->
          <div class="task-section">
            <div class="task-header">
              <h4>同步任务</h4>
              <el-tag v-if="syncTask?.status" :type="taskStatusType(syncTask.status)" size="small">
                {{ taskStatusText(syncTask.status) }}
              </el-tag>
            </div>
            <el-descriptions v-if="syncTask" :column="3" border style="margin-top: 12px">
              <el-descriptions-item label="任务ID">{{ syncTask.taskId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="taskStatusType(syncTask.status)" size="small">
                  {{ taskStatusText(syncTask.status) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="进度">
                <el-progress 
                  :percentage="syncTask.progress || 0" 
                  :status="syncTask.status === 'SUCCESS' ? 'success' : undefined"
                  style="width: 100px"
                />
              </el-descriptions-item>
              <el-descriptions-item label="数据源">{{ syncTask.dataSourceKey || '-' }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ syncTask.startedAt || '-' }}</el-descriptions-item>
              <el-descriptions-item label="结束时间">{{ syncTask.finishedAt || '-' }}</el-descriptions-item>
              <el-descriptions-item label="拉取条数">{{ syncTask.totalPulledCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="落库条数">{{ syncTask.totalSavedCount || 0 }}</el-descriptions-item>
            </el-descriptions>
            <el-empty v-else description="暂无同步任务" />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 初始化任务 (带自动刷新) -->
      <el-tab-pane label="初始化任务" name="init">
        <el-card class="content-card">
          <template #header>
            <div class="card-header">
              <span>初始化任务管理</span>
              <div>
                <el-button @click="fetchInitTask()" :loading="initTaskLoading">刷新状态</el-button>
                <el-button type="success" @click="startInitData" :loading="syncLoading">初始化数据</el-button>
              </div>
            </div>
          </template>

          <!-- 实时状态提示 -->
          <el-alert
            v-if="initTask?.status === 'RUNNING'"
            title="初始化任务运行中..."
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 16px"
          >
            <template #default>
              <span>状态每 3 秒自动刷新</span>
            </template>
          </el-alert>

          <!-- 初始化任务 -->
          <div class="task-section">
            <div class="task-header">
              <h4>初始化任务</h4>
              <el-tag v-if="initTask?.status" :type="taskStatusType(initTask.status)" size="small">
                {{ taskStatusText(initTask.status) }}
              </el-tag>
            </div>
            <el-descriptions v-if="initTask" :column="3" border style="margin-top: 12px">
              <el-descriptions-item label="任务ID">{{ initTask.taskId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="taskStatusType(initTask.status)" size="small">
                  {{ taskStatusText(initTask.status) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="进度">
                <el-progress 
                  :percentage="initTask.progress || 0" 
                  :status="initTask.status === 'COMPLETED' ? 'success' : undefined"
                  style="width: 100px"
                />
              </el-descriptions-item>
              <el-descriptions-item label="开始时间" :span="2">{{ initTask.startTime || '-' }}</el-descriptions-item>
            </el-descriptions>
            <el-empty v-else description="暂无初始化任务" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 数据源对话框 -->
    <el-dialog v-model="datasourceDialogVisible" title="注册数据源" width="600px">
      <el-form :model="datasourceForm" label-width="100px">
        <el-form-item label="数据源标识" required>
          <el-input v-model="datasourceForm.key" placeholder="唯一标识，如 mysql_prod" />
        </el-form-item>
        <el-form-item label="数据源名称" required>
          <el-input v-model="datasourceForm.name" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="数据库类型" required>
          <el-select v-model="datasourceForm.datasourceType" style="width: 100%">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="PostgreSQL" value="POSTGRESQL" />
            <el-option label="Oracle" value="ORACLE" />
            <el-option label="SQL Server" value="SQLSERVER" />
          </el-select>
        </el-form-item>
        <el-form-item label="上游类型" required>
          <el-select v-model="datasourceForm.upstreamType" placeholder="请选择上游系统类型" style="width: 100%">
            <el-option label="交易 OMS 系统" value="TRADE_OMS" />
            <el-option label="券商 Broker 系统" value="TRADE_BROKER" />
            <el-option label="自定义上游" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接地址" required>
          <el-input v-model="datasourceForm.url" placeholder="jdbc:mysql://host:port/database" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="datasourceForm.username" placeholder="数据库用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="datasourceForm.password" type="password" placeholder="数据库密码" show-password />
        </el-form-item>
        <el-form-item label="连接池大小">
          <el-input-number v-model="datasourceForm.poolSize" :min="1" :max="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasourceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="syncLoading" @click="registerDatasource">注册</el-button>
      </template>
    </el-dialog>

    <!-- 同步对话框 -->
    <el-dialog v-model="syncDialogVisible" title="发起数据同步" width="500px">
      <el-form label-width="100px">
        <el-form-item label="选择数据源" required>
          <el-select v-model="syncForm.dataSourceKey" placeholder="请选择数据源" style="width: 100%">
            <el-option v-for="ds in datasourceList" :key="ds.key" :label="ds.name" :value="ds.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="每页条数">
          <el-input-number v-model="syncForm.pageSize" :min="10" :max="10000" :step="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="syncDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="syncLoading" @click="startSync">开始同步</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.data-hub {
  max-width: 1400px;
  margin: 0 auto;
}
.hub-tabs {
  margin-top: 16px;
}
.content-card {
  border-radius: 12px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stats-row {
  padding: 10px 0;
}
.detail-section {
  margin-top: 20px;
}
.detail-section h4 {
  margin: 0;
  color: #303133;
  font-size: 16px;
}
.task-section {
  margin-top: 16px;
}
.task-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.task-header h4 {
  margin: 0;
  color: #303133;
  font-size: 16px;
}
</style>
