package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.MusicViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Musicly", appName)
  }

  @Test
  fun `test viewModel initialization`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MusicViewModel(app)
    assertNotNull(viewModel)
  }

  @Test
  fun `test mainActivity lifecycle`() {
    try {
      val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
      val activity = controller.get()
      assertNotNull(activity)
    } catch (e: Throwable) {
      e.printStackTrace()
      throw e
    }
  }
}
