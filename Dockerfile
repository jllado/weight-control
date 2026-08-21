FROM node:lts-alpine

WORKDIR /app

ARG VUE_APP_GOOGLE_CLIENT_ID
ARG VUE_APP_CHATGPT_COACH_URL
ENV VUE_APP_GOOGLE_CLIENT_ID=$VUE_APP_GOOGLE_CLIENT_ID
ENV VUE_APP_CHATGPT_COACH_URL=$VUE_APP_CHATGPT_COACH_URL

COPY package.json ./
RUN npm install

COPY . .

RUN npm run build

RUN npm install -g http-server

EXPOSE 8080

CMD ["http-server", "dist", "-p", "8080", "-c-1"]
