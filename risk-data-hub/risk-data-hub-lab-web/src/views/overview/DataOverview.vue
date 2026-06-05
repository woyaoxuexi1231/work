<!--
  DataOverview — 数据总览页
  展示中台核心统计数据、上游拓扑和业务表统计
-->
<script setup>
import { ref, onMounted } from 'vue'
import { getOverview } from '@/api/modules/hub'
import { ElMessage } from 'element-plus'

const overview = ref(null)
const loading = ref(false)

onMounted(() => fetchOverview())

async function fetchOverview() {
  loading.value = true
  try {
    const res = await getOverview()
    overview.value = res.data
  } catch (e) {
    console.error('[DataOverview] 获取总览失败:', e.message)
  } finally {
    loading.value = false
  }
}

const upstreamTypeMap = {
  TRADE_OMS: { text: '交易 OMS', type: 'primary' },
  TRADE_BROKER: { text: '券商 Broker', type: 'success' },
  CUSTOM: { text: '自定义', type: 'info' }
}
</script>

<template>
  <div class="overview">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">工作台</el-breadcrumb-item>
      <el-breadcrumb-item>数据总览</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 统计卡片 -->
    <el-row :gutter="20" style="margin-top: 16px">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <el-statistic title="数据源数量" :value="overview?.datasourceCount || 0">
            <template #prefix><el-icon color="#409EFF" size="20"><Connection /></el-icon></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <el-statistic title="上游库" :value="(overview?.topology?.upstreams || []).length">
            <template #prefix><el-icon color="#67c23a" size="20"><Database /></el-icon></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <el-statistic title="交易表 (OMS)" :value="overview?.businessTableStats?.trade_oms?.length || 0">
            <template #prefix><el-icon color="#e6a23c" size="20"><ShoppingCart /></el-icon></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <el-statistic title="交易表 (Broker)" :value="overview?.businessTableStats?.trade_broker?.length || 0">
            <template #prefix><el-icon color="#f56c6c" size="20"><TrendCharts /></el-icon></template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <!-- 上游拓扑 -->
    <el-card class="content-card">
      <template #header>
        <div class="card-header">
          <span>上游拓扑</span>
          <el-button @click="fetchOverview" :loading="loading">刷新</el-button>
        </div>
      </template>
      <el-table v-if="overview?.topology?.upstreams?.length" :data="overview.topology.upstreams" stripe>
        <el-table-column prop="key" label="标识" width="180" />
        <el-table-column label="类型" width="150">
          <template #default="{ row }">
            <el-tag :type="upstreamTypeMap[row.type]?.type || 'info'" size="small">
              {{ upstreamTypeMap[row.type]?.text || row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="同步表" min-width="350">
          <template #default="{ row }">
            <el-tag v-for="t in (row.syncTables || [])" :key="t" size="small" style="margin: 2px 4px 2px 0">{{ t }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无拓扑数据" />
    </el-card>

    <!-- 业务表详情 -->
    <el-card class="content-card" v-if="overview?.businessTableStats">
      <template #header><span>业务表统计</span></template>
      <el-row :gutter="20">
        <el-col :span="12">
          <h4>OMS 交易表 ({{ overview.businessTableStats.trade_oms?.length || 0 }})</h4>
          <el-tag v-for="t in (overview.businessTableStats.trade_oms || [])" :key="t" size="small" type="primary" style="margin: 2px 4px 2px 0">{{ t }}</el-tag>
          <el-empty v-if="!overview.businessTableStats.trade_oms?.length" description="无" :image-size="40" />
        </el-col>
        <el-col :span="12">
          <h4>Broker 交易表 ({{ overview.businessTableStats.trade_broker?.length || 0 }})</h4>
          <el-tag v-for="t in (overview.businessTableStats.trade_broker || [])" :key="t" size="small" type="success" style="margin: 2px 4px 2px 0">{{ t }}</el-tag>
          <el-empty v-if="!overview.businessTableStats.trade_broker?.length" description="无" :image-size="40" />
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.overview { max-width: 1400px; margin: 0 auto; }
.stat-card { border-radius: 12px; cursor: default; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
h4 { margin: 0 0 12px; color: #606266; font-size: 15px; }
</style>
