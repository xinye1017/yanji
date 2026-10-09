/*
 * folly F14 探针符号的唯一定义。
 *
 * 背景：libreactnative.so 由 RN 自己构建，内部链的是 folly_runtime 静态库，
 * folly::f14::detail::F14LinkCheck<Mode>::check() 的定义在 folly 的
 * F14Table.cpp 里。但 ReactAndroid 的 prefab 包并不导出这个符号（RN 自身构建开了
 * LTO，F14Table 拷贝构造 / rehash 的 catch(...) 冷路径被消掉后符号就没了）。
 *
 * 后果：任何自己编译 RN Fabric 头文件的 .so（本工程的 libappmodules.so）都会在
 * 链接期缺这三个 folly::f14::detail 符号：
 *   - F14LinkCheck<(F14IntrinsicsMode)1>::check()
 *   - tlsMinstdRand / tlsPendingSafeInserts（仅 folly::kIsDebug 为真时引用）
 * 后两个由本工程 CMakeLists 里的 -DNDEBUG 关掉（folly::kIsDebug 就是 !NDEBUG，
 * 关掉后 F14Table 的调试用插入扰动代码不再实例化，ABI 不受影响）。
 *
 * 第一个不行：F14Table 在 catch(...) 冷路径上无条件调用它，与 kIsDebug 无关，
 * 只能由使用方提供这唯一一份定义。这本来就是 folly 的设计意图——它是个探针，
 * 「编译选项跨编译单元不一致就让链接失败」。本库通过
 * target_compile_reactnative_options 与 libreactnative.so 使用同一套编译宏，
 * 探针通过，所以这里给空实现即可，不需要复刻 folly 的任何行为。
 */

#include <folly/container/detail/F14Table.h>

namespace folly::f14::detail {

void F14LinkCheck<getF14IntrinsicsMode()>::check() noexcept {}

} // namespace folly::f14::detail
