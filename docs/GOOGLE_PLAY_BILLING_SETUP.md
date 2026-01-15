# Google Play Billing Setup Guide

This guide covers setting up in-app subscriptions for VoiceInk Android using Google Play Billing and RevenueCat.

## Overview

**Two options for implementation:**
1. **RevenueCat (Recommended)** - Handles receipts, analytics, and cross-platform sync
2. **Google Play Billing Library directly** - More control, more code

This guide covers both, with RevenueCat as the primary recommendation.

---

## Part 1: Google Play Console Setup

### 1.1 Create a Google Play Developer Account

1. Go to [Google Play Console](https://play.google.com/console)
2. Pay the one-time $25 registration fee
3. Complete identity verification (can take 24-48 hours)

### 1.2 Create Your App in Play Console

1. Click **"Create app"**
2. Fill in:
   - App name: `VoiceInk`
   - Default language: English
   - App or game: App
   - Free or paid: Free (with in-app purchases)
3. Accept declarations and create

### 1.3 Set Up Your App for Testing

Before you can test purchases, you need to:

1. **Upload a signed APK/AAB** (at least to Internal Testing track)
   ```bash
   # Generate a signed release build
   ./gradlew bundleRelease
   ```

2. **Complete the Store Listing** (basic info required):
   - App description
   - Screenshots (phone required)
   - App icon
   - Feature graphic
   - Privacy policy URL

3. **Complete the App Content questionnaire**:
   - Privacy policy
   - Ads declaration
   - Content rating
   - Target audience
   - Data safety

### 1.4 Create Subscription Products

1. Go to **Monetize > Products > Subscriptions**
2. Click **"Create subscription"**

#### Monthly Subscription
- **Product ID:** `voiceink_pro_monthly`
- **Name:** VoiceInk Pro Monthly
- **Description:** Unlimited transcription, auto-punctuation, priority processing
- **Base plan:**
  - Billing period: 1 month
  - Price: $4.99 USD
  - Free trial: 7 days (optional)
  - Grace period: 7 days

#### Yearly Subscription
- **Product ID:** `voiceink_pro_yearly`
- **Name:** VoiceInk Pro Yearly
- **Description:** Unlimited transcription, auto-punctuation, priority processing - Save 33%
- **Base plan:**
  - Billing period: 1 year
  - Price: $39.99 USD
  - Free trial: 7 days (optional)
  - Grace period: 7 days

3. **Activate** both subscriptions

### 1.5 Set Up License Testing

1. Go to **Settings > License testing**
2. Add your test email addresses
3. Set license response to **"RESPOND_NORMALLY"**

> **Important:** Testers must use these exact Google accounts on their test devices.

---

## Part 2: RevenueCat Setup (Recommended)

### 2.1 Create RevenueCat Account

1. Go to [RevenueCat Dashboard](https://app.revenuecat.com)
2. Sign up with Google/GitHub/Email
3. Create a new project: `VoiceInk`

### 2.2 Connect Google Play Store

1. In RevenueCat, go to **Project Settings > Apps**
2. Click **"+ New"** and select **Google Play Store**
3. Enter your **Package Name:** `com.voiceink.android`

### 2.3 Set Up Google Play Service Account

RevenueCat needs a service account to verify purchases:

1. **In Google Cloud Console:**
   - Go to [Google Cloud Console](https://console.cloud.google.com)
   - Create a new project or select existing
   - Go to **IAM & Admin > Service Accounts**
   - Click **"+ Create Service Account"**
   - Name: `revenuecat-integration`
   - Click **Create and Continue**
   - Skip role assignment, click **Done**

2. **Create a key:**
   - Click on the service account
   - Go to **Keys** tab
   - Click **Add Key > Create new key**
   - Select **JSON**
   - Download and save securely

3. **In Google Play Console:**
   - Go to **Settings > API access**
   - Click **"Link"** to link your Cloud project
   - Find your service account and click **"Grant access"**
   - Set permissions:
     - **Financial data:** View financial data
     - **App access:** All apps (or select VoiceInk)
   - Click **Invite user**

4. **In RevenueCat:**
   - Upload the JSON key file
   - Click **Save**

### 2.4 Create Products in RevenueCat

1. Go to **Products** in your RevenueCat project
2. Click **"+ New"**
3. Add products matching your Play Console:

| Identifier | Store | Product ID |
|------------|-------|------------|
| pro_monthly | Google Play | voiceink_pro_monthly |
| pro_yearly | Google Play | voiceink_pro_yearly |

### 2.5 Create Entitlements

1. Go to **Entitlements**
2. Click **"+ New"**
3. Create entitlement:
   - **Identifier:** `pro`
   - **Description:** VoiceInk Pro access
4. Attach both products to this entitlement

### 2.6 Create Offerings

1. Go to **Offerings**
2. The default offering should exist
3. Add packages:
   - **$rc_monthly** → pro_monthly
   - **$rc_annual** → pro_yearly

### 2.7 Get Your API Key

1. Go to **Project Settings > API Keys**
2. Copy your **Public SDK Key** (starts with `goog_...`)
3. Keep this safe - you'll need it for the app

---

## Part 3: Android App Integration

### 3.1 Add RevenueCat Dependency

In `app/build.gradle.kts`:

```kotlin
dependencies {
    // RevenueCat
    implementation("com.revenuecat.purchases:purchases:7.5.0")
}
```

### 3.2 Initialize RevenueCat

In `VoiceInkApplication.kt`:

```kotlin
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

@HiltAndroidApp
class VoiceInkApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize RevenueCat
        Purchases.logLevel = LogLevel.DEBUG // Remove in production
        Purchases.configure(
            PurchasesConfiguration.Builder(this, "goog_YOUR_API_KEY_HERE")
                .build()
        )
    }
}
```

### 3.3 Create Subscription Repository

Create `data/subscription/RevenueCatSubscriptionRepository.kt`:

```kotlin
package com.voiceink.android.data.subscription

import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.models.StoreProduct
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class RevenueCatSubscriptionRepository @Inject constructor() : SubscriptionRepository {

    private val _subscriptionStatus = MutableStateFlow(SubscriptionStatus())
    override val subscriptionStatus: Flow<SubscriptionStatus> = _subscriptionStatus

    init {
        refreshSubscriptionStatus()
    }

    fun refreshSubscriptionStatus() {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                val isPro = customerInfo.entitlements["pro"]?.isActive == true
                _subscriptionStatus.value = SubscriptionStatus(
                    tier = if (isPro) SubscriptionTier.PRO else SubscriptionTier.FREE,
                    expirationDate = customerInfo.entitlements["pro"]?.expirationDate
                )
            }

            override fun onError(error: PurchasesError) {
                // Keep current status on error
            }
        })
    }

    suspend fun getOfferings() = suspendCancellableCoroutine { cont ->
        Purchases.sharedInstance.getOfferings({ offerings ->
            cont.resume(offerings)
        }, { error ->
            cont.resumeWithException(Exception(error.message))
        })
    }

    suspend fun purchase(activity: android.app.Activity, product: StoreProduct): Result<CustomerInfo> {
        return suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.purchase(
                activity = activity,
                storeProduct = product,
                onSuccess = { _, customerInfo ->
                    refreshSubscriptionStatus()
                    cont.resume(Result.success(customerInfo))
                },
                onError = { error, userCancelled ->
                    if (userCancelled) {
                        cont.resume(Result.failure(Exception("Purchase cancelled")))
                    } else {
                        cont.resume(Result.failure(Exception(error.message)))
                    }
                }
            )
        }
    }

    suspend fun restorePurchases(): Result<CustomerInfo> {
        return suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.restorePurchases(
                onSuccess = { customerInfo ->
                    refreshSubscriptionStatus()
                    cont.resume(Result.success(customerInfo))
                },
                onError = { error ->
                    cont.resume(Result.failure(Exception(error.message)))
                }
            )
        }
    }
}
```

### 3.4 Update the Pro Modal to Purchase

Update the `onSubscribe` callback in `SettingsScreen.kt`:

```kotlin
// In SettingsScreen composable
val activity = LocalContext.current as Activity
val coroutineScope = rememberCoroutineScope()

// In ProFeaturesModal
onSubscribe = {
    coroutineScope.launch {
        try {
            val offerings = subscriptionRepository.getOfferings()
            val product = offerings.current?.monthly?.product
            if (product != null) {
                subscriptionRepository.purchase(activity, product)
            }
        } catch (e: Exception) {
            // Handle error
        }
    }
}
```

---

## Part 4: Testing

### 4.1 Test with License Testers

1. Add test accounts in Play Console > License Testing
2. Install app on device with test account
3. Purchases will be free but simulate real flow

### 4.2 Test Subscription States

RevenueCat test cards allow testing:
- Successful purchase
- Payment declined
- Subscription renewal
- Subscription cancellation
- Grace period
- Account hold

### 4.3 Debug Checklist

- [ ] API key is correct
- [ ] Package name matches Play Console
- [ ] Product IDs match exactly
- [ ] App is uploaded to Play Console (any track)
- [ ] Service account has correct permissions
- [ ] Test account is added to License Testing
- [ ] Test device uses the test Google account

---

## Part 5: Go Live Checklist

Before releasing:

- [ ] Remove `LogLevel.DEBUG` from RevenueCat init
- [ ] Test all purchase flows
- [ ] Test restore purchases
- [ ] Verify subscription status updates correctly
- [ ] Set up RevenueCat webhooks (optional)
- [ ] Configure subscription grace period
- [ ] Add subscription management deep link
- [ ] Privacy policy mentions subscription

---

## Useful Links

- [Google Play Billing Documentation](https://developer.android.com/google/play/billing)
- [RevenueCat Android SDK Docs](https://www.revenuecat.com/docs/android-native-sdk)
- [RevenueCat Dashboard](https://app.revenuecat.com)
- [Google Play Console](https://play.google.com/console)
- [Google Cloud Console](https://console.cloud.google.com)

---

## Quick Reference: Product IDs

| Product | Play Console ID | RevenueCat ID |
|---------|-----------------|---------------|
| Monthly | voiceink_pro_monthly | pro_monthly |
| Yearly | voiceink_pro_yearly | pro_yearly |
| Entitlement | - | pro |

---

*Last updated: January 2026*
