export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export interface PaginationParams {
  page?: number
  pageSize?: number
}

export interface DateRangeParams {
  startTime?: string
  endTime?: string
}

export type ApiPromise<T> = Promise<ApiResponse<T>>
