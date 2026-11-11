package be.orbinson.felix.bundle.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DecompiledLineMappingTest {

    private fun parse(text: String) = DecompiledLineMapping.parse(text.trimIndent().reader().buffered())

    @Test
    fun `maps every original line marker to its decompiled line`() {
        val mapping = parse(
            """
            $DECOMPILED_BANNER
            package foo;

            public class Foo {
                public void bar() {
                    String url = "http://example.com";// 53
                    call(url);// 54 55
                    // not a marker
                }
            }
            """
        )
        assertArrayEquals(intArrayOf(53, 6, 54, 7, 55, 7), mapping)
    }

    @Test
    fun `realign moves statements onto their original lines with gaps between members`() {
        val realigned = DecompiledLineMapping.realign(
            listOf(
                DECOMPILED_BANNER,
                "class Foo {",
                "    void a() {",
                "        x();// 5",
                "    }",
                "",
                "    void b() {",
                "        y();// 12",
                "    }",
                "}",
            )
        )
        assertEquals("        x();// 5", realigned[4])
        assertEquals("    void b() {", realigned[10])
        assertEquals("        y();// 12", realigned[11])
        // Gap goes at the blank separator, not between the method signature and its first statement.
        assertEquals("    }", realigned[5])
        assertTrue(realigned.subList(6, 10).all { it.isEmpty() })
    }

    @Test
    fun `realign never moves statements up`() {
        val lines = listOf(DECOMPILED_BANNER, "class Foo {", "    int a = 1;// 2", "    int b = 2;// 2", "}")
        val realigned = DecompiledLineMapping.realign(lines)
        assertEquals(lines, realigned)
    }

    @Test
    fun `realign drops blank lines and joins closing braces to move statements up`() {
        val realigned = DecompiledLineMapping.realign(
            listOf(
                DECOMPILED_BANNER,
                "class Foo {",
                "    void a() {",
                "        if (x) {",
                "            y();// 5",
                "        }",
                "",
                "        z();// 6",
                "    }",
                "}",
            )
        )
        assertEquals("            y(); }// 5", realigned[4])
        assertEquals("        z();// 6", realigned[5])
    }

    @Test
    fun `realign does not join lines when the statement still would not land on its line`() {
        // y() wants line 2, but only "void a() {" can be joined (the banner is a comment): one line short.
        val lines = listOf(DECOMPILED_BANNER, "class Foo {", "    void a() {", "        y();// 2", "    }", "}")
        assertEquals(lines, DecompiledLineMapping.realign(lines))
    }

    @Test
    fun `realign leaves text blocks intact`() {
        val lines = listOf(DECOMPILED_BANNER, "class Foo {", "    String s = \"\"\"", "", "        a", "        \"\"\";// 3",
            "}")
        assertEquals(lines, DecompiledLineMapping.realign(lines))
    }

    @Test
    fun `ignores sources without the decompiler banner`() {
        val mapping = parse(
            """
            package foo;
            class Foo { int x = 1;// 12
            }
            """
        )
        assertArrayEquals(IntArray(0), mapping)
    }
}
