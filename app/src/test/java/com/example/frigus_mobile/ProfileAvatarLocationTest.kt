package com.example.frigus_mobile

import com.example.frigus_mobile.data.repository.ProfileAvatarLocation
import org.junit.Assert.*
import org.junit.Test

class ProfileAvatarLocationTest {
    @Test fun onlyPublicGoogleHttpsImagesAreAccepted() {
        assertNotNull(ProfileAvatarLocation.googleHttpsUrl("https://lh3.googleusercontent.com/a/photo=s96-c"))
        assertNotNull(ProfileAvatarLocation.googleHttpsUrl("https://yt3.ggpht.com/photo"))
        listOf("http://lh3.googleusercontent.com/photo", "https://googleusercontent.com.evil.example/photo",
            "https://evil.example/googleusercontent.com", "https://user:password@lh3.googleusercontent.com/photo",
            "https://lh3.googleusercontent.com:8080/photo", "file:///tmp/photo", "https://127.0.0.1/photo").forEach {
            assertNull(ProfileAvatarLocation.googleHttpsUrl(it))
        }
    }

    @Test fun cachedPhotosAreSeparatedByAccountAndImageSource() {
        val first = ProfileAvatarLocation.cacheKey("account-a", "https://lh3.googleusercontent.com/a")
        assertEquals(64, first.length)
        assertNotEquals(first, ProfileAvatarLocation.cacheKey("account-b", "https://lh3.googleusercontent.com/a"))
        assertNotEquals(first, ProfileAvatarLocation.cacheKey("account-a", "https://lh3.googleusercontent.com/b"))
    }
}
