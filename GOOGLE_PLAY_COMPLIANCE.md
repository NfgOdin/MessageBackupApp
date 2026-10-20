# Google Play Store Compliance & Submission Guide

This document outlines the exact compliance requirements, video demonstration instructions, and declaration form details required to ensure **Muninn** passes Google Play review without rejection.

---

## 1. Sensitive Permissions Declared
The app requests:
- `android.permission.READ_SMS`
- `android.permission.RECEIVE_MMS`
- `android.permission.READ_CONTACTS` (Optional)

---

## 2. Google Play Permissions Declaration Form
When preparing your Play Console store release, navigate to:
**Policy and Programs > App Content > SMS and Call Log Permissions > Manage**.

### A. Permitted Use Case Selection
- **Select:** `Backup and restore for SMS or calls`
- **Do NOT select:** `Default SMS handler` (since we are using the non-intrusive backup utility exception model).

### B. Core Functionality Justification Text
Copy and paste the following into the declaration form text box:
> *"Muninn is a local backup and utility application that allows users to export and archive their SMS and MMS conversation histories into cleanly formatted Markdown, HTML, and Plain Text files on their device. The app requires READ_SMS and RECEIVE_MMS strictly to query past conversation threads, format the message history (including MMS images), and package them into local documents. All data is processed entirely on-device and is never transmitted to external servers without explicit user-initiated sharing."*

### C. Prominent Disclosure Requirement
Google Play requires a clear in-app disclosure **prior** to showing the Android OS permission prompt.
- This is fully implemented in [`SmsPermissionDisclosureDialog.kt`](file:///c:/Users/jonat/Documents/Antigravity%20Projects/Google%20Play%20Apps/MessageBackupApp/app/src/main/java/com/messagebackup/app/ui/components/SmsPermissionDisclosureDialog.kt).
- When a user taps *"Grant SMS Access"*, the disclosure modal appears explaining *why* SMS access is required and confirming that data remains strictly on-device.
- Only when the user clicks *"Continue"* does the system runtime permission dialog trigger.

### D. Demonstration Video (Required by Google Reviewers)
You must provide a publicly accessible video link (e.g. YouTube Unlisted or Google Drive link) demonstrating the permissions in action:
1. Show app launch.
2. Show the in-app disclosure dialog appearing.
3. Show the user accepting the disclosure and granting the OS SMS permission.
4. Show the conversation threads populating.
5. Show selecting a thread and initiating an export into Markdown / ZIP.

---

## 3. Google AdMob & UMP Compliance (EU / UK)
- **User Messaging Platform (UMP) SDK:** Integrated in [`AdManager.kt`](file:///c:/Users/jonat/Documents/Antigravity%20Projects/Google%20Play%20Apps/MessageBackupApp/app/src/main/java/com/messagebackup/app/ads/AdManager.kt) to gather GDPR/EEA user consent before initializing AdMob.
- **Rewarded Ad Flow:** When the user taps Export, an explicit dialog notifies the user that a sponsored video ad will play before export packaging begins.
- **In-App Purchase ("Remove Ads Forever"):** Integrated with Google Play Billing 7 (`BillingManager.kt`). Upon purchasing, all banners and rewarded ad gates are permanently disabled.
