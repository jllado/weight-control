# syntax=docker/dockerfile:1.7
FROM node:lts-alpine

WORKDIR /app

ARG VUE_APP_GOOGLE_CLIENT_ID
ARG VUE_APP_CHATGPT_COACH_URL
ENV VUE_APP_GOOGLE_CLIENT_ID=$VUE_APP_GOOGLE_CLIENT_ID
ENV VUE_APP_CHATGPT_COACH_URL=$VUE_APP_CHATGPT_COACH_URL

COPY package.json yarn.lock ./
RUN --mount=type=cache,target=/usr/local/share/.cache/yarn yarn install --frozen-lockfile --non-interactive \
    && npm install --global http-server

COPY . .

RUN --mount=type=cache,target=/app/node_modules/.cache yarn build

EXPOSE 8080

CMD ["http-server", "dist", "-p", "8080", "-c-1"]
