FROM node:20-alpine

WORKDIR /app

COPY package.json package-lock.json* ./

RUN npm ci --production

COPY . .

RUN addgroup -S app && adduser -S app -G app && \
    chown -R app:app /app

USER app

ENV NODE_ENV=production
ENV API_BASE_URL=http://data-ingest:8081
ENV REPORT_INTERVAL_MINUTES=120
ENV DEVICE_COUNT=70

CMD ["node", "src/index.js", "--mode", "realtime"]
