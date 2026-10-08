import './global.css';
import React from 'react';
import { SafeAreaView, StatusBar, Text, View } from 'react-native';

export default function App(): React.JSX.Element {
  return (
    <SafeAreaView className="flex-1 bg-slate-900 justify-center items-center">
      <StatusBar barStyle="light-content" />
      <View className="p-6 rounded-2xl bg-slate-800 items-center">
        <Text className="text-2xl font-bold text-slate-100">研迹 (Yanji)</Text>
        <Text className="text-sm text-slate-400 mt-2">
          React Native + NativeWind Add-to-App
        </Text>
      </View>
    </SafeAreaView>
  );
}
