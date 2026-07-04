package com.ethiopialibrary.app.sync

import com.ethiopialibrary.app.R
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
class RestoreErrorsTest {

    @Test
    fun `permission denied maps to the locked-account message`() {
        val error = FirebaseFirestoreException(
            "denied",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )

        assertEquals(R.string.restore_permission_denied, restoreFailureMessageRes(error))
    }

    @Test
    fun `network problems map to the connectivity message`() {
        assertEquals(
            R.string.restore_network_error,
            restoreFailureMessageRes(UnknownHostException("offline")),
        )
        assertEquals(
            R.string.restore_network_error,
            restoreFailureMessageRes(
                FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE),
            ),
        )
    }

    @Test
    fun `anything else maps to the generic restore failure`() {
        assertEquals(R.string.restore_failed, restoreFailureMessageRes(RuntimeException("boom")))
    }
}
