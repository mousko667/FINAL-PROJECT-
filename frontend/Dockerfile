# ============================================================
#  OCT Invoice System — Frontend Docker Build
# ============================================================

# Build stage
FROM node:22-alpine AS build
WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm install

# Build the application
COPY . .
RUN npm run build

# Runtime stage
FROM nginx:alpine
RUN mkdir -p /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Copy built assets
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/index.html || exit 1

CMD ["nginx", "-g", "daemon off;"]
