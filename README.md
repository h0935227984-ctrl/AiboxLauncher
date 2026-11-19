# AIBOX 桌面 (輕量 Launcher)

## 編譯
Android Studio → Open 此資料夾 → 等 Gradle Sync → Build > Build APK(s)

## 安裝並設為預設桌面
adb install -r app-release.apk
按 Home 鍵 → 選「AIBOX 桌面」→「一律」(選「僅此一次」則每次按 Home 都會問)

## 功能
- 時鐘:長按進入編輯,拖曳移動、拉右下角圓點縮放、「重設」回預設、「完成」結束
- 右上角設定:分割管理、桌布(圖片 / MP4)、背景效果(虛化 / 調暗)、還原桌布、時鐘編輯、虛擬導覽列
- 分割管理:選上下兩個 App 存成資料夾方塊,點一下以自由視窗各佔一半(需開啟開發人員選項的「自由格式視窗」與「強制調整大小」)
- 虛擬導覽列:碰螢幕才出現,5 秒後淡出;需到「設定 > 無障礙」開啟 AIBOX 桌面

## 虛擬導覽列大小
尺寸以「螢幕短邊的 1%」為單位自適應。想再調整,改 NavOverlay.kt 最下方的 ICON_U 等常數。

## 找出原廠桌面套件名稱 / 停用(進階,可逆)
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell pm disable-user --user 0 <原廠桌面套件名>
adb shell pm enable <原廠桌面套件名>
