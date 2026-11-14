# Money Manager Android App (Kotlin + Jetpack Compose)

A native Android Money Manager application featuring:
1. **5-Slot Bottom Navigation with Center Hover Button:** `Transactions | Analytics | Add (Hover) | Budget | Settings`
2. **Real-Time Bank SMS Auto-Detection (`BankSmsReceiver` & `BankSmsParser`):** Automatically detects UPI, Card, and Bank alerts in the background, saves them to a Pending Approval queue, and sends an instant push notification so you can categorize them with one tap.
3. **Budget Alerts & Daily Wrap-Up Reminders:** Built with Android `WorkManager` and `NotificationManager` to alert you at 80% budget usage and send daily spending reminders.
4. **Automated Cloud APK Build (`.github/workflows/android-build.yml`):** Push or upload these files to a GitHub repository and GitHub Actions will automatically compile the `.apk` and publish it to your repository's **Releases** and **Actions Artifacts**.
