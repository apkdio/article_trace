# =============================================================================
# 前端镜像：Node 构建 Vue 3 产物 → Nginx 托管
# =============================================================================

# ---------- 构建阶段 ----------
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
# 国内服务器直连 registry.npmjs.org 很慢，默认走 npmmirror；海外构建可覆盖
ARG NPM_REGISTRY=https://registry.npmmirror.com
RUN npm ci --registry=$NPM_REGISTRY

COPY . .
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
