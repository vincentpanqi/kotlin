// !LANGUAGE: +InlineClasses

inline class UInt(val u: Int) {
    override fun toString(): String {
        return "UInt: $u"
    }
}

fun Any.isUInt(): Boolean = this is UInt
fun Any.notIsUInt(): Boolean = this !is UInt

fun UInt.extension(): String = "OK:"

fun foo(x: UInt?): String {
    if (x is UInt) {
        return x.extension() + x.toString()
    }

    return "fail"
}

fun bar(x: UInt?): String {
    if (x is Any) {
        return x.extension()
    }

    return "fail"
}

fun box(): String {
    val u = UInt(12)
    if (!u.isUInt()) return "fail"
    if (u.notIsUInt()) return "fail"

    if (1.isUInt()) return "fail"
    if (!1.notIsUInt()) return "fail"

    if (foo(u) != "OK:UInt: 12") return "fail"
    if (bar(u) != "OK:") return "fail"

    return "OK"
}