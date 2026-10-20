# Privacy Policy for Muninn: Message Memory & Vault

**Last updated:** October 20, 2026

**Muninn: Message Memory & Vault** ("we", "our", or "us") is committed to protecting your privacy. This Privacy Policy explains how information is handled when you use the Muninn mobile application on Android.

---

## 1. On-Device Processing & Zero Data Collection Principle
- **100% On-Device Architecture:** All core operations performed by Muninn—including reading your SMS/MMS messages, resolving local contact names, computing Conversation Insights, detecting promises in Commitment Radar, formatting documents (PDF, HTML, Desktop Offline Viewer, CSV, Markdown, Plain Text), and packaging AES-256 encrypted zip archives—occur exclusively on your local device.
- **No Proprietary Cloud Servers:** We do **not** operate any proprietary backend servers to collect, store, inspect, transmit, or sell your personal text messages, photos, audio attachments, phone numbers, or contact records.
- **User-Controlled Export & Cloud Storage:** When you choose to export your messages or connect cloud providers (Google Drive, Microsoft OneDrive, Dropbox, or WebDAV / Local NAS), the files are uploaded directly from your device to your personal account using official APIs and OAuth credentials. We never have access to your cloud accounts or stored files.

---

## 2. Artificial Intelligence (O.D.I.N. AI & Commitment Radar)
Muninn provides an intelligent message analysis engine with strict privacy controls:
- **Local AI Mode (Ollama):** If you configure local AI using an on-premise Ollama endpoint on your private home network, **100% of your message data stays entirely within your private local network**. Zero data leaves your network.
- **Cloud AI Mode (Google Gemini BYOK):** If you choose to enable cloud AI assistance, queries are executed using your own personal Gemini API key directly to Google's official Gemini API endpoints. Message excerpts are sent solely to answer your explicit query and are governed by Google's Cloud API Privacy Terms. We do not intermediate, intercept, or log these queries.
- **Commitment & Promise Radar:** Scans for promises, financial amounts, and deadlines using fast on-device pattern recognition and regex. No message data is transmitted to any third party for radar scanning.

---

## 3. Biometric & Vault Security
- **App Lock & Media Vault Cloaking:** You may secure Muninn with your device's biometric authentication (Fingerprint / Face Unlock) or a custom Master Security PIN.
- **Local Cryptographic Storage:** Biometric verification is managed directly by Android's native `BiometricPrompt` hardware security module. PINs are salted and hashed using SHA-256 before being stored in private local device preferences. Neither your raw biometric data nor your plain PIN ever leaves your device.

---

## 4. Permissions Used and Why
Muninn requests runtime Android permissions strictly when needed for core functionality:
- `android.permission.READ_SMS` & `RECEIVE_MMS`: Required to read conversation threads and extract embedded multimedia attachments so they can be archived, searched, and formatted into documents on your device.
- `android.permission.READ_CONTACTS`: Optional. Used solely to match phone numbers with local address book names so your exports and thread list display familiar contact names. You can disable contact resolution at any time in Settings.
- `android.permission.INTERNET` & `ACCESS_NETWORK_STATE`: Required for Google AdMob, Google User Messaging Platform (UMP), Google Play In-App Billing, and user-initiated cloud backup syncs.
- `android.permission.POST_NOTIFICATIONS`: Used only to notify you of completed scheduled auto-backups and export status.
- `android.permission.USE_BIOMETRIC`: Used strictly to authenticate the user before unlocking the app or uncloaking the private Media Vault.

---

## 5. Third-Party Services
Muninn integrates compliant third-party SDKs for monetization and billing:
- **Google AdMob & User Messaging Platform (UMP):** Used to display banner, interstitial, rewarded video, and rewarded interstitial ads. For users in the European Economic Area (EEA), the UK, and regions requiring consent, consent is gathered via Google UMP prior to ad requests. [Google Privacy Policy](https://policies.google.com/privacy)
- **Google Play In-App Billing:** Used to process one-time in-app purchases ("Remove Ads & Pro Lifetime") and voluntary Tip Jar contributions. All payment transactions are handled securely by Google Play.

---

## 6. Children’s Privacy
Our application does not knowingly collect, transmit, or solicit any personal information from children under the age of 13.

---

## 7. Changes to This Privacy Policy
We may update this Privacy Policy periodically. Any changes will be reflected in the updated document with a new effective date.

---

## 8. Contact Us
If you have any questions or feedback regarding this Privacy Policy or your privacy while using Muninn, please reach out through our official GitHub repository:
- **Repository:** https://github.com/NfgOdin/MessageBackupApp
- **Website:** https://NfgOdin.github.io/MessageBackupApp/
