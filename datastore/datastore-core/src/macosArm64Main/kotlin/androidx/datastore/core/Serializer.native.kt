/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.datastore.core

import okio.BufferedSink
import okio.BufferedSource

// TODO try to clean this up or at least have real classes.
internal fun BufferedSource.toInputStream() = object : OkioInputStream(this){}
internal fun BufferedSink.toOutputStream() = object : OkioOutputStream(this){}
public actual abstract class InputStream(
    private val delegate: okio.BufferedSource
) {
    @Throws(IOException::class)
    actual abstract fun read(): Int

}

actual abstract class OutputStream(
    private val delegate: okio.BufferedSink
) {
    @Throws(IOException::class)
    actual abstract fun write(var1: Int)

}

internal open class OkioInputStream(private val delegate: okio.BufferedSource)
    : InputStream(delegate) {
    @Throws(IOException::class)
    override fun read(): Int {
        return delegate.readInt()
    }
}

internal open class OkioOutputStream(private val delegate: okio.BufferedSink)
    : OutputStream(delegate) {
    @Throws(IOException::class)
    override fun write(var1: Int) {
        delegate.writeInt(var1)
    }
}


actual typealias IOException = okio.IOException