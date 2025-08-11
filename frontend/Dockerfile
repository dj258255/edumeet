# Build stage
FROM node:22-alpine AS builder
WORKDIR /app

# Dependencies
COPY package*.json ./
RUN npm ci

# Build
COPY . .

ARG VITE_API_URL
ARG VITE_WS_URL
ARG VITE_API_BASE_URL
ARG VITE_BASE_URL

ENV VITE_API_URL=$VITE_API_URL
ENV VITE_WS_URL=$VITE_WS_URL
ENV VITE_API_BASE_URL="https://i13c205.p.ssafy.io/api/v1"
ENV VITE_BASE_URL="https://i13c205.p.ssafy.io"

RUN npm run build

# Production stage
FROM nginx:alpine

# Copy nginx configs
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-ssl.conf /etc/nginx/conf.d/ssl.conf.disabled
COPY entrypoint.sh /docker-entrypoint.sh
RUN chmod 755 /docker-entrypoint.sh

# Copy built files
COPY --from=builder /app/dist /usr/share/nginx/html

# Make entrypoint executable
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80 443

ENTRYPOINT ["/docker-entrypoint.sh"]
