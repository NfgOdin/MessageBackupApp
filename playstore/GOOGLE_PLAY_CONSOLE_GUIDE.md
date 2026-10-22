# Google Play Console Submission & Review Guide for Muninn

This guide walks you through every single screen and questionnaire in the **Google Play Console** to ensure **100% compliance** and a smooth review approval on your very first submission.

---

## 📁 1. Your Ready-to-Upload Assets
All required release files have been prepared and organized inside the `playstore/` folder in your project root:
1. **App Bundle (AAB):** `playstore/app-release.aab` *(20.2 MB, Signed with your release keystore)*
2. **App Icon (512x512):** `playstore/icon_512x512.png` *(High-res 32-bit PNG)*
3. **Feature Graphic (1024x500):** `playstore/feature_graphic_1024x500.png` *(High-res landscape banner)*
4. **Privacy Policy URL:** `https://nfgodin.github.io/muninn/`
5. **Listing Copy:** `playstore/STORE_LISTING.md`

---

## 📋 2. Step-by-Step Console Walkthrough

### Step A: Create App
1. Go to **[Google Play Console](https://play.google.com/console/)**.
2. Click **Create app**.
3. **App name:** `Muninn: Message Memory Vault`
4. **Default language:** `English (United States) - en-US`
5. **App or game:** `App`
6. **Free or paid:** `Free` (Contains optional in-app purchases and ads)
7. Check the declarations for Developer Program Policies & US export laws, then click **Create app**.

---

### Step B: App Content & Policy Questionnaires (Under "Policy and programs > App content")

Complete each of the required policy forms as follows:

#### 1. Privacy Policy
- **Privacy Policy URL:** `https://nfgodin.github.io/muninn/`

#### 2. App Access
- Select: **"All functionality is available without special access"**
  *(Explain if prompted: "All core features including SMS backup, exports, offline viewer, and Raven Mesh work with zero account required. Google Sign-In is optional.")*

#### 3. Ads
- Select: **"Yes, my app contains ads"**
  *(Muninn uses Google AdMob for banner, interstitial, and rewarded ads).*

#### 4. Content Rating (IARC)
- **Email:** Your developer contact email.
- **Category:** Select **Utility, Productivity, Communication, or Other**.
- Question answers:
  - *Does the app share user's current physical location?* No (Raven Mesh location is optional peer-to-peer pin only, never stored on servers).
  - *Does the app allow users to purchase digital goods?* Yes (In-app purchases for Pro unlock / tips).
  - *Does the app contain offensive language, violence, or drugs?* No.
- Expected Rating: **Everyone (E / PEGI 3 / USK 0)**.

#### 5. Target Audience and Content
- Target age group: Check **18 and over** (and optionally 13-17).
- Could your store listing appeal to children? **No**.

#### 6. News Apps
- Select: **No**.

#### 7. COVID-19 Contact Tracing & Status Apps
- Select: **My app is not a COVID-19 contact tracing or status app**.

#### 8. Data Safety (VERY IMPORTANT)
Complete the Data Safety form with these answers:
- **Does your app collect or share any of the required user data types?**
  - Select **Yes** (because Google AdMob and Google Play Billing collect device identifiers and purchase tokens).
- **Is all of the user data collected by your app encrypted in transit?**
  - Select **Yes**.
- **Do you provide a way for users to request that their data be deleted?**
  - Select **Yes** (Local data is deleted by clearing app storage; cloud backup files can be deleted directly from user's Google Drive).
- **Data Types to declare:**
  1. **Messages (SMS/MMS):**
     - Collected: **No** (Processed locally on device).
     - Shared: **No**.
  2. **Contacts:**
     - Collected: **No** (Processed locally on device).
     - Shared: **No**.
  3. **Audio (Voice PTT):**
     - Collected: **No** (Streamed directly peer-to-peer / locally).
     - Shared: **No**.
  4. **Device or other IDs (Advertising ID):**
     - Collected: **Yes** (Collected by Google Mobile Ads SDK for advertising, analytics, and fraud prevention).
     - Shared: **Yes** (Shared with Google AdMob).
     - Ephemeral: No.
     - Is this data required: Yes (for free ad-supported tier).
  5. **Financial info (Purchase history):**
     - Collected: **Yes** (Handled securely by Google Play Billing for in-app purchases).
     - Shared: **No**.

#### 9. Government Apps
- Select: **No**.

#### 10. Financial Features
- Select: **My app doesn't provide any financial features** (standard Google Play in-app purchases do not count as a banking/loan app).

---

### ⚠️ Step C: SMS and Call Log Permissions Declaration Form (CRITICAL)

Because Muninn declares `android.permission.READ_SMS`, Google Play will prompt you to complete the **SMS and Call Log Permissions Declaration**:

1. **Core Feature / Permitted Use Case:**
   - Select: **"SMS/MMS Backup and restore"**
   *(Note: This is an explicitly approved, official exception under Google Play's SMS and Call Log policy!)*

2. **Declaration Statement / Justification Text:**
   Copy and paste this exact text into the description field:
   ```
   Muninn is an SMS and MMS message backup, archival, and document export application. 
   The android.permission.READ_SMS permission is strictly necessary for the primary core functionality of the application: to read the user's stored SMS/MMS messages and generate structured, searchable offline document archives (court-ready PDF with photos, offline interactive HTML viewer, spreadsheet CSV, Markdown, and plain text) with optional AES-256 encryption directly on the user's device. 
   Muninn operates under a strict zero-data-collection policy: message data is never transmitted to developer servers. Before requesting the permission at runtime, Muninn presents a prominent in-app disclosure modal detailing why the permission is required. Without READ_SMS, the core purpose of the application cannot function.
   ```

3. **Demonstration Video Link:**
   - Google Play reviewers typically require an unlisted YouTube video showing the feature in action.
   - Record a brief 30–60 second phone screen video showing:
     1. Opening the Muninn app on cold launch.
     2. The prominent in-app disclosure dialog appearing ("SMS Access Required").
     3. Tapping "Continue" and granting the Android system SMS permission.
     4. The conversation list loading and tapping "Export" to show a backup being created.
   - Upload this video to YouTube as **Unlisted** and paste the link into the declaration form.

---

## 💰 3. In-App Products Setup (Under "Monetize > Products > In-app products")

Create the following in-app products to match `BillingManager.kt`:

| Product ID | Name | Default Price | Type | Description |
|---|---|---|---|---|
| `remove_ads_permanent` | Pro Lifetime License | $4.99 | One-time | Permanently remove all ads and unlock all features forever |
| `auto_backup_standalone` | Standalone Auto-Backup | $0.99 | One-time | Unlock scheduled automatic background backups |
| `huginn_live_dispatch_standalone` | Huginn Message Dispatcher | $0.99 | One-time | Unlock Huginn quick SMS dispatcher & draft polisher |
| `odin_eye_of_mimir_standalone` | O.D.I.N. Eye of Mimir | $0.99 | One-time | Unlock unrestricted O.D.I.N. AI archive queries |
| `pack_ansuz_100` | 100 Ansuz Runes | $0.99 | Consumable | 100 AI Thought Runes for Huginn message drafting |
| `pack_othala_100` | 100 Othala Runes | $0.99 | Consumable | 100 AI Archive Runes for O.D.I.N. deep queries |
| `pack_jera_10` | 10 Jera Runes | $0.99 | Consumable | 10 Phone-to-Phone Migration transfer credits |
| `tip_coffee_099` | Tip Jar: Cup of Coffee | $0.99 | Consumable | Voluntary developer support tip |
| `tip_lunch_299` | Tip Jar: Delicious Lunch | $2.99 | Consumable | Voluntary developer support tip |
| `tip_pizza_499` | Tip Jar: Pizza Feast | $4.99 | Consumable | Voluntary developer support tip |

*Note: You can activate just `remove_ads_permanent` first if you prefer to launch with a simple Pro unlock! The app handles any unconfigured products gracefully with safe fallback prices.*

---

## 🚀 4. Uploading the Release Bundle

1. In Google Play Console, navigate to **Release > Production** (or **Testing > Closed testing** if you want to test with 20 testers first).
2. Click **Create new release**.
3. Under **App bundles**, upload:
   `playstore/app-release.aab`
4. **Release name:** `1.0.0 (10000)`
5. **Release notes (en-US):**
   ```
   Initial launch of Muninn: Message Understanding & Neural Intelligence Navigation Nexus!
   • 100% on-device private SMS and MMS backup
   • Export to Court-Ready PDF, Interactive Offline HTML, CSV, and Plain Text
   • O.D.I.N. AI message copilot & Commitment Radar
   • Raven Mesh off-grid PTT walkie-talkie & parcel transfer
   • Encrypted ZIP exports and biometric Media Vault
   ```
6. Click **Next**, review any warnings, and click **Save and submit for review**!
