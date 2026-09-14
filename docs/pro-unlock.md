# Battery Widget Pro

## Product decision

User requested all high/medium features first, followed by Google Play paid-unlock exploration. The feature baseline was completed and tested before Billing was added (23 JVM tests, 9 API 36 instrumentation tests; snapshot in `play/artifacts/3.3-33/features-before-billing`).

Use a single, non-consumable one-time purchase, `battery_widget_pro`. A subscription is not justified by the current local utilities and absence of a recurring hosted service.

| Free | Pro (one-time purchase) |
| --- | --- |
| Phone and separate Bluetooth widgets | Responsive combined phone + Bluetooth widget |
| Manual refresh, monitoring diagnostics/recovery | Phone charge-target reminders |
| Device visibility, alias and ordering | Selected Bluetooth device low-battery reminders |
| Freshness/unknown/cached status | Quiet hours and reminder deduplication |
| Per-widget colors, device selection and tap action | |

Configuration previews are available without buying. Saving a combined widget or enabling reminders requires verified Pro entitlement. Existing settings survive entitlement loss; combined widgets render as a free phone widget and reminders stop. Users can still disable reminders without Pro. Framework reflection now uses HiddenApiBypass 6.1, replacing FreeReflection at the user's explicit request on 2026-09-11. Pro does not promise faster OS scheduling or guaranteed real-time Bluetooth updates.

## Historical Console inspection (2026-09-09)

- App: `com.github.xckevin927.android.battery.widget`.
- Publishing overview and application list show **32 (3.2) under review**. Earlier notes saying 32 was unsubmitted are superseded by this read. This feature task did not resubmit, cancel or modify that review.
- Normal Google Play payments profile already exists. No bank/tax/payment settings were changed.
- One-time products page has no products and asks for an APK with the `BILLING` permission before creation. Publisher API `gplay onetimeproducts list` also returned an empty catalog.
- Public license verification key was read from Monetization setup. Only that public RSA key belongs in the app; the service-account private key is never included.
- No product, offer, price, purchase, test distribution or release has been activated by this task.

## Integration choices

- Billing **8.0.0** preserves the application's Android 5 / API 21 minimum. Billing 8.1 raises its minimum to API 23. Version 8 is allowed for new uploads until **2027-08-31**; revisit the minimum supported Android version before then. [Official release notes](https://developer.android.com/google/play/billing/release-notes), [deprecation schedule](https://developer.android.com/google/play/billing/deprecation-faq).
- Query Play for localized product details and price. Never show a hardcoded price or launch a purchase without an available product.
- Unlock only a verified `PURCHASED` non-consumable receipt; `PENDING` does not grant Pro. Do not consume this product. Acknowledge delivery and retry unacknowledged purchases on foreground refresh/restore. [Integration guide](https://developer.android.com/google/play/billing/integrate).
- Recheck owned purchases when the app enters the foreground. Offline/query errors retain a previously verified receipt. A successful complete owned-purchase query can remove revoked entitlement.
- Client verification is an initial implementation for this local utility. It is not equivalent to server verification: a modified app can bypass checks, offline refund revocation is delayed, and acknowledgement can fail if the user stays offline. Google recommends a secure backend and RTDN for stronger validation and timely acknowledgement; unacknowledged orders can be refunded after three days. [Security guidance](https://developer.android.com/google/play/billing/security).

## Active Console configuration (2026-09-11)

- Product ID: `battery_widget_pro`
- Purchase option ID: `buy`
- Purchase type: **Buy**; not rent, no expiry, no multi-quantity, no consumption.
- English name: `Battery Widget Pro`
- English description: `Unlock combined phone and Bluetooth widgets, charge-target reminders, Bluetooth low-battery reminders, and quiet hours with one purchase.`
- Chinese name: `Battery Widget Pro`
- Chinese description: `一次购买，解锁手机与蓝牙组合组件、充电目标提醒、蓝牙低电量提醒和免打扰时段。`
- Owner-approved base price: **US$1.99**, one-time purchase. Play conversion supplies regional prices; the app continues to read the localized price from Play.
- Created and activated on 2026-09-11: purchase option `buy`, `ACTIVE`, legacy-compatible, available in 173 regions. Verified examples: US USD 1.99, Hong Kong HKD 15.00, Iraq IQD 2,600. English and Simplified Chinese listings are saved.
- Signed 3.3 (33) was uploaded, validated and published to internal testing. The previously paused track is now active, and the Samsung work account accepted its test invitation with the owner's approval. Production remains published 3.2 (32).
- Both Google accounts already on the Samsung were added to the existing selected license-test list; its original member is preserved. Private account evidence stays in ignored artifacts.
- At 13:25 HKT, live catalog queries started returning `buy` at IQD 2,600 without a new APK or catalog change. Samsung tests subsequently passed cancellation, test-card decline, approved purchase, both slow pending outcomes, acknowledgement, purchased-user offline launch, missing-receipt recovery/manual restore, and test refund with revocation after successful online query. Actual alert and combined-widget save checks passed both locked and purchased states (2 cases per state, no skips). The final free test order is PURCHASED and acknowledged=true; normal Pro is unlocked and original preferences/widget ID 8 are preserved.
- A reproduced UX issue remains: an always-declined test payment shows device-level Billing unavailability and disables buying until Restore refreshes it. Offline ownership also survives an online refund until the device reconnects and successfully queries Play. No production Billing change or real charge was made. Full uninstall/reinstall, purchase-account switching and Play-signed installation checkout remain untested.
- See [2026-09-11 Billing validation](billing-validation-2026-09-11.md) for exact test boundaries and remaining work.

Products need at least one purchase option and regional pricing/availability. [Console one-time product setup](https://support.google.com/googleplay/android-developer/answer/16430488).

## Before making Pro available to customers

1. Completed 2026-09-11: upload a signed Billing-enabled artifact to internal testing; production 32 remains unchanged.
2. Completed 2026-09-11: create and activate the product/purchase option, confirm the owner-approved price and regions, and configure a license tester. Checkout must still show a test payment method; no real-money purchase is authorized.
3. Core live flows completed 2026-09-11: purchase → acknowledgement → unlock, cancel, decline, pending → approved/declined, missing-receipt restore, refund/revoke, offline launch, and actual paid-feature save gates. Still check complete reinstall/restore and purchase-account changes; resolve the reproduced decline message/retry issue. Local unit tests cannot replace these Play flows. [Official testing guide](https://developer.android.com/google/play/billing/test).
4. Recheck store feature descriptions, purchase disclosure, privacy/Data safety against the final Billing/Firebase behavior, and test the actual signed release. Then separately submit the new release.

## Validation

Final evidence: `play/artifacts/3.3-33/with-pro/validation.json` and logs. All five Gradle checks passed (`assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, `lintRelease`, `bundleRelease`). **33 JVM tests and 14 instrumentation tests passed**, no skips. Lint: **0 errors / 194 warnings**. `bundletool validate` passed; decoded bundle manifest confirms 33/3.3, min21, target36 and Billing8.0.0.

Tests cover signed-field tampering and pending receipts, stale query ordering, cached-entitlement revocation, actual locked Pro configuration flow, unavailable catalog recovery, receipt interruption/corruption handling, alert execution gates and the existing widget/notification regressions. Independent review verified callback ownership/timeouts, acknowledgement retry, pending cache clearing and entitlement-change rendering/reminder evaluation. No production test bypass was added.

The debug APK and **unsigned** release AAB in that historical folder describe the implementation-stage build. A later signed AAB was uploaded to internal testing on 2026-09-11; the earlier 23 + 9 results describe the feature baseline before monetization. These earlier automated results alone did not prove a Google Play purchase. Subsequent real license-test transactions, acknowledgement, restoration, refund/revocation, pending outcomes and paid save gates are documented in the linked Billing report. Bluetooth hardware and conclusive long-running standby/refresh behavior remain outside that Billing validation.
