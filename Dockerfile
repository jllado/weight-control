# syntax=docker/dockerfile:1.7
FROM node:24.21.0-alpine

WORKDIR /app

ARG VITE_GOOGLE_CLIENT_ID
ARG VITE_CHATGPT_COACH_URL
ENV VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID
ENV VITE_CHATGPT_COACH_URL=$VITE_CHATGPT_COACH_URL

COPY package.json yarn.lock ./
RUN --mount=type=cache,target=/usr/local/share/.cache/yarn yarn install --frozen-lockfile --non-interactive \
    && npm install --global http-server

COPY . .

RUN --mount=type=cache,target=/app/node_modules/.cache yarn build

EXPOSE 8080

CMD ["http-server", "dist", "-p", "8080", "-c-1"]
