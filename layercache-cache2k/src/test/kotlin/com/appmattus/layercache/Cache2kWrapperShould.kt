/*
 * Copyright 2017 Appmattus Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appmattus.layercache

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.cache2k.Cache2kBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.util.concurrent.TimeUnit


class Cache2kWrapperShould {

    @Mock
    private lateinit var cache2k: org.cache2k.Cache<String, String>

    private lateinit var wrappedCache: Cache<String, String>

    private lateinit var integratedCache: Cache<String, String>

    @Mock
    private lateinit var loaderFetcher: AbstractFetcher<String, String>

    private lateinit var integratedCacheWithLoader: Cache<String, String>

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        wrappedCache = Cache2kWrapper(cache2k)

        val cache2k = object : Cache2kBuilder<String, String>() {}
                // expire/refresh after 5 minutes
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build()
        integratedCache = Cache.fromCache2k(cache2k)


        Mockito.`when`(loaderFetcher.get(Mockito.anyString())).then { async(CommonPool) { "hello" } }

        val cache2kWithLoader = object : Cache2kBuilder<String, String>() {}
                .expireAfterWrite(5, TimeUnit.MINUTES)    // expire/refresh after 5 minutes
                // exceptions
                .refreshAhead(true)                       // keep fresh when expiring
                .loader(loaderFetcher.toCache2kLoader())         // auto populating function
                .build()
        integratedCacheWithLoader = Cache.fromCache2k(cache2kWithLoader)
    }

    // get
    @Test
    fun `get returns value from cache`() {
        runBlocking {
            // given value available in first cache only
            Mockito.`when`(cache2k.get("key")).thenReturn("value")

            // when we get the value
            val result = wrappedCache.get("key").await()

            // then we return the value
            assertEquals("value", result)
        }
    }

    @Test(expected = TestException::class)
    fun `get throws`() {
        runBlocking {
            // given value available in first cache only
            Mockito.`when`(cache2k.get("key")).then { throw TestException() }

            // when we get the value
            wrappedCache.get("key").await()

            // then we throw an exception
        }
    }

    // set
    @Test
    fun `set returns value from cache`() {
        runBlocking {
            // given

            // when we set the value
            wrappedCache.set("key", "value").await()

            // then put is called
            Mockito.verify(cache2k).put("key", "value")
        }
    }

    @Test(expected = TestException::class)
    fun `set throws`() {
        runBlocking {
            // given value available in first cache only
            Mockito.`when`(cache2k.put("key", "value")).then { throw TestException() }

            // when we get the value
            wrappedCache.set("key", "value").await()

            // then we throw an exception
        }
    }

    // evict
    @Test
    fun `evict returns value from cache`() {
        runBlocking {
            // given

            // when we get the value
            wrappedCache.evict("key").await()

            // then we return the value
            //assertEquals("value", result)
            Mockito.verify(cache2k).remove("key")
        }
    }

    @Test(expected = TestException::class)
    fun `evict throws`() {
        runBlocking {
            // given value available in first cache only
            Mockito.`when`(cache2k.remove("key")).then { throw TestException() }

            // when we get the value
            wrappedCache.evict("key").await()

            // then we throw an exception
        }
    }

    @Test
    fun `return null when the cache is empty`() {
        runBlocking {
            // given we have an empty cache
            // integratedCache

            // when we retrieve a value
            val result = integratedCache.get("key").await()

            // then it is null
            assertNull(result)
        }
    }

    @Test
    fun `return value when cache has value`() {
        runBlocking {
            // given we have a cache with a value
            integratedCache.set("key", "value").await()

            // when we retrieve a value
            val result = integratedCache.get("key").await()

            // then it is returned
            assertEquals("value", result)
        }
    }

    @Test
    fun `return null when the key has been evicted`() {
        runBlocking {
            // given we have a cache with a value
            integratedCache.set("key", "value").await()

            // when we evict the value
            integratedCache.evict("key").await()

            // then the value is null
            assertNull(integratedCache.get("key").await())
        }
    }


    @Test
    fun `return from loader when the cache is empty`() {
        runBlocking {
            // given we have an empty cache
            // integratedCacheWithLoader

            // when we retrieve a value
            val result = integratedCacheWithLoader.get("key").await()

            // then the value comes from the loader
            assertEquals("hello", result)
        }
    }

    @Test
    fun `return value and not from loader when cache has value`() {
        runBlocking {
            // given we have a cache with a value
            integratedCacheWithLoader.set("key", "value").await()

            // when we retrieve a value
            val result = integratedCacheWithLoader.get("key").await()

            // then it is returned
            assertEquals("value", result)
        }
    }

    @Test
    fun `return value from loader when the key has been evicted`() {
        runBlocking {
            // given we have a cache with a value
            integratedCacheWithLoader.set("key", "value").await()

            // when we evict the value
            integratedCacheWithLoader.evict("key").await()

            // then the value comes from the loader
            assertEquals("hello", integratedCacheWithLoader.get("key").await())
        }
    }
}
