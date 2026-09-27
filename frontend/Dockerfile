FROM node:22-alpine AS build

ENV CI=true
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@10.10.0 --activate

COPY . .
RUN pnpm install --frozen-lockfile
RUN pnpm build

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
