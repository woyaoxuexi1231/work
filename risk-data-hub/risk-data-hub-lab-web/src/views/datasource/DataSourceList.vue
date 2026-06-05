<!--
  DataSourceList — 数据源管理页
  注册、查看、删除数据源连接
-->
<script setup>
import { ref, onMounted } from 'vue'
import { listDatasources, registerDatasource, removeDatasource } from '@/api/modules/hub'
import { ElMessage, ElMessageBox } from 'element-plus'

const dsList = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const form = ref({ key: '', name: '', datasourceType: 'MYSQL', upstreamType: '', url: '', username: '', password: '', poolSize: 10 })

const dsTypeMap = {
  MYSQL: { t: 'MySQL', c: 'primary' },
  POSTGRESQL: { t: 'PostgreSQL', c: 'success' },
  ORACLE: { t: 'Oracle', c: 'warning' },
  SQLSERVER: { t: 'SQL Server', c: 'danger' }
}

onMounted(() => fetchList())

async function fetchList() {
  loading.value = true
  try {
    const res = await listDatasources()
    dsList.value = res.data || []
  } catch (e) { console.error('[DS] 获取数据源失败:', e.message) }
  finally { loading.value = false }
}

async function handleRegister() {
  if (!form.value.key || !form.value.name || !form.value.upstreamType) {
    ElMessage.warning('标识、名称、上游类型为必填')
    return
  }
  submitLoading.value = true
  try {
    await registerDatasource(form.value)
    ElMessage.success('注册成功')
    dialogVisible.value = false
    form.value = { key: '', name: '', datasourceType: 'MYSQL', upstreamType: '', url: '', username: '', password: '', poolSize: 10 }
    await fetchList()
  } catch (e) { console.error('[DS] 注册失败:', e.message) }
  finally { submitLoading.value = false }
}

async function handleRemove(key) {
  try {
    await ElMessageBox.confirm('确定删除该数据源？', '确认', { type: 'warning' })
    await removeDatasource(key)
    ElMessage.success('已删除')
    await fetchList()
  } catch (e) { if (e !== 'cancel') console.error('[DS] 删除失败:', e.message) }
}
</script>

<template>
  <div class="ds-list">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">工作台</el-breadcrumb-item>
      <el-breadcrumb-item>数据源管理</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="content-card">
      <template #header>
        <div class="card-header">
          <span>数据源配置 ({{ dsList.length }})</span>
          <el-button type="primary" @click="dialogVisible = true">注册数据源</el-button>
        </div>
      </template>

      <el-table :data="dsList" v-loading="loading" stripe>
        <el-table-column prop="key" label="标识" width="150" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="dsTypeMap[row.datasourceType]?.c || 'info'" size="small">{{ dsTypeMap[row.datasourceType]?.t || row.datasourceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="url" label="连接地址" min-width="280" show-overflow-tooltip />
        <el-table-column label="连接池" width="90">
          <template #default="{ row }">{{ row.poolSize || 10 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'" size="small">{{ row.active ? '活跃' : '空闲' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="handleRemove(row.key)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !dsList.length" description="暂无数据源" />
    </el-card>

    <el-dialog v-model="dialogVisible" title="注册数据源" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标识" required>
          <el-input v-model="form.key" placeholder="唯一标识，如 mysql_prod" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="数据库类型">
          <el-select v-model="form.datasourceType" style="width: 100%">
            <el-option v-for="(v, k) in dsTypeMap" :key="k" :label="v.t" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="上游类型" required>
          <el-select v-model="form.upstreamType" placeholder="选择上游系统类型" style="width: 100%">
            <el-option label="交易 OMS 系统" value="TRADE_OMS" />
            <el-option label="券商 Broker 系统" value="TRADE_BROKER" />
            <el-option label="自定义上游" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接地址">
          <el-input v-model="form.url" placeholder="jdbc:mysql://host:port/db" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="数据库用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="数据库密码" />
        </el-form-item>
        <el-form-item label="连接池大小">
          <el-input-number v-model="form.poolSize" :min="1" :max="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleRegister">注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ds-list { max-width: 1400px; margin: 0 auto; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
