package com.example.frigus_mobile

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.UserResponse
import com.example.frigus_mobile.ui.home.MainActivity
import com.example.frigus_mobile.ui.login.LoginActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionAccessTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun resetProcessState() {
        SessionManager::class.java.getDeclaredField("sharedState").apply { isAccessible = true }.set(null, null)
    }
    @Before fun before() { SessionManager(context).clearSession(); resetProcessState() }
    @After fun after() { SessionManager(context).clearSession(); resetProcessState() }

    @Test fun legacyCredentialsAreDiscardedAndAccessIsEncrypted() {
        context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit().putString("accessToken", "core-token").commit()
        val session = SessionManager(context)
        assertTrue(context.getSharedPreferences("auth", Context.MODE_PRIVATE).all.isEmpty())
        val generation = session.state.beginAuthentication()
        assertTrue(session.state.authenticate(generation, "secret-access", UserResponse(name = "Pessoa")))
        val stored = context.getSharedPreferences(SessionManager.PREF_NAME, Context.MODE_PRIVATE).all
        assertFalse(stored.values.any { it.toString().contains("secret-access") })
        assertFalse(stored.containsKey("refreshToken"))
        resetProcessState()
        val restored = SessionManager(context)
        assertEquals("secret-access", restored.accessToken)
        assertFalse(restored.isLoggedIn)
        assertNull(restored.state.user?.birthDate)
    }

    @Test fun invalidCiphertextFailsClosed() {
        context.getSharedPreferences(SessionManager.PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString("origin", BuildConfig.BFF_BASE_URL).putString("access", "bad").putString("iv", "bad").commit()
        val restored = SessionManager(context)
        assertNull(restored.accessToken)
        assertFalse(restored.isLoggedIn)
    }

    @Test fun loginSurvivesRotationWithoutStartingLegacyFeatures() {
        ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { assertNotNull(it.findViewById<android.view.View>(R.id.btnEntrar)) }
        }
    }

    @Test fun authenticatedLandingSurvivesRotationAndLogoutClearsTask() {
        val session = SessionManager(context)
        session.state.authenticate(session.state.beginAuthentication(), "access", UserResponse(name = "Pessoa"))
        val monitor = instrumentation.addMonitor(LoginActivity::class.java.name, null, false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<android.view.View>(R.id.sessionIdentity))
                activity.findViewById<android.view.View>(R.id.btnSessionLogout).performClick()
            }
            val login = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
            assertNotNull(login)
            assertFalse(session.isLoggedIn)
            instrumentation.runOnMainSync { login?.finish() }
        }
        instrumentation.removeMonitor(monitor)
    }

    @Test fun mainWithoutSessionRedirectsToLogin() {
        val monitor = instrumentation.addMonitor(LoginActivity::class.java.name, null, false)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val login = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
        assertNotNull(login)
        instrumentation.runOnMainSync { login?.finish() }
        instrumentation.removeMonitor(monitor)
    }
}
