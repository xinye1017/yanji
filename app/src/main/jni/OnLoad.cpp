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

#include <rnworklets.h>
#include <rnreanimated.h>
#include <rnsvg.h>
#include <safeareacontext.h>

#include <react/renderer/components/rnworklets/ComponentDescriptors.h>
#include <react/renderer/components/rnreanimated/ComponentDescriptors.h>
#include <react/renderer/components/rnsvg/ComponentDescriptors.h>
#include <react/renderer/components/safeareacontext/ComponentDescriptors.h>

namespace facebook::react {

void registerComponents(
    std::shared_ptr<const ComponentDescriptorProviderRegistry> registry) {
  // Custom Fabric components registration

  // Fabric components coming from the autolinked RN libraries. Without these the
  // renderer cannot resolve RNSVGSvgView / RNCSafeAreaProvider and friends.
  rnworklets_registerComponentDescriptorsFromCodegen(registry);
  rnreanimated_registerComponentDescriptorsFromCodegen(registry);
  rnsvg_registerComponentDescriptorsFromCodegen(registry);
  safeareacontext_registerComponentDescriptorsFromCodegen(registry);
}

std::shared_ptr<TurboModule> cxxModuleProvider(
    const std::string& name,
    const std::shared_ptr<CallInvoker>& jsInvoker) {
  // Here you can provide your CXX Turbo Modules coming from either your
  // application or from external libraries.
  return nullptr;
}

std::shared_ptr<TurboModule> javaModuleProvider(
    const std::string& name,
    const JavaTurboModule::InitParams& params) {
  // Here you can provide your own module provider for TurboModules coming from
  // either your application or from external libraries.

  // We first try to look up core modules
  if (auto module = FBReactNativeSpec_ModuleProvider(name, params)) {
    return module;
  }

  // And we fallback to the module providers autolinked
  if (auto module = rnworklets_ModuleProvider(name, params)) {
    return module;
  }
  if (auto module = rnreanimated_ModuleProvider(name, params)) {
    return module;
  }
  if (auto module = rnsvg_ModuleProvider(name, params)) {
    return module;
  }
  if (auto module = safeareacontext_ModuleProvider(name, params)) {
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
