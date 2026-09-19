# IP as Logo 学习伙伴素材来源

本文件记录“卷卷”学习伙伴主题中来自 IP as Logo 官网素材库的角色资源。

## 使用原则

- 不重新绘制官网角色，不改变角色主体造型。
- 仅对官网 PNG 做背景去除与透明边缘裁切，以适配 App 内头像与学习伙伴选择器。
- App 内最终只展示角色主体与本地中文名称。
- 原始官网 PNG 临时下载到 build/ipaslogo，不进入正式资源包。
- 正式资源位于 app/src/main/res/drawable-nodpi/。

## 素材映射

| App 名称 | IP as Logo 条目 | 官网原始素材 |
| --- | --- | --- |
| 卷卷 | Cloud Navy | https://cdn.ipaslogo.com/logos/948843a6321049ab-cloud-navy.png |
| 绵绵 | Fluffy Cream Rabbit | https://cdn.ipaslogo.com/logos/e4a814eb9f5c8bf6-fluffy-cream-rabbit.png |
| 冰冰 | Baby Penguin | https://cdn.ipaslogo.com/logos/d5ae06131a75419e-baby-penguin.png |
| 豆豆 | Shiba Inu Puppy | https://cdn.ipaslogo.com/logos/630b50da9340fd21-shiba-inu-puppy.png |
| 芽芽 | Baby Frog | https://cdn.ipaslogo.com/logos/89fcc0a537b2ae23-baby-frog.png |

## 处理说明

静态 PNG 通过 Pillow 去除纯色背景并保留透明主体。卷卷的主题选择动画改为基于 Cloud Navy 原图生成的 6 张连续关键帧，再合成为循环 GIF；GIF 属于基于原始 IP 素材制作的动画衍生资源，不再使用局部切层动画。其他伙伴暂时保留当前实现，后续逐个替换为同类关键帧动画。

IP as Logo 项目主页说明其官网素材可免费下载并用于商业用途。若未来官网授权条款变化，应重新核对最新说明。
