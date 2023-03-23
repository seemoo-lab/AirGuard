package de.seemoo.at_tracking_detection

import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.activityScenarioRule
import de.seemoo.at_tracking_detection.ui.MainActivity
import org.hamcrest.CoreMatchers.*
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class DevicesTabUITest {


    /**
     * Use [ActivityScenarioRule] to create and launch the activity under test before each test,
     * and close it after each test. This is a replacement for
     * [androidx.test.rule.ActivityTestRule].
     */
    @get:Rule
    var activityScenarioRule = activityScenarioRule<MainActivity>()

    @Test
    fun openDevicesTab() {

        onView(withId(R.id.navigation_allDevicesFragment))
            .perform(click())

        onView(withId(R.id.tracker_devices_card))
            .perform(click())
        openFirstItem()

        onView(withId(R.id.ignored_devices_card))
            .perform(click())
        onView(withId(R.id.devices_recycler_view)).perform(ViewActions.pressBack())

        onView(withId(R.id.airtags_found_card))
            .perform(click())
        openFirstItem()

        onView(withId(R.id.findmy_found_card))
            .perform(click())
        openFirstItem()

        onView(withId(R.id.tiles_found_card))
            .perform(click())
        openFirstItem()

        onView(withId(R.id.smarttags_found_card))
            .perform(click())
        openFirstItem()

        onView(withId(R.id.all_devices_card))
            .perform(click())
        openFirstItem()
    }

    fun openFirstItem() {

        onView(withId(R.id.devices_recycler_view)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
        Thread.sleep(1000)
        //Back to the list
        onView(withId(R.id.tracking_tiles)).perform(ViewActions.pressBack())
        Thread.sleep(500)
        //Back to devices tab
        onView(withId(R.id.filter_fragment)).perform(ViewActions.pressBack())
    }

    @Test
    fun testFilters() {
        onView(withId(R.id.navigation_allDevicesFragment))
            .perform(click())

        onView(withId(R.id.all_devices_card))
            .perform(click())

        onView(withId(R.id.filter_button))
            .perform(click())

        onView(allOf(withText("Device Types"))).check(matches(isDisplayed()))

        //Disabling all devices
        onView(withText("AirTag")).perform(click())
        onView(withText("FindMy Device")).perform(click())
        onView(withText("AirPods")).perform(click())
        onView(withText("Apple Device")).perform(click())
        onView(withText("Galaxy SmartTag")).perform(click())
        onView(withText("Tile")).perform(click())


        onView(withText("Show all devices")).check(matches(isDisplayed()))

        //Enabling all devices
        onView(withText("AirTag")).perform(click())
        onView(withText("FindMy Device")).perform(click())
        onView(withText("AirPods")).perform(click())
        onView(withText("Apple Device")).perform(click())
        onView(withText("Galaxy SmartTag")).perform(click())
        onView(withText("Tile")).perform(click())

        onView(withText("identified trackers")).perform(click())
        onView(withText("identified trackers")).perform(click())

        onView(withText("ignored")).perform(click())
        onView(withText("ignored")).perform(click())

        onView(withId(R.id.filter_button))
            .perform(click())

//        Thread.sleep(500)
//        onView(allOf(withText("Device Types"))).check(doesNotExist())

    }
}