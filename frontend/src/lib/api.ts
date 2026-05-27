import axios from 'axios';
import Cookies from 'js-cookie';

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = Cookies.get('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      Cookies.remove('token');
      Cookies.remove('user');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export default api;

// Auth
export const authApi = {
  signup:        (data: any)                    => api.post('/auth/signup', data),
  login:         (data: any)                    => api.post('/auth/login', data),
  me:            ()                             => api.get('/auth/me'),
  logout:        ()                             => api.post('/auth/logout'),
  companies:     ()                             => api.get('/auth/companies'),
  /** selectionToken required for multi-company login; omit when switching from TopBar */
  selectCompany: (companyId: number, selectionToken?: string) =>
                   api.post('/auth/select-company', { companyId, ...(selectionToken ? { selectionToken } : {}) }),
  acceptInvite:  (token: string, fullName: string, password: string) =>
                   api.post('/auth/accept-invite', { token, fullName, password }),
};

// Notification settings (V20+ — daily monitor + Telegram opt-in)
export const notificationSettingsApi = {
  get:      ()              => api.get('/settings/notifications'),
  update:   (data: any)     => api.put('/settings/notifications', data),
  sendTest: ()              => api.post('/settings/notifications/test'),
};

// Dashboard (KPIs + operational health widget)
export const dashboardApi = {
  get: (period?: string) => api.get('/dashboard', { params: { period } }),
  operationalHealth: () => api.get('/dashboard/operational-health'),
};

// Products
export const productsApi = {
  list: (params?: any) => api.get('/products', { params }),
  create: (data: any) => api.post('/products', data),
  get: (id: number) => api.get(`/products/${id}`),
  update: (id: number, data: any) => api.put(`/products/${id}`, data),
  deactivate: (id: number) => api.delete(`/products/${id}`),
  history: (id: number) => api.get(`/products/${id}/history`),
  adjustStock: (data: any) => api.post('/products/stock/adjust', data),
  bulkDeactivate: (ids: number[]) => api.post('/products/bulk-deactivate', { ids }),
};

// Categories
export const categoriesApi = {
  list: () => api.get('/categories'),
  create: (data: any) => api.post('/categories', data),
  update: (id: number, data: any) => api.put(`/categories/${id}`, data),
  delete: (id: number) => api.delete(`/categories/${id}`),
};

// Stock
export const stockApi = {
  list: (params?: any) => api.get('/stock', { params }),
  movements: (params?: any) => api.get('/stock/movements', { params }),
};

// Warehouses (multi-sklad)
export const warehousesApi = {
  list:        ()                 => api.get('/warehouses'),
  summary:     ()                 => api.get('/warehouses/summary'),
  get:         (id: number)       => api.get(`/warehouses/${id}`),
  create:      (data: any)        => api.post('/warehouses', data),
  update:      (id: number, data: any) => api.put(`/warehouses/${id}`, data),
  archive:     (id: number)       => api.post(`/warehouses/${id}/archive`),
  restore:     (id: number)       => api.post(`/warehouses/${id}/restore`),
  remove:      (id: number)       => api.delete(`/warehouses/${id}`),
  stock:       (id: number)       => api.get(`/warehouses/${id}/stock`),
  productBreakdown: (productId: number) => api.get(`/warehouses/stock/product/${productId}`),
  stockIn:     (data: any)        => api.post('/warehouses/stock/in', data),
  stockOut:    (data: any)        => api.post('/warehouses/stock/out', data),
  stockSet:    (data: any)        => api.post('/warehouses/stock/set', data),
  transfer:    (data: any)        => api.post('/warehouses/stock/transfer', data),
  movements:   (params?: any)     => api.get('/warehouses/movements', { params }),
  transfers:   (params?: any)     => api.get('/warehouses/transfers', { params }),
};

// Logistics (foreign-warehouse → local-warehouse transfers + landed cost)
export const logisticsApi = {
  list:        (params?: any)  => api.get('/logistics', { params }),
  get:         (id: number)    => api.get(`/logistics/${id}`),
  create:      (data: any)     => api.post('/logistics', data),
  addItem:     (id: number, data: any) => api.post(`/logistics/${id}/items`, data),
  removeItem:  (id: number, itemId: number) => api.delete(`/logistics/${id}/items/${itemId}`),
  addExpense:  (id: number, data: any) => api.post(`/logistics/${id}/expenses`, data),
  removeExpense: (id: number, expenseId: number) => api.delete(`/logistics/${id}/expenses/${expenseId}`),
  confirm:     (id: number)    => api.post(`/logistics/${id}/confirm`),
  cancel:      (id: number)    => api.post(`/logistics/${id}/cancel`),
  reverse:     (id: number, reason?: string) => api.post(`/logistics/${id}/reverse`, { reason }),
  payExpense:  (id: number, expenseId: number, data: any) =>
                  api.post(`/logistics/${id}/expenses/${expenseId}/pay`, data),
  exportOne:   (id: number)    => api.get(`/logistics/${id}/export`, { responseType: 'blob' }),
  exportAll:   ()              => api.get('/logistics/export', { responseType: 'blob' }),
};

// Countries (international trade catalog; FK on warehouses + suppliers)
export const countriesApi = {
  list:    (params?: any)   => api.get('/countries', { params }),
  get:     (id: number)     => api.get(`/countries/${id}`),
  create:  (data: any)      => api.post('/countries', data),
  update:  (id: number, data: any) => api.put(`/countries/${id}`, data),
  archive: (id: number)     => api.post(`/countries/${id}/archive`),
  restore: (id: number)     => api.post(`/countries/${id}/restore`),
  remove:  (id: number)     => api.delete(`/countries/${id}`),
};

// Production (manufacturing)
export const bomApi = {
  list:   ()               => api.get('/production/bom'),
  get:    (id: number)     => api.get(`/production/bom/${id}`),
  create: (data: any)      => api.post('/production/bom', data),
  update: (id: number, data: any) => api.put(`/production/bom/${id}`, data),
  delete: (id: number)     => api.delete(`/production/bom/${id}`),
};

export const productionApi = {
  list:        (params?: any)  => api.get('/production/orders', { params }),
  get:         (id: number)    => api.get(`/production/orders/${id}`),
  create:      (data: any)     => api.post('/production/orders', data),
  plan:        (id: number)    => api.post(`/production/orders/${id}/plan`),
  start:       (id: number)    => api.post(`/production/orders/${id}/start`),
  qualityCheck:(id: number)    => api.post(`/production/orders/${id}/quality-check`),
  complete:    (id: number)    => api.post(`/production/orders/${id}/complete`),
  cancel:      (id: number)    => api.post(`/production/orders/${id}/cancel`),
  costs:       (id: number)    => api.get(`/production/orders/${id}/costs`),
  addCost:     (id: number, data: any) => api.post(`/production/orders/${id}/costs`, data),
  waste:       (id: number)    => api.get(`/production/orders/${id}/waste`),
  addWaste:    (id: number, data: any) => api.post(`/production/orders/${id}/waste`, data),
  summary:     ()              => api.get('/production/reports/summary'),
};

// Customers
export const customersApi = {
  list: (params?: any) => api.get('/customers', { params }),
  create: (data: any) => api.post('/customers', data),
  get: (id: number) => api.get(`/customers/${id}`),
  update: (id: number, data: any) => api.put(`/customers/${id}`, data),
};

// Suppliers
export const suppliersApi = {
  list: (params?: any) => api.get('/suppliers', { params }),
  create: (data: any) => api.post('/suppliers', data),
  get: (id: number) => api.get(`/suppliers/${id}`),
  update: (id: number, data: any) => api.put(`/suppliers/${id}`, data),
};

// Sales
export const salesApi = {
  list: (params?: any) => api.get('/sales', { params }),
  create: (data: any) => api.post('/sales', data),
  get: (id: number) => api.get(`/sales/${id}`),
  cancel: (id: number) => api.post(`/sales/${id}/cancel`),
  addPayment: (id: number, data: any) => api.post(`/sales/${id}/payment`, data),
  post: (id: number) => api.post(`/sales/${id}/post`),
  edit: (id: number, data: any) => api.put(`/sales/${id}`, data),
};

// Purchases
export const purchasesApi = {
  list: (params?: any) => api.get('/purchases', { params }),
  create: (data: any) => api.post('/purchases', data),
  get: (id: number) => api.get(`/purchases/${id}`),
  cancel: (id: number) => api.post(`/purchases/${id}/cancel`),
  addPayment: (id: number, data: any) => api.post(`/purchases/${id}/payment`, data),
  post: (id: number) => api.post(`/purchases/${id}/post`),
  edit: (id: number, data: any) => api.put(`/purchases/${id}`, data),
};

// Cash Transactions
export const cashApi = {
  list: (params?: any) => api.get('/cash-transactions', { params }),
  create: (data: any) => api.post('/cash-transactions', data),
  update: (id: number, data: any) => api.put(`/cash-transactions/${id}`, data),
  cancel: (id: number) => api.post(`/cash-transactions/${id}/cancel`),
};

// Bank
export const bankApi = {
  list: (params?: any) => api.get('/bank-transactions', { params }),
  create: (data: any) => api.post('/bank-transactions', data),
};

// Debts
export const debtsApi = {
  list: (params?: any) => api.get('/debts', { params }),
  addPayment: (id: number, data: any) => api.post(`/debts/${id}/payment`, data),
  history: (id: number) => api.get(`/debts/${id}/history`),
};

// Reports
export const reportsApi = {
  summary: (period?: string) => api.get('/reports/summary', { params: { period } }),
  stock: (params?: any) => api.get('/reports/stock', { params }),
  lowStock: () => api.get('/reports/low-stock'),
  deadStock: (days?: number) => api.get('/reports/dead-stock', { params: { days } }),
  customerScores: () => api.get('/reports/customer-scores'),
  moneyLeak: (period?: string) => api.get('/reports/money-leak', { params: { period } }),
  monthlyTrend: (months?: number) => api.get('/reports/monthly-trend', { params: { months } }),
  profitBreakdown: (period?: string) => api.get('/reports/profit-breakdown', { params: { period } }),
  healthScore: () => api.get('/reports/health-score'),
  smartPrice: (productId: number, targetMarginPct?: number) =>
    api.get(`/reports/smart-price/${productId}`, { params: { targetMarginPct } }),
};

// Accounting (double-entry ledger)
export const accountingApi = {
  accounts:     ()              => api.get('/accounting/accounts'),
  journal:      (params?: any)  => api.get('/accounting/journal', { params }),
  trialBalance: ()              => api.get('/accounting/trial-balance'),
  profitLoss:   ()              => api.get('/accounting/profit-loss'),
  balanceSheet: ()              => api.get('/accounting/balance-sheet'),
  reverse:      (id: number, reason?: string) => api.post(`/accounting/journal/${id}/reverse`, { reason }),
};

// Saved Views
export const savedViewsApi = {
  list: (module?: string) => api.get('/saved-views', { params: { module } }),
  create: (data: any) => api.post('/saved-views', data),
  update: (id: number, data: any) => api.put(`/saved-views/${id}`, data),
  delete: (id: number) => api.delete(`/saved-views/${id}`),
};

// Daily Close
export const dailyCloseApi = {
  list: (params?: any) => api.get('/daily-close', { params }),
  prepare: (date?: string) => api.get('/daily-close/prepare', { params: { date } }),
  createOrUpdate: (data: any) => api.post('/daily-close', data),
  close: (id: number) => api.post(`/daily-close/${id}/close`),
};

// Alerts
export const alertsApi = {
  list: () => api.get('/alerts'),
  count: () => api.get('/alerts/count'),
  markSeen: (id: number) => api.patch(`/alerts/${id}/seen`),
  markResolved: (id: number) => api.patch(`/alerts/${id}/resolved`),
};

// Audit (business action audit)
export const auditApi = {
  list: (params?: any) => api.get('/audit-logs', { params }),
};

// Access Logs (permission allow/deny events)
export const accessLogsApi = {
  list:    (params?: any)    => api.get('/access-logs', { params }),
  summary: ()                => api.get('/access-logs/summary'),
  forUser: (userId: number)  => api.get(`/access-logs/user/${userId}`),
};

// Exchange rates (multi-currency)
export const exchangeRatesApi = {
  list:    ()                              => api.get('/exchange-rates'),
  setRate: (currency: string, rate: number) => api.put('/exchange-rates', { currency, rate }),
};

// Company
export const companyApi = {
  get:            ()                              => api.get('/company'),
  balances:       ()                              => api.get('/company/balances'),
  update:         (data: any)                     => api.put('/company', data),
  users:          ()                              => api.get('/company/users'),
  inviteUser:     (data: {
                    email?: string; phone?: string;
                    roleId?: number; role?: string; fullName?: string;
                  })                              => api.post('/company/users/invite', data),
  changeRole:     (userId: number, roleIdOrCode: number | string) =>
                    api.put(`/company/users/${userId}/role`,
                      typeof roleIdOrCode === 'number' ? { roleId: roleIdOrCode } : { role: roleIdOrCode }),
  deactivateUser: (userId: number)                => api.post(`/company/users/${userId}/deactivate`),
  activateUser:   (userId: number)                => api.post(`/company/users/${userId}/activate`),
  removeUser:     (userId: number)                => api.delete(`/company/users/${userId}`),
  invitations:    ()                              => api.get('/company/invitations'),
  cancelInvite:   (id: number)                    => api.delete(`/company/invitations/${id}`),
};

// Roles
export const rolesApi = {
  list:           ()                              => api.get('/roles'),
  get:            (id: number)                    => api.get(`/roles/${id}`),
  create:         (data: any)                     => api.post('/roles', data),
  update:         (id: number, data: any)         => api.put(`/roles/${id}`, data),
  setPermissions: (id: number, permissionIds: number[]) =>
                    api.put(`/roles/${id}/permissions`, { permissionIds }),
  delete:         (id: number)                    => api.delete(`/roles/${id}`),
};

// Permissions (global seed)
export const permissionsApi = {
  grouped: ()                                     => api.get('/permissions'),
  flat:    ()                                     => api.get('/permissions/flat'),
};

// Import
export const importApi = {
  preview: (formData: FormData) =>
    api.post('/import/preview', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  confirm: (formData: FormData) =>
    api.post('/import/confirm', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  history: () => api.get('/import/history'),
  rollback: (id: number) => api.post(`/import/${id}/rollback`),
};

// Export — all support filter params
export const exportApi = {
  products: (params?: any) => api.get('/export/products', { params, responseType: 'blob' }),
  customers: () => api.get('/export/customers', { responseType: 'blob' }),
  suppliers: () => api.get('/export/suppliers', { responseType: 'blob' }),
  sales: (params?: any) => api.get('/export/sales', { params, responseType: 'blob' }),
  purchases: (params?: any) => api.get('/export/purchases', { params, responseType: 'blob' }),
  debts: (params?: any) => api.get('/export/debts', { params, responseType: 'blob' }),
  cashbox: (params?: any) => api.get('/export/cashbox', { params, responseType: 'blob' }),
  bank: (params?: any) => api.get('/export/bank', { params, responseType: 'blob' }),
  stock: (params?: any) => api.get('/export/stock', { params, responseType: 'blob' }),
  warehouseStock: (params?: any) => api.get('/export/warehouse-stock', { params, responseType: 'blob' }),
  productionOrders: () => api.get('/export/production/orders', { responseType: 'blob' }),
  bom: () => api.get('/export/production/bom', { responseType: 'blob' }),
  productionWaste: () => api.get('/export/production/waste', { responseType: 'blob' }),
  categories: () => api.get('/export/categories', { responseType: 'blob' }),
  stockMovements: () => api.get('/export/stock-movements', { responseType: 'blob' }),
  stockTransfers: () => api.get('/export/stock-transfers', { responseType: 'blob' }),
  auditLogs: (params?: any) => api.get('/export/audit-logs', { params, responseType: 'blob' }),
  users: () => api.get('/export/users', { responseType: 'blob' }),
  roles: () => api.get('/export/roles', { responseType: 'blob' }),
  dailyClose: () => api.get('/export/daily-close', { responseType: 'blob' }),
  accountingJournal: () => api.get('/export/accounting/journal', { responseType: 'blob' }),
  accountingLedger: () => api.get('/export/accounting/ledger', { responseType: 'blob' }),
  trialBalance: () => api.get('/export/accounting/trial-balance', { responseType: 'blob' }),
  profitLoss: () => api.get('/export/accounting/profit-loss', { responseType: 'blob' }),
  balanceSheet: () => api.get('/export/accounting/balance-sheet', { responseType: 'blob' }),
  cashflow: () => api.get('/export/accounting/cashflow', { responseType: 'blob' }),
  deadStock: (days?: number) => api.get('/export/reports/dead-stock', { params: { days }, responseType: 'blob' }),
  moneyLeak: () => api.get('/export/reports/money-leak', { responseType: 'blob' }),
  customerRating: () => api.get('/export/reports/customer-rating', { responseType: 'blob' }),
};

// Approvals
export const approvalsApi = {
  list:    (params?: any) => api.get('/approvals', { params }),
  pending: ()             => api.get('/approvals/pending'),
  count:   ()             => api.get('/approvals/count'),
  approve: (id: number, note?: string) => api.post(`/approvals/${id}/approve`, { note }),
  reject:  (id: number, note?: string) => api.post(`/approvals/${id}/reject`,  { note }),
  cancel:  (id: number)               => api.post(`/approvals/${id}/cancel`),
};

// AI Business Advisor
export const advisorApi = {
  insights:  ()                  => api.get('/advisor/insights'),
  forecasts: ()                  => api.get('/advisor/forecasts'),
  anomalies: (days?: number)     => api.get('/advisor/anomalies', { params: { days } }),
};

// Temporary Access
export const tempAccessApi = {
  list:       ()               => api.get('/temp-access'),
  listForUser:(userId: number) => api.get(`/temp-access/user/${userId}`),
  grant:      (data: { userId: number; permissionCode: string; expiresAt: string; reason?: string }) =>
                api.post('/temp-access/grant', data),
  revoke:     (id: number)     => api.delete(`/temp-access/${id}/revoke`),
};

export const downloadBlob = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
};
