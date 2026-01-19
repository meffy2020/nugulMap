# 1. 반드시 FROM이 제일 먼저 나와야 합니다!
FROM node:18-alpine AS builder

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 그 다음 ARG와 ENV를 적습니다
ARG NEXT_PUBLIC_KAKAOMAP_APIKEY
ARG NEXT_PUBLIC_API_BASE_URL

ENV NEXT_PUBLIC_KAKAOMAP_APIKEY=$NEXT_PUBLIC_KAKAOMAP_APIKEY
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL

# 4. 소스 복사 및 빌드
COPY package*.json ./
RUN npm install --legacy-peer-deps
COPY . .
RUN npm run build

# 5. 실행 단계 (가벼운 이미지를 위해)
FROM node:18-alpine AS runner
WORKDIR /app
COPY --from=builder /app ./
CMD ["npm", "start"]