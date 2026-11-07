package io.github.neatbrowser.search.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFoldTest {
    @Test fun removesVietnameseAccents() {
        assertEquals("doc bao moi", TextFold.fold("Đọc báo mới"))
        assertEquals("nguyen van a", TextFold.fold("NGUYỄN VĂN A"))
    }

    @Test fun handlesPrecomposedAndDecomposedInput() {
        assertEquals("cafe", TextFold.fold("café"))
        assertEquals("cafe", TextFold.fold("café"))
    }

    @Test fun leavesPlainTextAlone() {
        assertEquals("hello world 123", TextFold.fold("Hello World 123"))
    }
}
