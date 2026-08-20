package com.example

import com.example.data.PodcastRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastRepositoryTest {
    private val repository = PodcastRepository()

    @Test
    fun parsesHoursMinutesAndSeconds() {
        assertEquals(3723, repository.parseDurationToSeconds("1:02:03"))
    }

    @Test
    fun parsesMinutesAndSeconds() {
        assertEquals(125, repository.parseDurationToSeconds("2:05"))
    }

    @Test
    fun invalidDurationReturnsZero() {
        assertEquals(0, repository.parseDurationToSeconds("invalid"))
        assertEquals(0, repository.parseDurationToSeconds(""))
    }
}
