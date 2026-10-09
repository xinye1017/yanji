/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.yanji;

import com.facebook.react.soloader.OpenSourceMergedSoMapping;
import com.facebook.soloader.ExternalSoMapping;

/**
 * Kotlin 无法引用 {@link OpenSourceMergedSoMapping} 的成员（它是一个带 native 方法的
 * Kotlin object，其 JVM 元数据在 Kotlin 编译期不可解析：`INSTANCE` 与各 native 方法都会报
 * Unresolved reference）。Java 侧没有这个限制，因此用 Java 薄转发层把官方实现暴露给
 * {@code YanjiApplication} 的 SoLoader 初始化。
 *
 * 这个类是必需的：RN 0.87 以 target_merge_so() 把 react_featureflagsjni 等 17 个 JNI 库
 * 合并进 libreactnative.so，APK 里不再有独立 .so。SoLoader 自带的 MergedSoMapping 是空
 * 实现（mapLibName 恒返回 null），若不提供 ExternalSoMapping，SoLoader 会按原始 soname 去
 * dlopen libreact_featureflagsjni.so，必然 UnsatisfiedLinkError 并在启动时崩溃。
 */
public class YanjiMergedSoMapping implements ExternalSoMapping {

  @Override
  public String mapLibName(String libraryName) {
    return OpenSourceMergedSoMapping.INSTANCE.mapLibName(libraryName);
  }

  @Override
  public void invokeJniOnload(String libraryName) {
    OpenSourceMergedSoMapping.INSTANCE.invokeJniOnload(libraryName);
  }
}
