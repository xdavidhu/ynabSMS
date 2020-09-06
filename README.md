# ynabSMS
💵 Automatically upload transactions to YNAB by parsing SMS alerts from your bank.

There is a 99% chance that you unfortunately can't use this app. But if you are the 1%, this might be really useful.

## The requirements:
- You are Hungarian (magyar vagy)
- You have a bank account at OTP
- You have SMS alerts turned on for transactions
- You use YNAB for budgeting
- You want to import your transactions automatically
- You can build an app from source with Android Studio

If you meet all of these requirements, thats amazing, read on!<br>
If not, but you are a developer and would be interested in porting this app to your own bank, contact me!

## Setting up:
1. Clone the repository
2. Open the project in Android Studio
3. Choose `Build > Generate Signed Bundle / APK...` and follow the instructions
4. Install the APK on your phone
5. Open the app
6. Allow SMS permissions
7. Go to https://app.youneedabudget.com/settings/developer and generate a new `Personal Access Token`
8. Paste the token into the `API Key:` field, and click `UPDATE`
9. Turn on YNAB by tapping the switch in the center
10. Contact me! Let me know if you are using this, so we can discuss any issues / new features!

**Done!** 🎉<br>
Now if any new transaction SMS arrives, ynabSMS will automatically upload it into your YNAB budget!<br>
All you have to do is open YNAB every few days, and categorise the transactions.
