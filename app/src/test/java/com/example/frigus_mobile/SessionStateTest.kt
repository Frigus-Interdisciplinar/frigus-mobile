package com.example.frigus_mobile

import com.example.frigus_mobile.data.local.SessionState
import com.example.frigus_mobile.data.model.UserResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateTest {
    private val user = UserResponse(id = "123e4567-e89b-12d3-a456-426614174000")

    @Test fun restoredTokenNeedsProfileValidationAnd401ClearsIt() {
        val state = SessionState(initialToken = "stored")
        assertFalse(state.validated)
        val credential = state.credential()!!
        assertTrue(state.validate(credential.generation, credential.token, user))
        assertTrue(state.validated)
        state.invalidate(credential)
        assertNull(state.accessToken)
        assertFalse(state.validated)
    }

    @Test fun stale401CannotClearANewerLogin() {
        val state = SessionState()
        val first = state.beginAuthentication()
        assertTrue(state.authenticate(first, "old", user))
        val stale = state.credential()!!
        val second = state.beginAuthentication()
        assertTrue(state.authenticate(second, "new", user))
        state.invalidate(stale)
        assertEquals("new", state.accessToken)
        assertTrue(state.validated)
    }
}
