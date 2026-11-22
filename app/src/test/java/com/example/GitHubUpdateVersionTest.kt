package com.example

import com.example.data.remote.GitHubUpdateRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateVersionTest {

  @Test
  fun isNewerVersion_handlesSemverAndPrefix() {
    assertTrue(GitHubUpdateRepository.isNewerVersion("v1.1.0", "1.0"))
    assertTrue(GitHubUpdateRepository.isNewerVersion("2.0.0", "1.9.5"))
    assertFalse(GitHubUpdateRepository.isNewerVersion("v1.0.0", "1.0"))
    assertFalse(GitHubUpdateRepository.isNewerVersion("v0.9.5", "1.0.0"))
  }
}
