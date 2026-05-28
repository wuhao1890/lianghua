<template>
  <router-view />
</template>

<script setup lang="ts">
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', Arial, sans-serif;
}

/* Element Plus 全局主题覆盖 — 绿色主题 */
:root {
  --el-color-primary: #33b86a;
  --el-color-primary-light-3: #5cd689;
  --el-color-primary-light-5: #8ae6aa;
  --el-color-primary-light-7: #b8f0cc;
  --el-color-primary-light-8: #d4f5e0;
  --el-color-primary-light-9: #ebfaf0;
  --el-color-primary-dark-2: #2a9e5a;
  --el-color-success: #33d685;
  --el-color-warning: #e6a23c;
  --el-color-danger: #f56c6c;
  --el-color-info: #909399;
  --el-border-color: #e0f0e5;
  --el-border-color-light: #ebf5ee;
  --el-fill-color: #f0faf4;
  --el-fill-color-light: #f5fcf8;
  --el-text-color-primary: #303133;
  --el-text-color-regular: #606266;
  --el-text-color-secondary: #909399;
  --el-text-color-placeholder: #c0c4cc;
  --el-bg-color: #ffffff;
  --el-bg-color-overlay: #ffffff;
  --el-bg-color-page: #f0f7f2;
}

/* 覆盖 Element Plus 按钮主题 */
.el-button--primary {
  --el-button-bg-color: #33b86a;
  --el-button-border-color: #33b86a;
  --el-button-hover-bg-color: #5cd689;
  --el-button-hover-border-color: #5cd689;
  --el-button-active-bg-color: #2a9e5a;
  --el-button-active-border-color: #2a9e5a;
}

.el-button--primary.is-plain {
  --el-button-bg-color: #ebfaf0;
  --el-button-border-color: #8ae6aa;
  --el-button-hover-bg-color: #33b86a;
  --el-button-hover-border-color: #33b86a;
}

/* 覆盖 Element Plus 标签 */
.el-tag--success {
  --el-tag-bg-color: #ebfaf0;
  --el-tag-border-color: #8ae6aa;
  --el-tag-text-color: #2a9e5a;
}

/* 覆盖 Element Plus 进度条 */
.el-progress-bar__inner {
  background-color: #33b86a !important;
}

/* 覆盖 Element Plus 表格 */
.el-table__header th {
  background-color: #f0faf4 !important;
  color: #2a9e5a !important;
}

.el-table--striped .el-table__body tr.el-table__row--striped td {
  background-color: #f5fcf8 !important;
}

/* 覆盖 Element Plus 菜单 */
.el-menu-item.is-active {
  color: #33d685 !important;
}

/* 覆盖 Element Plus 卡片 */
.el-card {
  border-color: #e0f0e5 !important;
  border-radius: 10px !important;
}

.el-card__header {
  border-bottom-color: #e0f0e5 !important;
  padding: 14px 20px !important;
}

/* 覆盖 Element Plus Tabs */
.el-tabs__item.is-active {
  color: #33b86a !important;
}

.el-tabs__active-bar {
  background-color: #33b86a !important;
}

.el-tabs__item:hover {
  color: #5cd689 !important;
}

/* 覆盖分页 */
.el-pagination.is-background .el-pager li.is-active {
  background-color: #33b86a !important;
}

/* NProgress 自定义样式 */
#nprogress .bar {
  background: #33d685 !important;
  height: 3px !important;
}

/* 全局滚动条样式 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-thumb {
  background: #b8dcc6;
  border-radius: 3px;
}

::-webkit-scrollbar-track {
  background: transparent;
}
</style>
