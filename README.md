# Lizoo (لیزو) - MVP

Ad platform on couriers' motorbike boxes.

- `android/`  courier app (Kotlin, Jetpack Compose)
- `backend/`  API + admin panel (Node.js, Express, PostgreSQL), panel at `/panel/`
- `.github/workflows/` APK build and backend tests

## Build the APK (GitHub Actions)
1. Settings > Secrets and variables > Actions > Variables:
   - `BASE_URL` = `https://api.example.ir/` (must end with `/`)
   - `CLEARTEXT` = `false` (use `true` only for plain-HTTP testing)
2. Actions tab > "Build Android APK" > Run workflow.
3. Download the `lizoo-apk` artifact.
4. Optional signed release: add Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Run the backend
```
cd backend
cp env.example .env     # fill DATABASE_URL, JWT_SECRET, SMS_API_KEY
npm install
npm run migrate
npm run make-admin -- 09XXXXXXXXX
npm start
```
Admin panel: `https://YOUR_HOST/panel/`

## Secrets
Never commit `.env`, keystores or API keys. Keys live only in the server `.env` and in GitHub Secrets.
