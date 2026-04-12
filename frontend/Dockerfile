# ============================================================
#  OCT Invoice System — Simple Frontend Server with Nginx
# ============================================================

FROM nginx:alpine

# Create a basic redirect to the backend
RUN mkdir -p /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Create a basic index.html that redirects to login
RUN cat > /usr/share/nginx/html/index.html <<'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>OCT Invoice - Redirecting...</title>
    <meta http-equiv="refresh" content="0;URL=/login" />
</head>
<body>
    <p>Redirecting to login page...</p>
</body>
</html>
EOF

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/index.html || exit 1

CMD ["nginx", "-g", "daemon off;"]
