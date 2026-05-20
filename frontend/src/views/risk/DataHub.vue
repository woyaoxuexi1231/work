<script setup>
import { ref, onMounted, computed } from 'vue'
import { post } from '../../api/request.js'
import { ElMessage } from 'element-plus'

const overview = ref(null)
const loading = ref(false)
let fetching = false  // 防止重复请求

onMounted(async () => {
  await fetchOverview()
})

async function fetchOverview() {
  // 防止重复请求
  if (fetching) return
  fetching = true
  loading.value = true
  try {
    const res = await post('/risk-api/api/hub/overview')
    // 处理 HTTP 错误（如 502 Bad Gateway）
    if (res._httpError) {
      ElMessage.error(res.message || '网关错误')
      return
    }
    if (res.code === 200) {
      overview.value = res.data
    } else {
      ElMessage.error(res.message || '获取概览失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    loading.value = false
    fetching = false
  }
}

const hubRecordCount = computed(() => {
  if (!overview.value) return 0
  const stats = overview.value.hubTableStats || {}
  return (stats.clean_stock || 0) + (stats.clean_trade || 0) + 
         (stats.clean_position || 0) + (stats.clean_asset || 0)
})

const businessTableCount = computed(() => {
  if (!overview.value) return 0
  const business = overview.value.businessTableStats || {}
  return Object.keys(business.trade_oms || {}).length + 
         Object.keys(business.trade_broker || {}).length
})
</script>

<template>
  <div class="data-hub">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>Risk Hub</el-breadcrumb-item>
      <el-breadcrumb-item>数据中台</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="content-card">
      <template #header>
        <div class="card-header">
          <span>数据中台同步实验室</span>
          <el-button type="primary" :icon="Refresh" size="small" @click="fetchOverview">刷新</el-button>
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
          <el-statistic title="中台落库条数" :value="hubRecordCount">
            <template #prefix>
              <el-icon color="#e6a23c"><DocumentCopy /></el-icon>
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
      
      <div class="detail-section">
        <h4>上游拓扑</h4>
        <el-table 
          v-if="overview?.topology?.upstreams?.length" 
          :data="overview.topology.upstreams" 
          stripe
          style="width: 100%; margin-top: 12px"
        >
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'connected' ? 'success' : 'danger'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无上游拓扑数据" />
      </div>

      <el-skeleton v-if="loading" :rows="5" animated />
    </el-card>
  </div>
</template>

<style scoped>
.data-hub {
  max-width: 1200px;
  margin: 0 auto;
}
.content-card {
  margin-top: 16px;
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
</style>
