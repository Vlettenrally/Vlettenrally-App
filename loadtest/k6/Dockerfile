# k6 with the load-test scripts baked in. Uses the official grafana/k6 image (entrypoint: k6),
# which includes the experimental-prometheus-rw output for streaming to Grafana.
FROM grafana/k6:latest
# app-load.js imports ./lib/*.js, so the directory structure must be preserved.
COPY app-load.js /scripts/app-load.js
COPY lib /scripts/lib
