import { request } from '../request';

/** 获取充值套餐列表 */
export function fetchRechargePackages() {
  return request<Api.Recharge.Package[]>({
    url: '/recharge/packages',
    method: 'get'
  });
}

/** 创建充值订单 */
export function fetchCreateRechargeOrder(packageId?: number, customAmount?: number) {
  return request<Api.Recharge.OrderInfo>({
    url: '/recharge/create-order',
    method: 'post',
    data: {
      packageId,
      customAmount: customAmount ? Number(customAmount) : undefined
    }
  });
}

/** 查询用户订单列表 */
export function fetchRechargeOrders(status?: string) {
  return request<Api.Recharge.Order[]>({
    url: '/recharge/orders',
    method: 'get',
    params: { status }
  });
}

/** 查询订单详情 */
export function fetchRechargeOrderDetail(tradeNo: string) {
  return request<Api.Recharge.Order>({
    url: `/recharge/orders/${tradeNo}`,
    method: 'get'
  });
}
