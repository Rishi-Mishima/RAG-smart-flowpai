import { request } from '../request';

export function fetchGetInviteCodeList(params: Api.InviteCode.SearchParams) {
  return request<Api.InviteCode.ListPayload>({ url: '/admin/invite-codes', params });
}

export function fetchCreateInviteCode(data: {
  code?: string;
  maxUses: number;
  count?: number;
  expiresAt?: string | null;
}) {
  return request({
    url: '/admin/invite-codes',
    method: 'post',
    data
  });
}

export function fetchDisableInviteCode(id: number) {
  return request({
    url: `/admin/invite-codes/${id}/disable`,
    method: 'patch'
  });
}

export function fetchDeleteInviteCode(id: number) {
  return request({
    url: `/admin/invite-codes/${id}`,
    method: 'delete'
  });
}

export function fetchUpdateInviteCode(
  id: number,
  data: {
    code: string;
    maxUses: number;
    expiresAt?: string | null;
  }
) {
  return request({
    url: `/admin/invite-codes/${id}`,
    method: 'put',
    data
  });
}
