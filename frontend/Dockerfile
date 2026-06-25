# Runtime image — serves the prebuilt frontend assets.
#
# This image does NOT build the app. The `dist/` directory must already exist
# in the build context. In CI it is built once in the `test-frontend` job and
# handed to the `build-frontend` job as an artifact (see .github/workflows/build.yml).
# For a local image build, run `npm run build` first to produce `dist/`.
FROM nginx:alpine

# Copy prebuilt assets from the build context
COPY dist /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
