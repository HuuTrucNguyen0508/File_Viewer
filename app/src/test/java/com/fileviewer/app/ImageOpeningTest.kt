package com.fileviewer.app

import android.content.ContextWrapper
import com.fileviewer.app.viewers.image.imageRequest
import org.junit.Assert.*
import org.junit.Test

class ImageOpeningTest {
    @Test fun sameUriUsesDifferentMemoryIdentityForEachOpening() {
        val context = ContextWrapper(null)
        val old = imageRequest(context, "content://provider/1", "opening:old") {}
        val fresh = imageRequest(context, "content://provider/1", "opening:new") {}
        assertEquals(old.data, fresh.data)
        assertEquals("opening:old", old.memoryCacheKey)
        assertEquals("opening:new", fresh.memoryCacheKey)
    }
}
