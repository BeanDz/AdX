package dev.doji.adx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class TimelineFilterTest {
    private val filter =
        TimelineFilter(
            moduleClass = Module::class.java,
            moduleItemClass = ModuleItem::class.java,
            promotedUserClass = User::class.java,
            promotedMetadataMethod =
                TimelineFilterTest::class.java.getDeclaredMethod(
                    "promotedMetadata",
                    Item::class.java,
                ),
        )

    @Test
    fun `removes promoted posts and users while preserving order and source`() {
        val post = Post(null)
        val user = User(null)
        val promotedPost = Post(Metadata())
        val promotedUser = User(Metadata())
        val source = listOf(promotedPost, post, promotedUser, user)

        assertEquals(listOf(post, user), filter.filter(source))
        assertEquals(listOf(promotedPost, post, promotedUser, user), source)
    }

    @Test
    fun `copies mixed modules and preserves other fields and retained wrappers`() {
        val post = ModuleItem(Post(null), true)
        val user = ModuleItem(User(null), false)
        val promotedPost = ModuleItem(Post(Metadata()), false)
        val promotedUser = ModuleItem(User(Metadata()), true)
        val content = listOf(promotedPost, post, promotedUser, user)
        val module = Module(content, 42, "recommendations")

        val result = filter.filter(listOf(module)).single() as Module

        assertNotSame(module, result)
        assertEquals(listOf(post, user), result.content)
        assertEquals(module.id, result.id)
        assertEquals(module.label, result.label)
        assertEquals(2, result.cachedContentSize)
        assertSame(content, module.content)
        assertEquals(listOf(promotedPost, post, promotedUser, user), module.content)
        assertEquals(4, module.cachedContentSize)
    }

    @Test
    fun `removes all promoted modules and reuses unchanged modules`() {
        val promoted =
            Module(
                listOf(ModuleItem(Post(Metadata()), false), ModuleItem(User(Metadata()), false)),
                1,
                "promoted",
            )
        val ordinary = Module(listOf(ModuleItem(Post(null), true)), 2, "ordinary")

        val result = filter.filter(listOf(promoted, ordinary))

        assertSame(ordinary, result.single())
        assertEquals(2, promoted.content.size)
    }

    @Test
    fun `propagates accessor failures without mutating source modules`() {
        val failure = IllegalStateException("metadata unavailable")
        val module =
            Module(
                listOf(ModuleItem(Post(Metadata()), false), ModuleItem(FailedItem(failure), false)),
                3,
                "failed",
            )
        val source = listOf(module)
        val content = module.content

        val thrown = assertThrows(InvocationTargetException::class.java) { filter.filter(source) }

        assertSame(failure, thrown.targetException)
        assertSame(module, source.single())
        assertSame(content, module.content)
        assertEquals(2, module.content.size)
        assertEquals(2, module.cachedContentSize)
    }

    private interface Item

    private class Metadata

    private class Post(
        val metadata: Metadata?,
    ) : Item

    private class User(
        val metadata: Metadata?,
    ) : Item

    private class FailedItem(
        val failure: IllegalStateException,
    ) : Item

    private class ModuleItem(
        val item: Item,
        val flag: Boolean,
    )

    private class Module(
        val content: List<ModuleItem>,
        val id: Long,
        val label: String,
    ) : Item {
        val cachedContentSize = content.size
    }

    companion object {
        @JvmStatic
        private fun promotedMetadata(item: Item): Metadata? =
            when (item) {
                is Post -> item.metadata
                is FailedItem -> throw item.failure
                else -> null
            }
    }
}
