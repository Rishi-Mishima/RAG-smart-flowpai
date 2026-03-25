import { fakePaginationRequest } from '../request';

export function fetchGetOrgTagList() {
  return fakePaginationRequest<Api.OrgTag.List>({ url: '/admin/org-tags/tree' });
}
