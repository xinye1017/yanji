/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#include <DefaultComponentsRegistry.h>
#include <DefaultTurboModuleManagerDelegate.h>
#include <FBReactNativeSpec.h>
#include <fbjni/fbjni.h>
#include <react/renderer/componentregistry/ComponentDescriptorProviderRegistry.h>

namespace facebook::react {

void registerComponents(
    std::shared_ptr<const ComponentDescriptorProviderRegistry> registry) {
  // Custom Fabric components registration
}

std::shared_ptr<TurboModule> cxxModuleProvider(
    const std::string& name,
    const std::shared_ptr<CallInvoker>& jsInvoker) {
  return nullptr;
}

std::shared_ptr<TurboModule> javaModuleProvider(
    const std::string& name,
    const JavaTurboModule::InitParams& params) {
  if (auto module = FBReactNativeSpec_ModuleProvider(name, params)) {
    return module;
  }
  return nullptr;
}

} // namespace facebook::react

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  return facebook::jni::initialize(vm, [] {
    facebook::react::DefaultTurboModuleManagerDelegate::cxxModuleProvider =
        &facebook::react::cxxModuleProvider;
    facebook::react::DefaultTurboModuleManagerDelegate::javaModuleProvider =
        &facebook::react::javaModuleProvider;
    facebook::react::DefaultComponentsRegistry::
        registerComponentDescriptorsFromEntryPoint =
            &facebook::react::registerComponents;
  });
}
