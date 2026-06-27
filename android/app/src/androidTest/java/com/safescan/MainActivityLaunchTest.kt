package com.safescan

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    @Test
    fun testActivityLaunchAndStayResumed() {
        // Launch the MainActivity to verify no crash on startup
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            assertNotNull("Activity should be launched successfully", activity)
        }
        
        // Wait a few seconds to let startup and initializations complete
        Thread.sleep(3000)
        
        // Check if the activity is still resumed without crashing
        assertTrue(scenario.state.isAtLeast(Lifecycle.State.RESUMED))
        
        scenario.close()
    }
}
