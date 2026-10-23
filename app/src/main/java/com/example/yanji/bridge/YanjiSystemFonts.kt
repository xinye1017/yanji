package com.example.yanji.bridge

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import com.facebook.react.common.assets.ReactFontManager
import java.io.File

/**
 * 把设备上的**中文衬线**字体交给 React Native，让 `fontFamily: 'serif'` 真正排出宋体。
 *
 * 为什么需要这一步：Android 的 `serif` 家族在 AOSP 里是 Noto Serif，而 ColorOS
 * 的字体配置里 `zh-Hans` 只在 **400 一档**挂了 `fallbackFor="serif"`
 * （`/system/etc/fonts.xml`），其余权重仍回落到 OPPO Sans。于是同一页里标题会一半
 * 衬线一半无衬线，中文正文更是整段无衬线——这正是"字体不对"的观感来源。
 *
 * 设备上其实装着一套完整的中文衬线：`/system/fonts/Oplus-Serif.ttf`
 * （postScriptName `OPPOSerifSC`，可变字体，wght 100–1000），ColorOS 把它注册为
 * `sys-serif` 家族。这里读该文件并注册成 RN 的 `serif` 家族，权重由
 * [ReactFontManager] 用 `Typeface.create(base, weight, italic)` 继续细分。
 *
 * 非 OPPO 设备没有这个文件，注册直接跳过：`serif` 保持系统默认，不会出现伪造字体。
 * 文件缺失、不可读或解析失败都只降级、不崩溃——字体是外观，不该影响启动。
 */
object YanjiSystemFonts {

    /** ColorOS 的中文衬线字面；AOSP 设备上不存在。 */
    private const val OPPO_SERIF_PATH = "/system/fonts/Oplus-Serif.ttf"

    /** RN 侧 `fontFamily: 'serif'` 对应的键，也是 [YanjiPaperTypography] 用的名字。 */
    private const val SERIF_FAMILY = "serif"

    /** 可变字体的权重梯度；与设备 fonts_base.xml 里 sys-serif 的档位一致。 */
    private val SERIF_WEIGHTS = intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)

    /**
     * 在 React Native 装载字体之前调用一次。
     *
     * [ReactFontManager.addCustomFont] 是幂等的（同键覆盖），重复调用无副作用。
     */
    fun registerSystemSerif() {
        val typeface = loadOppoSerif() ?: return
        runCatching {
            ReactFontManager.getInstance().addCustomFont(SERIF_FAMILY, typeface)
            // 设备把同一套字面注册成 sys-serif；两个键都指向它，调用方写哪个都行。
            ReactFontManager.getInstance().addCustomFont("sys-serif", typeface)
        }
    }

    /** 读出 OPPO 中文衬线；设备没有这套字体时返回 null。 */
    private fun loadOppoSerif(): Typeface? {
        val file = File(OPPO_SERIF_PATH)
        if (!file.isFile || !file.canRead()) return null
        return buildWeightFamily(file) ?: runCatching { Typeface.createFromFile(file) }.getOrNull()
    }

    /**
     * 用可变字体的每一档权重拼出一个字体家族。
     *
     * 只给单档会把 100–900 全部压到同一粗细；而 `Oplus-Serif.ttf` 的 wght 轴是真实的，
     * 逐档 [Font.Builder.setWeight] 之后，RN 的 `Typeface.create(base, weight, italic)`
     * 才能取到对应粗细。
     *
     * 需要 API 29+ 的 `android.graphics.fonts`；更低版本退回单档，字体族至少是对的。
     */
    private fun buildWeightFamily(file: File): Typeface? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val fonts = ArrayList<Font>(SERIF_WEIGHTS.size)
            for (weight in SERIF_WEIGHTS) {
                val font = runCatching { Font.Builder(file).setWeight(weight).build() }.getOrNull()
                if (font != null) fonts.add(font)
            }
            if (fonts.isEmpty()) return@runCatching null
            val familyBuilder = FontFamily.Builder(fonts[0])
            for (index in 1 until fonts.size) familyBuilder.addFont(fonts[index])
            Typeface.CustomFallbackBuilder(familyBuilder.build()).build()
        }.getOrNull()
    }
}
