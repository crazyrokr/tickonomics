FROM node:22-alpine AS build

WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:22-alpine

RUN addgroup -S dashboard && adduser -S -G dashboard dashboard

WORKDIR /app
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

USER dashboard
EXPOSE 3000
ENV HOSTNAME=0.0.0.0

CMD ["node", "server.js"]
