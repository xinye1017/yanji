package com.example.yanji.data

import com.example.yanji.data.db.UserSettingsDao
import com.example.yanji.data.db.UserSettingsEntity

/**
 * 应用启动时可以写入的**唯一**内容。
 *
 * 这条规则此前被违反过：旧实现按 `count() == 0` 往 `focus_sessions` /
 * `exam_sessions` / `journal_entries` / `chat_messages` / `check_ins` 里灌样例数据，
 * 于是「用户主动删光数据」与「首次安装」被当成了同一个状态——用户清空记录后一重启，
 * 假数据就"复活"，真实统计也被污染。
 *
 * 现在的约定：
 *  - 「表为空」是**合法业务状态**，不是「需要初始化」；
 *  - 只有系统配置默认值（[UserSettingsEntity]）允许在缺失时补齐。
 *
 * 抽成独立的类是为了让「不写业务数据」这条不变量可以被插桩测试直接验证
 * （对着一次性数据库跑，而不是对着用户的真实库）。
 */
internal class AppInitializer(private val settingsDao: UserSettingsDao) {

    suspend fun initialize() {
        if (settingsDao.count() == 0) {
            settingsDao.saveSettings(UserSettingsEntity.fromDomainModel(UserSettings()))
        }
        // 刻意什么都不写：
        // 没有专注记录 / 没有模考 / 没有日记 / 没有对话 / 没有打卡 —— 都是正常状态。
    }
}
