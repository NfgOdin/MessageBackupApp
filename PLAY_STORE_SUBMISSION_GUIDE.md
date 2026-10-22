# Google Play Store v1.0.0 Submission & SMS Permission Declaration Guide

Because **Muninn** requests `android.permission.READ_SMS` and `android.permission.READ_CONTACTS`, Google Play requires a **Permissions Declaration Form** and a short **Video Walkthrough** during submission under the **SMS and Call Log Permissions Policy** ("Backup and restore for users" exception).

This guide provides copy-paste templates and a step-by-step checklist so your v1.0.0 submission passes review on the first try.

---

## 1. Core Functionality Selection (Permissions Declaration Form)

In **Google Play Console -> App content -> Sensitive app permissions -> SMS and Call Log permissions**:

- **Core Functionality:** Select **`Backup and restore for users`** (and if applicable, **`Export / Print / Document generation of user SMS/MMS history`**).
- **Does your app require `READ_SMS` to function?** `Yes`

### Copy-Paste Justification (Instructions for Reviewer)
> **Core Purpose & Permission Justification:**
> Muninn is a user-initiated SMS/MMS backup, archiving, and multi-format export tool (supporting PDF, HTML, TXT, CSV, JSON, DOCX, XLSX, and AES-256 encrypted ZIP archives).
>
> - `android.permission.READ_SMS`: Strictly required to read the user's on-device SMS and MMS conversations from `Telephony.Sms` and `Telephony.Mms` providers so the user can preview, select, back up, and export their message history into portable local files.
> - `android.permission.READ_CONTACTS`: Used exclusively on-device to resolve phone numbers to human-readable contact names and avatars in the conversation list and exported backup documents.
> - `android.permission.POST_NOTIFICATIONS`: Used to notify the user when an opt-in scheduled background backup completes.
>
> **Privacy & User Control Guarantee:**
> 1. All message reading, parsing, indexing, and file exports happen 100% locally on the user's device.
> 2. No SMS/MMS data or contact information is ever uploaded to developer servers.
> 3. Cloud Drive backup (Google Drive `drive.file` scope) and AI Copilot queries (O.D.I.N. - On-Device Intelligence Navigator) are strictly opt-in and only run when explicitly configured and triggered by the user.

---

## 2. 30-Second Reviewer Video Walkthrough Checklist

Google Play requires a link (e.g., an **Unlisted YouTube video** or public Google Drive link) demonstrating the app requesting and using the `READ_SMS` permission on a real device or emulator.

Record a quick 30–45 second screen recording showing:
1. **Fresh Launch & Prominent Disclosure:** Show the first-launch screen or permission prompt explaining why SMS and Contacts access are needed.
2. **Granting Permissions:** Tap "Allow" on the Android system permission dialogs for SMS and Contacts.
3. **Selecting Conversations:** Tap 1 or 2 SMS threads on the main screen (or open a thread preview to exclude specific messages).
4. **Configuring & Exporting a Backup:**
   - Tap the **Export** button.
   - Select formats (e.g., **PDF**, **HTML**, and **Password-Protect Archive (AES-256)**).
   - Tap **Export Now** and show the completed backup file opening in the in-app **Document Viewer**.

---

## 3. Data Safety Form Answers (Play Console -> App content -> Data safety)

Because Muninn operates locally on the user's device by default and does not transmit personal data to any developer-owned backend servers:

- **Does your app collect or share any of the required user data types?**
  - Note: Under Google Play's Data Safety rules, data processed **only locally on the user's device** and not sent off-device to the developer does **not** count as "collected," EXCEPT for third-party SDKs like **Google AdMob** (for free-tier ads) and **Google Play Billing**.
- **SMS or MMS messages:**
  - Processed locally on device for backup/export. If the user opts into Google Drive sync, files are saved directly to the user's own personal Google Drive (`drive.file` folder) using encryption in transit (HTTPS/TLS).
- **Device or other IDs (AdMob SDK):**
  - Collected/Shared: **Yes** (by Google Mobile Ads SDK for serving Banner/Rewarded Ads to free-tier users; disabled automatically for Pro users).
  - Encrypted in transit: **Yes**

---

## 4. Pre-Release Checklist (When Transitioning from Testing to Production)

Before building your final signed `.aab` (Android App Bundle) for Production:
1. **Replace Test AdMob IDs with Live Production AdMob IDs** (only when you are completely done testing on your own device):
   - `app/src/main/AndroidManifest.xml` (`com.google.android.gms.ads.APPLICATION_ID`)
   - `app/src/main/java/com/messagebackup/app/ads/AdManager.kt` (`REWARDED_AD_UNIT_ID`)
   - `app/src/main/java/com/messagebackup/app/ui/main/MainScreen.kt` (Banner `adUnitId`)
2. **Configure OAuth Client ID in Google Cloud Console** (for optional Google Drive sync):
   - Register your **Release Keystore SHA-1 fingerprint** and the **Google Play App Signing SHA-1 fingerprint** in Google Cloud Console under your Android OAuth 2.0 Client ID (`com.nfgodin.muninn`).
3. **Create In-App Products in Play Console** (matching `BillingManager.kt`):
   - `pro_lifetime` (One-time purchase)
   - `pro_monthly` / `pro_yearly` (Subscriptions)
   - `scheduler_unlock` (One-time purchase)
   - `credits_15` / `credits_50` (Consumable products)
