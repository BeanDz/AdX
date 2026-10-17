package dev.doji.adx

import org.junit.Assert.assertEquals
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
    fun `drops modules containing promoted items and reuses unchanged modules`() {
        val promoted =
            Module(
                listOf(ModuleItem(Post(Metadata())), ModuleItem(User(Metadata()))),
            )
        val ordinary = Module(listOf(ModuleItem(Post(null))))

        val result = filter.filter(listOf(promoted, ordinary))

        assertSame(ordinary, result.single())
        assertEquals(2, promoted.content.size)
    }

    @Test
    fun `drops a mixed module instead of only its promoted entries`() {
        val organic = ModuleItem(Post(null))
        val promoted = ModuleItem(Post(Metadata()))
        val module = Module(listOf(organic, promoted))

        assertEquals(emptyList<Any>(), filter.filter(listOf(module)))
        assertEquals(listOf(organic, promoted), module.content)
    }

    @Test
    fun `propagates accessor failures without mutating source modules`() {
        val failure = IllegalStateException("metadata unavailable")
        val module =
            Module(
                listOf(ModuleItem(Post(null)), ModuleItem(FailedItem(failure))),
            )
        val source = listOf(module)
        val content = module.content

        val thrown = assertThrows(InvocationTargetException::class.java) { filter.filter(source) }

        assertSame(failure, thrown.targetException)
        assertSame(module, source.single())
        assertSame(content, module.content)
        assertEquals(2, module.content.size)
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
    )

    private class Module(
        val content: List<ModuleItem>,
    ) : Item

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
