package com.example.finalproject_demo

import com.example.finalproject_demo.demo.scene.SceneKits
import org.junit.rules.ExternalResource

/**
 * Turns the felt scene kit off for one test class — for tests of the generated background during the session
 * (`StoryLiveFlow.updateBackground`), the documented alternative that the kit replaces by default (10-05).
 */
class SceneKitOff : ExternalResource() {
    private var before = true
    override fun before() { before = SceneKits.liveStory; SceneKits.liveStory = false }
    override fun after() { SceneKits.liveStory = before }
}
