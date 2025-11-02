# Example: Adding Banking Notifications

## Step 1: Add to notification_types.json

Add this to the `notification_types` array:

```json
{
  "id": "banking",
  "name": "Banking",
  "priority": "high",
  "enabled": true,
  "keywords": [
    "transaction", "payment", "received", "sent", "balance",
    "debit", "credit", "transfer", "withdrawal", "deposit",
    "otp", "verification", "alert", "fraud"
  ],
  "apps": [
    "com.hdfc.bank",
    "com.icici.bank",
    "com.sbi.mobile",
    "com.axis.mobile",
    "com.kotak.mobile",
    "com.paytm",
    "com.phonepe.app",
    "com.google.android.apps.nbu.paisa.user"
  ],
  "enabledApps": [],
  "disabledApps": [],
  "title_patterns": [
    "HDFC", "ICICI", "SBI", "Axis", "Kotak", 
    "Paytm", "PhonePe", "GPay", "Banking"
  ],
  "mcu_type": "banking"
}
```

## Step 2: Add to mcu_formats.json

Add this to the `notification_types` section:

```json
"banking": {
  "type": "banking",
  "fields": ["bank", "amount", "type", "balance"]
}
```

## Step 3: MCU Implementation

Your ESP32 can now handle banking notifications:

```cpp
else if (type == "banking") {
    String bank = notification["bank"];
    String amount = notification["amount"];
    String transactionType = notification["type"];
    String balance = notification["balance"];
    
    // Show banking notification on display
    showBankingNotification(bank, amount, transactionType);
    
    // Flash LED for important transactions
    if (amount.toFloat() > 1000) {
        flashHighValueTransactionLED();
    } else {
        flashNormalTransactionLED();
    }
    
    // Log transaction for security
    logTransaction(bank, amount, transactionType, balance);
}
```

## Result

Now when you receive money or make payments, your ESP32 will receive:

```json
{
  "type": "banking",
  "bank": "HDFC",
  "amount": "₹5,000",
  "type": "received",
  "balance": "₹25,000"
}
```

**Great for financial tracking!** Get instant notifications about all your banking activities.
