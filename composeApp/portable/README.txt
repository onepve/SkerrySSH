Skerry 便携模式数据目录
======================

本目录是便携模式的数据存放位置。请完整保留此目录，不要删除、不要改名。

1. 保留此目录
   所有应用数据（保险库、密钥、配置）都保存在此处。
   适合放在 U 盘里随身携带，或者解压即用。

2. 删除此目录
   删除后，应用自动切换回标准模式，数据改存在系统用户目录下：
     Windows:  %LOCALAPPDATA%\Skerry\config
     Linux:    ~/.config/skerry

3. 提示
   首次启动时本目录可以为空，数据会自动创建。

4. Linux 绿色包注意事项
   zip 在 Linux 上直接解压，文件权限完好。
   如果是在 Windows 上解压后再拷贝到 Linux，可执行权限会丢失，
   请在上一级目录执行一次下面的命令恢复：
     chmod +x skerry/skerry skerry/bin/skerry


Portable Mode Data Directory
============================

This directory holds your data in portable mode. Keep it intact — do not delete or rename it.

1. Keep this directory
   All application data (vault, keys, configuration) is stored here.
   Ideal for USB drives or portable use — extract and run.

2. Delete this directory
   Deleting it switches the app back to standard mode; data is then stored
   in your system user directory:
     Windows:  %LOCALAPPDATA%\Skerry\config
     Linux:    ~/.config/skerry

3. Tip
   The directory can be empty on first launch — data is created automatically.

4. Linux portable note
   Extracting the zip on Linux keeps file permissions intact.
   If the zip was extracted on Windows and then copied over, the executable
   bit is lost. Restore it by running the following from the parent folder:
     chmod +x skerry/skerry skerry/bin/skerry


Каталог данных портативного режима
===================================

Этот каталог — место хранения данных в портативном режиме. Сохраните его — не удаляйте и не переименовывайте.

1. Сохраните этот каталог
   Все данные приложения (хранилище, ключи, конфигурация) хранятся здесь.
   Подходит для USB-накопителей — распаковал и пользуешься.

2. Удалите этот каталог
   После удаления приложение вернётся в стандартный режим, и данные будут
   сохраняться в системном каталоге пользователя:
     Windows:  %LOCALAPPDATA%\Skerry\config
     Linux:    ~/.config/skerry

3. Совет
   При первом запуске каталог может быть пустым — данные создаются автоматически.

4. Примечание для Linux
   При распаковке zip в Linux права на файлы сохраняются.
   Если архив был распакован в Windows и скопирован на Linux, права на
   исполнение теряются. Восстановите их, выполнив из родительского каталога:
     chmod +x skerry/skerry skerry/bin/skerry
