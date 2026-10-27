package com.example.frigus_mobile

import com.example.frigus_mobile.data.api.MobileHttp
import com.example.frigus_mobile.push.PushNotifications
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRoutesTest {
    @Test fun mobileGatewayAllowsOnlyNotificationAndTokenRoutes() {
        val id = "123e4567-e89b-12d3-a456-426614174000"
        assertTrue(MobileHttp.allowed("GET", "/mobile/notifications"))
        assertTrue(MobileHttp.allowed("PATCH", "/mobile/notifications/$id/read"))
        assertTrue(MobileHttp.allowed("DELETE", "/mobile/notifications"))
        assertTrue(MobileHttp.allowed("POST", "/mobile/push-tokens"))
        assertTrue(MobileHttp.allowed("DELETE", "/mobile/push-tokens"))
        assertFalse(MobileHttp.allowed("PATCH", "/mobile/notifications/not-an-id/read"))
        assertFalse(MobileHttp.allowed("POST", "/mobile/notifications"))
        assertFalse(MobileHttp.public("/mobile/push-tokens"))
    }

    @Test fun pushIsLimitedToExpirationAndShoppingListReminders() {
        assertTrue(PushNotifications.supported("PRODUCT_NEAR_EXPIRATION"))
        assertTrue(PushNotifications.supported("SHOPPING_LIST_REMINDER"))
        assertFalse(PushNotifications.supported("GROUP_MEMBER_JOINED"))
        assertFalse(PushNotifications.supported(null))
    }
}
